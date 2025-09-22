package com.trading.marketdata.generator

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.trading.marketdata.config.MarketDataConfig
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.random.Random


//@Component
//@ConditionalOnProperty(
//    prefix = "test.order-generator",
//    name = ["enabled"],
//    havingValue = "true"
//)
class TestOrderGenerator(
    private val marketDataGenerator: MarketDataGenerator,
    private val restTemplate: RestTemplate,
    private val config: MarketDataConfig
) {
    companion object {
        private val logger = LoggerFactory.getLogger(TestOrderGenerator::class.java)
        private val TEST_USERS = listOf("USER001", "USER002", "USER003")
        private val ORDER_SIDES = listOf("BUY", "SELL")
        private val ORDER_TYPES = listOf("LIMIT", "MARKET")
        private val QUANTITIES = listOf(1, 5, 10, 20, 50, 100)
    }

    private val random = Random.Default
    private var orderCount = 0L

    @Scheduled(fixedDelayString = "\${test.order-generator.interval-ms:5000}")
    fun generateRandomOrder() {
        if (!marketDataGenerator.isRunning()) {
            logger.debug("Market data generator is not running, skipping order generation")
            return
        }

        try {
            val symbol = config.symbols.random()
            val currentPrice = marketDataGenerator.getCurrentPrice(symbol) ?: return

            val order = createRandomOrder(symbol, currentPrice)
            sendOrder(order)

        } catch (e: Exception) {
            logger.error("Failed to generate order", e)
        }
    }

    private fun createRandomOrder(symbol: String, currentPrice: BigDecimal): OrderRequest {
        val side = ORDER_SIDES.random()
        val orderType = ORDER_TYPES.random()
        val quantity = QUANTITIES.random()

        // LIMIT 주문의 경우 현재가 기준으로 ±5% 범위에서 가격 설정
        val price = if (orderType == "LIMIT") {
            val priceVariation = random.nextDouble(0.95, 1.05)
            currentPrice.multiply(BigDecimal.valueOf(priceVariation))
                .setScale(2, RoundingMode.HALF_UP)
        } else {
            null // MARKET 주문은 가격 없음
        }

        // SELL 주문은 USER001, USER002만 (이들이 주식 보유)
        val userId = if (side == "SELL") {
            listOf("USER001", "USER002").random()
        } else {
            TEST_USERS.random()
        }

        return OrderRequest(
            symbol = symbol,
            orderType = orderType,
            side = side,
            quantity = BigDecimal.valueOf(quantity.toLong()),
            price = price,
            userId = userId
        )
    }

    private fun sendOrder(order: OrderRequest) {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-User-Id", order.userId)
            set("X-Trace-Id", "test-order-${++orderCount}")
        }

        val requestBody = CreateOrderRequest(
            symbol = order.symbol,
            orderType = order.orderType,
            side = order.side,
            quantity = order.quantity,
            price = order.price
        )

        val request = HttpEntity(requestBody, headers)

        try {
            val response = restTemplate.postForEntity(
                "http://localhost:8080/api/v1/orders",
                request,
                OrderResponse::class.java
            )

            if (response.statusCode.is2xxSuccessful) {
                logger.info(
                    "✅ Order created successfully: {} {} {} @ {} for user {}",
                    order.side,
                    order.quantity,
                    order.symbol,
                    order.price ?: "MARKET",
                    order.userId
                )
                logger.debug("Order response: {}", response.body)
            }
        } catch (e: HttpClientErrorException) {
            logger.error("❌ Failed to create order: {} - {}", e.statusCode, e.responseBodyAsString)
        } catch (e: Exception) {
            logger.error("❌ Unexpected error creating order", e)
        }
    }

    fun getStatistics(): Map<String, Any> {
        return mapOf(
            "totalOrdersGenerated" to orderCount,
            "isEnabled" to true,
            "testUsers" to TEST_USERS,
            "supportedSymbols" to config.symbols
        )
    }
}

// DTOs
data class OrderRequest(
    val symbol: String,
    val orderType: String,
    val side: String,
    val quantity: BigDecimal,
    val price: BigDecimal?,
    val userId: String
)

data class CreateOrderRequest(
    val symbol: String,
    val orderType: String,
    val side: String,
    val quantity: BigDecimal,
    val price: BigDecimal?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OrderResponse(
    val orderId: String,
    val userId: String,
    val symbol: String,
    val orderType: String,
    val side: String,
    val quantity: BigDecimal,
    val price: BigDecimal?,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val filledQuantity: BigDecimal,
    val remainingQuantity: BigDecimal,
    val fillRatio: BigDecimal,
    val cancellationReason: String?,
    val version: Long
)
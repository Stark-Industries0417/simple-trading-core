package com.trading.order.domain

import com.trading.common.dto.OrderSide
import com.trading.common.dto.OrderType
import com.trading.common.exception.OrderValidationException
import com.trading.common.logging.StructuredLogger
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalTime
import java.time.ZoneId




@Component
class OrderValidator(
    private val structuredLogger: StructuredLogger,
    private val marketDataService: MarketDataService,
    private val accountService: AccountService,
    private val orderLimitService: OrderLimitService
) {
    companion object {
        private val SUPPORTED_SYMBOLS = setOf("AAPL", "GOOGL", "TSLA", "MSFT", "AMZN")
        
        private val MIN_QUANTITY = BigDecimal("0.001")
        private val MAX_QUANTITY = BigDecimal("10000.0")
        
        private val PRICE_DEVIATION_LIMIT = BigDecimal("0.10")
        
        private val MARKET_OPEN = LocalTime.of(9, 0)
        private val MARKET_CLOSE = LocalTime.of(15, 30)
        private val TIMEZONE = ZoneId.of("Asia/Seoul")
        
        private const val DAILY_ORDER_LIMIT = 100
        
        private val MARKET_ORDER_BUFFER = BigDecimal("1.10") // 10% 버퍼
    }

    fun validateOrThrow(order: Order) {
        try {
            structuredLogger.info("Starting business rules validation (fail-fast mode)", 
                mapOf(
                    "orderId" to order.id,
                    "userId" to order.userId,
                    "symbol" to order.symbol,
                    "orderType" to order.orderType.name,
                    "side" to order.side.name
                )
            )
            
            validateSymbolOrThrow(order.symbol)
            validateQuantityOrThrow(order.quantity)
            validateOrderConsistencyOrThrow(order)
            
            validateTradingHours()?.let { warning ->
                structuredLogger.warn("Trading hours warning", 
                    mapOf("orderId" to order.id, "warning" to warning)
                )
            }
            
            if (order.orderType == OrderType.LIMIT && order.price != null) {
                validatePriceRangeOrThrow(order.symbol, order.price, marketDataService)
            }
            
            validateBalanceOrThrow(order, accountService)
            validateUserLimitsOrThrow(order.userId, orderLimitService)
            
            structuredLogger.info("Business rules validation passed", 
                mapOf(
                    "orderId" to order.id, 
                    "userId" to order.userId,
                    "validationMode" to "fail-fast"
                )
            )
            
        } catch (ex: OrderValidationException) {
            structuredLogger.warn("Business rules validation failed",
                mapOf(
                    "orderId" to order.id,
                    "userId" to order.userId,
                    "error" to (ex.message ?: "Unknown error"),
                    "validationMode" to "fail-fast"
                )
            )
            throw ex
        } catch (ex: Exception) {
            structuredLogger.error("Validation system error", 
                mapOf(
                    "orderId" to order.id, 
                    "userId" to order.userId,
                    "validationMode" to "fail-fast"
                ), ex
            )
            throw OrderValidationException("Validation system error: ${ex.message}", ex)
                .withContext("orderId", order.id)
                .withContext("userId", order.userId)
        }
    }

    private fun validateTradingHours(): String? {
        val now = LocalTime.now(TIMEZONE)
        return if (now.isBefore(MARKET_OPEN) || now.isAfter(MARKET_CLOSE)) {
            "Order placed outside trading hours ($MARKET_OPEN - $MARKET_CLOSE). " +
                    "Order will be queued for next trading session."
        } else null
    }

    private fun calculateRequiredCash(order: Order, accountService: AccountService): BigDecimal {
        return when (order.orderType) {
            OrderType.MARKET -> {
                val currentPrice = accountService.getCurrentPrice(order.symbol)
                    ?: throw IllegalStateException("Market price unavailable for ${order.symbol}")
                currentPrice * order.quantity * MARKET_ORDER_BUFFER
            }
            OrderType.LIMIT -> {
                requireNotNull(order.price) { "Price required for limit order" }
                order.price * order.quantity
            }
        }
    }

    private fun validateSymbolOrThrow(symbol: String) {
        if (symbol !in SUPPORTED_SYMBOLS) {
            throw OrderValidationException(
                "Unsupported symbol: $symbol. Supported symbols: ${SUPPORTED_SYMBOLS.joinToString()}"
            ).withContext("symbol", symbol)
             .withContext("supportedSymbols", SUPPORTED_SYMBOLS.joinToString())
        }
    }
    
    private fun validateQuantityOrThrow(quantity: BigDecimal) {
        when {
            quantity < MIN_QUANTITY -> 
                throw OrderValidationException("Quantity $quantity below minimum $MIN_QUANTITY")
                    .withContext("quantity", quantity.toString())
                    .withContext("minQuantity", MIN_QUANTITY.toString())
            quantity > MAX_QUANTITY -> 
                throw OrderValidationException("Quantity $quantity exceeds maximum $MAX_QUANTITY")
                    .withContext("quantity", quantity.toString())
                    .withContext("maxQuantity", MAX_QUANTITY.toString())
        }
    }
    
    private fun validatePriceRangeOrThrow(
        symbol: String, 
        price: BigDecimal, 
        marketDataService: MarketDataService
    ) {
        try {
            val currentPrice = marketDataService.getCurrentPrice(symbol)
                ?: throw OrderValidationException("Cannot validate price: market data unavailable for $symbol")
                    .withContext("symbol", symbol)
                    .withContext("requestedPrice", price.toString())
            
            val upperLimit = currentPrice * (BigDecimal.ONE + PRICE_DEVIATION_LIMIT)
            val lowerLimit = currentPrice * (BigDecimal.ONE - PRICE_DEVIATION_LIMIT)
            
            if (price > upperLimit || price < lowerLimit) {
                throw OrderValidationException(
                    "Price $price outside allowed range [$lowerLimit - $upperLimit] " +
                    "based on current price $currentPrice (±${PRICE_DEVIATION_LIMIT.multiply(BigDecimal(100))}%)"
                ).withContext("symbol", symbol)
                 .withContext("requestedPrice", price.toString())
                 .withContext("currentPrice", currentPrice.toString())
                 .withContext("upperLimit", upperLimit.toString())
                 .withContext("lowerLimit", lowerLimit.toString())
                 .withContext("deviationLimit", PRICE_DEVIATION_LIMIT.toString())
            }
            
        } catch (ex: OrderValidationException) {
            throw ex
        } catch (ex: Exception) {
            structuredLogger.warn("Market data service error during price validation",
                mapOf(
                    "symbol" to symbol, 
                    "requestedPrice" to price.toString(),
                    "error" to (ex.message ?: "Unknown error"),
                    "exceptionType" to ex.javaClass.simpleName
                )
            )
            throw OrderValidationException("Price validation failed due to market data service error", ex)
                .withContext("symbol", symbol)
                .withContext("requestedPrice", price.toString())
        }
    }
    
    private fun validateBalanceOrThrow(order: Order, accountService: AccountService) {
        try {
            when (order.side) {
                OrderSide.BUY -> {
                    val requiredCash = calculateRequiredCash(order, accountService)
                    if (!accountService.hasSufficientCash(order.userId, requiredCash)) {
                        throw OrderValidationException("Insufficient cash balance. Required: $requiredCash")
                            .withContext("userId", order.userId)
                            .withContext("requiredCash", requiredCash.toString())
                            .withContext("orderType", order.orderType.name)
                            .withContext("side", order.side.name)
                    }
                }
                OrderSide.SELL -> {
                    if (!accountService.hasSufficientStock(order.userId, order.symbol, order.quantity)) {
                        throw OrderValidationException(
                            "Insufficient stock balance. Symbol: ${order.symbol}, Required: ${order.quantity}"
                        ).withContext("userId", order.userId)
                         .withContext("symbol", order.symbol)
                         .withContext("requiredQuantity", order.quantity.toString())
                         .withContext("side", order.side.name)
                    }
                }
            }
        } catch (ex: OrderValidationException) {
            throw ex
        } catch (ex: Exception) {
            structuredLogger.warn("Account service error during balance validation",
                mapOf(
                    "userId" to order.userId,
                    "symbol" to order.symbol,
                    "side" to order.side.name,
                    "error" to (ex.message ?: "Unknown error"),
                    "exceptionType" to ex.javaClass.simpleName
                )
            )
            throw OrderValidationException("Balance validation failed due to account service error", ex)
                .withContext("userId", order.userId)
                .withContext("symbol", order.symbol)
                .withContext("side", order.side.name)
        }
    }
    
    private fun validateUserLimitsOrThrow(userId: String, orderLimitService: OrderLimitService) {
        try {
            val dailyOrderCount = orderLimitService.getDailyOrderCount(userId)
            if (dailyOrderCount >= DAILY_ORDER_LIMIT) {
                throw OrderValidationException(
                    "Daily order limit ($DAILY_ORDER_LIMIT) exceeded. Current: $dailyOrderCount"
                ).withContext("userId", userId)
                 .withContext("dailyOrderCount", dailyOrderCount.toString())
                 .withContext("dailyOrderLimit", DAILY_ORDER_LIMIT.toString())
            }
        } catch (ex: OrderValidationException) {
            throw ex
        } catch (ex: Exception) {
            structuredLogger.warn("Order limit service error during user limits validation",
                mapOf(
                    "userId" to userId,
                    "error" to (ex.message ?: "Unknown error"),
                    "exceptionType" to ex.javaClass.simpleName
                )
            )
            throw OrderValidationException("User limits validation failed due to service error", ex)
                .withContext("userId", userId)
        }
    }
    
    private fun validateOrderConsistencyOrThrow(order: Order) {
        if (order.orderType == OrderType.LIMIT && order.price == null) {
            throw OrderValidationException("Limit order must have a price")
                .withContext("orderId", order.id)
                .withContext("orderType", order.orderType.name)
        }
        
        if (order.orderType == OrderType.MARKET && order.price != null) {
            throw OrderValidationException("Market order should not have a price")
                .withContext("orderId", order.id)
                .withContext("orderType", order.orderType.name)
                .withContext("unexpectedPrice", order.price.toString())
        }
    }
}



interface MarketDataService {
    fun getCurrentPrice(symbol: String): BigDecimal?
}




interface AccountService {
    fun hasSufficientCash(userId: String, amount: BigDecimal): Boolean
    fun hasSufficientStock(userId: String, symbol: String, quantity: BigDecimal): Boolean
    fun getCurrentPrice(symbol: String): BigDecimal?
}




interface OrderLimitService {
    fun getDailyOrderCount(userId: String): Long
}
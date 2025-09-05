package com.trading.order.integration

import com.trading.common.domain.saga.SagaStatus
import com.trading.common.dto.order.OrderSide
import com.trading.common.dto.order.OrderStatus
import com.trading.common.dto.order.OrderType
import com.trading.common.event.saga.AccountUpdatedEvent
import com.trading.common.event.saga.AccountUpdateFailedEvent
import com.trading.order.application.OrderSagaService
import com.trading.order.domain.OrderRepository
import com.trading.order.domain.saga.OrderSagaRepository
import com.trading.order.infrastructure.web.dto.CreateOrderRequest
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 1,
    brokerProperties = [
        "listeners=PLAINTEXT://localhost:9092",
        "port=9092"
    ],
    topics = ["order.events", "account.events", "saga.timeout.events"]
)
class OrderSagaIntegrationTest {
    
    @Autowired
    private lateinit var orderSagaService: OrderSagaService
    
    @Autowired
    private lateinit var orderRepository: OrderRepository
    
    @Autowired
    private lateinit var sagaRepository: OrderSagaRepository
    
    @BeforeEach
    fun setup() {
        // Clean up before each test
        sagaRepository.deleteAll()
        orderRepository.deleteAll()
    }
    
    @AfterEach
    fun tearDown() {
        sagaRepository.deleteAll()
        orderRepository.deleteAll()
    }
    
    @Test
    fun `should create order and start saga successfully`() {
        // Given
        val request = CreateOrderRequest(
            symbol = "AAPL",
            orderType = OrderType.LIMIT,
            side = OrderSide.BUY,
            quantity = BigDecimal("10"),
            price = BigDecimal("150.00")
        )
        val userId = "user123"
        val traceId = "trace123"
        
        // When
        val response = orderSagaService.createOrderWithSaga(request, userId, traceId)
        
        // Then
        assertNotNull(response)
        assertNotNull(response.orderId)
        
        // Verify order is created
        val order = orderRepository.findById(response.orderId).orElse(null)
        assertNotNull(order)
        assertEquals(OrderStatus.CREATED, order.status)
        assertEquals(userId, order.userId)
        assertEquals("AAPL", order.symbol)
        
        // Verify saga is started
        val saga = sagaRepository.findByOrderId(response.orderId)
        assertNotNull(saga)
        assertEquals(SagaStatus.STARTED, saga.state)
        assertNotNull(saga.timeoutAt)
    }
    
    @Test
    fun `should complete order when account update succeeds`() {
        // Given
        val request = CreateOrderRequest(
            symbol = "AAPL",
            orderType = OrderType.LIMIT,
            side = OrderSide.BUY,
            quantity = BigDecimal("10"),
            price = BigDecimal("150.00")
        )
        val userId = "user123"
        val traceId = "trace123"
        
        // Create order
        val response = orderSagaService.createOrderWithSaga(request, userId, traceId)
        val saga = sagaRepository.findByOrderId(response.orderId)!!
        
        // Simulate AccountUpdatedEvent
        val accountUpdatedEvent = AccountUpdatedEvent(
            eventId = "event123",
            aggregateId = "trade123",
            traceId = traceId,
            sagaId = saga.sagaId,
            tradeId = "trade123",
            orderId = response.orderId,
            buyUserId = userId,
            sellUserId = "seller123",
            amount = BigDecimal("1500.00"),
            quantity = BigDecimal("10"),
            symbol = "AAPL",
            buyerNewBalance = BigDecimal("8500.00"),
            sellerNewBalance = BigDecimal("11500.00")
        )
        
        // When
        simulateAccountUpdatedEvent(accountUpdatedEvent)
        
        // Then - wait for async processing
        await.atMost(Duration.ofSeconds(5)).until {
            val updatedOrder = orderRepository.findById(response.orderId).orElse(null)
            val updatedSaga = sagaRepository.findBySagaId(saga.sagaId)
            updatedOrder?.status == OrderStatus.COMPLETED && 
            updatedSaga?.state == SagaStatus.COMPLETED
        }
        
        // Verify final state
        val finalOrder = orderRepository.findById(response.orderId).orElse(null)
        assertEquals(OrderStatus.COMPLETED, finalOrder.status)
        assertEquals(BigDecimal("10"), finalOrder.filledQuantity)
        
        val finalSaga = sagaRepository.findBySagaId(saga.sagaId)
        assertEquals(SagaStatus.COMPLETED, finalSaga?.state)
        assertNotNull(finalSaga?.completedAt)
    }
    
    @Test
    fun `should cancel order when account update fails`() {
        // Given
        val request = CreateOrderRequest(
            symbol = "AAPL",
            orderType = OrderType.LIMIT,
            side = OrderSide.BUY,
            quantity = BigDecimal("10"),
            price = BigDecimal("150.00")
        )
        val userId = "user123"
        val traceId = "trace123"
        
        // Create order
        val response = orderSagaService.createOrderWithSaga(request, userId, traceId)
        val saga = sagaRepository.findByOrderId(response.orderId)!!
        
        // Simulate AccountUpdateFailedEvent
        val accountFailedEvent = AccountUpdateFailedEvent(
            eventId = "event123",
            aggregateId = "trade123",
            traceId = traceId,
            sagaId = saga.sagaId,
            tradeId = "trade123",
            orderId = response.orderId,
            buyUserId = userId,
            reason = "Insufficient balance",
            failureType = AccountUpdateFailedEvent.FailureType.INSUFFICIENT_BALANCE,
            shouldRetry = false
        )
        
        // When
        simulateAccountFailedEvent(accountFailedEvent)
        
        // Then - wait for async processing
        await.atMost(Duration.ofSeconds(5)).until {
            val updatedOrder = orderRepository.findById(response.orderId).orElse(null)
            val updatedSaga = sagaRepository.findBySagaId(saga.sagaId)
            updatedOrder?.status == OrderStatus.CANCELLED && 
            updatedSaga?.state == SagaStatus.COMPENSATED
        }
        
        // Verify final state
        val finalOrder = orderRepository.findById(response.orderId).orElse(null)
        assertEquals(OrderStatus.CANCELLED, finalOrder.status)
        assertEquals("Insufficient balance", finalOrder.cancellationReason)
        
        val finalSaga = sagaRepository.findBySagaId(saga.sagaId)
        assertEquals(SagaStatus.COMPENSATED, finalSaga?.state)
    }
    
    @Test
    fun `should handle saga timeout`() {
        // Given
        val request = CreateOrderRequest(
            symbol = "AAPL",
            orderType = OrderType.LIMIT,
            side = OrderSide.BUY,
            quantity = BigDecimal("10"),
            price = BigDecimal("150.00")
        )
        val userId = "user123"
        val traceId = "trace123"
        
        // Create order with very short timeout for testing
        val response = orderSagaService.createOrderWithSaga(request, userId, traceId)
        
        // When - wait for timeout scheduler to run
        Thread.sleep(6000) // Wait for scheduler (runs every 5 seconds)
        
        // Force timeout check
        orderSagaService.checkTimeouts()
        
        // Then
        await.atMost(Duration.ofSeconds(10)).until {
            val order = orderRepository.findById(response.orderId).orElse(null)
            val saga = sagaRepository.findByOrderId(response.orderId)
            order?.status == OrderStatus.TIMEOUT && 
            saga?.state == SagaStatus.TIMEOUT
        }
        
        // Verify final state
        val finalOrder = orderRepository.findById(response.orderId).orElse(null)
        assertEquals(OrderStatus.TIMEOUT, finalOrder.status)
        assertNotNull(finalOrder.cancellationReason)
        
        val finalSaga = sagaRepository.findByOrderId(response.orderId)
        assertEquals(SagaStatus.TIMEOUT, finalSaga?.state)
    }
    
    // Helper methods to simulate events
    private fun simulateAccountUpdatedEvent(event: AccountUpdatedEvent) {
        // In real scenario, this would come from Kafka
        // For testing, we directly call the handler
        val eventJson = """
            {
                "eventType": "AccountUpdated",
                "sagaId": "${event.sagaId}",
                "tradeId": "${event.tradeId}",
                "orderId": "${event.orderId}",
                "buyUserId": "${event.buyUserId}",
                "sellUserId": "${event.sellUserId}",
                "amount": "${event.amount}",
                "quantity": "${event.quantity}",
                "symbol": "${event.symbol}",
                "buyerNewBalance": "${event.buyerNewBalance}",
                "sellerNewBalance": "${event.sellerNewBalance}"
            }
        """.trimIndent()
        
        orderSagaService.handleAccountEvent(eventJson)
    }
    
    private fun simulateAccountFailedEvent(event: AccountUpdateFailedEvent) {
        // In real scenario, this would come from Kafka
        // For testing, we directly call the handler
        val eventJson = """
            {
                "eventType": "AccountUpdateFailed",
                "sagaId": "${event.sagaId}",
                "tradeId": "${event.tradeId}",
                "orderId": "${event.orderId}",
                "buyUserId": "${event.buyUserId}",
                "reason": "${event.reason}",
                "failureType": "${event.failureType}",
                "shouldRetry": ${event.shouldRetry}
            }
        """.trimIndent()
        
        orderSagaService.handleAccountEvent(eventJson)
    }
}
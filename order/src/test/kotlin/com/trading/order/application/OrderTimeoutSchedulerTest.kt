package com.trading.order.application

import com.trading.common.dto.order.OrderSide
import com.trading.common.dto.order.OrderStatus
import com.trading.common.dto.order.OrderType
import com.trading.common.util.UUIDv7Generator
import com.trading.order.domain.Order
import com.trading.order.domain.OrderRepository
import com.trading.order.infrastructure.outbox.OrderOutboxEvent
import com.trading.order.infrastructure.outbox.OrderOutboxRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OrderTimeoutSchedulerTest {

    private lateinit var orderRepository: OrderRepository
    private lateinit var orderOutboxRepository: OrderOutboxRepository
    private lateinit var uuidGenerator: UUIDv7Generator
    private lateinit var scheduler: OrderTimeoutScheduler

    @BeforeEach
    fun setUp() {
        orderRepository = mockk()
        orderOutboxRepository = mockk()
        uuidGenerator = mockk()
        scheduler = OrderTimeoutScheduler(
            orderRepository = orderRepository,
            orderOutboxRepository = orderOutboxRepository,
            uuidGenerator = uuidGenerator,
            timeoutDuration = "PT5M",
            batchSize = 100
        )
    }

    @Test
    fun `should process timeout orders successfully`() {
        // Given
        val now = Instant.now()
        val timeoutThreshold = now.minus(Duration.ofMinutes(5))
        val pageable = PageRequest.of(0, 100)

        val order = Order.create(
            userId = "user123",
            symbol = "AAPL",
            orderType = OrderType.LIMIT,
            side = OrderSide.BUY,
            quantity = BigDecimal.TEN,
            price = BigDecimal("150.00"),
            uuidGenerator = UUIDv7Generator()
        )

        val sagaId = "saga-123"
        val outboxEvent = mockk<OrderOutboxEvent>()

        every {
            orderRepository.findOrdersForTimeout(
                OrderStatus.CREATED,
                any(),
                pageable
            )
        } returns listOf(order)

        every { uuidGenerator.generateEventId() } returns sagaId
        every { orderRepository.save(any()) } returns order
        every { orderOutboxRepository.save(any()) } returns outboxEvent

        // When
        scheduler.processTimeoutOrders()

        // Then
        verify(exactly = 1) {
            orderRepository.findOrdersForTimeout(
                OrderStatus.CREATED,
                any(),
                pageable
            )
        }
        verify(exactly = 1) { orderRepository.save(order) }
        verify(exactly = 1) { orderOutboxRepository.save(any()) }

        assertEquals(OrderStatus.TIMEOUT, order.status)
        assertNotNull(order.cancellationReason)
    }

    @Test
    fun `should handle empty timeout list`() {
        // Given
        val pageable = PageRequest.of(0, 100)

        every {
            orderRepository.findOrdersForTimeout(
                OrderStatus.CREATED,
                any(),
                pageable
            )
        } returns emptyList()

        // When
        scheduler.processTimeoutOrders()

        // Then
        verify(exactly = 1) {
            orderRepository.findOrdersForTimeout(
                OrderStatus.CREATED,
                any(),
                pageable
            )
        }
        verify(exactly = 0) { orderRepository.save(any()) }
        verify(exactly = 0) { orderOutboxRepository.save(any()) }
    }

    @Test
    fun `should handle individual order timeout failure gracefully`() {
        // Given
        val pageable = PageRequest.of(0, 100)

        val order1 = mockk<Order>()
        val order2 = Order.create(
            userId = "user456",
            symbol = "GOOGL",
            orderType = OrderType.MARKET,
            side = OrderSide.SELL,
            quantity = BigDecimal("5"),
            price = null,
            uuidGenerator = UUIDv7Generator()
        )

        val sagaId = "saga-456"
        val outboxEvent = mockk<OrderOutboxEvent>()

        every {
            orderRepository.findOrdersForTimeout(
                OrderStatus.CREATED,
                any(),
                pageable
            )
        } returns listOf(order1, order2)

        every { uuidGenerator.generateEventId() } returns sagaId

        // First order throws exception
        every { order1.id } returns "order-1"
        every { order1.userId } returns "user123"
        every { order1.symbol } returns "AAPL"
        every { order1.timeout(any()) } throws RuntimeException("Timeout failed")

        // Second order succeeds
        every { orderRepository.save(order2) } returns order2
        every { orderOutboxRepository.save(any()) } returns outboxEvent

        // When
        scheduler.processTimeoutOrders()

        // Then
        verify(exactly = 1) {
            orderRepository.findOrdersForTimeout(
                OrderStatus.CREATED,
                any(),
                pageable
            )
        }
        verify(exactly = 1) { orderRepository.save(order2) }
        verify(exactly = 1) { orderOutboxRepository.save(any()) }

        assertEquals(OrderStatus.TIMEOUT, order2.status)
    }

    @Test
    fun `should handle scheduler exception gracefully`() {
        // Given
        val pageable = PageRequest.of(0, 100)

        every {
            orderRepository.findOrdersForTimeout(
                OrderStatus.CREATED,
                any(),
                pageable
            )
        } throws RuntimeException("Database error")

        // When & Then - Should not throw exception
        scheduler.processTimeoutOrders()

        verify(exactly = 1) {
            orderRepository.findOrdersForTimeout(
                OrderStatus.CREATED,
                any(),
                pageable
            )
        }
        verify(exactly = 0) { orderRepository.save(any()) }
        verify(exactly = 0) { orderOutboxRepository.save(any()) }
    }

    @Test
    fun `should validate timeout on non-CREATED order`() {
        // Given
        val order = Order.create(
            userId = "user123",
            symbol = "AAPL",
            orderType = OrderType.LIMIT,
            side = OrderSide.BUY,
            quantity = BigDecimal.TEN,
            price = BigDecimal("150.00"),
            uuidGenerator = UUIDv7Generator()
        )

        // Change status to PENDING
        order.status = OrderStatus.PENDING

        // When & Then
        val exception = assertThrows<Exception> {
            order.timeout("Test timeout")
        }

        assert(exception.message?.contains("Only CREATED orders can be timed out") == true)
    }
}
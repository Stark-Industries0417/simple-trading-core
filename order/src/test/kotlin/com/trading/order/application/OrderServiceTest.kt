package com.trading.order.application

import com.trading.common.dto.order.OrderSide
import com.trading.common.dto.order.OrderStatus
import com.trading.common.dto.order.OrderType
import com.trading.common.exception.order.OrderNotFoundException
import com.trading.common.exception.order.OrderValidationException
import com.trading.common.logging.StructuredLogger
import com.trading.common.util.UUIDv7Generator
import com.trading.order.domain.*
import com.trading.order.infrastructure.web.dto.CreateOrderRequest
import com.trading.order.infrastructure.outbox.OrderOutboxRepository
import com.trading.order.infrastructure.outbox.OrderOutboxEvent
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class OrderServiceTest {

    private lateinit var orderService: OrderService
    private lateinit var orderRepository: OrderRepository
    private lateinit var orderValidator: OrderValidator
    private lateinit var structuredLogger: StructuredLogger
    private lateinit var uuidGenerator: UUIDv7Generator
    private lateinit var orderMetrics: OrderMetrics
    private lateinit var outboxRepository: OrderOutboxRepository
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        orderRepository = mockk()
        orderValidator = mockk()
        structuredLogger = mockk(relaxed = true)
        uuidGenerator = mockk()
        orderMetrics = mockk(relaxed = true)
        outboxRepository = mockk()
        objectMapper = mockk(relaxed = true)

        orderService = OrderService(
            orderRepository, orderValidator,
            structuredLogger, uuidGenerator, orderMetrics,
            outboxRepository, objectMapper
        )

        every { uuidGenerator.generateOrderId() } returns "ORDER-123"
        every { uuidGenerator.generateEventId() } returns "EVENT-456"
        every { outboxRepository.save(any<OrderOutboxEvent>()) } answers { 
            firstArg<OrderOutboxEvent>()
        }
    }

    @Test
    fun `주문 생성 성공 - 지정가 매수`() {
        // Given
        val request = CreateOrderRequest(
            symbol = "AAPL",
            orderType = OrderType.LIMIT,
            side = OrderSide.BUY,
            quantity = BigDecimal("10"),
            price = BigDecimal("150.00")
        )
        val userId = "user123"
        val traceId = "trace-789"

        val order = Order.create(
            userId = userId,
            symbol = request.symbol,
            orderType = request.orderType,
            side = request.side,
            quantity = request.quantity,
            price = request.price,
            traceId = traceId,
            uuidGenerator = uuidGenerator
        )

        every { orderValidator.validateOrThrow(any()) } just Runs
        every { orderRepository.save(any()) } returns order

        // When
        val result = orderService.createOrder(request, userId, traceId)

        // Then
        assertThat(result.orderId).isEqualTo("ORDER-123")
        assertThat(result.symbol).isEqualTo("AAPL")
        assertThat(result.status).isEqualTo(OrderStatus.PENDING)

        verify(exactly = 1) { orderValidator.validateOrThrow(any()) }
        verify(exactly = 1) { orderRepository.save(any()) }
        verify(exactly = 1) { outboxRepository.save(any()) }
        verify(exactly = 1) { orderMetrics.recordOrderCreation(any()) }
    }

    @Test
    fun `주문 생성 실패 - 검증 실패`() {
        // Given
        val request = CreateOrderRequest(
            symbol = "INVALID",
            orderType = OrderType.LIMIT,
            side = OrderSide.BUY,
            quantity = BigDecimal("10"),
            price = BigDecimal("150.00")
        )
        val userId = "user123"
        val traceId = "trace-789"

        every {
            orderValidator.validateOrThrow(any())
        } throws OrderValidationException("Unsupported symbol")

        // When & Then
        assertThrows<OrderValidationException> {
            orderService.createOrder(request, userId, traceId)
        }

        verify(exactly = 1) { orderMetrics.incrementValidationFailures() }
        verify(exactly = 0) { orderRepository.save(any()) }
        verify(exactly = 0) { outboxRepository.save(any()) }
    }

    @Test
    fun `주문 취소 성공`() {
        // Given
        val orderId = "ORDER-123"
        val userId = "user123"
        val reason = "User requested"

        val order = mockk<Order>(relaxed = true) {
            every { id } returns orderId
            every { this@mockk.userId } returns userId
            every { status } returns OrderStatus.PENDING
            every { cancel(any()) } returns this
        }

        every { orderRepository.findByIdAndUserId(orderId, userId) } returns order
        every { orderRepository.save(any()) } returns order

        // When
        val result = orderService.cancelOrder(orderId, userId, reason)

        // Then
        assertThat(result.orderId).isEqualTo(orderId)

        verify(exactly = 1) { order.cancel(reason) }
        verify(exactly = 1) { orderRepository.save(order) }
        verify(exactly = 1) { outboxRepository.save(any()) }
    }

    @Test
    fun `주문 취소 실패 - 주문 없음`() {
        // Given
        val orderId = "ORDER-999"
        val userId = "user123"

        every { orderRepository.findByIdAndUserId(orderId, userId) } returns null

        // When & Then
        assertThrows<OrderNotFoundException> {
            orderService.cancelOrder(orderId, userId, "reason")
        }

        verify(exactly = 0) { orderRepository.save(any()) }
        verify(exactly = 0) { outboxRepository.save(any()) }
    }

    @Test
    fun `주문 생성 성공 - 시장가 매도`() {
        // Given
        val request = CreateOrderRequest(
            symbol = "AAPL",
            orderType = OrderType.MARKET,
            side = OrderSide.SELL,
            quantity = BigDecimal("5"),
            price = null
        )
        val userId = "user123"
        val traceId = "trace-789"

        val order = Order.create(
            userId = userId,
            symbol = request.symbol,
            orderType = request.orderType,
            side = request.side,
            quantity = request.quantity,
            price = request.price,
            traceId = traceId,
            uuidGenerator = uuidGenerator
        )

        every { orderValidator.validateOrThrow(any()) } just Runs
        every { orderRepository.save(any()) } returns order

        // When
        val result = orderService.createOrder(request, userId, traceId)

        // Then
        assertThat(result.orderId).isEqualTo("ORDER-123")
        assertThat(result.orderType).isEqualTo(OrderType.MARKET)
        assertThat(result.side).isEqualTo(OrderSide.SELL)

        verify(exactly = 1) { orderRepository.save(any()) }
        verify(exactly = 1) { outboxRepository.save(any()) }
    }
}
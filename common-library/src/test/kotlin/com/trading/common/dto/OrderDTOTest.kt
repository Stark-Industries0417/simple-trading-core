package com.trading.common.dto

import com.trading.common.dto.order.*
import com.trading.common.test.TestDataBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
class OrderDTOTest {
    @Test
    fun `OrderDTO should be created with all fields`() {
        val orderId = "ORD_123"
        val userId = "user-001"
        val symbol = "AAPL"
        val quantity = BigDecimal("100")
        val price = BigDecimal("150.00")
        val createdAt = Instant.now()
        val updatedAt = Instant.now()
        val order = TestDataBuilder.orderDTO(
            orderId = orderId,
            userId = userId,
            symbol = symbol,
            orderType = OrderType.LIMIT,
            side = OrderSide.BUY,
            quantity = quantity,
            price = price,
            createdAt = createdAt,
            updatedAt = updatedAt,
            status = OrderStatus.PENDING
        )
        assertThat(order.orderId).isEqualTo(orderId)
        assertThat(order.userId).isEqualTo(userId)
        assertThat(order.symbol).isEqualTo(symbol)
        assertThat(order.orderType).isEqualTo(OrderType.LIMIT)
        assertThat(order.side).isEqualTo(OrderSide.BUY)
        assertThat(order.quantity).isEqualTo(quantity)
        assertThat(order.price).isEqualTo(price)
        assertThat(order.createdAt).isEqualTo(createdAt)
        assertThat(order.updatedAt).isEqualTo(updatedAt)
        assertThat(order.status).isEqualTo(OrderStatus.PENDING)
    }
    @Test
    fun `Market order should have null price`() {
        val marketOrder = TestDataBuilder.orderDTO(
            orderType = OrderType.MARKET,
            price = null
        )
        assertThat(marketOrder.orderType).isEqualTo(OrderType.MARKET)
        assertThat(marketOrder.price).isNull()
    }
    @Test
    fun `Default status should be PENDING`() {
        val order = TestDataBuilder.orderDTO()
        assertThat(order.status).isEqualTo(OrderStatus.PENDING)
    }
}

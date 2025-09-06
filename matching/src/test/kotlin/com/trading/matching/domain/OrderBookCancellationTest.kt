package com.trading.matching.domain

import com.trading.common.dto.order.OrderDTO
import com.trading.common.dto.order.OrderSide
import com.trading.common.dto.order.OrderStatus
import com.trading.common.dto.order.OrderType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class OrderBookCancellationTest {

    private lateinit var orderBook: OrderBook
    private val symbol = "TEST"

    @BeforeEach
    fun setUp() {
        orderBook = OrderBook(symbol)
    }

    @Test
    fun `should cancel limit order from order book`() {
        // Given
        val buyOrder = OrderDTO(
            orderId = "buy-1",
            userId = "user-1",
            symbol = symbol,
            side = OrderSide.BUY,
            orderType = OrderType.LIMIT,
            quantity = BigDecimal("100"),
            price = BigDecimal("50.00"),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            status = OrderStatus.PENDING,
            traceId = "test-trace",
            version = 0
        )

        // Add order to book
        val trades = orderBook.processLimitOrder(buyOrder)
        assertTrue(trades.isEmpty(), "No trades should occur for initial order")
        assertEquals(1, orderBook.getOrderCount(), "Order should be in the book")
        assertEquals(1, orderBook.getBidLevels(), "Should have one bid level")

        // When - Cancel the order
        val cancelled = orderBook.cancelOrder(buyOrder.orderId)

        // Then
        assertTrue(cancelled, "Order should be cancelled successfully")
        assertEquals(0, orderBook.getOrderCount(), "Order should be removed from book")
        assertEquals(0, orderBook.getBidLevels(), "Bid level should be removed")
    }

    @Test
    fun `should return false when cancelling non-existent order`() {
        // When
        val cancelled = orderBook.cancelOrder("non-existent-order")

        // Then
        assertFalse(cancelled, "Should return false for non-existent order")
    }

    @Test
    fun `should cancel one order but keep others at same price level`() {
        // Given - Add multiple orders at same price
        val buyOrder1 = OrderDTO(
            orderId = "buy-1",
            userId = "user-1",
            symbol = symbol,
            side = OrderSide.BUY,
            orderType = OrderType.LIMIT,
            quantity = BigDecimal("100"),
            price = BigDecimal("50.00"),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            status = OrderStatus.PENDING,
            traceId = "test-trace",
            version = 0
        )

        val buyOrder2 = OrderDTO(
            orderId = "buy-2",
            userId = "user-2",
            symbol = symbol,
            side = OrderSide.BUY,
            orderType = OrderType.LIMIT,
            quantity = BigDecimal("200"),
            price = BigDecimal("50.00"),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            status = OrderStatus.PENDING,
            traceId = "test-trace",
            version = 0
        )

        orderBook.processLimitOrder(buyOrder1)
        orderBook.processLimitOrder(buyOrder2)
        
        assertEquals(2, orderBook.getOrderCount(), "Should have two orders")
        assertEquals(1, orderBook.getBidLevels(), "Should have one bid level")

        // When - Cancel first order
        val cancelled = orderBook.cancelOrder(buyOrder1.orderId)

        // Then
        assertTrue(cancelled, "First order should be cancelled")
        assertEquals(1, orderBook.getOrderCount(), "Should have one order remaining")
        assertEquals(1, orderBook.getBidLevels(), "Should still have one bid level")
        assertEquals(BigDecimal("50.00"), orderBook.getBestBid(), "Best bid should remain the same")
    }

    @Test
    fun `should not match cancelled order`() {
        // Given - Add buy order
        val buyOrder = OrderDTO(
            orderId = "buy-1",
            userId = "user-1",
            symbol = symbol,
            side = OrderSide.BUY,
            orderType = OrderType.LIMIT,
            quantity = BigDecimal("100"),
            price = BigDecimal("50.00"),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            status = OrderStatus.PENDING,
            traceId = "test-trace",
            version = 0
        )

        orderBook.processLimitOrder(buyOrder)
        
        // Cancel the order
        val cancelled = orderBook.cancelOrder(buyOrder.orderId)
        assertTrue(cancelled, "Order should be cancelled")

        // When - Add matching sell order
        val sellOrder = OrderDTO(
            orderId = "sell-1",
            userId = "user-2",
            symbol = symbol,
            side = OrderSide.SELL,
            orderType = OrderType.LIMIT,
            quantity = BigDecimal("100"),
            price = BigDecimal("50.00"),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            status = OrderStatus.PENDING,
            traceId = "test-trace",
            version = 0
        )

        val trades = orderBook.processLimitOrder(sellOrder)

        // Then
        assertTrue(trades.isEmpty(), "No trades should occur with cancelled order")
        assertEquals(1, orderBook.getOrderCount(), "Only sell order should remain")
        assertEquals(1, orderBook.getAskLevels(), "Should have one ask level")
        assertEquals(0, orderBook.getBidLevels(), "Should have no bid levels")
    }

    @Test
    fun `should handle cancellation of partially filled order`() {
        // Given - Add large buy order
        val buyOrder = OrderDTO(
            orderId = "buy-1",
            userId = "user-1",
            symbol = symbol,
            side = OrderSide.BUY,
            orderType = OrderType.LIMIT,
            quantity = BigDecimal("1000"),
            price = BigDecimal("50.00"),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            status = OrderStatus.PENDING,
            traceId = "test-trace",
            version = 0
        )

        orderBook.processLimitOrder(buyOrder)

        // Partially fill the order
        val smallSellOrder = OrderDTO(
            orderId = "sell-1",
            userId = "user-2",
            symbol = symbol,
            side = OrderSide.SELL,
            orderType = OrderType.LIMIT,
            quantity = BigDecimal("300"),
            price = BigDecimal("50.00"),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            status = OrderStatus.PENDING,
            traceId = "test-trace",
            version = 0
        )

        val trades = orderBook.processLimitOrder(smallSellOrder)
        assertEquals(1, trades.size, "Should have one trade")
        assertEquals(BigDecimal("300"), trades[0].quantity, "Trade quantity should be 300")
        
        // Order should still be in book with reduced quantity
        assertEquals(1, orderBook.getOrderCount(), "Partially filled order should remain")

        // When - Try to cancel the partially filled order
        val cancelled = orderBook.cancelOrder(buyOrder.orderId)

        // Then
        assertTrue(cancelled, "Partially filled order should be cancellable")
        assertEquals(0, orderBook.getOrderCount(), "Order should be removed")
        assertEquals(0, orderBook.getBidLevels(), "No bid levels should remain")

        // Verify cancelled order doesn't match new orders
        val anotherSellOrder = OrderDTO(
            orderId = "sell-2",
            userId = "user-3",
            symbol = symbol,
            side = OrderSide.SELL,
            orderType = OrderType.LIMIT,
            quantity = BigDecimal("700"),
            price = BigDecimal("50.00"),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            status = OrderStatus.PENDING,
            traceId = "test-trace",
            version = 0
        )

        val newTrades = orderBook.processLimitOrder(anotherSellOrder)
        assertTrue(newTrades.isEmpty(), "No trades should occur with cancelled order")
    }

    @Test
    fun `should maintain order book integrity after multiple cancellations`() {
        // Given - Add multiple orders at different price levels
        val now = Instant.now()
        val orders = listOf(
            OrderDTO("buy-1", "user-1", symbol, OrderType.LIMIT, OrderSide.BUY,
                    BigDecimal("100"), BigDecimal("52.00"), now, now, OrderStatus.PENDING, "test-trace", 0),
            OrderDTO("buy-2", "user-2", symbol, OrderType.LIMIT, OrderSide.BUY,
                    BigDecimal("200"), BigDecimal("51.00"), now, now, OrderStatus.PENDING, "test-trace", 0),
            OrderDTO("buy-3", "user-3", symbol, OrderType.LIMIT, OrderSide.BUY,
                    BigDecimal("150"), BigDecimal("50.00"), now, now, OrderStatus.PENDING, "test-trace", 0),
            OrderDTO("sell-1", "user-4", symbol, OrderType.LIMIT, OrderSide.SELL,
                    BigDecimal("100"), BigDecimal("53.00"), now, now, OrderStatus.PENDING, "test-trace", 0),
            OrderDTO("sell-2", "user-5", symbol, OrderType.LIMIT, OrderSide.SELL,
                    BigDecimal("200"), BigDecimal("54.00"), now, now, OrderStatus.PENDING, "test-trace", 0)
        )

        orders.forEach { orderBook.processLimitOrder(it) }
        assertEquals(5, orderBook.getOrderCount(), "Should have 5 orders")
        assertEquals(3, orderBook.getBidLevels(), "Should have 3 bid levels")
        assertEquals(2, orderBook.getAskLevels(), "Should have 2 ask levels")

        // When - Cancel orders in random order
        assertTrue(orderBook.cancelOrder("buy-2"), "Should cancel buy-2")
        assertTrue(orderBook.cancelOrder("sell-1"), "Should cancel sell-1")
        assertTrue(orderBook.cancelOrder("buy-1"), "Should cancel buy-1")

        // Then
        assertEquals(2, orderBook.getOrderCount(), "Should have 2 orders remaining")
        assertEquals(1, orderBook.getBidLevels(), "Should have 1 bid level")
        assertEquals(1, orderBook.getAskLevels(), "Should have 1 ask level")
        assertEquals(BigDecimal("50.00"), orderBook.getBestBid(), "Best bid should be 50.00")
        assertEquals(BigDecimal("54.00"), orderBook.getBestAsk(), "Best ask should be 54.00")

        // Verify remaining orders can still match
        val marketBuy = OrderDTO(
            orderId = "market-buy",
            userId = "user-6",
            symbol = symbol,
            side = OrderSide.BUY,
            orderType = OrderType.MARKET,
            quantity = BigDecimal("200"),
            price = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            status = OrderStatus.PENDING,
            traceId = "test-trace",
            version = 0
        )

        val trades = orderBook.processMarketOrder(marketBuy)
        assertEquals(1, trades.size, "Should match with remaining sell order")
        assertEquals(BigDecimal("200"), trades[0].quantity, "Should match full quantity")
        assertEquals(BigDecimal("54.00"), trades[0].price, "Should match at ask price")
    }
}
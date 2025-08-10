package com.trading.common.test
import com.trading.common.dto.*
import com.trading.common.event.*
import com.trading.common.util.UUIDv7Generator
import java.math.BigDecimal
import java.time.Instant
object TestDataBuilder {
    private val uuidGenerator = UUIDv7Generator()
    fun orderDTO(
        orderId: String = uuidGenerator.generateOrderId(),
        userId: String = "user-001",
        symbol: String = "AAPL",
        orderType: OrderType = OrderType.LIMIT,
        side: OrderSide = OrderSide.BUY,
        quantity: BigDecimal = BigDecimal("100"),
        price: BigDecimal? = BigDecimal("150.00"),
        timestamp: Instant = Instant.now(),
        status: OrderStatus = OrderStatus.PENDING
    ): OrderDTO {
        return OrderDTO(
            orderId = orderId,
            userId = userId,
            ticker = symbol,
            orderType = orderType,
            side = side,
            quantity = quantity,
            price = price,
            timestamp = timestamp,
            status = status
        )
    }
    fun marketDataDTO(
        symbol: String = "AAPL",
        price: BigDecimal = BigDecimal("150.00"),
        volume: BigDecimal = BigDecimal("1000"),
        timestamp: Instant = Instant.now(),
        bid: BigDecimal? = BigDecimal("149.99"),
        ask: BigDecimal? = BigDecimal("150.01"),
        bidSize: BigDecimal? = BigDecimal("500"),
        askSize: BigDecimal? = BigDecimal("500")
    ): MarketDataDTO {
        return MarketDataDTO(
            symbol = symbol,
            price = price,
            volume = volume,
            timestamp = timestamp,
            bid = bid,
            ask = ask,
            bidSize = bidSize,
            askSize = askSize
        )
    }
    fun accountDTO(
        userId: String = "user-001",
        cashBalance: BigDecimal = BigDecimal("100000.00"),
        availableCash: BigDecimal = BigDecimal("100000.00")
    ): AccountDTO {
        return AccountDTO(
            userId = userId,
            cashBalance = cashBalance,
            availableCash = availableCash
        )
    }
    fun stockHoldingDTO(
        userId: String = "user-001",
        symbol: String = "AAPL",
        quantity: BigDecimal = BigDecimal("100"),
        availableQuantity: BigDecimal = BigDecimal("100"),
        averagePrice: BigDecimal = BigDecimal("150.00")
    ): StockHoldingDTO {
        return StockHoldingDTO(
            userId = userId,
            symbol = symbol,
            quantity = quantity,
            availableQuantity = availableQuantity,
            averagePrice = averagePrice
        )
    }
    fun tradeDTO(
        tradeId: String = uuidGenerator.generateTradeId(),
        buyOrderId: String = uuidGenerator.generateOrderId(),
        sellOrderId: String = uuidGenerator.generateOrderId(),
        symbol: String = "AAPL",
        quantity: BigDecimal = BigDecimal("100"),
        price: BigDecimal = BigDecimal("150.00"),
        executedAt: Instant = Instant.now(),
        buyUserId: String = "buyer-001",
        sellUserId: String = "seller-001"
    ): TradeDTO {
        return TradeDTO(
            tradeId = tradeId,
            buyOrderId = buyOrderId,
            sellOrderId = sellOrderId,
            symbol = symbol,
            quantity = quantity,
            price = price,
            executedAt = executedAt,
            buyUserId = buyUserId,
            sellUserId = sellUserId
        )
    }
    fun orderCreatedEvent(
        eventId: String = uuidGenerator.generateEventId(),
        aggregateId: String = uuidGenerator.generateOrderId(),
        occurredAt: Instant = Instant.now(),
        traceId: String = uuidGenerator.generate(),
        order: OrderDTO = orderDTO()
    ): OrderCreatedEvent {
        return OrderCreatedEvent(
            eventId = eventId,
            aggregateId = aggregateId,
            occurredAt = occurredAt,
            traceId = traceId,
            order = order
        )
    }
    fun orderMatchedEvent(
        eventId: String = uuidGenerator.generateEventId(),
        aggregateId: String = uuidGenerator.generateTradeId(),
        occurredAt: Instant = Instant.now(),
        traceId: String = uuidGenerator.generate(),
        trade: TradeDTO = tradeDTO()
    ): OrderMatchedEvent {
        return OrderMatchedEvent(
            eventId = eventId,
            aggregateId = aggregateId,
            occurredAt = occurredAt,
            traceId = traceId,
            trade = trade
        )
    }
    fun marketDataUpdatedEvent(
        eventId: String = uuidGenerator.generateEventId(),
        aggregateId: String = "AAPL",
        occurredAt: Instant = Instant.now(),
        traceId: String = uuidGenerator.generate(),
        marketData: MarketDataDTO = marketDataDTO()
    ): MarketDataUpdatedEvent {
        return MarketDataUpdatedEvent(
            eventId = eventId,
            aggregateId = aggregateId,
            occurredAt = occurredAt,
            traceId = traceId,
            marketData = marketData
        )
    }
}

package com.trading.common.event.saga

import com.trading.common.event.base.DomainEvent
import java.time.Instant

data class TradeRollbackEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val occurredAt: Instant = Instant.now(),
    override val traceId: String,
    val sagaId: String,
    val tradeId: String,
    val orderId: String,
    val buyOrderId: String,
    val sellOrderId: String,
    val symbol: String,
    val reason: String,
    val rollbackType: RollbackType = RollbackType.FULL,
    val metadata: Map<String, Any>? = null
) : DomainEvent {
    enum class RollbackType {
        FULL,     // 전체 롤백
        PARTIAL   // 부분 롤백
    }
}
package com.trading.common.event.saga

import com.trading.common.event.base.DomainEvent
import java.math.BigDecimal
import java.time.Instant

data class AccountRollbackEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val occurredAt: Instant = Instant.now(),
    override val traceId: String,
    val sagaId: String,
    val tradeId: String,
    val orderId: String,
    val userId: String,
    val rollbackType: RollbackType,
    val amount: BigDecimal? = null,
    val quantity: BigDecimal? = null,
    val symbol: String? = null,
    val success: Boolean,
    val reason: String? = null,
    val metadata: Map<String, Any>? = null
) : DomainEvent {
    enum class RollbackType {
        RELEASE_FUNDS,      // 자금 예약 해제
        RELEASE_SHARES,     // 주식 예약 해제  
        REVERSE_TRADE,      // 체결 취소
        COMPENSATION        // 보상 트랜잭션
    }
}
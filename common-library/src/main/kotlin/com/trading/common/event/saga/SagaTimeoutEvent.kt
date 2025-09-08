package com.trading.common.event.saga

import com.trading.common.event.base.DomainEvent
import java.time.Instant

data class SagaTimeoutEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val occurredAt: Instant = Instant.now(),
    override val traceId: String,
    val sagaId: String,
    val orderId: String,
    val tradeId: String? = null,
    val failedAt: String,
    val timeoutDuration: Long,
    val eventPayload: String = "{}"
) : DomainEvent
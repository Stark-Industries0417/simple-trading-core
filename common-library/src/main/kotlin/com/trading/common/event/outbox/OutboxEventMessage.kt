package com.trading.common.event.outbox

data class OutboxEventMessage(
    val eventId: String,
    val aggregateId: String,
    val aggregateType: String,
    val eventType: String,
    val payload: String,
    val createdAt: String,
    val status: String? = null,
    val processedAt: String? = null
)
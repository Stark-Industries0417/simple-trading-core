package com.trading.common.event
import com.trading.common.dto.AccountDTO
import com.trading.common.dto.StockHoldingDTO
import java.time.Instant
data class AccountUpdatedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val occurredAt: Instant,
    override val traceId: String,
    val account: AccountDTO,
    val updatedHoldings: List<StockHoldingDTO> = emptyList(),
    val relatedTradeId: String? = null
) : DomainEvent

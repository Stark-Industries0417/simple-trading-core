package com.trading.common.event.account

import com.trading.common.dto.account.AccountDTO
import com.trading.common.dto.account.StockHoldingDTO
import com.trading.common.event.base.DomainEvent
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

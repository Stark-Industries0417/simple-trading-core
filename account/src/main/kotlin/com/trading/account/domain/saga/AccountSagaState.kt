package com.trading.account.domain.saga

import com.trading.common.domain.saga.SagaState
import com.trading.common.domain.saga.SagaStatus
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "account_saga_states")
class AccountSagaState(
    sagaId: String,
    tradeId: String,
    orderId: String,
    state: SagaStatus = SagaStatus.IN_PROGRESS,
    timeoutAt: Instant,
    eventType: String = "AccountUpdate",
    eventPayload: String = "{}"
) : SagaState(
    sagaId = sagaId,
    tradeId = tradeId,
    orderId = orderId,
    state = state,
    timeoutAt = timeoutAt,
    eventType = eventType,
    eventPayload = eventPayload
)
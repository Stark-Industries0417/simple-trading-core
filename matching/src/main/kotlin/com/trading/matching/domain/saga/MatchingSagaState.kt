package com.trading.matching.domain.saga

import com.trading.common.domain.saga.SagaState
import com.trading.common.domain.saga.SagaStatus
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "matching_saga_states")
class MatchingSagaState(
    sagaId: String,
    tradeId: String,
    orderId: String,
    state: SagaStatus = SagaStatus.IN_PROGRESS,
    timeoutAt: Instant,
    metadata: String? = null
) : SagaState(
    sagaId = sagaId,
    tradeId = tradeId,
    orderId = orderId,
    state = state,
    timeoutAt = timeoutAt,
    metadata = metadata
)
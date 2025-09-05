package com.trading.order.domain.saga

import com.trading.common.domain.saga.SagaState
import com.trading.common.domain.saga.SagaStatus
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "order_saga_states")
class OrderSagaState(
    sagaId: String,
    tradeId: String,
    orderId: String,
    state: SagaStatus = SagaStatus.STARTED,
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
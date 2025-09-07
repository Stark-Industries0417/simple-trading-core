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
    val userId: String,   
    val symbol: String,     
    val orderType: String,
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
) {
    
    fun isOrderRelatedOperation(): Boolean {
        return orderType in listOf("MARKET", "LIMIT", "STOP")
    }
    
    fun requiresMatchingEngine(): Boolean {
        return orderType == "LIMIT"
    }
    
    fun toSagaInfo(): String {
        return "OrderSaga[sagaId=$sagaId, orderId=$orderId, userId=$userId, symbol=$symbol, orderType=$orderType, state=$state]"
    }
}
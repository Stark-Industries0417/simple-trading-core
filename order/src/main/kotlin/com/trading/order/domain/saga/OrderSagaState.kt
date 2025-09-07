package com.trading.order.domain.saga

import com.trading.common.domain.saga.SagaState
import com.trading.common.domain.saga.SagaStatus
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "order_saga_states",
    indexes = [
        Index(name = "idx_saga_state", columnList = "state"),
        Index(name = "idx_saga_created", columnList = "startedAt"),
        Index(name = "idx_saga_order", columnList = "orderId"),
        Index(name = "idx_saga_user", columnList = "userId"),
        Index(name = "idx_saga_symbol", columnList = "symbol")
    ]
)
class OrderSagaState(
    sagaId: String,
    tradeId: String,
    orderId: String,
    val userId: String,   
    val symbol: String,     
    val orderType: String,
    state: SagaStatus = SagaStatus.STARTED,
    timeoutAt: Instant,
    metadata: String? = null,
    
    @Column(nullable = false)
    var eventType: String = "OrderCreated",
    
    @Column(columnDefinition = "JSON", nullable = false)
    var eventPayload: String = "{}",

    @Column(nullable = false)
    var lastModifiedAt: Instant = Instant.now()
) : SagaState(
    sagaId = sagaId,
    tradeId = tradeId,
    orderId = orderId,
    state = state,
    timeoutAt = timeoutAt,
    metadata = metadata
) {
    
    fun updateEvent(newEventType: String, newEventPayload: String) {
        this.eventType = newEventType
        this.eventPayload = newEventPayload
        this.lastModifiedAt = Instant.now()
    }
    
    fun isOrderRelatedOperation(): Boolean {
        return orderType in listOf("MARKET", "LIMIT", "STOP")
    }
    
    fun requiresMatchingEngine(): Boolean {
        return orderType == "LIMIT"
    }
    
    fun toSagaInfo(): String {
        return "OrderSaga[sagaId=$sagaId, orderId=$orderId, userId=$userId, symbol=$symbol, orderType=$orderType, state=$state]"
    }
    
    @PreUpdate
    fun preUpdate() {
        lastModifiedAt = Instant.now()
    }
}
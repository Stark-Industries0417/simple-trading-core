package com.trading.common.domain.saga

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@MappedSuperclass
abstract class SagaState(
    @Id
    open val sagaId: String = UUID.randomUUID().toString(),
    
    open val tradeId: String,
    open val orderId: String,
    
    @Enumerated(EnumType.STRING)
    open var state: SagaStatus,
    
    open val startedAt: Instant = Instant.now(),
    open var completedAt: Instant? = null,
    open var timeoutAt: Instant,
    
    @Column(nullable = false)
    open var eventType: String = "",
    
    @Column(columnDefinition = "JSON", nullable = false)
    open var eventPayload: String = "{}",
    
    @Column(nullable = false)
    open var lastModifiedAt: Instant = Instant.now(),
    
    @Version
    open var version: Long = 0
) {
    fun isTimedOut(): Boolean = Instant.now().isAfter(timeoutAt) && !isTerminalState()
    
    fun isTerminalState(): Boolean = state in listOf(
        SagaStatus.COMPLETED,
        SagaStatus.COMPENSATED,
        SagaStatus.FAILED
    )
    
    fun markCompleted() {
        state = SagaStatus.COMPLETED
        completedAt = Instant.now()
    }
    
    fun markFailed(failureReason: String? = null) {
        state = SagaStatus.FAILED
        completedAt = Instant.now()
        failureReason?.let { 
            val payload = """{"failureReason": "$it"}"""
            updateEvent("SagaFailed", payload)
        }
    }
    
    fun markCompensating() {
        state = SagaStatus.COMPENSATING
    }
    
    fun markCompensated() {
        state = SagaStatus.COMPENSATED
        completedAt = Instant.now()
    }
    
    fun markTimeout() {
        state = SagaStatus.TIMEOUT
        completedAt = Instant.now()
    }
    
    fun updateEvent(newEventType: String, newEventPayload: String) {
        this.eventType = newEventType
        this.eventPayload = newEventPayload
        this.lastModifiedAt = Instant.now()
    }
    
    @PreUpdate
    fun preUpdate() {
        lastModifiedAt = Instant.now()
    }
}
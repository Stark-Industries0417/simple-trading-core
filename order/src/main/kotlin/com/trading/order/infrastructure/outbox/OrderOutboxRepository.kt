package com.trading.order.infrastructure.outbox

import com.trading.common.outbox.OutboxStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface OrderOutboxRepository : JpaRepository<OrderOutboxEvent, String> {
    
    fun findByStatusOrderByCreatedAtAsc(status: OutboxStatus): List<OrderOutboxEvent>
    
    fun findByOrderId(orderId: String): List<OrderOutboxEvent>
    
    @Modifying
    @Query("UPDATE OrderOutboxEvent e SET e.status = :status, e.processedAt = :processedAt WHERE e.eventId = :eventId")
    fun updateStatus(
        @Param("eventId") eventId: String,
        @Param("status") status: OutboxStatus,
        @Param("processedAt") processedAt: Instant
    ): Int
    
    @Query("SELECT e FROM OrderOutboxEvent e WHERE e.status = :status AND e.createdAt < :cutoffTime")
    fun findStaleEvents(
        @Param("status") status: OutboxStatus,
        @Param("cutoffTime") cutoffTime: Instant
    ): List<OrderOutboxEvent>
}
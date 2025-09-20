package com.trading.order.infrastructure.outbox

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Order Outbox 이벤트 저장소
 * CDC 방식으로 INSERT만 처리하고 UPDATE는 하지 않음
 */
@Repository
interface OrderOutboxRepository : JpaRepository<OrderOutboxEvent, String> {

    fun findBySagaId(sagaId: String): List<OrderOutboxEvent>


    fun findBySagaIdAndEventType(sagaId: String, eventType: String): OrderOutboxEvent?


    fun findByOrderId(orderId: String): List<OrderOutboxEvent>


    @Query("SELECT e FROM OrderOutboxEvent e WHERE e.createdAt < :cutoffTime")
    fun findEventsCreatedBefore(@Param("cutoffTime") cutoffTime: Instant): List<OrderOutboxEvent>


    @Query("SELECT COUNT(e) FROM OrderOutboxEvent e WHERE e.createdAt BETWEEN :startTime AND :endTime")
    fun countEventsInPeriod(
        @Param("startTime") startTime: Instant,
        @Param("endTime") endTime: Instant
    ): Long


    @Modifying
    @Query("DELETE FROM OrderOutboxEvent e WHERE e.createdAt < :cutoffTime")
    fun deleteEventsCreatedBefore(@Param("cutoffTime") cutoffTime: Instant): Int


    @Modifying
    @Query("DELETE FROM OrderOutboxEvent e WHERE e.sagaId = :sagaId")
    fun deleteBySagaId(@Param("sagaId") sagaId: String): Int


    @Query("""
        SELECT e.eventType, COUNT(e)
        FROM OrderOutboxEvent e
        WHERE e.createdAt BETWEEN :startTime AND :endTime
        GROUP BY e.eventType
    """)
    fun countByEventType(
        @Param("startTime") startTime: Instant,
        @Param("endTime") endTime: Instant
    ): List<Array<Any>>


    fun findBySymbol(symbol: String): List<OrderOutboxEvent>


    fun findByUserId(userId: String): List<OrderOutboxEvent>
}
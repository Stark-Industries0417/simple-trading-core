package com.trading.common.outbox

import jakarta.persistence.*
import java.time.Instant

@MappedSuperclass
abstract class OutboxEvent(
    @Id
    val eventId: String = generateEventId(),
    
    val aggregateId: String,
    val aggregateType: String,
    val eventType: String,
    
    @Column(columnDefinition = "JSON")
    val payload: String, // JSON serialized event
    
    @Enumerated(EnumType.STRING)
    var status: OutboxStatus = OutboxStatus.PENDING,
    
    val createdAt: Instant = Instant.now(),
    var processedAt: Instant? = null,
    
    @Version
    var version: Long = 0
) {
    companion object {
        fun generateEventId(): String {
            // Simple UUID v4 generation for now
            // In production, consider using UUIDv7 for better timestamp ordering
            return java.util.UUID.randomUUID().toString()
        }
    }
}

enum class OutboxStatus {
    PENDING,    // 발행 대기
    PROCESSED,  // Debezium이 처리 완료
    FAILED      // 처리 실패 (재시도 필요)
}
package com.trading.common.outbox

import com.trading.common.util.UUIDv7Generator
import jakarta.persistence.*
import java.time.Instant

/**
 * Outbox 패턴의 기본 이벤트 클래스
 * CDC(Debezium)가 이 테이블의 INSERT를 감지하여 Kafka로 발행
 *
 * 설계 원칙:
 * - INSERT-Only: 생성 후 수정하지 않음 (CDC 중복 방지)
 * - 최소 필드: CDC 전용으로 필요한 최소한의 필드만 포함
 * - 모듈별 확장: 각 모듈이 필요한 필드 추가
 */
@MappedSuperclass
abstract class OutboxEvent(
    @Id
    val eventId: String = UUIDv7Generator.generate(),

    /**
     * Saga ID - 전체 분산 트랜잭션 추적용
     * Order → Matching → Account 전체 흐름을 하나의 ID로 추적
     */
    @Column(nullable = false, length = 50)
    val sagaId: String,

    /**
     * 이벤트 타입 - 이벤트 종류 구분
     * 예: OrderCreated, OrderCancelled, TradeExecuted, AccountUpdated
     */
    @Column(nullable = false, length = 50)
    val eventType: String,

    /**
     * 생성 시각 - 이벤트 발생 시각
     * 모니터링 및 지연 감지용
     */
    @Column(nullable = false)
    val createdAt: Instant = Instant.now()
) {
    override fun toString(): String {
        return "${this::class.simpleName}(eventId=$eventId, sagaId=$sagaId, eventType=$eventType, createdAt=$createdAt)"
    }
}
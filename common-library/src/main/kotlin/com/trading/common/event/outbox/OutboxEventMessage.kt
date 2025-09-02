package com.trading.common.event.outbox

/**
 * Outbox 패턴으로 CDC/Debezium이 Kafka로 전송하는 메시지 구조
 * 
 * DB의 outbox_events 테이블에서 읽은 데이터가 이 형태로 Kafka에 전달됨
 * JPA Entity가 아닌 순수 DTO로, 모든 모듈에서 사용 가능
 */
data class OutboxEventMessage(
    val eventId: String,
    val aggregateId: String,
    val aggregateType: String,
    val eventType: String,
    
    /**
     * 실제 도메인 이벤트의 JSON 문자열
     * 예: OrderCreatedEvent, TradeExecutedEvent 등이 JSON으로 직렬화되어 저장
     */
    val payload: String,
    
    val createdAt: String,
    
    // Optional fields (DB에는 있지만 Kafka 메시지에는 없을 수 있음)
    val status: String? = null,
    val processedAt: String? = null
)
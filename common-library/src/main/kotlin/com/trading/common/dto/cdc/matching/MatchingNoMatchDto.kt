package com.trading.common.dto.cdc.matching

import com.trading.common.event.base.DomainEvent
import java.math.BigDecimal

/**
 * 매칭 없음 이벤트 DTO
 * 지정가 주문이 주문북에 등록되었으나 즉시 매칭되지 않은 경우
 * CDC가 matching_outbox_events 테이블에서 NO_MATCH 이벤트를 감지하여 생성
 */
data class MatchingNoMatchDto(
    override val eventId: String,
    override val sagaId: String,
    val eventType: String,  // EventTypes.Trade.NO_MATCH
    val orderId: String,     // 주문 ID
    val userId: String,      // 주문 사용자 ID
    val symbol: String,      // 종목 코드
    val orderType: String,   // LIMIT
    val side: String,        // BUY/SELL
    val quantity: BigDecimal, // 주문 수량
    val price: BigDecimal,   // 지정가
    val status: String,      // PROCESSED (정상 처리됨)
    val processedAt: String?, // 처리 시각
    val createdAt: String,   // 생성 시각
    val metadata: Map<String, Any>? = null // 추가 메타데이터
) : DomainEvent
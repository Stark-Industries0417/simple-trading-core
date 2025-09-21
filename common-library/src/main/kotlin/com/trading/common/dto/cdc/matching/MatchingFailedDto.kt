package com.trading.common.dto.cdc.matching

import com.trading.common.event.base.DomainEvent
import java.math.BigDecimal

/**
 * 매칭 실패 이벤트 DTO
 * CDC가 matching_outbox_events 테이블에서 FAILED 이벤트를 감지하여 생성
 */
data class MatchingFailedDto(
    override val eventId: String,
    override val sagaId: String,
    val eventType: String,
    val buyOrderId: String,
    val sellOrderId: String,
    val buyUserId: String,
    val sellUserId: String,
    val symbol: String,
    val matchedQuantity: BigDecimal,
    val matchedPrice: BigDecimal?,
    val status: String,
    val processedAt: String?,
    val errorMessage: String?,
    val retryCount: Int,
    val createdAt: String
) : DomainEvent
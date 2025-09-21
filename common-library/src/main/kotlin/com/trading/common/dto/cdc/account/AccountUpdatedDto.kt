package com.trading.common.dto.cdc.account

import com.trading.common.event.base.DomainEvent
import java.math.BigDecimal

/**
 * 계좌 업데이트 성공 이벤트 DTO
 * CDC가 account_outbox_events 테이블에서 UPDATED 이벤트를 감지하여 생성
 */
data class AccountUpdatedDto(
    override val eventId: String,
    override val sagaId: String,
    val eventType: String,  // EventTypes.Account.UPDATED
    val tradeId: String,
    val orderId: String,
    val buyUserId: String,
    val sellUserId: String,
    val symbol: String,
    val amount: BigDecimal,
    val quantity: BigDecimal,
    val buyerNewBalance: BigDecimal,
    val sellerNewBalance: BigDecimal,
    val status: String,       // PROCESSED
    val processedAt: String?, // 처리 시각
    val createdAt: String     // 생성 시각
) : DomainEvent
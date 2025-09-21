package com.trading.common.dto.cdc.account

import com.trading.common.event.base.DomainEvent
import java.math.BigDecimal

/**
 * 계좌 롤백 이벤트 DTO
 * CDC가 account_outbox_events 테이블에서 ROLLBACK 이벤트를 감지하여 생성
 */
data class AccountRollbackDto(
    override val eventId: String,
    override val sagaId: String,
    val eventType: String,  // EventTypes.Account.ROLLBACK
    val tradeId: String,
    val orderId: String,
    val buyUserId: String,   // 롤백의 경우 한 명의 userId만 사용
    val sellUserId: String,  // 빈 문자열일 수 있음
    val symbol: String,
    val amount: BigDecimal,
    val quantity: BigDecimal,
    val rollbackType: String?, // RELEASE_FUNDS, RELEASE_SHARES, REVERSE_TRADE, COMPENSATION
    val reason: String,
    val success: Boolean,     // failureType이 null이면 성공, 있으면 실패
    val status: String,       // PROCESSED
    val processedAt: String?, // 처리 시각
    val createdAt: String     // 생성 시각
) : DomainEvent
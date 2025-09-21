package com.trading.common.dto.cdc.account

import com.trading.common.event.base.DomainEvent
import java.math.BigDecimal

/**
 * 계좌 업데이트 실패 이벤트 DTO
 * CDC가 account_outbox_events 테이블에서 UPDATE_FAILED 이벤트를 감지하여 생성
 */
data class AccountUpdateFailedDto(
    override val eventId: String,
    override val sagaId: String,
    val eventType: String,  // EventTypes.Account.UPDATE_FAILED
    val tradeId: String,
    val orderId: String,
    val buyUserId: String,
    val sellUserId: String,
    val symbol: String,
    val amount: BigDecimal,
    val quantity: BigDecimal,
    val failureType: String,  // INSUFFICIENT_BALANCE, INSUFFICIENT_SHARES, LOCK_TIMEOUT, etc.
    val reason: String,
    val shouldRetry: Boolean,
    val status: String,       // FAILED
    val processedAt: String?, // 처리 시각
    val errorMessage: String?, // 에러 메시지
    val retryCount: Int,      // 재시도 횟수
    val createdAt: String     // 생성 시각
) : DomainEvent
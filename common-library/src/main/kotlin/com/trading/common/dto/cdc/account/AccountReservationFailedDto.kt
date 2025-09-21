package com.trading.common.dto.cdc.account

import com.trading.common.event.base.DomainEvent
import java.math.BigDecimal

/**
 * 계좌 예약 실패 이벤트 DTO
 * Order 생성 시 자금/주식 예약이 실패했을 때 발행
 */
data class AccountReservationFailedDto(
    override val eventId: String,
    override val sagaId: String,
    val eventType: String,
    val orderId: String,
    val userId: String,
    val symbol: String,
    val quantity: BigDecimal,
    val failureType: String,  // INSUFFICIENT_FUNDS, INSUFFICIENT_SHARES, ACCOUNT_NOT_FOUND
    val reason: String,
    val shouldRetry: Boolean,
    val createdAt: String
) : DomainEvent
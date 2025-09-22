package com.trading.common.dto.cdc.account

import com.trading.common.event.base.DomainEvent
import java.math.BigDecimal

/**
 * CDC가 Account Outbox 테이블에서 읽어서 Kafka로 발행하는 이벤트
 *
 * account_outbox_events 테이블과 동일한 필드 구조
 */
data class AccountCreatedDto(
    override val eventId: String,
    override val sagaId: String,
    val eventType: String,
    val tradeId: String,
    val orderId: String,
    val buyUserId: String,
    val sellUserId: String,
    val symbol: String,
    val amount: BigDecimal,
    val quantity: BigDecimal,
    val buyerNewBalance: BigDecimal?,
    val sellerNewBalance: BigDecimal?,
    val failureType: String?,
    val reason: String?,
    val shouldRetry: Boolean,
    val status: String,
    val processedAt: String?,
    val errorMessage: String?,
    val retryCount: Int,
    val createdAt: String
) : DomainEvent
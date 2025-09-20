package com.trading.account.infrastructure.outbox

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AccountOutboxRepository : JpaRepository<AccountOutboxEvent, String> {
    fun findBySagaId(sagaId: String): List<AccountOutboxEvent>
    fun findByTradeId(tradeId: String): List<AccountOutboxEvent>
    fun findByOrderId(orderId: String): List<AccountOutboxEvent>
    fun findByBuyUserId(buyUserId: String): List<AccountOutboxEvent>
    fun findBySellUserId(sellUserId: String): List<AccountOutboxEvent>
    fun findByStatus(status: AccountOutboxStatus): List<AccountOutboxEvent>
    fun findByStatusAndRetryCountLessThan(status: AccountOutboxStatus, maxRetry: Int): List<AccountOutboxEvent>
    fun findByStatusOrderByCreatedAtAsc(status: AccountOutboxStatus): List<AccountOutboxEvent>
    fun existsBySagaIdAndEventType(sagaId: String, eventType: String): Boolean
}
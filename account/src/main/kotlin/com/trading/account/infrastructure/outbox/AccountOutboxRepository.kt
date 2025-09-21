package com.trading.account.infrastructure.outbox

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AccountOutboxRepository : JpaRepository<AccountOutboxEvent, String> {
    fun findByEventId(sagaId: String): List<AccountOutboxEvent>
}
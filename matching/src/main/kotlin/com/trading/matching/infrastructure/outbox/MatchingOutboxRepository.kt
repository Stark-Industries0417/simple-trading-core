package com.trading.matching.infrastructure.outbox

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MatchingOutboxRepository : JpaRepository<MatchingOutboxEvent, String> {
    fun findBySagaId(sagaId: String): List<MatchingOutboxEvent>
    fun findByBuyOrderId(buyOrderId: String): List<MatchingOutboxEvent>
    fun findByStatus(status: MatchingStatus): List<MatchingOutboxEvent>
    fun findByStatusAndRetryCountLessThan(status: MatchingStatus, maxRetry: Int): List<MatchingOutboxEvent>
    fun findByStatusOrderByCreatedAtAsc(status: MatchingStatus): List<MatchingOutboxEvent>
}
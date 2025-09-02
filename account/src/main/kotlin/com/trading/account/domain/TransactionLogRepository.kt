package com.trading.account.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface TransactionLogRepository : JpaRepository<TransactionLog, String> {
    
    fun findByUserId(userId: String): List<TransactionLog>
    
    fun findByTradeId(tradeId: String): List<TransactionLog>
    
    fun findByUserIdAndCreatedAtBetween(
        userId: String,
        startTime: Instant,
        endTime: Instant
    ): List<TransactionLog>
    
    @Query("""
        SELECT tl FROM TransactionLog tl 
        WHERE tl.userId = :userId 
        ORDER BY tl.createdAt DESC
    """)
    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<TransactionLog>
}
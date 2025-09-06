package com.trading.account.infrastructure.persistence

import com.trading.account.domain.TransactionLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TransactionLogRepository : JpaRepository<TransactionLog, String> {
    fun findByUserId(userId: String): List<TransactionLog>
    fun findByTradeId(tradeId: String): List<TransactionLog>
    fun findByUserIdAndSymbol(userId: String, symbol: String): List<TransactionLog>
}
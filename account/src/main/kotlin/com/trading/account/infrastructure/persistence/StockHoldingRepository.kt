package com.trading.account.infrastructure.persistence

import com.trading.account.domain.StockHolding
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.stereotype.Repository

@Repository
interface StockHoldingRepository : JpaRepository<StockHolding, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "javax.persistence.lock.timeout", value = "3000"))
    @Query("""
        SELECT sh FROM StockHolding sh 
        WHERE sh.userId = :userId AND sh.symbol = :symbol
    """)
    fun findByUserIdAndSymbolWithLock(
        userId: String,
        symbol: String
    ): StockHolding?

    fun findByUserId(userId: String): List<StockHolding>

    fun findByUserIdAndSymbol(userId: String, symbol: String): StockHolding?
}
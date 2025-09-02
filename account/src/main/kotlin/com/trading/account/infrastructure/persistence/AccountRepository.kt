package com.trading.account.infrastructure.persistence

import com.trading.account.domain.Account
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
interface AccountRepository : JpaRepository<Account, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "javax.persistence.lock.timeout", value = "3000"))
    @Query("SELECT a FROM Account a WHERE a.userId = :userId")
    fun findByUserIdWithLock(userId: String): Account?

    fun findByUserId(userId: String): Account?
    
    @Query("SELECT COUNT(a) FROM Account a WHERE a.cashBalance > :minBalance")
    fun countByBalanceGreaterThan(minBalance: BigDecimal): Long
    
    @Query("SELECT SUM(a.cashBalance) FROM Account a")
    fun sumAllBalances(): BigDecimal?
}
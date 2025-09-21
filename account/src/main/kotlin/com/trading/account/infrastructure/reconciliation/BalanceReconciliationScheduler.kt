package com.trading.account.infrastructure.reconciliation

import com.trading.account.application.AlertService
import com.trading.account.infrastructure.persistence.AccountRepository
import com.trading.account.infrastructure.persistence.TransactionLogRepository
import com.trading.account.domain.TransactionType
import com.trading.account.domain.Account
import com.trading.account.infrastructure.monitoring.ReconciliationMetrics
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class BalanceReconciliationScheduler(
    private val accountRepository: AccountRepository,
    private val transactionLogRepository: TransactionLogRepository,
    private val alertService: AlertService,
    private val reconciliationMetrics: ReconciliationMetrics,
    @Value("\${trading.reconciliation.initial-balance:100000.00}")
    private val defaultInitialBalance: BigDecimal
) {
    
    /**
     * 핵심 검증 공식:
     * 초기잔고 - Σ(매수금액) + Σ(매도금액) = 현재잔고
     * 
     * 이 단순한 공식으로 모든 거래의 정합성을 검증
     */
    @Scheduled(fixedDelay = 60_000)
    fun validateDataConsistency() {
        val startTime = System.currentTimeMillis()
        var inconsistencyCount = 0
        var totalAccounts = 0
        
        
        try {
            accountRepository.findAll().forEach { account ->
                totalAccounts++
                
                try {
                    val expectedBalance = calculateExpectedBalance(account.userId)
                    val actualBalance = account.getCashBalance()
                    val difference = expectedBalance - actualBalance
                    
                    if (difference.abs() > BigDecimal("0.01")) {
                        inconsistencyCount++
                        logInconsistency(account, expectedBalance, actualBalance, difference)
                        
                        alertService.sendCriticalAlert(
                            "Balance Inconsistency Detected",
                            "User: ${account.userId}, Difference: $difference"
                        )

                        reconciliationMetrics.recordInconsistency(
                            account.userId,
                            difference.toString()
                        )
                    }
                    
                    if (!account.isConsistent()) {
                        inconsistencyCount++
                        
                        alertService.sendCriticalAlert(
                            "Account Internal Inconsistency",
                            "User: ${account.userId}, Cash: ${account.getCashBalance()}, Available: ${account.getAvailableCash()}"
                        )
                    }
                    
                } catch (ex: Exception) {
                    inconsistencyCount++
                }
            }
            
            val consistencyRate = if (totalAccounts > 0) {
                ((totalAccounts - inconsistencyCount).toDouble() / totalAccounts) * 100
            } else 100.0
            
            reconciliationMetrics.recordConsistencyRate(consistencyRate)
            
            val duration = System.currentTimeMillis() - startTime
            reconciliationMetrics.recordReconciliationDuration(duration)

            if (consistencyRate < 99.99) {
                alertService.sendWarningAlert(
                    "Consistency Rate Below Target",
                    "Current: ${String.format("%.2f%%", consistencyRate)}, Target: 99.99%"
                )
            }
            
        } catch (ex: Exception) {
            alertService.sendCriticalAlert(
                "Reconciliation Scheduler Failed",
                "Error: ${ex.message}"
            )
        }
    }
    
    private fun calculateExpectedBalance(userId: String): BigDecimal {
        val logs = transactionLogRepository.findByUserId(userId)
        
        val initialBalance = getInitialBalance(userId)
        
        return logs.fold(initialBalance) { balance, log ->
            when (log.type) {
                TransactionType.BUY -> balance - log.amount
                TransactionType.SELL -> balance + log.amount
                TransactionType.DEPOSIT -> balance + log.amount
                TransactionType.WITHDRAWAL -> balance - log.amount
                TransactionType.ROLLBACK -> balance
            }
        }
    }
    
    private fun getInitialBalance(userId: String): BigDecimal {
        return defaultInitialBalance
    }
    
    private fun logInconsistency(
        account: Account,
        expected: BigDecimal,
        actual: BigDecimal,
        difference: BigDecimal
    ) {
        analyzeInconsistency(account.userId, expected, actual)
    }
    
    private fun analyzeInconsistency(
        userId: String,
        expected: BigDecimal,
        actual: BigDecimal
    ) {
        val logs = transactionLogRepository.findByUserIdOrderByCreatedAtDesc(userId)
        val summary = logs.groupBy { it.type }
            .mapValues { (_, transactions) ->
                transactions.sumOf { it.amount }
            }
    }
}
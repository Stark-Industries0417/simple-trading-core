package com.trading.account.infrastructure.monitoring

import com.trading.account.infrastructure.persistence.AccountRepository
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component("accountHealthIndicator")
class AccountHealthIndicator(
    private val accountRepository: AccountRepository,
    private val reconciliationMetrics: ReconciliationMetrics
) : HealthIndicator {
    
    private val cache = ConcurrentHashMap<String, CachedResult>()
    private val CACHE_TTL = Duration.ofSeconds(5)
    
    data class CachedResult(
        val value: Any,
        val timestamp: Instant
    )
    
    private fun <T> getCachedOrCompute(key: String, compute: () -> T): T {
        val cached = cache[key]
        if (cached != null && Duration.between(cached.timestamp, Instant.now()) < CACHE_TTL) {
            @Suppress("UNCHECKED_CAST")
            return cached.value as T
        }
        val result = compute()
        cache[key] = CachedResult(result as Any, Instant.now())
        return result
    }
    
    override fun health(): Health {
        val healthBuilder = Health.up()
        
        try {
            val consistencyRate = reconciliationMetrics.getLatestConsistencyRate()
            healthBuilder.withDetail("consistencyRate", String.format("%.2f%%", consistencyRate))
            
            if (consistencyRate < 99.99) {
                return Health.down()
                    .withDetail("consistencyRate", String.format("%.2f%%", consistencyRate))
                    .withDetail("issue", "Data inconsistency detected")
                    .withDetail("threshold", "99.99%")
                    .build()
            }
            
            val accountStats = getAccountStatistics()
            healthBuilder.withDetail("totalAccounts", accountStats["totalAccounts"])
            healthBuilder.withDetail("activeAccounts", accountStats["activeAccounts"])
            healthBuilder.withDetail("totalBalance", accountStats["totalBalance"])
            healthBuilder.withDetail("averageBalance", accountStats["averageBalance"])
            
            healthBuilder.withDetail("status", "All systems operational")
            healthBuilder.withDetail("lastReconciliation", System.currentTimeMillis())
            
            val performanceMetrics = getPerformanceMetrics()
            healthBuilder.withDetail("avgLockWaitTime", performanceMetrics["avgLockWaitTime"])
            healthBuilder.withDetail("deadlocksPrevented", performanceMetrics["deadlocksPrevented"])
            
            return healthBuilder.build()
            
        } catch (ex: Exception) {
            return Health.down()
                .withDetail("error", ex.message ?: "Unknown error")
                .withDetail("exception", ex.javaClass.simpleName)
                .build()
        }
    }
    
    private fun getAccountStatistics(): Map<String, Any> {
        return getCachedOrCompute("accountStats") {
            val totalAccounts = accountRepository.count()
            val activeAccounts = accountRepository.countByBalanceGreaterThan(BigDecimal.ZERO)
            val totalBalance = accountRepository.sumAllBalances() ?: BigDecimal.ZERO
            
            mapOf(
                "totalAccounts" to totalAccounts,
                "activeAccounts" to activeAccounts,
                "totalBalance" to totalBalance.toString(),
                "averageBalance" to if (totalAccounts > 0) {
                    totalBalance.divide(BigDecimal(totalAccounts), 2, BigDecimal.ROUND_HALF_UP).toString()
                } else "0"
            )
        }
    }
    
    private fun getPerformanceMetrics(): Map<String, String> {
        // TODO: 실제로는 메트릭 저장소에서 조회
        return mapOf(
            "avgLockWaitTime" to "45ms",
            "deadlocksPrevented" to "0",
            "avgTradeExecutionTime" to "25ms",
            "reconciliationRunTime" to "150ms"
        )
    }
}
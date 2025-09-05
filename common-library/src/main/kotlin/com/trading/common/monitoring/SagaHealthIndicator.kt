package com.trading.common.monitoring

import com.trading.common.domain.saga.SagaState
import com.trading.common.domain.saga.SagaStatus
import com.trading.common.domain.saga.SagaStateRepository
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class SagaHealthIndicator(
    private val sagaMetrics: SagaMetrics
) : HealthIndicator {
    
    companion object {
        private const val STUCK_SAGA_THRESHOLD = 10
        private const val TIMEOUT_RATE_THRESHOLD = 10.0
        private const val SUCCESS_RATE_THRESHOLD = 90.0
        private const val STUCK_DURATION_HOURS = 1L
    }
    
    override fun health(): Health {
        val builder = Health.Builder()
        
        try {
            val metrics = sagaMetrics.getMetricsSummary()
            val successRate = sagaMetrics.calculateSuccessRate()
            val timeoutRate = sagaMetrics.calculateTimeoutRate()
            
            
            val isHealthy = determineHealthStatus(successRate, timeoutRate)
            
            if (isHealthy) {
                builder.up()
            } else {
                builder.down()
            }
            
            
            builder
                .withDetail("metrics", metrics)
                .withDetail("successRate", String.format("%.2f%%", successRate))
                .withDetail("timeoutRate", String.format("%.2f%%", timeoutRate))
                .withDetail("totalStarted", metrics["started"] ?: 0)
                .withDetail("totalCompleted", metrics["completed"] ?: 0)
                .withDetail("totalFailed", metrics["failed"] ?: 0)
                .withDetail("totalTimeout", metrics["timeout"] ?: 0)
                .withDetail("avgDurationMs", metrics["avgDurationMs"] ?: 0)
            
            
            if (timeoutRate > 5.0) {
                builder.withDetail("warning", "High timeout rate detected")
            }
            
            if (successRate < 95.0) {
                builder.withDetail("warning", "Low success rate detected")
            }
            
        } catch (e: Exception) {
            builder.down()
                .withDetail("error", e.message ?: "Unknown error")
                .withException(e)
        }
        
        return builder.build()
    }
    
    private fun determineHealthStatus(successRate: Double, timeoutRate: Double): Boolean {
        return successRate >= SUCCESS_RATE_THRESHOLD && 
               timeoutRate <= TIMEOUT_RATE_THRESHOLD
    }
}

/**
 * Module-specific health indicator for detailed saga health
 */
abstract class ModuleSagaHealthIndicator<T : SagaState>(
    protected val sagaRepository: SagaStateRepository<T>,
    protected val sagaMetrics: SagaMetrics,
    protected val moduleName: String
) : HealthIndicator {
    
    companion object {
        private const val STUCK_SAGA_THRESHOLD = 5
        private const val STUCK_DURATION_MINUTES = 30L
    }
    
    override fun health(): Health {
        val builder = Health.Builder()
        
        try {
            
            val stuckThreshold = Instant.now().minus(STUCK_DURATION_MINUTES, ChronoUnit.MINUTES)
            val stuckSagas = sagaRepository.countStuckSagas(
                listOf(SagaStatus.IN_PROGRESS, SagaStatus.COMPENSATING),
                stuckThreshold
            )
            
            
            val timeoutRate = sagaMetrics.calculateTimeoutRate()
            
            
            val isHealthy = stuckSagas <= STUCK_SAGA_THRESHOLD && timeoutRate <= 10.0
            
            if (isHealthy) {
                builder.up()
            } else {
                builder.down()
            }
            
            
            builder
                .withDetail("module", moduleName)
                .withDetail("stuckSagas", stuckSagas)
                .withDetail("timeoutRate", String.format("%.2f%%", timeoutRate))
                .withDetail("threshold", STUCK_SAGA_THRESHOLD)
            
            
            val activeSagas = sagaRepository.findByStateIn(
                listOf(SagaStatus.STARTED, SagaStatus.IN_PROGRESS)
            )
            builder.withDetail("activeSagas", activeSagas.size)
            
            
            if (stuckSagas > STUCK_SAGA_THRESHOLD / 2) {
                builder.withDetail("warning", "Increasing number of stuck sagas")
            }
            
        } catch (e: Exception) {
            builder.down()
                .withDetail("module", moduleName)
                .withDetail("error", e.message ?: "Unknown error")
                .withException(e)
        }
        
        return builder.build()
    }
}
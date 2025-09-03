package com.trading.account.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.logging.StructuredLogger
import com.trading.common.util.TraceIdGenerator
import com.trading.common.util.UUIDv7Generator
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.math.BigDecimal

@Configuration
@EnableJpaRepositories(basePackages = [
    "com.trading.account.domain",
    "com.trading.account.infrastructure.persistence"
])
@EnableTransactionManagement
class AccountConfig {
    
    @Bean
    @ConfigurationProperties(prefix = "trading.account")
    fun accountProperties(): AccountProperties {
        return AccountProperties()
    }
    
    @Bean
    @ConditionalOnMissingBean
    fun uuidv7Generator(): UUIDv7Generator {
        return UUIDv7Generator()
    }
    
    @Bean
    @ConditionalOnMissingBean
    fun traceIdGenerator(): TraceIdGenerator {
        return TraceIdGenerator()
    }
    
    @Bean
    @ConditionalOnMissingBean
    fun structuredLogger(objectMapper: ObjectMapper): StructuredLogger {
        return StructuredLogger(objectMapper)
    }
}

data class AccountProperties(
    var initialCashBalance: BigDecimal = BigDecimal("100000.00"),
    var usePessimisticLock: Boolean = true,
    
    var reconciliation: ReconciliationProperties = ReconciliationProperties(),
    var saga: SagaProperties = SagaProperties(),
    var pessimisticLock: PessimisticLockProperties = PessimisticLockProperties(),
    var performance: PerformanceProperties = PerformanceProperties()
) {
    
    data class ReconciliationProperties(
        var enabled: Boolean = true,
        var initialBalance: BigDecimal = BigDecimal("100000.00"),
        var scheduleDelayMs: Long = 60000,
        var consistencyThreshold: Double = 99.99,
        var alertOnInconsistency: Boolean = true
    )
    
    data class SagaProperties(
        var maxRetryAttempts: Int = 3,
        var retryBackoffMultiplier: Double = 2.0,
        var retryInitialDelayMs: Long = 1000,
        var deadLetterQueueEnabled: Boolean = true
    )
    
    data class PessimisticLockProperties(
        var timeoutMs: Long = 3000,
        var deadlockPrevention: Boolean = true
    )
    
    data class PerformanceProperties(
        var targetProcessingTimeMs: Long = 50,
        var targetLockWaitTimeMs: Long = 50,
        var targetConsistencyRate: Double = 99.99
    )
}
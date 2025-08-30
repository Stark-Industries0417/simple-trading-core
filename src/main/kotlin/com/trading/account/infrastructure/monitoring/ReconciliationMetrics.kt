package com.trading.account.infrastructure.monitoring

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

@Component
class ReconciliationMetrics(private val meterRegistry: MeterRegistry) {
    
    private val latestConsistencyRate = AtomicReference(100.0)
    
    init {
        Gauge.builder("account.reconciliation.consistency.rate") { latestConsistencyRate.get() }
            .description("Data consistency rate in percentage")
            .baseUnit("%")
            .register(meterRegistry)
    }
    
    fun recordConsistencyRate(rate: Double) {
        latestConsistencyRate.set(rate)
        
        meterRegistry.counter(
            "account.reconciliation.checks",
            "status", if (rate >= 99.99) "success" else "failure"
        ).increment()
    }
    
    fun getLatestConsistencyRate(): Double = latestConsistencyRate.get()
    
    fun recordInconsistency(userId: String, difference: String) {
        meterRegistry.counter(
            "account.reconciliation.inconsistencies",
            "userId", userId
        ).increment()
    }
    
    fun recordReconciliationDuration(duration: Long) {
        meterRegistry.timer("account.reconciliation.duration")
            .record(java.time.Duration.ofMillis(duration))
    }
}
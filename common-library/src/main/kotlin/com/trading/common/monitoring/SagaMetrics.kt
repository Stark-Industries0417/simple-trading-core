package com.trading.common.monitoring

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class SagaMetrics(
    private val meterRegistry: MeterRegistry
) {
    
    
    private val sagaStartedCounter: Counter = Counter.builder("saga.started")
        .description("Number of sagas started")
        .register(meterRegistry)
    
    private val sagaCompletedCounter: Counter = Counter.builder("saga.completed")
        .description("Number of sagas completed successfully")
        .register(meterRegistry)
    
    private val sagaFailedCounter: Counter = Counter.builder("saga.failed")
        .description("Number of sagas failed")
        .register(meterRegistry)
    
    private val sagaTimeoutCounter: Counter = Counter.builder("saga.timeout")
        .description("Number of sagas timed out")
        .register(meterRegistry)
    
    private val sagaCompensatedCounter: Counter = Counter.builder("saga.compensated")
        .description("Number of sagas compensated")
        .register(meterRegistry)
    
    
    private val sagaDurationTimer: Timer = Timer.builder("saga.duration")
        .description("Duration of saga execution")
        .register(meterRegistry)
    
    
    fun recordSagaStarted(sagaId: String, module: String) {
        sagaStartedCounter.increment()
        meterRegistry.counter("saga.started.by.module", "module", module).increment()
    }
    
    fun recordSagaCompleted(sagaId: String, module: String, durationMs: Long) {
        sagaCompletedCounter.increment()
        sagaDurationTimer.record(Duration.ofMillis(durationMs))
        meterRegistry.counter("saga.completed.by.module", "module", module).increment()
        meterRegistry.timer("saga.duration.by.module", "module", module)
            .record(Duration.ofMillis(durationMs))
    }
    
    fun recordSagaFailed(sagaId: String, module: String, reason: String) {
        sagaFailedCounter.increment()
        meterRegistry.counter(
            "saga.failed.by.reason",
            "module", module,
            "reason", reason
        ).increment()
    }
    
    fun recordSagaTimeout(sagaId: String, module: String, stage: String) {
        sagaTimeoutCounter.increment()
        meterRegistry.counter(
            "saga.timeout.by.stage",
            "module", module,
            "stage", stage
        ).increment()
    }
    
    fun recordSagaCompensation(sagaId: String, module: String, reason: String) {
        sagaCompensatedCounter.increment()
        meterRegistry.counter(
            "saga.compensation.by.reason",
            "module", module,
            "reason", reason
        ).increment()
    }
    
    
    fun getMetricsSummary(): Map<String, Any> {
        return mapOf(
            "started" to sagaStartedCounter.count(),
            "completed" to sagaCompletedCounter.count(),
            "failed" to sagaFailedCounter.count(),
            "timeout" to sagaTimeoutCounter.count(),
            "compensated" to sagaCompensatedCounter.count(),
            "avgDurationMs" to sagaDurationTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS),
            "maxDurationMs" to sagaDurationTimer.max(java.util.concurrent.TimeUnit.MILLISECONDS)
        )
    }
    
    
    fun calculateSuccessRate(): Double {
        val total = sagaStartedCounter.count()
        val completed = sagaCompletedCounter.count()
        return if (total > 0) (completed / total) * 100 else 0.0
    }
    
    
    fun calculateTimeoutRate(): Double {
        val total = sagaStartedCounter.count()
        val timeouts = sagaTimeoutCounter.count()
        return if (total > 0) (timeouts / total) * 100 else 0.0
    }
}
package com.trading.matching.infrastructure.resilience

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong



@Component
class BackpressureMonitor {
    
    private val queueMetrics = ConcurrentHashMap<String, QueueMetrics>()
    
    companion object {
        private val logger = LoggerFactory.getLogger(BackpressureMonitor::class.java)
        private const val HIGH_REJECT_RATE_THRESHOLD = 0.1
        private const val MEDIUM_REJECT_RATE_THRESHOLD = 0.05
        private const val MAX_QUEUE_SIZE = 10_000
        private const val MEDIUM_QUEUE_SIZE = 7_500
        private const val LOW_QUEUE_SIZE = 5_000
        private const val METRICS_RESET_INTERVAL_MS = 60_000L // Reset metrics every minute
    }
    
    data class QueueMetrics(
        val queueSize: AtomicInteger = AtomicInteger(0),
        val rejectedCount: AtomicLong = AtomicLong(0),
        val acceptedCount: AtomicLong = AtomicLong(0),
        val lastResetTime: AtomicLong = AtomicLong(System.currentTimeMillis())
    )
    
    fun shouldReject(symbol: String): Boolean {
        val metrics = queueMetrics.computeIfAbsent(symbol) { QueueMetrics() }
        
        maybeResetMetrics(metrics)
        
        val threshold = calculateDynamicThreshold(metrics)
        val currentSize = metrics.queueSize.get()
        
        val shouldReject = currentSize > threshold
        
        if (shouldReject) {
            metrics.rejectedCount.incrementAndGet()
            logRejection(symbol, currentSize, threshold)
        } else {
            metrics.acceptedCount.incrementAndGet()
        }
        
        return shouldReject
    }
    
    fun recordQueueSize(symbol: String, size: Int) {
        val metrics = queueMetrics.computeIfAbsent(symbol) { QueueMetrics() }
        metrics.queueSize.set(size)
    }
    
    private fun calculateDynamicThreshold(metrics: QueueMetrics): Int {
        val elapsedTime = System.currentTimeMillis() - metrics.lastResetTime.get()
        if (elapsedTime <= 0) return MAX_QUEUE_SIZE
        
        // Calculate rejection rate
        val totalRequests = metrics.acceptedCount.get() + metrics.rejectedCount.get()
        if (totalRequests == 0L) return MAX_QUEUE_SIZE
        
        val rejectRate = metrics.rejectedCount.get().toDouble() / totalRequests
        
        return when {
            rejectRate > HIGH_REJECT_RATE_THRESHOLD -> LOW_QUEUE_SIZE
            rejectRate > MEDIUM_REJECT_RATE_THRESHOLD -> MEDIUM_QUEUE_SIZE
            else -> MAX_QUEUE_SIZE  // Low rejection: maximum capacity
        }
    }
    
    private fun maybeResetMetrics(metrics: QueueMetrics) {
        val now = System.currentTimeMillis()
        val lastReset = metrics.lastResetTime.get()
        
        if (now - lastReset > METRICS_RESET_INTERVAL_MS) {
            if (metrics.lastResetTime.compareAndSet(lastReset, now)) {
                val totalRequests = metrics.acceptedCount.get() + metrics.rejectedCount.get()
                if (totalRequests > 0) {
                    val rejectRate = metrics.rejectedCount.get().toDouble() / totalRequests
                    logger.info(
                        "Backpressure metrics reset",
                        mapOf(
                            "accepted" to metrics.acceptedCount.get(),
                            "rejected" to metrics.rejectedCount.get(),
                            "rejectRate" to String.format("%.2f%%", rejectRate * 100),
                            "currentQueueSize" to metrics.queueSize.get()
                        )
                    )
                }
                
                metrics.rejectedCount.set(0)
                metrics.acceptedCount.set(0)
            }
        }
    }
    
    private fun logRejection(symbol: String, currentSize: Int, threshold: Int) {
        if (shouldLogRejection()) {
            logger.warn(
                "Order rejected due to backpressure",
                mapOf(
                    "symbol" to symbol,
                    "currentQueueSize" to currentSize,
                    "threshold" to threshold,
                    "reason" to "QUEUE_OVERFLOW"
                )
            )
        }
    }
    
    private val lastRejectionLogTime = AtomicLong(0)

    private fun shouldLogRejection(): Boolean {
        val now = System.currentTimeMillis()
        val lastLog = lastRejectionLogTime.get()
        if (now - lastLog > 1000) {
            return lastRejectionLogTime.compareAndSet(lastLog, now)
        }
        return false
    }
    
    fun getMetrics(symbol: String): Map<String, Any> {
        val metrics = queueMetrics[symbol] ?: return emptyMap()
        val totalRequests = metrics.acceptedCount.get() + metrics.rejectedCount.get()
        val rejectRate = if (totalRequests > 0) {
            metrics.rejectedCount.get().toDouble() / totalRequests
        } else 0.0
        
        return mapOf(
            "queueSize" to metrics.queueSize.get(),
            "accepted" to metrics.acceptedCount.get(),
            "rejected" to metrics.rejectedCount.get(),
            "rejectRate" to rejectRate,
            "threshold" to calculateDynamicThreshold(metrics)
        )
    }
    
    fun getAllMetrics(): Map<String, Map<String, Any>> {
        return queueMetrics.mapValues { (symbol, _) -> getMetrics(symbol) }
    }
}
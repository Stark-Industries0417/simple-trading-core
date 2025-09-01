package com.trading.matching.infrastructure.engine

import com.trading.common.dto.order.OrderDTO
import com.trading.common.event.base.EventPublisher
import com.trading.common.event.OrderRejectedEvent
import org.slf4j.LoggerFactory
import com.trading.common.util.UUIDv7Generator
import com.trading.matching.infrastructure.resilience.BackpressureMonitor
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.TimeUnit
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlin.math.absoluteValue



@Component
class MatchingEngineManager(
    @Value("\${matching.thread-pool-size:16}")
    private val threadPoolSize: Int = Runtime.getRuntime().availableProcessors() * 2,
    private val eventPublisher: EventPublisher,
    private val backpressureMonitor: BackpressureMonitor,
    private val uuidGenerator: UUIDv7Generator
) {
    companion object {
        private val logger = LoggerFactory.getLogger(MatchingEngineManager::class.java)
    }
    
    private lateinit var workers: Array<MatchingWorker>
    private lateinit var workerThreads: Array<Thread>
    
    @PostConstruct
    fun initialize() {
        logger.info(
            "Initializing MatchingEngineManager",
            mapOf(
                "threadPoolSize" to threadPoolSize,
                "availableProcessors" to Runtime.getRuntime().availableProcessors()
            )
        )
        
        workers = Array(threadPoolSize) { workerId ->
            MatchingWorker(workerId, eventPublisher, uuidGenerator)
        }
        
        workerThreads = Array(threadPoolSize) { index ->
            Thread(workers[index], "matching-worker-$index").apply {
                isDaemon = false
                priority = Thread.NORM_PRIORITY + 1
                start()
            }
        }
        
        logger.info(
            "MatchingEngineManager initialized successfully",
            mapOf(
                "workers" to threadPoolSize,
                "status" to "RUNNING"
            )
        )
    }
    
    fun submitOrder(order: OrderDTO, traceId: String = ""): Boolean {
        val startTime = System.nanoTime()
        val workerIndex = order.symbol.hashCode().absoluteValue % threadPoolSize
        val worker = workers[workerIndex]
        
        backpressureMonitor.recordQueueSize(order.symbol, worker.getQueueSize())
        
        if (backpressureMonitor.shouldReject(order.symbol)) {
            logger.warn(
                "Order rejected due to backpressure",
                mapOf(
                    "orderId" to order.orderId,
                    "symbol" to order.symbol,
                    "workerId" to workerIndex,
                    "reason" to "BACKPRESSURE",
                    "traceId" to traceId
                )
            )
            publishOrderRejectedEvent(order, "BACKPRESSURE", traceId)
            return false
        }
        
        val submitted = worker.submitOrder(order, traceId)
        
        if (!submitted) {
            logger.warn(
                "Order submission failed",
                mapOf(
                    "orderId" to order.orderId,
                    "symbol" to order.symbol,
                    "workerId" to workerIndex,
                    "reason" to "QUEUE_FULL",
                    "traceId" to traceId
                )
            )
            publishOrderRejectedEvent(order, "QUEUE_FULL", traceId)
            return false
        }
        
        val latencyNanos = System.nanoTime() - startTime
        if (latencyNanos > 10_000_000) {
            logger.debug(
                "High order routing latency",
                mapOf(
                    "orderId" to order.orderId,
                    "symbol" to order.symbol,
                    "latencyMs" to (latencyNanos / 1_000_000).toString(),
                    "traceId" to traceId
                )
            )
        }
        
        return true
    }
    
    private fun publishOrderRejectedEvent(order: OrderDTO, reason: String, traceId: String) {
        try {
            val event = OrderRejectedEvent(
                eventId = uuidGenerator.generateEventId(),
                aggregateId = order.orderId,
                occurredAt = Instant.now(),
                traceId = traceId,
                orderId = order.orderId,
                userId = order.userId,
                symbol = order.symbol,
                reason = reason,
                timestamp = System.currentTimeMillis()
            )
            
            eventPublisher.publish(event)
            
        } catch (e: Exception) {
            logger.error(
                "Failed to publish order rejected event",
                mapOf(
                    "orderId" to order.orderId,
                    "reason" to reason,
                    "error" to e.message,
                    "traceId" to traceId
                )
            )
        }
    }
    
    fun getMetrics(): EngineMetrics {
        val workerMetrics = workers.map { it.getMetrics() }
        
        return EngineMetrics(
            workerCount = threadPoolSize,
            totalSymbols = workers.sumOf { it.getManagedSymbolCount() },
            totalQueueSize = workers.sumOf { it.getQueueSize() },
            totalOrdersProcessed = workers.sumOf { it.getOrdersProcessed() },
            totalOrdersRejected = workers.sumOf { it.getOrdersRejected() },
            totalTradesExecuted = workers.sumOf { it.getTradesExecuted() },
            backpressureMetrics = backpressureMonitor.getAllMetrics(),
            workerMetrics = workerMetrics
        )
    }
    
    fun waitForAllCompletion(timeout: Long = 30, unit: TimeUnit = TimeUnit.SECONDS): Boolean {
        val deadline = System.currentTimeMillis() + unit.toMillis(timeout)
        
        return workers.all { worker ->
            val remainingTime = deadline - System.currentTimeMillis()
            if (remainingTime <= 0) return false
            worker.waitForCompletion(remainingTime, TimeUnit.MILLISECONDS)
        }
    }
    
    @PreDestroy
    fun shutdown() {
        logger.info("Shutting down MatchingEngineManager")
        
        workers.forEach { it.shutdown() }
        
        val shutdownTimeout = 10L
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(shutdownTimeout)
        
        workerThreads.forEach { thread ->
            try {
                val remainingTime = deadline - System.currentTimeMillis()
                if (remainingTime > 0) {
                    thread.join(remainingTime)
                }
                if (thread.isAlive) {
                    logger.warn(
                        "Force interrupting worker thread",
                        mapOf("threadName" to thread.name)
                    )
                    thread.interrupt()
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                logger.error(
                    "Interrupted during shutdown",
                    mapOf("threadName" to thread.name)
                )
            }
        }
        
        val metrics = getMetrics()
        logger.info(
            "MatchingEngineManager shutdown complete",
            mapOf(
                "totalOrdersProcessed" to metrics.totalOrdersProcessed,
                "totalTradesExecuted" to metrics.totalTradesExecuted,
                "totalOrdersRejected" to metrics.totalOrdersRejected
            )
        )
    }
}

data class EngineMetrics(
    val workerCount: Int,
    val totalSymbols: Int,
    val totalQueueSize: Int,
    val totalOrdersProcessed: Long,
    val totalOrdersRejected: Long,
    val totalTradesExecuted: Long,
    val backpressureMetrics: Map<String, Map<String, Any>> = emptyMap(),
    val workerMetrics: List<Map<String, Any>> = emptyList()
)
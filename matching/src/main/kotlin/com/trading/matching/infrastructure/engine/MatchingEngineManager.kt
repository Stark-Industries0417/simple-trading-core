package com.trading.matching.infrastructure.engine

import com.trading.common.dto.order.OrderDTO
import org.slf4j.LoggerFactory
import com.trading.matching.domain.Trade
import com.trading.matching.infrastructure.resilience.BackpressureMonitor
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlin.math.absoluteValue



@Component
class MatchingEngineManager(
    @Value("\${matching.thread-pool-size:16}")
    private val threadPoolSize: Int = Runtime.getRuntime().availableProcessors() * 2,
    private val backpressureMonitor: BackpressureMonitor
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
            MatchingWorker(workerId)
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
    
    @JvmOverloads
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
            // Order rejected event handling removed - handled at Kafka layer
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
            // Order rejected event handling removed - handled at Kafka layer
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
    
    fun processOrderWithResult(order: OrderDTO, traceId: String = ""): List<Trade> {
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
            // Order rejected event handling removed - handled at Kafka layer
            return emptyList()
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
            // Order rejected event handling removed - handled at Kafka layer
            return emptyList()
        }
        
        // Poll for trades with exponential backoff
        val maxAttempts = 10
        val baseDelayMs = 1L
        var trades: List<Trade> = emptyList()

        for (attempt in 1..maxAttempts) {
            trades = worker.getTradesForOrder(order.orderId)
            if (trades.isNotEmpty()) {
                break
            }

            // Exponential backoff: 1ms, 2ms, 4ms, 8ms, ...
            val delayMs = baseDelayMs shl (attempt - 1)
            if (attempt < maxAttempts) {
                Thread.sleep(delayMs.coerceAtMost(50))
            }
        }
        
        val latencyNanos = System.nanoTime() - startTime
        if (latencyNanos > 10_000_000) {
            logger.debug(
                "Order processing latency",
                mapOf(
                    "orderId" to order.orderId,
                    "symbol" to order.symbol,
                    "latencyMs" to (latencyNanos / 1_000_000).toString(),
                    "tradesGenerated" to trades.size,
                    "traceId" to traceId
                )
            )
        }
        
        return trades
    }
    
    fun removeOrderFromBook(orderId: String, symbol: String, traceId: String = ""): Boolean {
        val startTime = System.nanoTime()
        val workerIndex = symbol.hashCode().absoluteValue % threadPoolSize
        val worker = workers[workerIndex]
        
        logger.info(
            "Cancelling order",
            mapOf(
                "orderId" to orderId,
                "symbol" to symbol,
                "workerId" to workerIndex,
                "traceId" to traceId
            )
        )
        
        val cancelled = worker.cancelOrder(orderId, symbol, traceId)
        
        val latencyNanos = System.nanoTime() - startTime
        if (latencyNanos > 10_000_000) {
            logger.debug(
                "Order cancellation latency",
                mapOf(
                    "orderId" to orderId,
                    "symbol" to symbol,
                    "latencyMs" to (latencyNanos / 1_000_000).toString(),
                    "cancelled" to cancelled,
                    "traceId" to traceId
                )
            )
        }
        
        if (cancelled) {
            logger.info(
                "Order cancelled successfully",
                mapOf(
                    "orderId" to orderId,
                    "symbol" to symbol,
                    "traceId" to traceId
                )
            )
        } else {
            logger.warn(
                "Order cancellation failed",
                mapOf(
                    "orderId" to orderId,
                    "symbol" to symbol,
                    "traceId" to traceId,
                    "reason" to "Order not found or already executed"
                )
            )
        }
        
        return cancelled
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
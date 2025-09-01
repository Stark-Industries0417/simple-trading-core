package com.trading.matching.application

import com.trading.common.dto.order.OrderDTO
import com.trading.common.event.order.OrderCreatedEvent
import com.trading.common.logging.StructuredLogger
import org.slf4j.LoggerFactory
import com.trading.matching.infrastructure.engine.EngineMetrics
import com.trading.matching.infrastructure.engine.MatchingEngineManager
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicLong


@Service
class MatchingService(
    private val matchingEngineManager: MatchingEngineManager,
    private val matchingMetrics: MatchingMetrics,
    private val structuredLogger: StructuredLogger
) {
    
    companion object {
        private val logger = LoggerFactory.getLogger(MatchingService::class.java)
    }
    

    @EventListener
    fun handleOrderCreatedEvent(event: OrderCreatedEvent) {
        val startTime = System.currentTimeMillis()
        
        structuredLogger.info(
            "Order received for matching",
            buildMap {
                put("eventId", event.eventId)
                put("orderId", event.order.orderId)
                put("userId", event.order.userId)
                put("symbol", event.order.symbol)
                put("side", event.order.side.name)
                put("orderType", event.order.orderType.name)
                put("quantity", event.order.quantity.toString())
                event.order.price?.let { put("price", it.toString()) }
                put("traceId", event.traceId)
            }
        )
        
        try {
            val submitted = matchingEngineManager.submitOrder(event.order, event.traceId)
            
            if (submitted) {
                matchingMetrics.incrementOrdersSubmitted()
                
                val duration = System.currentTimeMillis() - startTime
                matchingMetrics.recordOrderSubmission(duration)
                
                structuredLogger.info(
                    "Order submitted to matching engine",
                    mapOf(
                        "orderId" to event.order.orderId,
                        "symbol" to event.order.symbol,
                        "duration" to duration.toString(),
                        "traceId" to event.traceId
                    )
                )
            } else {
                matchingMetrics.incrementOrdersRejected()
                
                structuredLogger.warn(
                    "Order rejected by matching engine",
                    mapOf(
                        "orderId" to event.order.orderId,
                        "symbol" to event.order.symbol,
                        "traceId" to event.traceId
                    )
                )
            }
            
        } catch (e: Exception) {
            matchingMetrics.incrementErrors()
            
            structuredLogger.error(
                "Error submitting order to matching engine",
                buildMap {
                    put("orderId", event.order.orderId)
                    put("symbol", event.order.symbol)
                    e.message?.let { put("error", it) }
                    put("errorType", e.javaClass.simpleName)
                    put("traceId", event.traceId)
                }
            )
        }
    }
    

    fun getEngineMetrics(): EngineMetrics {
        return matchingEngineManager.getMetrics()
    }
    

    fun getApplicationMetrics(): Map<String, Any> {
        val engineMetrics = getEngineMetrics()
        
        return mapOf(
            "engine" to mapOf(
                "workers" to engineMetrics.workerCount,
                "symbols" to engineMetrics.totalSymbols,
                "queueSize" to engineMetrics.totalQueueSize
            ),
            "orders" to mapOf(
                "submitted" to matchingMetrics.getOrdersSubmitted(),
                "rejected" to matchingMetrics.getOrdersRejected(),
                "processed" to engineMetrics.totalOrdersProcessed
            ),
            "trades" to mapOf(
                "executed" to engineMetrics.totalTradesExecuted
            ),
            "errors" to matchingMetrics.getErrors(),
            "performance" to mapOf(
                "avgSubmissionTimeMs" to matchingMetrics.getAverageSubmissionTime()
            )
        )
    }
}


interface MatchingMetrics {
    fun incrementOrdersSubmitted()
    fun incrementOrdersRejected()
    fun incrementErrors()
    fun recordOrderSubmission(durationMs: Long)
    fun getOrdersSubmitted(): Long
    fun getOrdersRejected(): Long
    fun getErrors(): Long
    fun getAverageSubmissionTime(): Double
}


@Service
class MatchingMetricsImpl : MatchingMetrics {
    private val ordersSubmitted = AtomicLong(0)
    private val ordersRejected = AtomicLong(0)
    private val errors = AtomicLong(0)
    private val totalSubmissionTime = AtomicLong(0)
    private val submissionCount = AtomicLong(0)
    
    override fun incrementOrdersSubmitted() {
        ordersSubmitted.incrementAndGet()
    }
    
    override fun incrementOrdersRejected() {
        ordersRejected.incrementAndGet()
    }
    
    override fun incrementErrors() {
        errors.incrementAndGet()
    }
    
    override fun recordOrderSubmission(durationMs: Long) {
        totalSubmissionTime.addAndGet(durationMs)
        submissionCount.incrementAndGet()
    }
    
    override fun getOrdersSubmitted(): Long = ordersSubmitted.get()
    override fun getOrdersRejected(): Long = ordersRejected.get()
    override fun getErrors(): Long = errors.get()
    
    override fun getAverageSubmissionTime(): Double {
        val count = submissionCount.get()
        return if (count > 0) {
            totalSubmissionTime.get().toDouble() / count
        } else {
            0.0
        }
    }
}
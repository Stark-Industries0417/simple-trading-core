package com.trading.matching.infrastructure.engine

import com.trading.common.dto.cdc.order.OrderCreatedDto
import com.trading.common.dto.order.OrderType
import org.slf4j.LoggerFactory
import com.trading.matching.domain.OrderBook
import com.trading.matching.domain.Trade
import com.trading.matching.infrastructure.resilience.CircuitBreaker
import com.trading.matching.infrastructure.monitoring.MatchingMetrics
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong


class MatchingWorker(
    val id: Int,
    private val matchingMetrics: MatchingMetrics
) : Runnable {
    
    companion object {
        private val logger = LoggerFactory.getLogger(MatchingWorker::class.java)
    }
    
    private val orderQueue = LinkedBlockingQueue<OrderCreatedDto>(100_000)
    private val removeQueue = LinkedBlockingQueue<CancelRequest>(10_000)
    private val orderBooks = ConcurrentHashMap<String, OrderBook>()
    
    private val ordersProcessed = AtomicLong(0)
    private val ordersRejected = AtomicLong(0)
    private val tradesExecuted = AtomicLong(0)
    
    private val circuitBreaker = CircuitBreaker(
        failureThreshold = 10,
        resetTimeoutMs = 30_000,
        halfOpenRequests = 5
    )
    
    @Volatile
    private var running = true
    @Volatile
    private var processingComplete = false
    
    // Store trades temporarily for retrieval
    private val recentTrades = ConcurrentHashMap<String, MutableList<Trade>>()
    
    fun cancelOrder(orderId: String, symbol: String, traceId: String = ""): Boolean {
        return try {
            val request = CancelRequest(orderId, symbol)
            val offered = removeQueue.offer(request)
            
            if (!offered) {
                logger.warn(
                    "Cancel queue full",
                    mapOf(
                        "workerId" to id,
                        "orderId" to orderId,
                        "symbol" to symbol,
                        "queueSize" to removeQueue.size
                    )
                )
                false
            } else {
                // Successfully queued for cancellation
                // Actual cancellation will happen asynchronously in processCancellations()
                true
            }
        } catch (e: Exception) {
            logger.error(
                "Failed to submit cancel request",
                mapOf(
                    "workerId" to id,
                    "orderId" to orderId,
                    "symbol" to symbol,
                    "error" to e.message
                )
            )
            false
        }
    }
    
    fun submitOrder(order: OrderCreatedDto): Boolean {
        return try {
            circuitBreaker.execute {
                val offered = orderQueue.offer(order)
                
                if (!offered) {
                    ordersRejected.incrementAndGet()
                    logger.warn(
                        "Order queue full",
                        mapOf(
                            "workerId" to id,
                            "orderId" to order.orderId,
                            "symbol" to order.symbol,
                            "queueSize" to orderQueue.size
                        )
                    )
                    false
                } else {
                    true
                }
            }
        } catch (e: Exception) {
            logger.error(
                "Failed to submit order",
                mapOf(
                    "workerId" to id,
                    "orderId" to order.orderId,
                    "symbol" to order.symbol,
                    "error" to e.message
                )
            )
            false
        }
    }
    
    override fun run() {
        Thread.currentThread().name = "matching-worker-$id"
        logger.info("MatchingWorker started", mapOf("workerId" to id))
        
        while (running) {
            try {
                processNextBatch()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                logger.info("MatchingWorker interrupted", mapOf("workerId" to id))
                break
            } catch (e: Exception) {
                logger.error(
                    "Unexpected error in MatchingWorker",
                    mapOf(
                        "workerId" to id,
                        "error" to e.message,
                        "errorType" to e.javaClass.simpleName
                    )
                )
            }
        }
        
        processingComplete = true
        logger.info(
            "MatchingWorker stopped",
            mapOf(
                "workerId" to id,
                "ordersProcessed" to ordersProcessed.get(),
                "tradesExecuted" to tradesExecuted.get()
            )
        )
    }
    
    private fun processNextBatch() {
        processCancellations()
        
        // Then process orders
        val batch = mutableListOf<OrderCreatedDto>()
        val maxBatchSize = 100
        val timeout = 10L
        
        val firstOrder = orderQueue.poll(timeout, TimeUnit.MILLISECONDS)
        if (firstOrder != null) {
            batch.add(firstOrder)
            orderQueue.drainTo(batch, maxBatchSize - 1)
        }
        
        if (batch.isEmpty()) {
            Thread.sleep(1)
            return
        }
        
        val ordersBySymbol = batch.groupBy { it.symbol }
        
        ordersBySymbol.forEach { (symbol, orders) ->
            val orderBook = orderBooks.computeIfAbsent(symbol) { OrderBook(symbol) }
            
            orders.forEach { orderWithContext ->
                processOrderInternal(orderBook, orderWithContext)
            }

            // Update OrderBook size metrics
            matchingMetrics.updateOrderBookSize(
                symbol,
                "BUY",
                orderBook.getBuyOrderCount()
            )
            matchingMetrics.updateOrderBookSize(
                symbol,
                "SELL",
                orderBook.getSellOrderCount()
            )
        }
    }
    
    private fun processCancellations() {
        val cancellations = mutableListOf<CancelRequest>()
        removeQueue.drainTo(cancellations, 10)

        matchingMetrics.updateQueueSize("order_queue_$id", orderQueue.size)
        matchingMetrics.updateQueueSize("cancel_queue_$id", removeQueue.size)
        
        cancellations.forEach { request ->
            val orderBook = orderBooks[request.symbol]
            if (orderBook != null) {
                val cancelled = orderBook.removeOrderFromBook(request.orderId)
                if (cancelled) {
                    logger.info(
                        "Order cancelled in order book",
                        mapOf(
                            "workerId" to id,
                            "orderId" to request.orderId,
                            "symbol" to request.symbol,
                        )
                    )
                } else {
                    logger.debug(
                        "Order not found in order book",
                        mapOf(
                            "workerId" to id,
                            "orderId" to request.orderId,
                            "symbol" to request.symbol,
                        )
                    )
                }
            }
        }
    }
    
    private fun processOrderInternal(orderBook: OrderBook, order: OrderCreatedDto) {
        val startTime = System.currentTimeMillis()
        try {
            logger.debug(
                "Processing order",
                mapOf(
                    "workerId" to id,
                    "orderId" to order.orderId,
                    "symbol" to order.symbol,
                    "side" to order.side,
                    "orderType" to order.orderType,
                    "quantity" to order.quantity.toString(),
                    "price" to order.price?.toString(),
                )
            )
            
            val trades = when (order.orderType) {
                OrderType.MARKET -> orderBook.processMarketOrder(order)
                OrderType.LIMIT -> orderBook.processLimitOrder(order)
                else -> {
                    logger.warn(
                        "Unsupported order type",
                        mapOf(
                            "orderId" to order.orderId,
                            "orderType" to order.orderType.name
                        )
                    )
                    emptyList()
                }
            }
            
            if (trades.isNotEmpty()) {
                recentTrades
                    .computeIfAbsent(order.orderId) { mutableListOf() }.addAll(trades)

                trades.forEach { trade ->
                    matchingMetrics.recordTradeExecuted(
                        order.symbol,
                        trade.quantity.toLong()
                    )
                }
            }
            tradesExecuted.addAndGet(trades.size.toLong())
            ordersProcessed.incrementAndGet()

            val duration = System.currentTimeMillis() - startTime
            matchingMetrics.recordMatchingAttempt(order.symbol, trades.isNotEmpty())
            matchingMetrics.recordMatchingLatency(duration, order.symbol)
        } catch (e: Exception) {
            logger.error(
                "Error processing order",
                mapOf(
                    "workerId" to id,
                    "orderId" to order.orderId,
                    "symbol" to order.symbol,
                    "error" to e.message,
                    "errorType" to e.javaClass.simpleName,
                )
            )
        }
    }
    
    
    fun shutdown() {
        running = false
    }
    
    fun waitForCompletion(timeout: Long, unit: TimeUnit): Boolean {
        shutdown()
        val deadline = System.currentTimeMillis() + unit.toMillis(timeout)
        
        while (!processingComplete && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        
        return processingComplete
    }
    
    fun getManagedSymbolCount(): Int = orderBooks.size
    fun getQueueSize(): Int = orderQueue.size
    fun getOrdersProcessed(): Long = ordersProcessed.get()
    fun getOrdersRejected(): Long = ordersRejected.get()
    fun getTradesExecuted(): Long = tradesExecuted.get()
    
    fun getTradesForOrder(orderId: String): List<Trade> {
        val trades = recentTrades.remove(orderId)
        return trades ?: emptyList()
    }
    
    fun getMetrics(): Map<String, Any> = mapOf(
        "workerId" to id,
        "managedSymbols" to getManagedSymbolCount(),
        "queueSize" to getQueueSize(),
        "ordersProcessed" to getOrdersProcessed(),
        "ordersRejected" to getOrdersRejected(),
        "tradesExecuted" to getTradesExecuted(),
        "circuitBreakerState" to circuitBreaker.getState()
    )
    
    private data class CancelRequest(
        val orderId: String,
        val symbol: String,
    )
}
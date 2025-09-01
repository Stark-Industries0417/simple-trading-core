package com.trading.matching.infrastructure.engine

import com.trading.common.dto.order.OrderDTO
import com.trading.common.dto.order.OrderType
import com.trading.common.event.base.EventPublisher
import com.trading.common.event.TradeExecutedEvent
import org.slf4j.LoggerFactory
import com.trading.common.util.UUIDv7Generator
import com.trading.matching.domain.OrderBook
import com.trading.matching.domain.Trade
import com.trading.matching.infrastructure.resilience.CircuitBreaker
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong


class MatchingWorker(
    val id: Int,
    private val eventPublisher: EventPublisher,
    private val uuidGenerator: UUIDv7Generator = UUIDv7Generator()
) : Runnable {
    
    companion object {
        private val logger = LoggerFactory.getLogger(MatchingWorker::class.java)
    }
    
    private val orderQueue = LinkedBlockingQueue<OrderWithContext>(100_000)
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
    
    fun submitOrder(order: OrderDTO, traceId: String = ""): Boolean {
        return try {
            circuitBreaker.execute {
                val orderWithContext = OrderWithContext(order, traceId)
                val offered = orderQueue.offer(orderWithContext)
                
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
        val batch = mutableListOf<OrderWithContext>()
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
        
        val ordersBySymbol = batch.groupBy { it.order.symbol }
        
        ordersBySymbol.forEach { (symbol, orders) ->
            val orderBook = orderBooks.computeIfAbsent(symbol) { OrderBook(symbol) }
            
            orders.forEach { orderWithContext ->
                processOrderInternal(orderBook, orderWithContext)
            }
        }
    }
    
    private fun processOrderInternal(orderBook: OrderBook, orderWithContext: OrderWithContext) {
        val startTime = System.nanoTime()
        val order = orderWithContext.order
        val traceId = orderWithContext.traceId
        
        try {
            logger.debug(
                "Processing order",
                mapOf(
                    "workerId" to id,
                    "orderId" to order.orderId,
                    "symbol" to order.symbol,
                    "side" to order.side.name,
                    "orderType" to order.orderType.name,
                    "quantity" to order.quantity.toString(),
                    "price" to order.price?.toString(),
                    "traceId" to traceId
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
            
            trades.forEach { trade ->
                publishTradeEvent(trade, traceId)
                tradesExecuted.incrementAndGet()
            }
            
            if (order.quantity > BigDecimal.ZERO && order.orderType == OrderType.LIMIT) {
                logger.debug(
                    "Order partially filled",
                    mapOf(
                        "orderId" to order.orderId,
                        "remainingQuantity" to order.quantity.toString()
                    )
                )
            }
            
            ordersProcessed.incrementAndGet()
            
            val latencyNanos = System.nanoTime() - startTime
            if (latencyNanos > 50_000_000) {

                logger.warn(
                    "High order processing latency",
                    mapOf(
                        "workerId" to id,
                        "orderId" to order.orderId,
                        "latencyMs" to (latencyNanos / 1_000_000).toString()
                    )
                )
            }
            
        } catch (e: Exception) {
            logger.error(
                "Error processing order",
                mapOf(
                    "workerId" to id,
                    "orderId" to order.orderId,
                    "symbol" to order.symbol,
                    "error" to e.message,
                    "errorType" to e.javaClass.simpleName,
                    "traceId" to traceId
                )
            )
        }
    }
    
    private fun publishTradeEvent(trade: Trade, traceId: String) {
        try {
            val event = TradeExecutedEvent(
                eventId = uuidGenerator.generateEventId(),
                aggregateId = trade.tradeId,
                occurredAt = Instant.now(),
                traceId = traceId,
                tradeId = trade.tradeId,
                symbol = trade.symbol,
                buyOrderId = trade.buyOrderId,
                sellOrderId = trade.sellOrderId,
                buyUserId = trade.buyUserId,
                sellUserId = trade.sellUserId,
                price = trade.price,
                quantity = trade.quantity,
                timestamp = trade.timestamp
            )
            
            eventPublisher.publish(event)
            
            logger.info(
                "Trade executed",
                mapOf(
                    "workerId" to id,
                    "tradeId" to trade.tradeId,
                    "symbol" to trade.symbol,
                    "price" to trade.price.toString(),
                    "quantity" to trade.quantity.toString(),
                    "buyOrderId" to trade.buyOrderId,
                    "sellOrderId" to trade.sellOrderId,
                    "traceId" to traceId
                )
            )
            
        } catch (e: Exception) {
            logger.error(
                "Failed to publish trade event",
                mapOf(
                    "workerId" to id,
                    "tradeId" to trade.tradeId,
                    "error" to e.message,
                    "traceId" to traceId
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
    
    fun getMetrics(): Map<String, Any> = mapOf(
        "workerId" to id,
        "managedSymbols" to getManagedSymbolCount(),
        "queueSize" to getQueueSize(),
        "ordersProcessed" to getOrdersProcessed(),
        "ordersRejected" to getOrdersRejected(),
        "tradesExecuted" to getTradesExecuted(),
        "circuitBreakerState" to circuitBreaker.getState()
    )
    
    private data class OrderWithContext(
        val order: OrderDTO,
        val traceId: String
    )
}
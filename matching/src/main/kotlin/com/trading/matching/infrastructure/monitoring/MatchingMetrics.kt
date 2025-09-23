package com.trading.matching.infrastructure.monitoring

import io.micrometer.core.instrument.*
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@Component
class MatchingMetrics(
    private val meterRegistry: MeterRegistry
) {
    private val orderBookSizes = ConcurrentHashMap<String, AtomicLong>()
    private val queueSizes = ConcurrentHashMap<String, AtomicLong>()

    private val matchingAttemptCounter: Counter = Counter.builder("matching.attempt.total")
        .description("Total matching attempts")
        .register(meterRegistry)

    private val matchingSuccessCounter: Counter = Counter.builder("matching.success.total")
        .description("Successful matches")
        .register(meterRegistry)

    private val matchingFailureCounter: Counter = Counter.builder("matching.failure.total")
        .description("Failed matches")
        .register(meterRegistry)

    private val tradeExecutedCounter: Counter = Counter.builder("matching.trade.executed.total")
        .description("Total trades executed")
        .register(meterRegistry)

    private val orderRejectedCounter: Counter = Counter.builder("matching.order.rejected.total")
        .description("Orders rejected due to backpressure or queue full")
        .register(meterRegistry)

    private val matchingLatencyTimer: Timer = Timer.builder("matching.latency")
        .description("Matching engine processing latency")
        .publishPercentiles(0.5, 0.95, 0.99)
        .publishPercentileHistogram()
        .register(meterRegistry)

    fun recordMatchingAttempt(symbol: String, success: Boolean) {
        matchingAttemptCounter.increment()
        if (success) {
            matchingSuccessCounter.increment()
        } else {
            matchingFailureCounter.increment()
        }
    }

    fun recordMatchingLatency(durationMs: Long, symbol: String) {
        matchingLatencyTimer.record(durationMs, TimeUnit.MILLISECONDS)
    }

    fun recordTradeExecuted(symbol: String, quantity: Long) {
        tradeExecutedCounter.increment()
        meterRegistry.counter("matching.trade.volume", "symbol", symbol)
            .increment(quantity.toDouble())
    }

    fun recordOrderRejected(symbol: String, reason: String) {
        orderRejectedCounter.increment()
        meterRegistry.counter("matching.order.rejected.by.reason", "reason", reason)
            .increment()
    }

    fun updateOrderBookSize(symbol: String, side: String, size: Int) {
        meterRegistry.gauge("matching.orderbook.size",
            Tags.of("symbol", symbol, "side", side),
            size)
    }

    fun updateQueueSize(queue: String, size: Int) {
        val key = "queue.$queue"
        queueSizes.computeIfAbsent(key) { AtomicLong() }.set(size.toLong())
        meterRegistry.gauge("matching.queue.size",
            Tags.of("queue", queue),
            size)
    }

    fun recordBackpressure(symbol: String, rejected: Boolean) {
        meterRegistry.counter("matching.backpressure",
            Tags.of("symbol", symbol, "rejected", rejected.toString()))
            .increment()
    }

    fun recordWorkerLoad(workerId: Int, queueSize: Int, activeSymbols: Int) {
        meterRegistry.gauge("matching.worker.queue.size",
            Tags.of("worker", workerId.toString()),
            queueSize)

        meterRegistry.gauge("matching.worker.symbols.active",
            Tags.of("worker", workerId.toString()),
            activeSymbols)
    }
}
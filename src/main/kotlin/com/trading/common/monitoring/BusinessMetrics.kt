package com.trading.common.monitoring
import io.micrometer.core.instrument.*
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.DoubleAdder
@Component
class BusinessMetrics(private val meterRegistry: MeterRegistry) {
    private val tradingVolume: DoubleAdder = DoubleAdder()
    private val activeOrdersCount: AtomicLong = AtomicLong(0)
    init {
        meterRegistry.gauge("trading.volume.total", tradingVolume) { it.sum() }
        meterRegistry.gauge("trading.orders.active", activeOrdersCount) { it.get().toDouble() }
    }

    fun recordOrderCreated(symbol: String, orderType: String, side: String) {
        Counter.builder("trading.orders.created.total")
            .description("Total number of orders created")
            .tags("symbol", symbol, "type", orderType, "side", side)
            .register(meterRegistry)
            .increment()
        activeOrdersCount.incrementAndGet()
    }

    fun recordOrderMatched(symbol: String, processingTimeMs: Long) {
        Counter.builder("trading.orders.matched.total")
            .description("Total number of orders matched")
            .tags("symbol", symbol)
            .register(meterRegistry)
            .increment()
        Timer.builder("trading.orders.processing.duration")
            .description("Time taken to process orders")
            .tags("symbol", symbol)
            .register(meterRegistry)
            .record(Duration.ofMillis(processingTimeMs))
        activeOrdersCount.decrementAndGet()
    }

    fun recordOrderCancelled(symbol: String, reason: String) {
        Counter.builder("trading.orders.cancelled.total")
            .description("Total number of orders cancelled")
            .tags("symbol", symbol, "reason", reason)
            .register(meterRegistry)
            .increment()
        activeOrdersCount.decrementAndGet()
    }

    fun recordTradeExecuted(symbol: String, volume: BigDecimal, price: BigDecimal) {
        Counter.builder("trading.trades.executed.total")
            .description("Total number of trades executed")
            .tags("symbol", symbol)
            .register(meterRegistry)
            .increment()
        val tradeValue = volume.multiply(price).toDouble()
        tradingVolume.add(tradeValue)
    }

    fun recordAccountUpdate(userId: String, success: Boolean) {
        val counterName = if (success) "trading.accounts.updates.total" else "trading.accounts.failures.total"
        val description = if (success) "Total number of account updates" else "Total number of account update failures"
        Counter.builder(counterName)
            .description(description)
            .tags("userId", userId, "success", success.toString())
            .register(meterRegistry)
            .increment()
    }

    fun recordMarketDataUpdate(symbol: String) {
        Counter.builder("trading.marketdata.updates.total")
            .description("Total number of market data updates")
            .tags("symbol", symbol)
            .register(meterRegistry)
            .increment()
    }

    fun setActiveOrdersCount(count: Long) {
        activeOrdersCount.set(count)
    }

    fun resetTradingVolume() {
        tradingVolume.reset()
    }
}

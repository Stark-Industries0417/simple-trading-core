package com.trading.app.config
import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigDecimal

@ConfigurationProperties(prefix = "trading")
data class TradingProperties(
    val marketData: MarketDataProperties = MarketDataProperties(),
    val matching: MatchingProperties = MatchingProperties(),
    val event: EventProperties = EventProperties(),
    val account: AccountProperties = AccountProperties(),
    val tracing: TracingProperties = TracingProperties()
)
data class MarketDataProperties(
    val generator: GeneratorProperties = GeneratorProperties()
)
data class GeneratorProperties(
    val enabled: Boolean = false,
    val symbols: List<String> = listOf("AAPL", "GOOGL", "MSFT"),
    val intervalMs: Long = 1000,
    val priceVolatility: Double = 0.02
)
data class MatchingProperties(
    val engine: MatchingEngineProperties = MatchingEngineProperties()
)
data class MatchingEngineProperties(
    val enabled: Boolean = true,
    val processingMode: ProcessingMode = ProcessingMode.SINGLE_THREAD,
    val batchSize: Int = 100,
    val maxOrdersPerSymbol: Int = 10000
)
enum class ProcessingMode {
    SINGLE_THREAD,
    MULTI_THREAD
}
data class EventProperties(
    val async: AsyncEventProperties = AsyncEventProperties()
)
data class AsyncEventProperties(
    val enabled: Boolean = true,
    val corePoolSize: Int = 2,
    val maxPoolSize: Int = 4
)
data class AccountProperties(
    val initialCashBalance: BigDecimal = BigDecimal("100000.00")
)
data class TracingProperties(
    val enabled: Boolean = true
)

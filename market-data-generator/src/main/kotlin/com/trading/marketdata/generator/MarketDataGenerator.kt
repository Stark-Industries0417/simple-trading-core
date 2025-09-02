package com.trading.marketdata.generator

import com.trading.common.event.market.MarketDataUpdatedEvent
import com.trading.common.dto.market.MarketDataDTO
import com.trading.common.util.TraceIdGenerator
import com.trading.common.util.UUIDv7Generator
import com.trading.marketdata.config.MarketDataConfig
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.concurrent.*
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import com.google.common.util.concurrent.ThreadFactoryBuilder
import kotlin.math.abs

class MarketDataGenerator(
    private val config: MarketDataConfig,
    private val eventPublisher: ApplicationEventPublisher,
    private val uuidGenerator: UUIDv7Generator,
    private val traceIdGenerator: TraceIdGenerator
) {
    companion object {
        private val logger = LoggerFactory.getLogger(MarketDataGenerator::class.java)
        private const val TICK_SIZE = "0.01"
        private const val MAX_PRICE_CHANGE_PERCENT = 0.10
        private const val DRIFT = 0.0001
    }

    private lateinit var scheduler: ScheduledExecutorService
    private val priceData = ConcurrentHashMap<String, PriceData>()
    private val random = ThreadLocalRandom.current()

    data class PriceData(
        var currentPrice: BigDecimal,
        var previousPrice: BigDecimal,
        var dailyHigh: BigDecimal,
        var dailyLow: BigDecimal,
        var totalVolume: BigDecimal
    )

    @PostConstruct
    fun start() {
        if (!config.enabled) {
            logger.info("Market data generator is disabled")
            return
        }

        initializePriceData()

        val threadFactory = ThreadFactoryBuilder()
            .setNameFormat("market-data-generator-%d")
            .setUncaughtExceptionHandler { thread, exception ->
                logger.error("Uncaught exception in thread {}", thread.name, exception)
            }
            .build()

        scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory)

        scheduler.scheduleAtFixedRate(
            this::generateAndPublishBatch,
            config.startupDelay.toMillis(),
            config.updateIntervalMs,
            TimeUnit.MILLISECONDS
        )

        logger.info(
            "Market data generator started for symbols: {} with interval: {}ms",
            config.symbols,
            config.updateIntervalMs
        )
    }

    private fun initializePriceData() {
        config.symbols.forEach { symbol ->
            val initialPrice = config.initialPrices[symbol] ?: config.defaultInitialPrice
            priceData[symbol] = PriceData(
                currentPrice = initialPrice,
                previousPrice = initialPrice,
                dailyHigh = initialPrice,
                dailyLow = initialPrice,
                totalVolume = BigDecimal.ZERO
            )
        }
    }

    private fun generateAndPublishBatch() {
        try {
            val batchTraceId = traceIdGenerator.generate()
            val batchTimestamp = Instant.now()

            config.symbols.forEach { symbol ->
                generateAndPublishSingle(symbol, batchTraceId, batchTimestamp)
            }

        } catch (e: Exception) {
            logger.error("Error in market data generation batch", e)
        }
    }

    private fun generateAndPublishSingle(
        symbol: String,
        traceId: String,
        timestamp: Instant
    ) {
        val data = priceData[symbol] ?: return

        val newPrice = generateNextPrice(data.currentPrice, symbol)
        val volume = generateVolume(data.currentPrice, newPrice)

        data.previousPrice = data.currentPrice
        data.currentPrice = newPrice
        data.dailyHigh = data.dailyHigh.max(newPrice)
        data.dailyLow = data.dailyLow.min(newPrice)
        data.totalVolume = data.totalVolume.add(volume)

        val marketDataDTO = MarketDataDTO(
            symbol = symbol,
            price = newPrice,
            volume = volume,
            timestamp = timestamp,
            bid = calculateBid(newPrice),
            ask = calculateAsk(newPrice),
            bidSize = generateOrderSize(),
            askSize = generateOrderSize()
        )

        val event = MarketDataUpdatedEvent(
            eventId = uuidGenerator.generateEventId(),
            aggregateId = symbol,
            occurredAt = timestamp,
            traceId = traceId,
            marketData = marketDataDTO
        )

        eventPublisher.publishEvent(event)

        if (data.totalVolume.toLong() % 100 == 0L) {
            logger.debug(
                "Market data: symbol={}, price={}, change={}%, volume={}",
                symbol,
                newPrice,
                calculateChangePercent(data.previousPrice, newPrice),
                data.totalVolume
            )
        }
    }

    private fun generateNextPrice(currentPrice: BigDecimal, symbol: String): BigDecimal {
        val volatility = config.symbolVolatility[symbol] ?: config.defaultVolatility

        val dt = config.updateIntervalMs / 1000.0 / 86400.0
        val randomShock = random.nextGaussian() * Math.sqrt(dt)
        val returns = DRIFT * dt + volatility * randomShock

        var newPrice = currentPrice.multiply(BigDecimal.valueOf(1 + returns))

        val maxChange = currentPrice.multiply(BigDecimal.valueOf(MAX_PRICE_CHANGE_PERCENT))
        val minPrice = currentPrice.subtract(maxChange).max(config.minPrice)
        val maxPrice = currentPrice.add(maxChange).min(config.maxPrice)

        newPrice = newPrice.max(minPrice).min(maxPrice)

        val tickSize = BigDecimal(TICK_SIZE)
        newPrice = newPrice.divide(tickSize, 0, RoundingMode.HALF_UP)
            .multiply(tickSize)

        return newPrice
    }

    private fun generateVolume(oldPrice: BigDecimal, newPrice: BigDecimal): BigDecimal {
        val priceChangePercent = abs(
            newPrice.subtract(oldPrice)
                .divide(oldPrice, 4, RoundingMode.HALF_UP)
                .toDouble()
        )

        val volumeMultiplier = 1.0 + priceChangePercent * 10
        val baseVolume = random.nextLong(100, 1000)

        return BigDecimal.valueOf(baseVolume * volumeMultiplier)
            .setScale(0, RoundingMode.HALF_UP)
    }

    private fun calculateBid(price: BigDecimal): BigDecimal {
        val spread = price.multiply(BigDecimal("0.0001"))  // 0.01% 스프레드
        return price.subtract(spread).setScale(2, RoundingMode.HALF_UP)
    }

    private fun calculateAsk(price: BigDecimal): BigDecimal {
        val spread = price.multiply(BigDecimal("0.0001"))  // 0.01% 스프레드
        return price.add(spread).setScale(2, RoundingMode.HALF_UP)
    }

    private fun generateOrderSize(): BigDecimal {
        return BigDecimal.valueOf(random.nextLong(100, 5000))
    }

    private fun calculateChangePercent(oldPrice: BigDecimal, newPrice: BigDecimal): BigDecimal {
        if (oldPrice.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO

        return newPrice.subtract(oldPrice)
            .divide(oldPrice, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal("100"))
    }

    @PreDestroy
    fun stop() {
        if (!this::scheduler.isInitialized) return

        logger.info("Shutting down market data generator...")

        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.error("Market data generator did not terminate")
                }
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
            Thread.currentThread().interrupt()
        }

        logger.info("Market data generator stopped")
    }

    fun isRunning(): Boolean =
        this::scheduler.isInitialized && !scheduler.isShutdown

    fun getCurrentPrice(symbol: String): BigDecimal? =
        priceData[symbol]?.currentPrice

    fun getPriceData(symbol: String): PriceData? =
        priceData[symbol]?.copy()
}
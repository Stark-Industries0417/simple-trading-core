package com.trading.common.monitoring
import com.trading.app.config.TradingProperties
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.time.Instant


@Component("trading-system")
class CustomHealthIndicator(
    private val tradingProperties: TradingProperties
) : HealthIndicator {
    private val lastHealthCheck = AtomicLong(System.currentTimeMillis())
    private val systemStartTime = Instant.now()
    override fun health(): Health {
        lastHealthCheck.set(System.currentTimeMillis())
        return try {
            val uptime = java.time.Duration.between(systemStartTime, Instant.now())
            Health.up()
                .withDetail("status", "Trading system is operational")
                .withDetail("uptime", "${uptime.toHours()}h ${uptime.toMinutes() % 60}m")
                .withDetail("matching-engine", if (tradingProperties.matching.engine.enabled) "enabled" else "disabled")
                .withDetail("market-data-generator", if (tradingProperties.marketData.generator.enabled) "enabled" else "disabled")
                .withDetail("event-processing", if (tradingProperties.event.async.enabled) "async" else "sync")
                .withDetail("last-check", Instant.ofEpochMilli(lastHealthCheck.get()))
                .build()
        } catch (e: Exception) {
            Health.down()
                .withDetail("status", "Trading system has issues")
                .withDetail("error", e.message)
                .withException(e)
                .build()
        }
    }
}

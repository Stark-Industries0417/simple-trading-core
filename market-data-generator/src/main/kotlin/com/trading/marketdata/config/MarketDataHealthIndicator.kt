package com.trading.marketdata.config

import com.trading.marketdata.generator.MarketDataGenerator
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator

/**
 * Health Check for Market Data Generator
 */
class MarketDataHealthIndicator(
    private val generator: MarketDataGenerator
) : HealthIndicator {

    override fun health(): Health {
        return if (generator.isRunning()) {
            Health.up()
                .withDetail("status", "generating")
                .withDetail("symbols", generator.getPriceData("AAPL")?.currentPrice ?: "N/A")
                .build()
        } else {
            Health.down()
                .withDetail("status", "stopped")
                .build()
        }
    }
}
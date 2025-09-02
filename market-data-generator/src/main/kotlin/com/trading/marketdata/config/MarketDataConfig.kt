package com.trading.marketdata.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigDecimal
import java.time.Duration
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import com.trading.common.util.UUIDv7Generator
import com.trading.common.util.TraceIdGenerator
import com.trading.marketdata.generator.MarketDataGenerator
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean


@ConfigurationProperties(prefix = "market.data")
data class MarketDataConfig(
    val enabled: Boolean = false,

    @field:NotEmpty(message = "At least one symbol must be configured")
    val symbols: List<String> = listOf("AAPL", "GOOGL", "MSFT"),

    @field:Positive(message = "Update interval must be positive")
    val updateIntervalMs: Long = 100L,

    val startupDelay: Duration = Duration.ofSeconds(1),

    val defaultVolatility: Double = 0.02,

    @field:Positive(message = "Initial price must be positive")
    val defaultInitialPrice: BigDecimal = BigDecimal("100.00"),

    @field:Positive(message = "Min price must be positive")
    val minPrice: BigDecimal = BigDecimal("0.01"),

    @field:Positive(message = "Max price must be positive")
    val maxPrice: BigDecimal = BigDecimal("100000.00"),

    val initialPrices: Map<String, BigDecimal> = emptyMap(),

    val symbolVolatility: Map<String, Double> = emptyMap()
) {
    init {
        require(updateIntervalMs >= 10) {
            "Update interval must be at least 10ms for stability"
        }
        require(defaultVolatility > 0 && defaultVolatility < 1) {
            "Volatility must be between 0 and 1"
        }
        require(minPrice < maxPrice) {
            "Min price must be less than max price"
        }

        symbolVolatility.forEach { (symbol, volatility) ->
            require(volatility > 0 && volatility < 1) {
                "Volatility for $symbol must be between 0 and 1"
            }
        }

        initialPrices.forEach { (symbol, price) ->
            require(price >= minPrice && price <= maxPrice) {
                "Initial price for $symbol must be between min and max price"
            }
        }
    }
}

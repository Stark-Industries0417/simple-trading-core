package com.trading.order.config

import com.trading.common.adapter.AccountServiceProvider
import com.trading.common.adapter.MarketDataProvider
import com.trading.common.event.base.EventPublisher
import com.trading.common.event.base.SpringEventPublisher
import com.trading.common.util.TraceIdGenerator
import com.trading.common.util.UUIDv7Generator
import com.trading.order.infrastructure.adapter.StubAccountServiceProvider
import com.trading.order.infrastructure.adapter.StubMarketDataProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.math.BigDecimal
import java.time.LocalTime




@Configuration
@EnableJpaRepositories(basePackages = ["com.trading.order"])
@EnableTransactionManagement
class OrderConfig {
    
    @Bean
    @ConfigurationProperties(prefix = "order")
    fun orderProperties(): OrderProperties {
        return OrderProperties()
    }
    
    @Bean
    @Primary
    fun accountServiceProvider(): AccountServiceProvider {
        return StubAccountServiceProvider()
    }
    
    @Bean
    @Primary
    fun marketDataProvider(): MarketDataProvider {
        return StubMarketDataProvider()
    }
    
    @Bean
    @ConditionalOnMissingBean
    fun traceIdGenerator(): TraceIdGenerator {
        return TraceIdGenerator()
    }
    
    @Bean
    @ConditionalOnMissingBean
    fun uuidv7Generator(): UUIDv7Generator {
        return UUIDv7Generator()
    }
}




data class OrderProperties(
    var supportedSymbols: Set<String> = setOf("AAPL", "GOOGL", "TSLA", "MSFT", "AMZN"),

    var minQuantity: BigDecimal = BigDecimal("0.001"),
    var maxQuantity: BigDecimal = BigDecimal("10000.0"),

    var priceDeviationLimit: BigDecimal = BigDecimal("0.10"), // ±10%

    var dailyOrderLimit: Int = 100,

    var marketOrderBuffer: BigDecimal = BigDecimal("1.10"),

    var defaultPageSize: Int = 20,
    var maxPageSize: Int = 100,

    var timeout: TimeoutProperties = TimeoutProperties(),

    var healthCheck: HealthCheckProperties = HealthCheckProperties(),
) {

    data class TimeoutProperties(
        var duration: String = "PT5M",  // ISO-8601 duration format (5 minutes)
        var batchSize: Int = 100,
        var scheduler: SchedulerProperties = SchedulerProperties()
    ) {
        data class SchedulerProperties(
            var interval: Long = 60000  // milliseconds (1 minute)
        )
    }

    data class HealthCheckProperties(
        var maxValidationFailureRate: Double = 0.1,
        var maxErrorRate: Double = 0.05,
        var maxAvgResponseTimeMs: Long = 100,
        var timeoutSeconds: Long = 5
    )
}
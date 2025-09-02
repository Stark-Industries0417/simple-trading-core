package com.trading.order.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.adapter.AccountServiceProvider
import com.trading.common.adapter.MarketDataProvider
import com.trading.common.event.base.EventPublisher
import com.trading.common.event.base.SpringEventPublisher
import com.trading.common.logging.StructuredLogger
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
@EnableJpaRepositories(basePackages = ["com.trading.order.domain"])
@EnableTransactionManagement
class OrderConfig {
    
    @Bean
    @ConfigurationProperties(prefix = "trading.order")
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
    
    @Bean
    @ConditionalOnMissingBean
    fun structuredLogger(objectMapper: ObjectMapper): StructuredLogger {
        return StructuredLogger(objectMapper)
    }
    
    @Bean
    @ConditionalOnMissingBean
    fun eventPublisher(
        applicationEventPublisher: org.springframework.context.ApplicationEventPublisher,
        traceIdGenerator: TraceIdGenerator
    ): EventPublisher {
        return SpringEventPublisher(applicationEventPublisher, traceIdGenerator)
    }
}




data class OrderProperties(
    var supportedSymbols: Set<String> = setOf("AAPL", "GOOGL", "TSLA", "MSFT", "AMZN"),
    
    var minQuantity: BigDecimal = BigDecimal("0.001"),
    var maxQuantity: BigDecimal = BigDecimal("10000.0"),
    
    var priceDeviationLimit: BigDecimal = BigDecimal("0.10"), // ±10%
    
    var marketOpenTime: String = "09:00",
    var marketCloseTime: String = "15:30",
    var timezone: String = "Asia/Seoul",
    
    var dailyOrderLimit: Int = 100,
    
    var marketOrderBuffer: BigDecimal = BigDecimal("1.10"),
    
    var defaultPageSize: Int = 20,
    var maxPageSize: Int = 100,
    
    var healthCheck: HealthCheckProperties = HealthCheckProperties()
) {
    
    fun getMarketOpenTime(): LocalTime = LocalTime.parse(marketOpenTime)
    fun getMarketCloseTime(): LocalTime = LocalTime.parse(marketCloseTime)
    
    data class HealthCheckProperties(
        var maxValidationFailureRate: Double = 0.1,
        var maxErrorRate: Double = 0.05,
        var maxAvgResponseTimeMs: Long = 100,
        var timeoutSeconds: Long = 5
    )
}
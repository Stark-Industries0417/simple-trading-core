package com.trading.order.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
    
    var marketOrderBuffer: BigDecimal = BigDecimal("1.10"), // 10% 버퍼
    
    var defaultPageSize: Int = 20,
    var maxPageSize: Int = 100,
    
    var healthCheck: HealthCheckProperties = HealthCheckProperties()
) {
    
    fun getMarketOpenTime(): LocalTime = LocalTime.parse(marketOpenTime)
    fun getMarketCloseTime(): LocalTime = LocalTime.parse(marketCloseTime)
    
    data class HealthCheckProperties(
        var maxValidationFailureRate: Double = 0.1, // 10%
        var maxErrorRate: Double = 0.05, // 5%
        var maxAvgResponseTimeMs: Long = 100, // 100ms
        var timeoutSeconds: Long = 5
    )
}
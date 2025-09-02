package com.trading.marketdata.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.event.base.EventPublisher
import com.trading.common.event.base.SpringEventPublisher
import com.trading.common.logging.StructuredLogger
import com.trading.common.util.TraceIdGenerator
import com.trading.common.util.UUIDv7Generator
import com.trading.marketdata.generator.MarketDataGenerator
import com.trading.marketdata.service.MarketDataEventListener
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean

/**
 * Market Data 모듈 자동 설정
 * 
 * Spring Boot의 AutoConfiguration 메커니즘을 활용하여
 * market-data-generator 모듈의 빈들을 자동으로 등록합니다.
 */
@Configuration
@EnableConfigurationProperties(MarketDataConfig::class)
@ComponentScan(basePackages = ["com.trading.marketdata"])
@ConditionalOnProperty(
    prefix = "market.data",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class MarketDataAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    fun marketDataGenerator(
        config: MarketDataConfig,
        eventPublisher: ApplicationEventPublisher,
        uuidGenerator: UUIDv7Generator,
        traceIdGenerator: TraceIdGenerator
    ): MarketDataGenerator {
        return MarketDataGenerator(
            config = config,
            eventPublisher = eventPublisher,
            uuidGenerator = uuidGenerator,
            traceIdGenerator = traceIdGenerator
        )
    }
    
    @Bean
    @ConditionalOnMissingBean
    fun marketDataHealthIndicator(
        generator: MarketDataGenerator
    ): HealthIndicator {
        return MarketDataHealthIndicator(generator)
    }
    
    @Bean
    @ConditionalOnMissingBean
    fun uuidv7Generator(): UUIDv7Generator {
        return UUIDv7Generator()
    }
    
    @Bean
    @ConditionalOnMissingBean
    fun traceIdGenerator(): TraceIdGenerator {
        return TraceIdGenerator()
    }
    
    @Bean
    @ConditionalOnMissingBean
    fun structuredLogger(objectMapper: ObjectMapper): StructuredLogger {
        return StructuredLogger(objectMapper)
    }
    
    @Bean
    @ConditionalOnMissingBean
    fun eventPublisher(
        applicationEventPublisher: ApplicationEventPublisher,
        traceIdGenerator: TraceIdGenerator
    ): EventPublisher {
        return SpringEventPublisher(applicationEventPublisher, traceIdGenerator)
    }
    
    /**
     * ConfigurationProperties 검증을 위한 Bean
     */
    @Bean
    @ConditionalOnMissingBean
    fun configurationPropertiesValidator(): LocalValidatorFactoryBean {
        return LocalValidatorFactoryBean()
    }
}
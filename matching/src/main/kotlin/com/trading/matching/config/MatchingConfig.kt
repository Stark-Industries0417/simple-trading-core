package com.trading.matching.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.event.base.EventPublisher
import com.trading.common.event.base.SpringEventPublisher
import com.trading.common.logging.StructuredLogger
import com.trading.common.util.TraceIdGenerator
import com.trading.common.util.UUIDv7Generator
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ComponentScan

/**
 * Matching 모듈 설정
 * 
 * MSA 분리를 위한 독립적인 빈 설정을 관리합니다.
 */
@Configuration
@ComponentScan(basePackages = ["com.trading.matching"])
class MatchingConfig {
    
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
}
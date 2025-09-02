package com.trading.matching.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.trading.common.logging.StructuredLogger
import com.trading.common.util.TraceIdGenerator
import com.trading.common.util.UUIDv7Generator
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ComponentScan
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Matching 모듈 설정
 * 
 * Kafka 기반 이벤트 처리를 위한 독립적인 빈 설정을 관리합니다.
 * ApplicationEventPublisher 대신 Kafka를 통해 이벤트를 처리합니다.
 */
@Configuration
@ComponentScan(basePackages = ["com.trading.matching"])
@EnableScheduling // TransactionalMatchingProcessor의 @Scheduled 메서드를 위해 필요
class MatchingConfig {
    
    @Bean
    @ConditionalOnMissingBean
    fun objectMapper(): ObjectMapper {
        return ObjectMapper().apply {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
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
}
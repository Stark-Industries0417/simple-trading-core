package com.trading.account.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties

@Configuration
@EnableKafka
class KafkaConfig(
    private val kafkaProperties: KafkaProperties,
    private val objectMapper: ObjectMapper
) {
    
    @Bean
    fun consumerFactory(): ConsumerFactory<String, String> {
        val props = mutableMapOf<String, Any>()
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaProperties.bootstrapServers
        props[ConsumerConfig.GROUP_ID_CONFIG] = kafkaProperties.consumer.groupId
        
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        
        props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = kafkaProperties.consumer.enableAutoCommit
        props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = kafkaProperties.consumer.autoOffsetReset
        props[ConsumerConfig.ISOLATION_LEVEL_CONFIG] = kafkaProperties.consumer.isolationLevel
        props[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = kafkaProperties.consumer.maxPollRecords
        props[ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG] = kafkaProperties.consumer.sessionTimeoutMs
        
        return DefaultKafkaConsumerFactory(props)
    }
    
    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = consumerFactory()
        
        factory.setConcurrency(3)
        factory.containerProperties.pollTimeout = 3000
        
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        return factory
    }
}
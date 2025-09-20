package com.trading.matching.config

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer


@Configuration
@EnableKafka
class KafkaConsumerConfig(
    private val kafkaProperties: KafkaProperties
) {

    @Bean
    fun consumerFactory(): ConsumerFactory<String, String> {
        val props = HashMap<String, Any>()

        // 연결 설정
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaProperties.bootstrapServers
        props[ConsumerConfig.GROUP_ID_CONFIG] = kafkaProperties.consumer.groupId

        // 오프셋 관리 - 수동 커밋
        props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false // 수동 커밋 활성화
        props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = kafkaProperties.consumer.autoOffsetReset

        // 트랜잭션 격리 수준
        props[ConsumerConfig.ISOLATION_LEVEL_CONFIG] = kafkaProperties.consumer.isolationLevel

        // 폴링 설정
        props[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = kafkaProperties.consumer.maxPollRecords
        props[ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG] = kafkaProperties.consumer.sessionTimeoutMs

        // 역직렬화
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java

        // 에러 핸들링
        props[ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS] = StringDeserializer::class.java
        props[ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS] = StringDeserializer::class.java

        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    fun kafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, String>
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = consumerFactory
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        factory.containerProperties.isSyncCommits = true
        factory.setConcurrency(3)
        return factory
    }
}
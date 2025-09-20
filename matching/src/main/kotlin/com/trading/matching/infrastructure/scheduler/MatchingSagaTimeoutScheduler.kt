package com.trading.matching.infrastructure.scheduler

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.domain.saga.SagaStatus
import com.trading.common.event.saga.SagaTimeoutEvent
import com.trading.common.util.UUIDv7Generator
import com.trading.matching.domain.saga.MatchingSagaRepository
import com.trading.matching.domain.saga.MatchingSagaState
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class MatchingSagaTimeoutScheduler(
    private val sagaRepository: MatchingSagaRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val uuidGenerator: UUIDv7Generator,
    @Value("\${saga.timeouts.matching:10}") private val matchingTimeoutSeconds: Long = 10
) {
    
    @Scheduled(fixedDelay = 3000)
    @Transactional
    fun checkTimeouts() {
        val timedOutSagas = sagaRepository.findTimedOutSagas(
            listOf(SagaStatus.IN_PROGRESS),
            Instant.now()
        )
        timedOutSagas.forEach { saga ->
            try {
                handleTimeout(saga)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun handleTimeout(saga: MatchingSagaState) {
        
        saga.markTimeout()
        sagaRepository.save(saga)

        val timeoutEvent = SagaTimeoutEvent(
            eventId = uuidGenerator.generateEventId(),
            aggregateId = saga.orderId,
            occurredAt = Instant.now(),
            traceId = "",
            sagaId = saga.sagaId,
            orderId = saga.orderId,
            tradeId = saga.tradeId,
            failedAt = "Matching",
            timeoutDuration = matchingTimeoutSeconds,
        )

        kafkaTemplate.send(
            "saga.timeout.events",
            saga.orderId,
            objectMapper.writeValueAsString(timeoutEvent)
        )
    }
}
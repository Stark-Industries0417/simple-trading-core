package com.trading.account.infrastructure.scheduler

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.trading.account.domain.saga.AccountSagaRepository
import com.trading.account.domain.saga.AccountSagaState
import com.trading.common.domain.saga.SagaStatus
import com.trading.common.event.saga.AccountUpdateFailedEvent
import com.trading.common.event.saga.SagaTimeoutEvent
import com.trading.common.logging.StructuredLogger
import com.trading.common.util.UUIDv7Generator
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class AccountSagaTimeoutScheduler(
    private val sagaRepository: AccountSagaRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val structuredLogger: StructuredLogger,
    private val uuidGenerator: UUIDv7Generator,
    @Value("\${saga.timeouts.account:5}") private val accountTimeoutSeconds: Long = 5
) {
    
    @Scheduled(fixedDelay = 2000)
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
                structuredLogger.error("Error handling saga timeout",
                    mapOf(
                        "sagaId" to saga.sagaId,
                        "tradeId" to saga.tradeId,
                        "error" to (e.message ?: "Unknown error")
                    ),
                    e
                )
            }
        }
    }
    
    private fun handleTimeout(saga: AccountSagaState) {
        structuredLogger.error("Account saga timeout detected",
            mapOf(
                "sagaId" to saga.sagaId,
                "tradeId" to saga.tradeId,
                "orderId" to saga.orderId,
                "state" to saga.state.name
            )
        )
        
        saga.markTimeout()
        sagaRepository.save(saga)
        
        val metadata = objectMapper.readValue(saga.metadata ?: "{}", Map::class.java)
        
        // Publish AccountUpdateFailedEvent for saga compensation
        val failedEvent = AccountUpdateFailedEvent(
            eventId = uuidGenerator.generateEventId(),
            aggregateId = saga.tradeId,
            occurredAt = Instant.now(),
            traceId = "",
            sagaId = saga.sagaId,
            tradeId = saga.tradeId,
            orderId = saga.orderId,
            buyUserId = metadata["buyUserId"] as? String,
            sellUserId = metadata["sellUserId"] as? String,
            reason = "Account update timeout after $accountTimeoutSeconds seconds",
            failureType = AccountUpdateFailedEvent.FailureType.TECHNICAL_ERROR,
            shouldRetry = true
        )
        
        val timeoutEventNode = objectMapper.createObjectNode()
        timeoutEventNode.put("eventType", "AccountUpdateFailed")
        timeoutEventNode.put("sagaId", saga.sagaId)
        val failedEventNode = objectMapper.valueToTree<ObjectNode>(failedEvent)
        timeoutEventNode.setAll<ObjectNode>(failedEventNode)
        
        kafkaTemplate.send(
            "account.events",
            metadata["symbol"] as? String ?: "",
            objectMapper.writeValueAsString(timeoutEventNode)
        )
        
        // Publish SagaTimeoutEvent for monitoring
        val timeoutEvent = SagaTimeoutEvent(
            eventId = uuidGenerator.generateEventId(),
            aggregateId = saga.orderId,
            occurredAt = Instant.now(),
            traceId = "",
            sagaId = saga.sagaId,
            orderId = saga.orderId,
            tradeId = saga.tradeId,
            failedAt = "Account",
            timeoutDuration = accountTimeoutSeconds,
            metadata = mapOf("reason" to "Account processing timeout")
        )
        
        kafkaTemplate.send(
            "saga.timeout.events",
            saga.orderId,
            objectMapper.writeValueAsString(timeoutEvent)
        )
        
        structuredLogger.info("Account timeout events published",
            mapOf(
                "sagaId" to saga.sagaId,
                "tradeId" to saga.tradeId,
                "topics" to "account.events, saga.timeout.events"
            )
        )
    }
}
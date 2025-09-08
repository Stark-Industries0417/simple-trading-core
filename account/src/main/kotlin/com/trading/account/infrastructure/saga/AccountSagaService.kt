package com.trading.account.infrastructure.saga

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.trading.account.application.AccountService
import com.trading.account.application.AccountUpdateResult
import com.trading.account.application.RollbackResult
import com.trading.account.domain.saga.AccountSagaRepository
import com.trading.account.domain.saga.AccountSagaState
import com.trading.common.domain.saga.SagaStatus
import com.trading.common.event.matching.TradeExecutedEvent
import com.trading.common.event.saga.AccountRollbackEvent
import com.trading.common.event.saga.AccountUpdateFailedEvent
import com.trading.common.event.saga.AccountUpdatedEvent
import com.trading.common.event.saga.TradeFailedEvent
import com.trading.common.event.saga.TradeRollbackEvent
import com.trading.common.logging.StructuredLogger
import com.trading.common.util.UUIDv7Generator
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class AccountSagaService(
    private val accountService: AccountService,
    private val sagaRepository: AccountSagaRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val structuredLogger: StructuredLogger,
    private val uuidGenerator: UUIDv7Generator,
    @Value("\${saga.timeouts.account:5}") private val accountTimeoutSeconds: Long = 5
) {
    
    @KafkaListener(topics = ["trade.events"], groupId = "account-saga-group")
    fun handleTradeEvent(message: String) {
        try {
            val jsonNode = objectMapper.readTree(message)
            val eventType = jsonNode.get("eventType")?.asText()
            if (eventType == null) {
                structuredLogger.warn("Unknown event format, no eventType found",
                    mapOf("message" to message.take(200))
                )
                return
            }
            val sagaId = jsonNode.get("sagaId")?.asText()
            
            when (eventType) {
                "TradeExecutedEvent" -> {
                    if (sagaId == null) {
                        structuredLogger.error("No sagaId found in TradeExecuted event",
                            mapOf("eventType" to eventType)
                        )
                        return
                    }
                    val event = objectMapper.readValue(message, TradeExecutedEvent::class.java)
                    processAccountUpdate(event, sagaId)
                }
                "TradeRollbackEvent" -> {
                    val event = objectMapper.readValue(message, TradeRollbackEvent::class.java)
                    rollbackAccount(event)
                }
                "TradeFailedEvent" -> {
                    val event = objectMapper.readValue(message, TradeFailedEvent::class.java)
                    handleTradeFailed(event)
                }
            }
        } catch (e: Exception) {
            structuredLogger.error("Error handling trade event",
                mapOf(
                    "error" to (e.message ?: "Unknown error"),
                    "message" to message
                )
            )
        }
    }
    
    private fun processAccountUpdate(event: TradeExecutedEvent, sagaId: String) {
        val startTime = System.currentTimeMillis()
        
        structuredLogger.info("Processing TradeExecutedEvent",
            mapOf(
                "eventId" to event.eventId,
                "sagaId" to sagaId,
                "tradeId" to event.tradeId,
                "symbol" to event.symbol,
                "traceId" to event.traceId
            )
        )
        
        val sagaState = AccountSagaState(
            sagaId = sagaId,
            tradeId = event.tradeId,
            orderId = event.buyOrderId,
            state = SagaStatus.IN_PROGRESS,
            timeoutAt = Instant.now().plusSeconds(accountTimeoutSeconds),
            eventType = "TradeExecutedEvent",
            eventPayload = objectMapper.writeValueAsString(event)
        )
        val savedSaga = sagaRepository.save(sagaState)
        
        try {
            val result = accountService.processTradeExecution(event)
            
            when (result) {
                is AccountUpdateResult.Success -> {
                    savedSaga.markCompleted()
                    sagaRepository.save(savedSaga)
                    
                    val updatedEvent = AccountUpdatedEvent(
                        eventId = uuidGenerator.generateEventId(),
                        aggregateId = event.tradeId,
                        occurredAt = Instant.now(),
                        traceId = event.traceId,
                        sagaId = savedSaga.sagaId,
                        tradeId = event.tradeId,
                        orderId = event.buyOrderId,
                        buyUserId = event.buyUserId,
                        sellUserId = event.sellUserId,
                        amount = event.price * event.quantity,
                        quantity = event.quantity,
                        symbol = event.symbol,
                        buyerNewBalance = result.buyerNewBalance,
                        sellerNewBalance = result.sellerNewBalance
                    )
                    
                    val eventNode = objectMapper.createObjectNode()
                    eventNode.put("eventType", "AccountUpdated")
                    val updatedEventNode = objectMapper.valueToTree<ObjectNode>(updatedEvent)
                    eventNode.setAll<ObjectNode>(updatedEventNode)
                    
                    kafkaTemplate.send(
                        "account.events",
                        event.symbol,
                        objectMapper.writeValueAsString(eventNode)
                    )
                    
                    val duration = System.currentTimeMillis() - startTime
                    structuredLogger.info("Account update completed",
                        mapOf(
                            "sagaId" to savedSaga.sagaId,
                            "tradeId" to event.tradeId,
                            "buyerNewBalance" to result.buyerNewBalance.toString(),
                            "sellerNewBalance" to result.sellerNewBalance.toString(),
                            "duration" to duration.toString()
                        )
                    )
                }
                
                is AccountUpdateResult.Failure -> {
                    handleAccountUpdateFailure(savedSaga, event, result)
                }
            }
            
        } catch (e: Exception) {
            handleAccountUpdateException(savedSaga, event, e)
        }
    }
    
    private fun handleAccountUpdateFailure(
        saga: AccountSagaState,
        event: TradeExecutedEvent,
        result: AccountUpdateResult.Failure
    ) {
        saga.markFailed(result.reason)
        sagaRepository.save(saga)
        
        val failureType = when {
            result.reason.contains("Insufficient balance", ignoreCase = true) -> 
                AccountUpdateFailedEvent.FailureType.INSUFFICIENT_BALANCE
            result.reason.contains("Insufficient shares", ignoreCase = true) -> 
                AccountUpdateFailedEvent.FailureType.INSUFFICIENT_SHARES
            result.reason.contains("Lock", ignoreCase = true) -> 
                AccountUpdateFailedEvent.FailureType.LOCK_TIMEOUT
            result.reason.contains("Validation", ignoreCase = true) -> 
                AccountUpdateFailedEvent.FailureType.VALIDATION_ERROR
            else -> 
                AccountUpdateFailedEvent.FailureType.TECHNICAL_ERROR
        }
        val failedEvent = AccountUpdateFailedEvent(
            eventId = uuidGenerator.generateEventId(),
            aggregateId = event.tradeId,
            occurredAt = Instant.now(),
            traceId = event.traceId,
            sagaId = saga.sagaId,
            tradeId = event.tradeId,
            orderId = event.buyOrderId,
            buyUserId = event.buyUserId,
            sellUserId = event.sellUserId,
            reason = result.reason,
            failureType = failureType,
            shouldRetry = result.shouldRetry
        )
        
        val eventNode = objectMapper.createObjectNode()
        eventNode.put("eventType", "AccountUpdateFailed")
        eventNode.put("sagaId", saga.sagaId)
        val failedEventNode = objectMapper.valueToTree<ObjectNode>(failedEvent)
        eventNode.setAll<ObjectNode>(failedEventNode)
        
        kafkaTemplate.send(
            "account.events",
            event.symbol,
            objectMapper.writeValueAsString(eventNode)
        )
        
        structuredLogger.error("Account update failed",
            mapOf(
                "sagaId" to saga.sagaId,
                "tradeId" to event.tradeId,
                "reason" to result.reason,
                "failureType" to failureType.name,
                "shouldRetry" to result.shouldRetry.toString()
            )
        )
    }
    
    private fun handleAccountUpdateException(
        saga: AccountSagaState,
        event: TradeExecutedEvent,
        exception: Exception
    ) {
        saga.markFailed(exception.message)
        sagaRepository.save(saga)
        
        val failedEvent = AccountUpdateFailedEvent(
            eventId = uuidGenerator.generateEventId(),
            aggregateId = event.tradeId,
            occurredAt = Instant.now(),
            traceId = event.traceId,
            sagaId = saga.sagaId,
            tradeId = event.tradeId,
            orderId = event.buyOrderId,
            buyUserId = event.buyUserId,
            sellUserId = event.sellUserId,
            reason = exception.message ?: "Account update failed",
            failureType = AccountUpdateFailedEvent.FailureType.TECHNICAL_ERROR,
            shouldRetry = false
        )
        
        val eventNode = objectMapper.createObjectNode()
        eventNode.put("eventType", "AccountUpdateFailed")
        eventNode.put("sagaId", saga.sagaId)
        val failedEventNode = objectMapper.valueToTree<ObjectNode>(failedEvent)
        eventNode.setAll<ObjectNode>(failedEventNode)
        
        kafkaTemplate.send(
            "account.events",
            event.symbol,
            objectMapper.writeValueAsString(eventNode)
        )
        
        structuredLogger.error("Account update exception",
            mapOf(
                "sagaId" to saga.sagaId,
                "tradeId" to event.tradeId,
                "error" to (exception.message ?: "Unknown error")
            ),
            exception = exception
        )
    }
    
    private fun rollbackAccount(event: TradeRollbackEvent) {
        val saga = sagaRepository.findBySagaId(event.sagaId)
        if (saga == null) {
            structuredLogger.warn("No saga found for trade rollback",
                mapOf("sagaId" to event.sagaId, "tradeId" to event.tradeId)
            )
            return
        }
        
        if (saga.state != SagaStatus.COMPLETED) {
            structuredLogger.info("Saga not completed, no rollback needed",
                mapOf("sagaId" to event.sagaId, "state" to saga.state.name)
            )
            saga.markCompensated()
            sagaRepository.save(saga)
            return
        }
        
        try {
            val originalEvent = objectMapper.readValue(saga.eventPayload, TradeExecutedEvent::class.java)
            
            val rollbackResult = accountService.rollbackTradeExecution(
                tradeId = event.tradeId,
                buyUserId = originalEvent.buyUserId,
                sellUserId = originalEvent.sellUserId,
                symbol = originalEvent.symbol,
                quantity = originalEvent.quantity,
                price = originalEvent.price,
                traceId = event.traceId
            )

            val rollbackEvent = when (rollbackResult) {
                is RollbackResult.Success -> {
                    saga.markCompensated()
                    sagaRepository.save(saga)

                    structuredLogger.info("Account rollback completed successfully",
                        mapOf(
                            "sagaId" to event.sagaId,
                            "tradeId" to event.tradeId,
                            "buyUserId" to originalEvent.buyUserId,
                            "sellUserId" to originalEvent.sellUserId,
                            "buyerNewBalance" to rollbackResult.buyerNewBalance.toString(),
                            "sellerNewBalance" to rollbackResult.sellerNewBalance.toString()
                        )
                    )

                    AccountRollbackEvent(
                        eventId = uuidGenerator.generateEventId(),
                        aggregateId = event.tradeId,
                        occurredAt = Instant.now(),
                        traceId = event.traceId,
                        sagaId = event.sagaId,
                        tradeId = event.tradeId,
                        orderId = event.orderId,
                        userId = originalEvent.buyUserId,
                        rollbackType = AccountRollbackEvent.RollbackType.REVERSE_TRADE,
                        amount = originalEvent.price * originalEvent.quantity,
                        quantity = originalEvent.quantity,
                        symbol = originalEvent.symbol,
                        success = true,
                        reason = "Trade rollback completed successfully"
                    )
                }

                is RollbackResult.Failure -> {
                    saga.markFailed("Rollback failed: ${rollbackResult.reason}")
                    sagaRepository.save(saga)

                    structuredLogger.error("Account rollback failed",
                        mapOf(
                            "sagaId" to event.sagaId,
                            "tradeId" to event.tradeId,
                            "reason" to rollbackResult.reason,
                            "error" to (rollbackResult.exception.message ?: "Unknown error")
                        ),
                        exception = rollbackResult.exception
                    )

                    AccountRollbackEvent(
                        eventId = uuidGenerator.generateEventId(),
                        aggregateId = event.tradeId,
                        occurredAt = Instant.now(),
                        traceId = event.traceId,
                        sagaId = event.sagaId,
                        tradeId = event.tradeId,
                        orderId = event.orderId,
                        userId = originalEvent.buyUserId,
                        rollbackType = AccountRollbackEvent.RollbackType.REVERSE_TRADE,
                        amount = originalEvent.price * originalEvent.quantity,
                        quantity = originalEvent.quantity,
                        symbol = originalEvent.symbol,
                        success = false,
                        reason = "Rollback failed: ${rollbackResult.reason}"
                    )
                }
            }

            kafkaTemplate.send(
                "account.events",
                originalEvent.symbol,
                objectMapper.writeValueAsString(rollbackEvent)
            )

        } catch (e: Exception) {
            structuredLogger.error("Failed to rollback account",
                mapOf(
                    "sagaId" to event.sagaId,
                    "tradeId" to event.tradeId,
                    "error" to (e.message ?: "Unknown error")
                ),
                exception = e
            )

            saga.markFailed("Rollback exception: ${e.message}")
            sagaRepository.save(saga)
        }
    }
    
    private fun handleTradeFailed(event: TradeFailedEvent) {
        structuredLogger.info("Trade failed, releasing reservations",
            mapOf(
                "sagaId" to event.sagaId,
                "orderId" to event.orderId,
                "symbol" to event.symbol,
                "reason" to event.reason,
                "traceId" to event.traceId
            )
        )
        
        val releaseSuccess = accountService.releaseReservationByOrderId(
            orderId = event.orderId,
            traceId = event.traceId
        )
        
        if (releaseSuccess) {
            structuredLogger.info("Reservation released successfully",
                mapOf(
                    "sagaId" to event.sagaId,
                    "orderId" to event.orderId,
                    "traceId" to event.traceId
                )
            )
        } else {
            structuredLogger.warn("Failed to release reservation",
                mapOf(
                    "sagaId" to event.sagaId,
                    "orderId" to event.orderId,
                    "reason" to event.reason,
                    "traceId" to event.traceId
                )
            )
        }
    }
}
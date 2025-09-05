package com.trading.account.infrastructure.saga

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.trading.account.application.AccountService
import com.trading.account.application.AccountUpdateResult
import com.trading.account.domain.saga.AccountSagaRepository
import com.trading.account.domain.saga.AccountSagaState
import com.trading.common.domain.saga.SagaStatus
import com.trading.common.event.matching.TradeExecutedEvent
import com.trading.common.event.saga.*
import com.trading.common.logging.StructuredLogger
import com.trading.common.util.UUIDv7Generator
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
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
            val sagaId = jsonNode.get("sagaId")?.asText()
            
            when (eventType) {
                "TradeExecuted" -> {
                    val event = objectMapper.readValue(message, TradeExecutedEvent::class.java)
                    processAccountUpdate(event, sagaId ?: "")
                }
                "TradeRollback" -> {
                    val event = objectMapper.readValue(message, TradeRollbackEvent::class.java)
                    rollbackAccount(event)
                }
                "TradeFailed" -> {
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
        
        val sagaState = AccountSagaState(
            sagaId = sagaId.ifEmpty { uuidGenerator.generateEventId() },
            tradeId = event.tradeId,
            orderId = event.buyOrderId,
            state = SagaStatus.IN_PROGRESS,
            timeoutAt = Instant.now().plusSeconds(accountTimeoutSeconds),
            metadata = objectMapper.writeValueAsString(mapOf(
                "tradeId" to event.tradeId,
                "buyOrderId" to event.buyOrderId,
                "sellOrderId" to event.sellOrderId,
                "buyUserId" to event.buyUserId,
                "sellUserId" to event.sellUserId,
                "symbol" to event.symbol,
                "quantity" to event.quantity.toString(),
                "price" to event.price.toString(),
                "amount" to (event.price * event.quantity).toString()
            ))
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
                    eventNode.put("sagaId", savedSaga.sagaId)
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
            val metadata = objectMapper.readValue(saga.metadata ?: "{}", Map::class.java)
            val buyUserId = metadata["buyUserId"] as? String ?: ""
            val sellUserId = metadata["sellUserId"] as? String ?: ""
            val amount = BigDecimal(metadata["amount"] as? String ?: "0")
            val quantity = BigDecimal(metadata["quantity"] as? String ?: "0")
            val symbol = metadata["symbol"] as? String ?: ""

            val rollbackEvent = AccountRollbackEvent(
                eventId = uuidGenerator.generateEventId(),
                aggregateId = event.tradeId,
                occurredAt = Instant.now(),
                traceId = "",
                sagaId = event.sagaId,
                tradeId = event.tradeId,
                orderId = event.orderId,
                userId = buyUserId,
                rollbackType = AccountRollbackEvent.RollbackType.REVERSE_TRADE,
                amount = amount,
                quantity = quantity,
                symbol = symbol,
                success = true,
                reason = "Trade rollback completed"
            )
            
            kafkaTemplate.send(
                "account.events",
                symbol,
                objectMapper.writeValueAsString(rollbackEvent)
            )
            
            
            saga.markCompensated()
            sagaRepository.save(saga)
            
            structuredLogger.info("Account rollback completed",
                mapOf(
                    "sagaId" to event.sagaId,
                    "tradeId" to event.tradeId,
                    "buyUserId" to buyUserId,
                    "sellUserId" to sellUserId,
                    "amount" to amount.toString()
                )
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
        }
    }
    
    private fun handleTradeFailed(event: TradeFailedEvent) {
        structuredLogger.info("Trade failed, no account update needed",
            mapOf(
                "sagaId" to event.sagaId,
                "orderId" to event.orderId,
                "reason" to event.reason
            )
        )
    }
    
    @Scheduled(fixedDelay = 2000)
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
            reason = "Account update timeout after ${accountTimeoutSeconds} seconds",
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
    }
}
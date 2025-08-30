package com.trading.account.saga

import com.trading.account.application.OrderServiceClient
import com.trading.account.infrastructure.persistence.AccountRepository
import com.trading.common.event.AccountUpdateFailedEvent
import com.trading.common.event.EventPublisher
import com.trading.common.logging.StructuredLogger
import com.trading.common.util.UUIDv7Generator
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Component
class AccountSagaOrchestrator(
    private val accountRepository: AccountRepository,
    private val orderServiceClient: OrderServiceClient,
    private val eventPublisher: EventPublisher,
    private val structuredLogger: StructuredLogger
) {
    
    private val retryAttempts = ConcurrentHashMap<String, Int>()
    private val maxRetryAttempts = 3
    private val retryExecutor = Executors.newScheduledThreadPool(2)
    
    private val deadLetterQueue = ConcurrentHashMap<String, AccountUpdateFailedEvent>()
    
    @EventListener
    @Async
    fun handleAccountUpdateFailed(event: AccountUpdateFailedEvent) {
        structuredLogger.warn("Starting compensation for failed account update",
            mapOf(
                "tradeId" to event.relatedTradeId,
                "userId" to event.userId,
                "reason" to event.failureReason,
                "shouldRetry" to event.shouldRetry.toString()
            )
        )
        
        try {
            val orderReverted = orderServiceClient.revertOrderStatus(event.relatedTradeId)
            
            if (!orderReverted) {
                structuredLogger.error("Failed to revert order status",
                    mapOf("tradeId" to event.relatedTradeId)
                )
            }
            
            if (event.shouldRetry) {
                scheduleRetry(event)
            } else {
                releaseReservations(event)
            }
            
            publishCompensationCompletedEvent(event)
            
            structuredLogger.info("Compensation completed successfully",
                mapOf("tradeId" to event.relatedTradeId)
            )
            
        } catch (ex: Exception) {
            handleCompensationFailure(event, ex)
        }
    }
    
    @Transactional
    fun releaseReservations(event: AccountUpdateFailedEvent) {
        try {
            val account = accountRepository.findByUserIdWithLock(event.userId)
            
            if (account != null) {
                account.releaseReservation(event.amount)
                accountRepository.save(account)
                
                structuredLogger.info("Reservations released",
                    mapOf(
                        "userId" to event.userId,
                        "amount" to event.amount.toString()
                    )
                )
            } else {
                structuredLogger.warn("Account not found for reservation release",
                    mapOf("userId" to event.userId)
                )
            }
        } catch (ex: Exception) {
            structuredLogger.error("Failed to release reservations",
                mapOf("userId" to event.userId),
                ex
            )
            throw ex
        }
    }
    
    private fun scheduleRetry(event: AccountUpdateFailedEvent) {
        val attemptCount = retryAttempts.compute(event.relatedTradeId) { _, count ->
            (count ?: 0) + 1
        }
        
        if (attemptCount!! <= maxRetryAttempts) {
            val delay = calculateBackoffDelay(attemptCount)
            
            structuredLogger.info("Scheduling retry for failed transaction",
                mapOf(
                    "tradeId" to event.relatedTradeId,
                    "attempt" to attemptCount.toString(),
                    "delayMs" to delay.toString()
                )
            )
            
            retryExecutor.schedule({
                retryTransaction(event)
            }, delay, TimeUnit.MILLISECONDS)
        } else {
            structuredLogger.error("Max retry attempts exceeded",
                mapOf(
                    "tradeId" to event.relatedTradeId,
                    "attempts" to attemptCount.toString()
                )
            )
            sendToDeadLetterQueue(event, RuntimeException("Max retry attempts exceeded"))
        }
    }
    
    private fun retryTransaction(event: AccountUpdateFailedEvent) {
        structuredLogger.info("Retrying failed transaction",
            mapOf("tradeId" to event.relatedTradeId)
        )
        
        releaseReservations(event)
        publishCompensationCompletedEvent(event)
        retryAttempts.remove(event.relatedTradeId)
    }
    
    private fun calculateBackoffDelay(attempt: Int): Long {
        return (Math.pow(2.0, (attempt - 1).toDouble()) * 1000).toLong()
    }
    
    private fun publishCompensationCompletedEvent(event: AccountUpdateFailedEvent) {
        structuredLogger.info("Publishing compensation completed event",
            mapOf("tradeId" to event.relatedTradeId)
        )
        
    }
    
    private fun handleCompensationFailure(event: AccountUpdateFailedEvent, ex: Exception) {
        structuredLogger.error("Compensation failed - Manual intervention required",
            mapOf(
                "tradeId" to event.relatedTradeId,
                "userId" to event.userId
            ),
            ex
        )
        
        sendToDeadLetterQueue(event, ex)
    }
    
    private fun sendToDeadLetterQueue(event: AccountUpdateFailedEvent, ex: Exception) {
        deadLetterQueue[event.relatedTradeId] = event
        
        structuredLogger.error("Event sent to Dead Letter Queue",
            mapOf(
                "tradeId" to event.relatedTradeId,
                "userId" to event.userId,
                "reason" to (ex.message ?: "Unknown"),
                "dlqSize" to deadLetterQueue.size.toString()
            )
        )
        
    }
    
    fun processDeadLetterQueue() {
        structuredLogger.info("Processing Dead Letter Queue",
            mapOf("size" to deadLetterQueue.size.toString())
        )
        
        deadLetterQueue.forEach { (tradeId, event) ->
            try {
                structuredLogger.info("Reprocessing DLQ event",
                    mapOf("tradeId" to tradeId)
                )
                
                releaseReservations(event)
                
                deadLetterQueue.remove(tradeId)
                
            } catch (ex: Exception) {
                structuredLogger.error("Failed to reprocess DLQ event",
                    mapOf("tradeId" to tradeId),
                    ex
                )
            }
        }
    }
    
    fun cleanup() {
        retryExecutor.shutdown()
        try {
            if (!retryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                retryExecutor.shutdownNow()
            }
        } catch (ex: InterruptedException) {
            retryExecutor.shutdownNow()
        }
    }
}
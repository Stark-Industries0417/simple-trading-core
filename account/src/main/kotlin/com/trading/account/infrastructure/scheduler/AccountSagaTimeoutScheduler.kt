package com.trading.account.infrastructure.scheduler

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.account.domain.saga.AccountSagaRepository
import com.trading.account.domain.saga.AccountSagaState
import com.trading.common.domain.saga.SagaStatus
import com.trading.account.infrastructure.outbox.AccountOutboxEvent
import com.trading.account.infrastructure.outbox.AccountOutboxRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class AccountSagaTimeoutScheduler(
    private val sagaRepository: AccountSagaRepository,
    private val accountOutboxRepository: AccountOutboxRepository,
    private val objectMapper: ObjectMapper,
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
                e.printStackTrace()
            }
        }
    }
    
    private fun handleTimeout(saga: AccountSagaState) {
        saga.markTimeout()
        sagaRepository.save(saga)
        
        val originalEventJson = saga.eventPayload
        val originalEvent = try {
            objectMapper.readTree(originalEventJson)
        } catch (e: Exception) {
            objectMapper.createObjectNode()
        }
        
        val buyUserId = originalEvent.get("buyUserId")?.asText()
        val sellUserId = originalEvent.get("sellUserId")?.asText()
        val symbol = originalEvent.get("symbol")?.asText() ?: ""
        
        val quantity = originalEvent.get("quantity")?.decimalValue() ?: java.math.BigDecimal.ZERO
        val price = originalEvent.get("price")?.decimalValue() ?: java.math.BigDecimal.ZERO

        val outboxEvent = AccountOutboxEvent.createAccountUpdateFailedEvent(
            sagaId = saga.sagaId,
            tradeId = saga.tradeId,
            orderId = saga.orderId,
            buyUserId = buyUserId ?: "",
            sellUserId = sellUserId ?: "",
            symbol = symbol,
            amount = price * quantity,
            quantity = quantity,
            reason = "Account update timeout after $accountTimeoutSeconds seconds",
            failureType = "TIMEOUT",
            shouldRetry = true
        )
        accountOutboxRepository.save(outboxEvent)
    }
}
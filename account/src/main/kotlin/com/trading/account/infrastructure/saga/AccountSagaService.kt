package com.trading.account.infrastructure.saga

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.account.application.AccountService
import com.trading.account.application.AccountUpdateResult
import com.trading.account.application.RollbackResult
import com.trading.account.infrastructure.outbox.AccountOutboxEvent
import com.trading.account.infrastructure.outbox.AccountOutboxRepository
import com.trading.common.dto.cdc.matching.MatchingCreatedDto
import com.trading.common.outbox.EventTypes
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AccountSagaService(
    private val accountService: AccountService,
    private val accountOutboxRepository: AccountOutboxRepository,
    private val objectMapper: ObjectMapper
) {

    @KafkaListener(topics = ["trade.events"], groupId = "account-saga-group")
    fun handleTradeEvent(message: String) {
        try {
            val jsonNode = objectMapper.readTree(message)
            val eventType = jsonNode.get("eventType")?.asText()
            if (eventType == null) return
            val sagaId = jsonNode.get("sagaId")?.asText()

            when (eventType) {
                EventTypes.Trade.CREATED -> {
                    if (sagaId == null) return
                    val event = objectMapper.readValue(message, MatchingCreatedDto::class.java)
                    processAccountUpdate(event)
                }
                EventTypes.Trade.ROLLBACK -> {
                    val event = objectMapper.readValue(message, MatchingCreatedDto::class.java)
                    rollbackAccount(event)
                }
                EventTypes.Trade.FAILED -> {
                    val event = objectMapper.readValue(message, MatchingCreatedDto::class.java)
                    handleTradeFailed(event)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processAccountUpdate(event: MatchingCreatedDto) {
        try {
            val result = accountService.processTradeExecution(event)

            when (result) {
                is AccountUpdateResult.Success -> {
                    val outboxEvent = AccountOutboxEvent.createAccountUpdatedEvent(
                        sagaId = event.sagaId,
                        tradeId = event.tradeId,
                        orderId = event.buyOrderId,
                        buyUserId = event.buyUserId,
                        sellUserId = event.sellUserId,
                        symbol = event.symbol,
                        amount = event.price * event.quantity,
                        quantity = event.quantity,
                        buyerNewBalance = result.buyerNewBalance,
                        sellerNewBalance = result.sellerNewBalance
                    )
                    accountOutboxRepository.save(outboxEvent)
                }

                is AccountUpdateResult.Failure -> {
                    handleAccountUpdateFailure(event, result)
                }
            }

        } catch (e: Exception) {
            handleAccountUpdateException(event, e)
        }
    }

    private fun handleAccountUpdateFailure(
        event: MatchingCreatedDto,
        result: AccountUpdateResult.Failure
    ) {
        try {
            accountService.releaseReservationByOrderId(event.buyOrderId)
            accountService.releaseReservationByOrderId(event.sellOrderId)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val failureType = when {
            result.reason.contains("Insufficient balance", ignoreCase = true) ->
                "INSUFFICIENT_BALANCE"
            result.reason.contains("Insufficient shares", ignoreCase = true) ->
                "INSUFFICIENT_SHARES"
            result.reason.contains("Lock", ignoreCase = true) ->
                "LOCK_TIMEOUT"
            result.reason.contains("Validation", ignoreCase = true) ->
                "VALIDATION_ERROR"
            else ->
                "TECHNICAL_ERROR"
        }

        val outboxEvent = AccountOutboxEvent.createAccountUpdateFailedEvent(
            sagaId = event.sagaId,
            tradeId = event.tradeId,
            orderId = event.buyOrderId,
            buyUserId = event.buyUserId,
            sellUserId = event.sellUserId,
            symbol = event.symbol,
            amount = event.price * event.quantity,
            quantity = event.quantity,
            reason = result.reason,
            failureType = failureType,
            shouldRetry = result.shouldRetry
        )
        accountOutboxRepository.save(outboxEvent)
    }

    private fun handleAccountUpdateException(
        event: MatchingCreatedDto,
        exception: Exception
    ) {
        try {
            accountService.releaseReservationByOrderId(event.buyOrderId)
            accountService.releaseReservationByOrderId(event.sellOrderId)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val outboxEvent = AccountOutboxEvent.createAccountUpdateFailedEvent(
            sagaId = event.sagaId,
            tradeId = event.tradeId,
            orderId = event.buyOrderId,
            buyUserId = event.buyUserId,
            sellUserId = event.sellUserId,
            symbol = event.symbol,
            amount = event.price * event.quantity,
            quantity = event.quantity,
            reason = exception.message ?: "Account update failed",
            failureType = "TECHNICAL_ERROR",
            shouldRetry = false
        )
        accountOutboxRepository.save(outboxEvent)
    }

    private fun rollbackAccount(event: MatchingCreatedDto) {
        try {
            val rollbackResult = accountService.rollbackTradeExecution(
                tradeId = event.tradeId,
                buyUserId = event.buyUserId,
                sellUserId = event.sellUserId,
                symbol = event.symbol,
                quantity = event.quantity,
                price = event.price
            )

            when (rollbackResult) {
                is RollbackResult.Success -> {
                    val outboxEvent = AccountOutboxEvent.createAccountRollbackEvent(
                        sagaId = event.sagaId,
                        tradeId = event.tradeId,
                        orderId = event.buyOrderId,
                        userId = event.buyUserId,
                        symbol = event.symbol,
                        amount = event.price * event.quantity,
                        quantity = event.quantity,
                        rollbackType = "REVERSE_TRADE",
                        success = true,
                        reason = "Trade rollback completed successfully"
                    )
                    accountOutboxRepository.save(outboxEvent)
                }

                is RollbackResult.Failure -> {
                    val outboxEvent = AccountOutboxEvent.createAccountRollbackEvent(
                        sagaId = event.sagaId,
                        tradeId = event.tradeId,
                        orderId = event.buyOrderId,
                        userId = event.buyUserId,
                        symbol = event.symbol,
                        amount = event.price * event.quantity,
                        quantity = event.quantity,
                        rollbackType = "REVERSE_TRADE",
                        success = false,
                        reason = "Rollback failed: ${rollbackResult.reason}"
                    )
                    accountOutboxRepository.save(outboxEvent)
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleTradeFailed(event: MatchingCreatedDto) {
        try {
            accountService.releaseReservationByOrderId(event.buyOrderId)
            accountService.releaseReservationByOrderId(event.sellOrderId)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val outboxEvent = AccountOutboxEvent.createAccountUpdateFailedEvent(
            sagaId = event.sagaId,
            tradeId = event.tradeId,
            orderId = event.buyOrderId,
            buyUserId = event.buyUserId,
            sellUserId = event.sellUserId,
            symbol = event.symbol,
            amount = event.price * event.quantity,
            quantity = event.quantity,
            reason = "Matching failed",
            failureType = "MATCHING_FAILED",
            shouldRetry = false
        )
        accountOutboxRepository.save(outboxEvent)
    }
}
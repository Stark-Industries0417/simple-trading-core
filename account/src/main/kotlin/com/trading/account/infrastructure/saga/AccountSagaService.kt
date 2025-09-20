package com.trading.account.infrastructure.saga

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.account.application.AccountService
import com.trading.account.application.AccountUpdateResult
import com.trading.account.application.RollbackResult
import com.trading.account.infrastructure.outbox.AccountOutboxEvent
import com.trading.account.infrastructure.outbox.AccountOutboxRepository
import com.trading.common.dto.cdc.matching.MatchingCreatedDto
import com.trading.common.outbox.EventTypes
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AccountSagaService(
    private val accountService: AccountService,
    private val accountOutboxRepository: AccountOutboxRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(AccountSagaService::class.java)

    @KafkaListener(
        topics = ["trade.events"],
        groupId = "account-saga-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleTradeEvent(
        message: String,
        acknowledgment: Acknowledgment
    ) {
        try {
            val jsonNode = objectMapper.readTree(message)
            val eventType = jsonNode.get("eventType")?.asText()
            val sagaId = jsonNode.get("sagaId")?.asText()

            if (eventType == null || sagaId == null) {
                logger.warn("Invalid event - missing eventType or sagaId: {}", message)
                acknowledgment.acknowledge()
                return
            }

            if (isAlreadyProcessed(sagaId)) {
                logger.info("Event already processed for sagaId: {}", sagaId)
                acknowledgment.acknowledge()
                return
            }

            val success = when (eventType) {
                EventTypes.Trade.CREATED -> {
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
                else -> {
                    logger.warn("Unknown event type: {}", eventType)
                    false
                }
            }

            if (success) {
                acknowledgment.acknowledge()
                logger.debug("Successfully processed and acknowledged event for sagaId: {}", sagaId)
            } else {
                logger.error("Failed to process event for sagaId: {}, will retry", sagaId)
            }

        } catch (e: Exception) {
            logger.error("Error processing event: {}", e.message, e)
            acknowledgment.acknowledge()
        }
    }

    /**
     * sagaId로 이미 처리된 이벤트인지 확인 (멱등성 체크)
     */
    private fun isAlreadyProcessed(sagaId: String): Boolean {
        val existingEvents = accountOutboxRepository.findBySagaId(sagaId)
        return existingEvents.isNotEmpty()
    }

    private fun processAccountUpdate(event: MatchingCreatedDto): Boolean {
        return try {
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
            true

        } catch (e: Exception) {
            handleAccountUpdateException(event, e)
            false
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

    private fun rollbackAccount(event: MatchingCreatedDto): Boolean {
        return try {
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
            true

        } catch (e: Exception) {
            logger.error("Failed to rollback account for sagaId: {}", event.sagaId, e)
            false
        }
    }

    private fun handleTradeFailed(event: MatchingCreatedDto): Boolean {
        return try {
            try {
                accountService.releaseReservationByOrderId(event.buyOrderId)
                accountService.releaseReservationByOrderId(event.sellOrderId)
            } catch (e: Exception) {
                logger.error("Failed to release reservation for sagaId: {}", event.sagaId, e)
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
            true
        } catch (e: Exception) {
            logger.error("Failed to handle trade failure for sagaId: {}", event.sagaId, e)
            false
        }
    }
}
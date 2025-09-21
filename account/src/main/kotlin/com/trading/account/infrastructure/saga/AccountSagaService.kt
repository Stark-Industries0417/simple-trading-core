package com.trading.account.infrastructure.saga

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.account.application.AccountNotFoundException
import com.trading.account.application.AccountService
import com.trading.account.application.AccountUpdateResult
import com.trading.account.domain.ReservationResult
import com.trading.account.domain.StockReservationResult
import com.trading.account.infrastructure.outbox.AccountOutboxEvent
import com.trading.account.infrastructure.outbox.AccountOutboxRepository
import com.trading.common.dto.cdc.matching.MatchingCreatedDto
import com.trading.common.dto.cdc.matching.MatchingFailedDto
import com.trading.common.dto.cdc.order.OrderCancelledDto
import com.trading.common.dto.cdc.order.OrderCreatedDto
import com.trading.common.dto.order.OrderSide
import com.trading.common.dto.order.OrderType
import com.trading.common.outbox.EventTypes
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional
class AccountSagaService(
    private val accountService: AccountService,
    private val accountOutboxRepository: AccountOutboxRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(AccountSagaService::class.java)

    @KafkaListener(
        topics = ["#{@kafkaProperties.topics.orderEvents}", "#{@kafkaProperties.topics.tradeEvents}"],
        groupId = "#{@kafkaProperties.consumer.groupId}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleTradeEvent(
        message: String,
        acknowledgment: Acknowledgment
    ) {
        try {
            val jsonNode = objectMapper.readTree(message)
            val eventType = jsonNode.get("eventType")?.asText()
            val eventId = jsonNode.get("eventId")?.asText()

            if (eventType == null || eventId == null) {
                logger.warn("Invalid event - missing eventType or eventId: {}", message)
                acknowledgment.acknowledge()
                return
            }

            if (isAlreadyProcessed(eventId)) {
                logger.info("Event already processed for eventId: {}", eventId)
                acknowledgment.acknowledge()
                return
            }

            val success = when (eventType) {
                EventTypes.Trade.CREATED -> {
                    val event = objectMapper.readValue(message, MatchingCreatedDto::class.java)
                    processAccountUpdate(event)
                }
                EventTypes.Trade.FAILED -> {
                    val event = objectMapper.readValue(message, MatchingFailedDto::class.java)
                    handleTradeFailed(event)
                }
                EventTypes.Order.CREATED -> {
                    val event = objectMapper.readValue(message, OrderCreatedDto::class.java)
                    handleOrderCreated(event)
                }
                EventTypes.Order.CANCELLED -> {
                    val event = objectMapper.readValue(message, OrderCancelledDto::class.java)
                    handleOrderCancelled(event)
                }
                else -> {
                    logger.warn("Unknown event type: {}", eventType)
                    false
                }
            }

            if (success) {
                acknowledgment.acknowledge()
                logger.debug("Successfully processed and acknowledged event for eventId: {}", eventId)
            } else {
                logger.error("Failed to process event for eventId: {}, will retry", eventId)
            }

        } catch (e: Exception) {
            logger.error("Error processing event: {}", e.message, e)
            acknowledgment.acknowledge()
        }
    }

    private fun handleOrderCreated(event: OrderCreatedDto): Boolean {
        try {
            val result = when (event.side) {
                OrderSide.BUY -> {
                    val amountToReserve = when (event.orderType) {
                        OrderType.MARKET -> {
                            // TODO 시장가 추후 구현
                            BigDecimal.TEN
                        }
                        OrderType.LIMIT -> {
                            event.price?.multiply(event.quantity)
                                ?: throw IllegalArgumentException("Price must be provided for limit buy order")
                        }
                    }
                    accountService.reserveFundsForOrder(
                        orderId = event.orderId,
                        userId = event.userId,
                        symbol = event.symbol,
                        quantity = event.quantity,
                        price = event.price!!,
                        amount = amountToReserve
                    )
                }
                OrderSide.SELL -> {
                    accountService.reserveStocksForOrder(
                        orderId = event.orderId,
                        userId = event.userId,
                        symbol = event.symbol,
                        quantity = event.quantity,
                        price = event.price,
                        traceId = event.sagaId
                    )
                }
            }

            when (result) {
                is ReservationResult.Success -> {
                    logger.info("Successfully reserved funds for orderId: {}", event.orderId)
                }
                is ReservationResult.InsufficientFunds -> {
                    logger.warn("Insufficient funds for orderId: {}. Required: {}, Available: {}", event.orderId, result.required, result.available)
                    handleReservationFailure(event, "Insufficient funds", "INSUFFICIENT_FUNDS")
                }
                is StockReservationResult.Success -> {
                    logger.info("Successfully reserved stock for orderId: {}", event.orderId)
                }
                is StockReservationResult.InsufficientShares -> {
                    logger.warn("Insufficient shares for orderId: {}. Required: {}, Available: {}", event.orderId, result.required, result.available)
                    handleReservationFailure(event, "Insufficient shares", "INSUFFICIENT_SHARES")
                }
                else -> {
                    logger.error("Unknown reservation result type: {}", result.javaClass.name)
                    handleReservationFailure(event, "Unknown reservation result", "TECHNICAL_ERROR")
                }
            }
            return true

        } catch (e: AccountNotFoundException) {
            logger.error("Account not found for userId: {} in orderId: {}", event.userId, event.orderId, e)
            handleReservationFailure(event, "Account not found", "ACCOUNT_NOT_FOUND")
            return true
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid order data for orderId: {}. Error: {}", event.orderId, e.message, e)
            handleReservationFailure(event, e.message ?: "Invalid order data", "VALIDATION_ERROR")
            return true
        } catch (e: Exception) {
            logger.error("Failed to handle order creation for orderId: {}. Error: {}", event.orderId, e.message, e)
            handleReservationFailure(event, e.message ?: "Unknown error during reservation", "TECHNICAL_ERROR")
            return true
        }
    }

    private fun handleReservationFailure(event: OrderCreatedDto, reason: String, failureType: String) {
        val outboxEvent = AccountOutboxEvent.createAccountReservationFailedEvent(
            sagaId = event.sagaId,
            orderId = event.orderId,
            userId = event.userId,
            symbol = event.symbol,
            quantity = event.quantity,
            reason = reason,
            failureType = failureType,
            shouldRetry = false
        )
        accountOutboxRepository.save(outboxEvent)
    }


    private fun isAlreadyProcessed(eventId: String): Boolean {
        val existingEvents = accountOutboxRepository.findByEventId(eventId)
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
        performCompensation(event.buyOrderId, event.sellOrderId, event.sagaId)

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

        logger.info(
            "Account update failed for sagaId: {}, failureType: {}, reason: {}",
            event.sagaId, failureType, result.reason
        )
    }

    private fun handleAccountUpdateException(
        event: MatchingCreatedDto,
        exception: Exception
    ) {
        performCompensation(event.buyOrderId, event.sellOrderId, event.sagaId)

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

        logger.error(
            "Account update exception for sagaId: {}, error: {}",
            event.sagaId, exception.message, exception
        )
    }


    private fun handleTradeFailed(event: MatchingFailedDto): Boolean {
        return try {
            performCompensation(event.buyOrderId, event.sellOrderId, event.sagaId)

            logger.info(
                "Compensation completed for matching failure - sagaId: {}, buyOrderId: {}, sellOrderId: {}",
                event.sagaId, event.buyOrderId, event.sellOrderId
            )
            true
        } catch (e: Exception) {
            logger.error("Failed to handle trade failure compensation for sagaId: {}", event.sagaId, e)
            false
        }
    }


    private fun handleOrderCancelled(event: OrderCancelledDto): Boolean {
        return try {
            performCompensation(event.orderId, "", event.sagaId)

            logger.info(
                "Compensation completed for order cancellation - sagaId: {}, orderId: {}, userId: {}",
                event.sagaId, event.orderId, event.userId
            )
            true
        } catch (e: Exception) {
            logger.error("Failed to handle order cancellation compensation for sagaId: {}", event.sagaId, e)
            false
        }
    }


    private fun performCompensation(buyOrderId: String, sellOrderId: String, sagaId: String) {
        try {
            if (buyOrderId.isNotEmpty()) {
                accountService.releaseReservationByOrderId(buyOrderId)
                logger.debug("Released reservation for buy order: {}", buyOrderId)
            }
            if (sellOrderId.isNotEmpty()) {
                accountService.releaseReservationByOrderId(sellOrderId)
                logger.debug("Released reservation for sell order: {}", sellOrderId)
            }
        } catch (e: Exception) {
            logger.error(
                "Failed to release reservation for sagaId: {}, buyOrderId: {}, sellOrderId: {}",
                sagaId, buyOrderId, sellOrderId, e
            )
            // 보상 실패는 로그만 남기고 계속 진행 추후에 DLQ (베스트 에포트)
        }
    }
}
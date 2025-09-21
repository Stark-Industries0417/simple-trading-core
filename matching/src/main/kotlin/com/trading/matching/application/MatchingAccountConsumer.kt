package com.trading.matching.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.dto.cdc.account.AccountUpdateFailedDto
import com.trading.common.dto.cdc.account.AccountUpdatedDto
import com.trading.common.outbox.EventTypes.Account
import com.trading.matching.domain.MatchingRepository
import com.trading.matching.infrastructure.engine.MatchingEngineManager
import com.trading.matching.infrastructure.outbox.MatchingStatus
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class MatchingAccountConsumer(
    private val matchingRepository: MatchingRepository,
    private val matchingEngineManager: MatchingEngineManager,
    private val objectMapper: ObjectMapper
) {

    @KafkaListener(
        topics = ["#{@matchingKafkaProperties.topics.accountEvents}"],
        groupId = "#{@matchingKafkaProperties.consumer.groupId}"
    )
    fun handleAccountEvent(message: String) {
        try {
            val jsonNode = objectMapper.readTree(message)
            val eventType = jsonNode.get("eventType")?.asText() ?: return

            when (eventType) {
                Account.UPDATED -> {
                    val event = objectMapper.readValue(message, AccountUpdatedDto::class.java)
                    handleAccountUpdated(event)
                }
                Account.UPDATE_FAILED -> {
                    val event = objectMapper.readValue(message, AccountUpdateFailedDto::class.java)
                    handleAccountUpdateFailed(event)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleAccountUpdated(event: AccountUpdatedDto) {
        val matching = matchingRepository.findByTradeId(event.tradeId)
        if (matching != null) {
            matching.complete()
            matchingRepository.save(matching)

            println("Trade completed successfully - TradeId: ${event.tradeId}, " +
                    "BuyUser: ${event.buyUserId}, SellUser: ${event.sellUserId}, " +
                    "Symbol: ${event.symbol}, Quantity: ${event.quantity}")
        }
    }

    private fun handleAccountUpdateFailed(event: AccountUpdateFailedDto) {
        val matching = matchingRepository.findByTradeId(event.tradeId)
        if (matching != null) {
            matching.markAsFailed("Account update failed: ${event.reason}")
            matchingRepository.save(matching)

            executeCompensation(event)

            println("Trade failed due to account error - TradeId: ${event.tradeId}, " +
                    "FailureType: ${event.failureType}, Reason: ${event.reason}, " +
                    "ShouldRetry: ${event.shouldRetry}")
        }
    }

    private fun executeCompensation(event: AccountUpdateFailedDto) {
        // 보상 로직 구현
        when (event.failureType) {
            "INSUFFICIENT_BALANCE" -> {
                // 잔액 부족: 해당 거래 취소 처리
                handleInsufficientBalance(event)
            }
            "INSUFFICIENT_SHARES" -> {
                // 주식 부족: 해당 거래 취소 처리
                handleInsufficientShares(event)
            }
            "LOCK_TIMEOUT" -> {
                // 락 타임아웃: 재시도 가능한 경우 재시도 플래그 설정
                handleLockTimeout(event)
            }
            else -> {
                // 기타 실패: 일반적인 보상 처리
                handleGeneralFailure(event)
            }
        }
    }

    private fun handleInsufficientBalance(event: AccountUpdateFailedDto) {
        // 구매자 잔액 부족 처리
        println("Compensating for insufficient balance - " +
                "TradeId: ${event.tradeId}, BuyUser: ${event.buyUserId}, " +
                "Required Amount: ${event.amount}")

        // 매칭 엔진에서 주문 제거
        val buyOrderCancelled = matchingEngineManager.removeOrderFromBook(
            orderId = event.orderId,
            symbol = event.symbol
        )

        if (buyOrderCancelled) {
            println("Buy order removed from matching engine - OrderId: ${event.orderId}")
        }

        // TODO: 사용자에게 잔액 부족 알림 발송
    }

    private fun handleInsufficientShares(event: AccountUpdateFailedDto) {
        // 판매자 주식 부족 처리
        println("Compensating for insufficient shares - " +
                "TradeId: ${event.tradeId}, SellUser: ${event.sellUserId}, " +
                "Required Quantity: ${event.quantity}")

        // 매칭 엔진에서 관련 주문 제거
        val matching = matchingRepository.findByTradeId(event.tradeId)
        if (matching != null) {
            // Sell 주문 취소
            val sellOrderCancelled = matchingEngineManager.removeOrderFromBook(
                orderId = matching.sellOrderId,
                symbol = matching.symbol
            )
            if (sellOrderCancelled) {
                println("Sell order removed from matching engine - OrderId: ${matching.sellOrderId}")
            }
        }

        // TODO: 사용자에게 주식 부족 알림 발송
    }

    private fun handleLockTimeout(event: AccountUpdateFailedDto) {
        // 락 타임아웃 처리
        if (event.shouldRetry) {
            println("Scheduling retry for lock timeout - " +
                    "TradeId: ${event.tradeId}, RetryCount: ${event.retryCount}")

            // TODO: 재시도 스케줄러에 등록
            // TODO: 지수 백오프 적용 (예: 2^retryCount 초 후 재시도)
            // TODO: 최대 재시도 횟수 체크 (예: 3회)
        } else {
            println("Lock timeout exceeded max retries - TradeId: ${event.tradeId}")
            handleGeneralFailure(event)
        }
    }

    private fun handleGeneralFailure(event: AccountUpdateFailedDto) {
        // 일반적인 실패 처리
        println("General compensation for trade failure - " +
                "TradeId: ${event.tradeId}, Error: ${event.errorMessage}")

        // 매칭 엔진에서 관련 주문들 롤백
        val orderCancelled = matchingEngineManager.removeOrderFromBook(
            orderId = event.orderId,
            symbol = event.symbol
        )

        if (orderCancelled) {
            println("Order rolled back from matching engine - OrderId: ${event.orderId}")
        }

        // TODO: 사용자에게 거래 실패 알림 발송
        // TODO: 실패 로그를 감사(audit) 테이블에 기록
    }
}
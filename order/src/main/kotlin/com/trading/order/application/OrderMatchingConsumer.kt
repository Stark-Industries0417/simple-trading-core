package com.trading.order.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.dto.cdc.matching.MatchingFailedDto
import com.trading.common.outbox.EventTypes.Trade
import com.trading.order.domain.OrderRepository
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional


@Component
@Transactional
class OrderMatchingConsumer(
    private val orderRepository: OrderRepository,
    private val objectMapper: ObjectMapper
) {

    @KafkaListener(
        topics = ["#{@orderKafkaProperties.topics.tradeEvents}"],
        groupId = "#{@orderKafkaProperties.consumer.groupId}"
    )
    fun handleTradeEvent(message: String) {
        try {
            val jsonNode = objectMapper.readTree(message)
            val eventType = jsonNode.get("eventType")?.asText() ?: return

            when (eventType) {
                Trade.FAILED -> {
                    val event = objectMapper.readValue(message, MatchingFailedDto::class.java)
                    cancelOrderByMatchingFailure(event)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cancelOrderByMatchingFailure(event: MatchingFailedDto) {
        val reason = "Matching failed: ${event.errorMessage ?: "Unknown error"}"

        cancelOrderIfExists(event.buyOrderId, reason, event.buyUserId)
        cancelOrderIfExists(event.sellOrderId, reason, event.sellUserId)
    }

    private fun cancelOrderIfExists(orderId: String, reason: String, userId: String) {
        if (orderId.isEmpty()) return

        orderRepository.findById(orderId).ifPresent { order ->
            order.cancel(reason)
            orderRepository.save(order)
            // TODO: 사용자에게 매칭 실패 알림 (userId: $userId)
        }
    }
}
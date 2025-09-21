package com.trading.order.application

import com.trading.common.dto.cdc.account.AccountUpdatedDto
import com.trading.common.dto.cdc.account.AccountUpdateFailedDto
import com.trading.order.domain.OrderRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.outbox.EventTypes.Account
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class OrderConsumer(
    private val orderRepository: OrderRepository,
    private val objectMapper: ObjectMapper
) {

    @KafkaListener(
        topics = ["#{@kafkaProperties.topics.accountEvents}"],
        groupId = "#{@kafkaProperties.consumer.groupId}"
    )
    fun handleAccountEvent(message: String) {
        try {
            val jsonNode = objectMapper.readTree(message)
            val eventType = jsonNode.get("eventType")?.asText() ?: return
            
            when (eventType) {
                Account.UPDATED -> {
                    val event = objectMapper.readValue(message, AccountUpdatedDto::class.java)
                    completeOrder(event)
                }
                Account.UPDATE_FAILED -> {
                    val event = objectMapper.readValue(message, AccountUpdateFailedDto::class.java)
                    cancelOrder(event)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun completeOrder(event: AccountUpdatedDto) {
        val order = orderRepository.findById(event.orderId).orElse(null)
        if (order == null) return

        order.partialFill(event.quantity)
        /**
         * if (it.status == OrderStatus.FILLED)
         *    // 사용자에게 모든 주문 체결 알림
         *else
         *    // 사용자에게 지정가 체결된 거래 알림
         */

        orderRepository.save(order)
    }
    
    private fun cancelOrder(event: AccountUpdateFailedDto) {
        // 지정가 취소는 체결된 거래는 유지 => 사용자에게 취소된 거래 알림
        // 시장가 취소는 모두 취소 => 주문 취소 알림

        val order = orderRepository.findById(event.orderId).orElse(null)
        if (order == null) return

        order.cancel(event.reason)
        orderRepository.save(order)
    }
}
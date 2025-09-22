package com.trading.order.application

import com.trading.common.dto.order.OrderStatus
import com.trading.common.util.UUIDv7Generator
import com.trading.order.domain.OrderRepository
import com.trading.order.infrastructure.outbox.OrderOutboxRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Component
class OrderTimeoutScheduler(
    private val orderRepository: OrderRepository,
    private val orderOutboxRepository: OrderOutboxRepository,
    private val uuidGenerator: UUIDv7Generator,
    @Value("\${order.timeout.duration:PT5M}") private val timeoutDuration: String,
    @Value("\${order.timeout.batch-size:100}") private val batchSize: Int
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val timeout = Duration.parse(timeoutDuration)

    @Scheduled(fixedDelayString = "\${order.timeout.scheduler.interval:60000}")
    @Transactional
    fun processTimeoutOrders() {
        val timeoutThreshold = Instant.now().minus(timeout)
        val pageable = PageRequest.of(0, batchSize)

        try {
            val ordersToTimeout = orderRepository.findOrdersForTimeout(
                status = OrderStatus.CREATED,
                timeoutThreshold = timeoutThreshold,
                pageable = pageable
            )

            if (ordersToTimeout.isEmpty()) {
                return
            }

            logger.info(
                "Processing {} orders for timeout with threshold: {}",
                ordersToTimeout.size,
                timeoutThreshold
            )

            ordersToTimeout.forEach { order ->
                try {
                    val sagaId = uuidGenerator.generateEventId()
                    val timeoutReason = "Order timeout after ${timeout.toMinutes()} minutes"

                    order.timeout(timeoutReason)
                    val savedOrder = orderRepository.save(order)
                    savedOrder.toCancelledOutboxEvent(sagaId)
                        .also { orderOutboxRepository.save(it) }

                    logger.info(
                        "Order {} timed out. SagaId: {}, UserId: {}, Symbol: {}",
                        order.id,
                        sagaId,
                        order.userId,
                        order.symbol
                    )
                } catch (e: Exception) {
                    logger.error(
                        "Failed to process timeout for order {}: {}",
                        order.id,
                        e.message,
                        e
                    )
                }
            }

            logger.info("Successfully processed {} timeout orders", ordersToTimeout.size)
        } catch (e: Exception) {
            logger.error("Error in order timeout scheduler: {}", e.message, e)
        }
    }
}
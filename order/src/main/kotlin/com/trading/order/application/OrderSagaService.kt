package com.trading.order.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.domain.saga.SagaStatus
import com.trading.common.dto.order.OrderSide
import com.trading.common.dto.order.OrderStatus
import com.trading.common.dto.order.OrderType
import com.trading.common.event.order.OrderCreatedEvent
import com.trading.common.event.saga.AccountUpdatedEvent
import com.trading.common.event.saga.AccountUpdateFailedEvent
import com.trading.common.event.saga.SagaTimeoutEvent
import com.trading.common.exception.order.OrderPersistenceException
import com.trading.common.exception.order.OrderProcessingException
import com.trading.common.exception.order.OrderValidationException
import com.trading.common.logging.StructuredLogger
import com.trading.common.util.UUIDv7Generator
import com.trading.order.domain.Order
import com.trading.order.domain.OrderRepository
import com.trading.order.domain.OrderValidator
import com.trading.order.domain.saga.OrderSagaRepository
import com.trading.order.domain.saga.OrderSagaState
import com.trading.order.domain.toDTO
import com.trading.order.infrastructure.web.dto.CreateOrderRequest
import com.trading.order.infrastructure.web.dto.OrderResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
@Transactional
class OrderSagaService(
    private val orderRepository: OrderRepository,
    private val sagaRepository: OrderSagaRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val structuredLogger: StructuredLogger,
    private val uuidGenerator: UUIDv7Generator,
    private val orderMetrics: OrderMetrics,
    private val orderValidator: OrderValidator,
    @Value("\${saga.timeouts.order:30}") private val orderTimeoutSeconds: Long = 30
) {
    
    fun createOrderWithSaga(request: CreateOrderRequest, userId: String, traceId: String): OrderResponse {
        val startTime = System.currentTimeMillis()

        structuredLogger.info("Order creation started", buildMap {
            put("userId", userId)
            put("symbol", request.symbol)
            put("orderType", request.orderType.name)
            put("side", request.side.name)
            put("quantity", request.quantity.toString())
            request.price?.let { put("price", it.toString()) }
            put("traceId", traceId)
        })
        var order: Order? = null

        try {
            order = Order.create(
                userId = userId,
                symbol = request.getNormalizedSymbol(),
                orderType = request.orderType,
                side = request.side,
                quantity = request.quantity,
                price = request.price,
                traceId = traceId,
                uuidGenerator = uuidGenerator
            )
            order.status = OrderStatus.CREATED

            orderValidator.validateOrThrow(order)
            val savedOrder = orderRepository.save(order)

            val sagaState = OrderSagaState(
                sagaId = uuidGenerator.generateEventId(),
                orderId = savedOrder.id,
                tradeId = uuidGenerator.generateEventId(),
                state = SagaStatus.STARTED,
                timeoutAt = Instant.now().plusSeconds(orderTimeoutSeconds),
                metadata = objectMapper.writeValueAsString(mapOf(
                    "orderId" to savedOrder.id,
                    "userId" to savedOrder.userId,
                    "symbol" to savedOrder.symbol,
                    "quantity" to savedOrder.quantity.toString(),
                    "price" to savedOrder.price?.toString(),
                    "side" to savedOrder.side.name,
                    "orderType" to savedOrder.orderType.name
                ))
            )
            val savedSaga = sagaRepository.save(sagaState)

            val event = OrderCreatedEvent(
                eventId = uuidGenerator.generateEventId(),
                aggregateId = savedOrder.id,
                occurredAt = Instant.now(),
                traceId = traceId,
                order = savedOrder.toDTO()
            )

            kafkaTemplate.send(
                "order.events",
                savedOrder.symbol,
                objectMapper.writeValueAsString(event)
            )

            val duration = System.currentTimeMillis() - startTime
            structuredLogger.info("Order created with saga",
                buildMap {
                    put("sagaId", savedSaga.sagaId)
                    put("orderId", savedOrder.id)
                    put("userId", userId)
                    put("symbol", savedOrder.symbol)
                    put("traceId", traceId)
                    put("duration", duration.toString())
                }
            )
            return OrderResponse.from(savedOrder)
        } catch (ex: OrderValidationException) {
            orderMetrics.incrementValidationFailures()
            throw ex.withServiceContext(userId, request.symbol)
        } catch (ex: DataIntegrityViolationException) {
            handlePersistenceException(ex, order, userId, request.symbol, startTime)
        } catch (ex: Exception) {
            handleUnexpectedException(ex, order, userId, request.symbol, startTime, "order creation")
        }
    }
    
    @KafkaListener(topics = ["account.events"], groupId = "order-saga-group")
    fun handleAccountEvent(message: String) {
        try {
            val jsonNode = objectMapper.readTree(message)
            val eventType = jsonNode.get("eventType")?.asText() ?: return
            
            when (eventType) {
                "AccountUpdated" -> {
                    val event = objectMapper.readValue(message, AccountUpdatedEvent::class.java)
                    completeOrder(event)
                }
                "AccountUpdateFailed" -> {
                    val event = objectMapper.readValue(message, AccountUpdateFailedEvent::class.java)
                    cancelOrder(event)
                }
            }
        } catch (e: Exception) {
            structuredLogger.error("Error handling account event",
                mapOf(
                    "error" to (e.message ?: "Unknown error"),
                    "message" to message
                )
            )
        }
    }
    
    private fun completeOrder(event: AccountUpdatedEvent) {
        val saga = sagaRepository.findBySagaId(event.sagaId)
        if (saga == null) {
            structuredLogger.warn("Saga not found for AccountUpdated event",
                mapOf("sagaId" to event.sagaId, "orderId" to event.orderId)
            )
            return
        }
        
        val order = orderRepository.findById(saga.orderId).orElse(null)
        if (order == null) {
            structuredLogger.warn("Order not found for saga",
                mapOf("sagaId" to event.sagaId, "orderId" to saga.orderId)
            )
            return
        }
        
        
        order.status = OrderStatus.COMPLETED
        order.filledQuantity = event.quantity
        order.filledAt = Instant.now()
        orderRepository.save(order)
        
        
        saga.markCompleted()
        sagaRepository.save(saga)
        
        structuredLogger.info("Order completed through saga",
            mapOf(
                "sagaId" to event.sagaId,
                "orderId" to order.id,
                "tradeId" to event.tradeId,
                "amount" to event.amount.toString()
            )
        )
    }
    
    private fun cancelOrder(event: AccountUpdateFailedEvent) {
        val saga = sagaRepository.findBySagaId(event.sagaId)
        if (saga == null) {
            structuredLogger.warn("Saga not found for AccountUpdateFailed event",
                mapOf("sagaId" to event.sagaId, "orderId" to event.orderId)
            )
            return
        }
        
        val order = orderRepository.findById(saga.orderId).orElse(null)
        if (order == null) {
            structuredLogger.warn("Order not found for saga",
                mapOf("sagaId" to event.sagaId, "orderId" to saga.orderId)
            )
            return
        }

        order.cancel(event.reason)
        orderRepository.save(order)

        saga.markCompensated()
        sagaRepository.save(saga)
        
        structuredLogger.info("Order cancelled due to account failure",
            mapOf(
                "sagaId" to event.sagaId,
                "orderId" to order.id,
                "reason" to event.reason,
                "failureType" to event.failureType.name
            )
        )
    }
    
    @Scheduled(fixedDelay = 5000)
    fun checkTimeouts() {
        val timedOutSagas = sagaRepository.findTimedOutSagas(
            listOf(SagaStatus.STARTED, SagaStatus.IN_PROGRESS),
            Instant.now()
        )
        
        timedOutSagas.forEach { saga ->
            try {
                handleTimeout(saga)
            } catch (e: Exception) {
                structuredLogger.error("Error handling saga timeout",
                    mapOf(
                        "sagaId" to saga.sagaId,
                        "orderId" to saga.orderId,
                        "error" to (e.message ?: "Unknown error")
                    )
                )
            }
        }
    }
    
    private fun handleTimeout(saga: OrderSagaState) {
        structuredLogger.warn("Order saga timeout detected",
            mapOf(
                "sagaId" to saga.sagaId,
                "orderId" to saga.orderId,
                "state" to saga.state.name
            )
        )
        
        val order = orderRepository.findById(saga.orderId).orElse(null)
        if (order != null && order.status == OrderStatus.CREATED) {
            
            order.status = OrderStatus.TIMEOUT
            order.cancellationReason = "Saga timeout after ${orderTimeoutSeconds} seconds"
            orderRepository.save(order)
        }
        
        
        saga.markTimeout()
        sagaRepository.save(saga)
        
        
        val timeoutEvent = SagaTimeoutEvent(
            eventId = uuidGenerator.generateEventId(),
            aggregateId = saga.orderId,
            occurredAt = Instant.now(),
            traceId = order?.traceId ?: "",
            sagaId = saga.sagaId,
            orderId = saga.orderId,
            tradeId = saga.tradeId,
            failedAt = "Order",
            timeoutDuration = orderTimeoutSeconds,
            metadata = mapOf("reason" to "Order processing timeout")
        )
        
        kafkaTemplate.send(
            "saga.timeout.events",
            saga.orderId,
            objectMapper.writeValueAsString(timeoutEvent)
        )
    }

    private fun handlePersistenceException(
        ex: DataIntegrityViolationException,
        order: Order?,
        userId: String,
        symbol: String,
        startTime: Long
    ): Nothing {
        val duration = System.currentTimeMillis() - startTime
        orderMetrics.incrementDatabaseErrors()

        structuredLogger.error("Order persistence failed: constraint violation",
            buildOrderContext(
                order = order,
                userId = userId,
                symbol = symbol,
                duration = duration,
                additionalFields = mapOf(
                    "error" to (ex.message ?: "Unknown error"),
                    "constraintViolation" to true
                )
            )
        )

        val exception = OrderPersistenceException("Failed to save order: constraint violation", ex)
            .withContext("userId", userId)
            .withContext("symbol", symbol)
        order?.id?.let { exception.withContext("orderId", it) }
        throw exception
    }

    private fun handleUnexpectedException(
        ex: Exception,
        order: Order?,
        userId: String,
        symbol: String,
        startTime: Long,
        operation: String
    ): Nothing {
        val duration = System.currentTimeMillis() - startTime
        orderMetrics.incrementUnexpectedErrors()

        structuredLogger.error("Unexpected error during $operation",
            buildOrderContext(
                order = order,
                userId = userId,
                symbol = symbol,
                duration = duration,
                additionalFields = mapOf(
                    "error" to (ex.message ?: "Unknown error"),
                    "exceptionType" to ex.javaClass.simpleName
                )
            )
        )

        val exception = OrderProcessingException("$operation failed due to unexpected error", ex)
            .withContext("userId", userId)
            .withContext("symbol", symbol)
        order?.id?.let { exception.withContext("orderId", it) }
        throw exception
    }

    private fun buildOrderContext(
        order: Order? = null,
        orderId: String? = null,
        userId: String? = null,
        symbol: String? = null,
        traceId: String? = null,
        duration: Long? = null,
        additionalFields: Map<String, Any> = emptyMap()
    ): Map<String, Any> = buildMap {
        order?.let {
            put("orderId", it.id)
            put("userId", it.userId)
            put("symbol", it.symbol)
            put("side", it.side.name)
            put("orderType", it.orderType.name)
            put("quantity", it.quantity.toString())
            it.price?.let { price -> put("price", price.toString()) }
            put("status", it.status.name)
            put("version", it.version.toString())
        }
        orderId?.let { put("orderId", it) }
        userId?.let { put("userId", it) }
        symbol?.let { put("symbol", it) }
        traceId?.let { put("traceId", it) }
        duration?.let { put("duration", it.toString()) }
        putAll(additionalFields)
    }
}
package com.trading.order.infrastructure.web

import com.trading.common.util.TraceIdGenerator
import com.trading.order.application.OrderSagaService
import com.trading.order.infrastructure.web.dto.CreateOrderRequest
import com.trading.order.infrastructure.web.dto.OrderResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
@RestController
@RequestMapping("/api/v1/orders")
@CrossOrigin(origins = ["http://localhost:3000"])
class OrderController(
    private val orderSagaService: OrderSagaService,
    private val traceIdGenerator: TraceIdGenerator
) {
    
    @PostMapping
    fun createOrder(
        @Valid @RequestBody request: CreateOrderRequest,
        @RequestHeader("X-User-Id") userId: String,
        @RequestHeader(value = "X-Trace-Id", required = false) traceId: String?,
    ): ResponseEntity<OrderResponse> {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(userId.length <= 50) { "User ID too long" }

        val orderResponse = orderSagaService.createOrderWithSaga(request, userId)

        return ResponseEntity.status(HttpStatus.CREATED).body(orderResponse)
    }

    @PostMapping("/{orderId}/cancel")
    fun cancelOrder(
        @PathVariable orderId: String,
        @RequestHeader("X-User-Id") userId: String,
        @RequestHeader(value = "X-Trace-Id", required = false) traceId: String?,
        @RequestBody(required = false) cancelRequest: CancelOrderRequest?,
    ): ResponseEntity<OrderResponse> {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(orderId.isNotBlank()) { "Order ID cannot be blank" }

        val reason = cancelRequest?.reason ?: "User cancelled"

        val cancelledOrder = orderSagaService.cancelOrderWithSaga(orderId, userId, reason)
        return ResponseEntity.ok(cancelledOrder)
    }
    
}


data class CancelOrderRequest(
    val reason: String?
)

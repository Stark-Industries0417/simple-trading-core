package com.trading.order.infrastructure.web

import com.trading.common.logging.StructuredLogger
import com.trading.common.util.TraceIdGenerator
import com.trading.order.application.OrderService
import com.trading.order.infrastructure.web.dto.CreateOrderRequest
import com.trading.order.infrastructure.web.dto.OrderResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*




@RestController
@RequestMapping("/api/v1/orders")
@CrossOrigin(origins = ["http://localhost:3000"])
class OrderController(
    private val orderService: OrderService,
    private val structuredLogger: StructuredLogger,
    private val traceIdGenerator: TraceIdGenerator
) {
    
    @PostMapping
    fun createOrder(
        @Valid @RequestBody request: CreateOrderRequest,
        @RequestHeader("X-User-Id") userId: String,
        @RequestHeader(value = "X-Trace-Id", required = false) traceId: String?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<OrderResponse> {
        
        val effectiveTraceId = traceId ?: traceIdGenerator.generate()
        val startTime = System.currentTimeMillis()
        
        try {
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            require(userId.length <= 50) { "User ID too long" }
            
            structuredLogger.logUserAction(
                userId = userId,
                action = "CREATE_ORDER",
                resource = "orders",
                details = buildMap {
                    put("symbol", request.symbol)
                    put("orderType", request.orderType.name)
                    put("side", request.side.name)
                    put("quantity", request.quantity.toString())
                    request.price?.let { put("price", it.toString()) }
                    put("userAgent", httpRequest.getHeader("User-Agent") ?: "Unknown")
                }
            )
            
            val orderResponse = orderService.createOrder(request, userId, effectiveTraceId)
            
            val duration = System.currentTimeMillis() - startTime
            structuredLogger.logPerformance(
                operation = "CREATE_ORDER",
                durationMs = duration,
                success = true,
                details = mapOf(
                    "orderId" to orderResponse.orderId,
                    "userId" to userId,
                    "symbol" to orderResponse.symbol
                )
            )
            
            return ResponseEntity.status(HttpStatus.CREATED).body(orderResponse)
            
        } catch (ex: IllegalArgumentException) {
            val duration = System.currentTimeMillis() - startTime
            structuredLogger.logPerformance(
                operation = "CREATE_ORDER",
                durationMs = duration,
                success = false,
                details = mapOf(
                    "userId" to userId,
                    "error" to "INVALID_USER_ID"
                )
            )
            throw ex
        }
    }


    @PostMapping("/{orderId}/cancel")
    fun cancelOrder(
        @PathVariable orderId: String,
        @RequestHeader("X-User-Id") userId: String,
        @RequestBody(required = false) cancelRequest: CancelOrderRequest?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<OrderResponse> {
        
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(orderId.isNotBlank()) { "Order ID cannot be blank" }
        
        val reason = cancelRequest?.reason ?: "User cancelled"
        
        structuredLogger.logUserAction(
            userId = userId,
            action = "CANCEL_ORDER",
            resource = "orders/$orderId",
            details = mapOf(
                "orderId" to orderId,
                "reason" to reason,
                "userAgent" to (httpRequest.getHeader("User-Agent") ?: "Unknown")
            )
        )
        
        val cancelledOrder = orderService.cancelOrder(orderId, userId, reason)
        
        structuredLogger.logPerformance(
            operation = "CANCEL_ORDER",
            durationMs = 0, // 추후 측정 로직 추가
            success = true,
            details = mapOf(
                "orderId" to orderId,
                "userId" to userId,
                "previousStatus" to "PENDING_OR_PARTIAL" // 실제 이전 상태는 서비스에서 로깅
            )
        )
        
        return ResponseEntity.ok(cancelledOrder)
    }
    
    @GetMapping("/active")
    fun getActiveOrders(
        @RequestHeader("X-User-Id") userId: String,
        @PageableDefault(size = 10, sort = ["createdAt"]) pageable: Pageable,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Page<OrderResponse>> {
        
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        
        structuredLogger.logUserAction(
            userId = userId,
            action = "LIST_ACTIVE_ORDERS",
            resource = "orders/active",
            details = mapOf(
                "page" to pageable.pageNumber.toString(),
                "size" to pageable.pageSize.toString(),
            )
        )
        
        val activeOrders = orderService.getActiveOrders(userId, pageable)
        
        structuredLogger.logPerformance(
            operation = "LIST_ACTIVE_ORDERS",
            durationMs = 0,
            success = true,
            details = mapOf(
                "userId" to userId,
                "activeOrdersCount" to activeOrders.totalElements.toString()
            )
        )
        
        return ResponseEntity.ok(activeOrders)
    }
    
    @GetMapping("/health")
    fun healthCheck(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(
            mapOf(
                "status" to "UP",
                "service" to "order-service",
                "timestamp" to System.currentTimeMillis()
            )
        )
    }
    
}


data class CancelOrderRequest(
    val reason: String?
)
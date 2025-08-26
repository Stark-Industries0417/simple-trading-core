package com.trading.order.domain

import com.trading.common.dto.OrderSide
import com.trading.common.dto.OrderStatus
import com.trading.common.dto.OrderType
import com.trading.common.exception.OrderStateException
import com.trading.common.util.UUIDv7Generator
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant




@Entity
@Table(
    name = "orders",
    indexes = [
        Index(name = "idx_user_created", columnList = "userId,createdAt"),
        Index(name = "idx_symbol_status", columnList = "symbol,status"),
        Index(name = "idx_trace_id", columnList = "traceId"),
        Index(name = "idx_created_at", columnList = "createdAt")
    ]
)
class Order private constructor(
    @Id
    @Column(length = 36)
    val id: String,
    
    @Column(nullable = false, length = 50)
    val userId: String,
    
    @Column(nullable = false, length = 20)
    val symbol: String,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val orderType: OrderType,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    val side: OrderSide,
    
    @Column(nullable = false, precision = 19, scale = 8)
    val quantity: BigDecimal,
    
    @Column(precision = 19, scale = 2) // 가격은 소수점 2자리까지
    val price: BigDecimal?,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OrderStatus = OrderStatus.PENDING,
    
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
    
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),
    
    @Column(nullable = false, length = 36)
    val traceId: String,
    
    @Version
    var version: Long = 0,
    
    @Column(precision = 19, scale = 8)
    var filledQuantity: BigDecimal = BigDecimal.ZERO,
    
    @Column(length = 500)
    var cancellationReason: String? = null
) {
    companion object {
        fun create(
            userId: String,
            symbol: String,
            orderType: OrderType,
            side: OrderSide,
            quantity: BigDecimal,
            price: BigDecimal?,
            traceId: String,
            uuidGenerator: UUIDv7Generator
        ): Order {
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            require(symbol.isNotBlank()) { "Symbol cannot be blank" }
            require(quantity > BigDecimal.ZERO) { "Quantity must be positive" }
            require(traceId.isNotBlank()) { "Trace ID cannot be blank" }
            
            if (orderType == OrderType.LIMIT) {
                requireNotNull(price) { "Price is required for LIMIT orders" }
                require(price > BigDecimal.ZERO) { "Price must be positive" }
            }
            
            return Order(
                id = uuidGenerator.generateOrderId(),
                userId = userId,
                symbol = symbol.uppercase(),
                orderType = orderType,
                side = side,
                quantity = quantity,
                price = price,
                traceId = traceId
            )
        }
    }
    
    fun cancel(reason: String = "User cancelled"): Order =
        try {
            require(canBeCancelled()) {
                "Order cannot be cancelled in current state: $status"
            }
            this.apply {
                status = OrderStatus.CANCELLED
                updatedAt = Instant.now()
                cancellationReason = reason
            }
        } catch(ex: IllegalArgumentException) {
            throw OrderStateException(
                ex.message ?: "Cannot cancel order in current state", ex
            ).withContext("orderId", id).withContext("currentStatus", status.name)
        }
    
    fun reject(reason: String): Order {
        require(status == OrderStatus.PENDING) { 
            "Only pending orders can be rejected" 
        }
        
        return this.apply {
            status = OrderStatus.REJECTED
            updatedAt = Instant.now()
            cancellationReason = reason
        }
    }
    
    fun partialFill(executedQuantity: BigDecimal): Order {
        require(status in listOf(OrderStatus.PENDING, OrderStatus.PARTIALLY_FILLED)) {
            "Order not in fillable state: $status"
        }
        require(executedQuantity > BigDecimal.ZERO) {
            "Executed quantity must be positive"
        }
        require(filledQuantity + executedQuantity <= quantity) {
            "Executed quantity would exceed order quantity"
        }
        
        return this.apply {
            filledQuantity = filledQuantity + executedQuantity
            status = if (filledQuantity == quantity) {
                OrderStatus.FILLED
            } else {
                OrderStatus.PARTIALLY_FILLED
            }
            updatedAt = Instant.now()
        }
    }
    
    fun completeFill(): Order {
        require(status in listOf(OrderStatus.PENDING, OrderStatus.PARTIALLY_FILLED)) {
            "Order not in fillable state: $status"
        }
        
        return this.apply {
            filledQuantity = quantity
            status = OrderStatus.FILLED
            updatedAt = Instant.now()
        }
    }
    
    fun getRemainingQuantity(): BigDecimal {
        return quantity - filledQuantity
    }
    
    fun getFillRatio(): BigDecimal {
        return if (quantity == BigDecimal.ZERO) {
            BigDecimal.ZERO
        } else {
            filledQuantity.divide(quantity, 4, BigDecimal.ROUND_HALF_UP)
        }
    }
    
    fun isFillable(): Boolean {
        return status in setOf(OrderStatus.PENDING, OrderStatus.PARTIALLY_FILLED)
    }
    
    private fun canBeCancelled(): Boolean {
        return status in setOf(OrderStatus.PENDING, OrderStatus.PARTIALLY_FILLED)
    }
    
    fun isActive(): Boolean {
        return status in setOf(OrderStatus.PENDING, OrderStatus.PARTIALLY_FILLED)
    }
    
    fun isCompleted(): Boolean {
        return status in setOf(OrderStatus.FILLED, OrderStatus.CANCELLED, OrderStatus.REJECTED)
    }
    
    fun isMarketOrder(): Boolean = orderType == OrderType.MARKET
    
    fun isLimitOrder(): Boolean = orderType == OrderType.LIMIT
    
    fun isBuyOrder(): Boolean = side == OrderSide.BUY
    
    fun isSellOrder(): Boolean = side == OrderSide.SELL
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Order) return false
        return id == other.id
    }
    
    override fun hashCode(): Int {
        return id.hashCode()
    }
    
    override fun toString(): String {
        return "Order(id='$id', userId='$userId', symbol='$symbol', type=$orderType, " +
                "side=$side, quantity=$quantity, price=$price, status=$status, " +
                "filledQuantity=$filledQuantity, version=$version)"
    }
}
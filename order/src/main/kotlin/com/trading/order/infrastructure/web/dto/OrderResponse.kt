package com.trading.order.infrastructure.web.dto

import com.trading.common.dto.order.OrderSide
import com.trading.common.dto.order.OrderStatus
import com.trading.common.dto.order.OrderType
import com.trading.order.domain.Order
import java.math.BigDecimal
import java.time.Instant




data class OrderResponse(
    val orderId: String,
    val userId: String,
    val symbol: String,
    val orderType: OrderType,
    val side: OrderSide,
    val quantity: BigDecimal,
    val price: BigDecimal?,
    val status: OrderStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val filledQuantity: BigDecimal,
    val remainingQuantity: BigDecimal,
    val fillRatio: BigDecimal,
    val cancellationReason: String?,
    val version: Long
) {
    companion object {
        fun from(order: Order): OrderResponse {
            return OrderResponse(
                orderId = order.id,
                userId = order.userId,
                symbol = order.symbol,
                orderType = order.orderType,
                side = order.side,
                quantity = order.quantity,
                price = order.price,
                status = order.status,
                createdAt = order.createdAt,
                updatedAt = order.updatedAt,
                filledQuantity = order.filledQuantity,
                remainingQuantity = order.getRemainingQuantity(),
                fillRatio = order.getFillRatio(),
                cancellationReason = order.cancellationReason,
                version = order.version
            )
        }
        
        fun fromList(orders: List<Order>): List<OrderResponse> {
            return orders.map { from(it) }
        }
    }
    
    fun isActive(): Boolean {
        return status in listOf(OrderStatus.PENDING, OrderStatus.PARTIALLY_FILLED)
    }
    
    fun isCompleted(): Boolean {
        return status in listOf(OrderStatus.FILLED, OrderStatus.CANCELLED, OrderStatus.REJECTED)
    }
    
    fun getFilledValue(): BigDecimal? {
        return price?.let { filledQuantity * it }
    }
    
    fun getRemainingValue(): BigDecimal? {
        return price?.let { remainingQuantity * it }
    }
    
    fun isBuyOrder(): Boolean = side == OrderSide.BUY
    
    fun isSellOrder(): Boolean = side == OrderSide.SELL
    
    fun isMarketOrder(): Boolean = orderType == OrderType.MARKET
    
    fun isLimitOrder(): Boolean = orderType == OrderType.LIMIT
}
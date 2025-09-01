package com.trading.order.infrastructure.web.dto

import com.trading.common.dto.order.OrderSide
import com.trading.common.dto.order.OrderType
import jakarta.validation.constraints.*
import java.math.BigDecimal




data class CreateOrderRequest(
    @field:NotBlank(message = "Symbol is required")
    @field:Size(min = 1, max = 20, message = "Symbol length must be 1-20 characters")
    @field:Pattern(
        regexp = "^[A-Z0-9]+$", 
        message = "Symbol must contain only uppercase letters and numbers"
    )
    val symbol: String,
    
    @field:NotNull(message = "Order type is required")
    val orderType: OrderType,
    
    @field:NotNull(message = "Side is required")
    val side: OrderSide,
    
    @field:NotNull(message = "Quantity is required")
    @field:DecimalMin(
        value = "0.00000001", 
        message = "Quantity must be at least 0.00000001"
    )
    @field:DecimalMax(
        value = "999999999.99999999", 
        message = "Quantity too large"
    )
    @field:Digits(
        integer = 9, 
        fraction = 8, 
        message = "Invalid quantity precision"
    )
    val quantity: BigDecimal,
    
    @field:DecimalMin(
        value = "0.01", 
        message = "Price must be at least 0.01"
    )
    @field:DecimalMax(
        value = "999999999.99", 
        message = "Price too large"
    )
    @field:Digits(
        integer = 9, 
        fraction = 2, 
        message = "Invalid price precision"
    )
    val price: BigDecimal?
) {

    @AssertTrue(message = "Price is required for LIMIT orders")
    fun isValidPriceForOrderType(): Boolean {
        return when (orderType) {
            OrderType.LIMIT -> price != null && price > BigDecimal.ZERO
            OrderType.MARKET -> true // 시장가 주문은 가격 불필요
        }
    }
    
    fun getNormalizedSymbol(): String = symbol.uppercase()
    
    fun isBuyOrder(): Boolean = side == OrderSide.BUY
    
    fun isSellOrder(): Boolean = side == OrderSide.SELL
    
    fun isMarketOrder(): Boolean = orderType == OrderType.MARKET
    
    fun isLimitOrder(): Boolean = orderType == OrderType.LIMIT
    
    fun getEstimatedValue(): BigDecimal? {
        return if (isLimitOrder() && price != null) {
            price * quantity
        } else {
            null
        }
    }
}
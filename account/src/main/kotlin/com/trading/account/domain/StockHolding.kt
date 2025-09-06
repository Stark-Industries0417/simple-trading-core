package com.trading.account.domain

import com.trading.common.util.UUIDv7Generator
import jakarta.persistence.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

@Entity
@Table(
    name = "stock_holdings",
    indexes = [
        Index(name = "idx_stock_holdings_user_symbol", columnList = "userId, symbol")
    ],
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["userId", "symbol"])
    ]
)
class StockHolding private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(nullable = false, length = 50)
    val userId: String,
    
    @Column(nullable = false, length = 10)
    val symbol: String,
    
    @Column(nullable = false, precision = 19, scale = 4)
    private var quantity: BigDecimal = BigDecimal.ZERO,
    
    @Column(nullable = false, precision = 19, scale = 4)
    private var availableQuantity: BigDecimal = BigDecimal.ZERO,
    
    @Column(nullable = false, precision = 19, scale = 4)
    private var averagePrice: BigDecimal = BigDecimal.ZERO,
    
    @Version
    var version: Long = 0,
    
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
    
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    companion object {
        fun create(userId: String, symbol: String): StockHolding {
            require(userId.isNotBlank()) { "UserId cannot be blank" }
            require(symbol.isNotBlank()) { "Symbol cannot be blank" }
            
            return StockHolding(
                userId = userId,
                symbol = symbol
            )
        }
    }
    
    fun addShares(purchaseQuantity: BigDecimal, purchasePrice: BigDecimal) {
        require(purchaseQuantity > BigDecimal.ZERO) { "Purchase quantity must be positive" }
        require(purchasePrice > BigDecimal.ZERO) { "Purchase price must be positive" }
        
        val totalCost = averagePrice * quantity + purchasePrice * purchaseQuantity
        val newQuantity = quantity + purchaseQuantity
        
        quantity = newQuantity
        availableQuantity = availableQuantity + purchaseQuantity
        averagePrice = if (newQuantity > BigDecimal.ZERO) {
            totalCost.divide(newQuantity, 4, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
        updatedAt = Instant.now()
    }
    
    fun reserveShares(reserveQuantity: BigDecimal): StockReservationResult {
        require(reserveQuantity > BigDecimal.ZERO) { "Reserve quantity must be positive" }
        
        return if (availableQuantity >= reserveQuantity) {
            availableQuantity = availableQuantity - reserveQuantity
            updatedAt = Instant.now()
            StockReservationResult.Success(reservationId = generateReservationId())
        } else {
            StockReservationResult.InsufficientShares(
                required = reserveQuantity,
                available = availableQuantity
            )
        }
    }
    
    fun confirmReservation(sellQuantity: BigDecimal) {
        require(sellQuantity > BigDecimal.ZERO) { "Sell quantity must be positive" }
        require(quantity >= sellQuantity) { 
            "Cannot sell more shares than owned" 
        }
        
        quantity = quantity - sellQuantity
        
        if (quantity == BigDecimal.ZERO) {
            averagePrice = BigDecimal.ZERO
        }
        updatedAt = Instant.now()
    }
    
    fun releaseReservation(releaseQuantity: BigDecimal) {
        require(releaseQuantity > BigDecimal.ZERO) { "Release quantity must be positive" }
        
        availableQuantity = availableQuantity + releaseQuantity
        updatedAt = Instant.now()
    }
    
    fun rollbackPurchase(rollbackQuantity: BigDecimal, purchasePrice: BigDecimal) {
        require(rollbackQuantity > BigDecimal.ZERO) { "Rollback quantity must be positive" }
        require(purchasePrice > BigDecimal.ZERO) { "Purchase price must be positive" }
        require(quantity >= rollbackQuantity) { "Cannot rollback more shares than owned" }
        
        quantity = quantity - rollbackQuantity
        availableQuantity = availableQuantity - rollbackQuantity
        
        if (quantity > BigDecimal.ZERO) {
            val totalCost = averagePrice * (quantity + rollbackQuantity) - purchasePrice * rollbackQuantity
            averagePrice = totalCost.divide(quantity, 4, RoundingMode.HALF_UP)
        } else {
            averagePrice = BigDecimal.ZERO
        }
        updatedAt = Instant.now()
    }
    
    fun rollbackSale(rollbackQuantity: BigDecimal) {
        require(rollbackQuantity > BigDecimal.ZERO) { "Rollback quantity must be positive" }
        
        quantity = quantity + rollbackQuantity
        availableQuantity = availableQuantity + rollbackQuantity
        updatedAt = Instant.now()
    }
    
    fun isConsistent(): Boolean {
        return availableQuantity <= quantity && 
               quantity >= BigDecimal.ZERO &&
               availableQuantity >= BigDecimal.ZERO &&
               averagePrice >= BigDecimal.ZERO
    }
    
    fun getQuantity(): BigDecimal = quantity
    fun getAvailableQuantity(): BigDecimal = availableQuantity
    fun getAveragePrice(): BigDecimal = averagePrice
    
    private fun generateReservationId(): String = UUIDv7Generator.generate()
}

sealed class StockReservationResult {
    data class Success(val reservationId: String) : StockReservationResult()
    data class InsufficientShares(
        val required: BigDecimal,
        val available: BigDecimal
    ) : StockReservationResult()
}
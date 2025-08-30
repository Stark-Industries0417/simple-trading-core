package com.trading.account.domain

import com.trading.common.util.UUIDv7Generator
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "accounts")
class Account private constructor(
    @Id
    @Column(length = 50)
    val userId: String,
    
    @Column(nullable = false, precision = 19, scale = 4)
    private var cashBalance: BigDecimal = BigDecimal.ZERO,
    
    @Column(nullable = false, precision = 19, scale = 4)
    private var availableCash: BigDecimal = BigDecimal.ZERO,
    
    @Version
    var version: Long = 0,
    
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
    
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    companion object {
        fun create(userId: String, initialBalance: BigDecimal): Account {
            require(userId.isNotBlank()) { "UserId cannot be blank" }
            require(initialBalance >= BigDecimal.ZERO) { "Initial balance cannot be negative" }
            
            return Account(
                userId = userId,
                cashBalance = initialBalance,
                availableCash = initialBalance
            )
        }
    }
    
    fun reserveCash(amount: BigDecimal): ReservationResult {
        require(amount > BigDecimal.ZERO) { "Amount must be positive" }
        
        return if (availableCash >= amount) {
            availableCash = availableCash - amount
            updatedAt = Instant.now()
            ReservationResult.Success(reservationId = generateReservationId())
        } else {
            ReservationResult.InsufficientFunds(
                required = amount,
                available = availableCash
            )
        }
    }
    
    fun confirmReservation(reservationId: String, amount: BigDecimal) {
        require(amount > BigDecimal.ZERO) { "Amount must be positive" }
        require(cashBalance - amount >= availableCash) { 
            "Confirmation would violate balance invariant" 
        }
        
        cashBalance = cashBalance - amount
        updatedAt = Instant.now()
    }
    
    fun releaseReservation(amount: BigDecimal) {
        require(amount > BigDecimal.ZERO) { "Amount must be positive" }
        
        availableCash = availableCash + amount
        updatedAt = Instant.now()
    }
    
    fun deposit(amount: BigDecimal) {
        require(amount > BigDecimal.ZERO) { "Deposit amount must be positive" }
        
        cashBalance = cashBalance + amount
        availableCash = availableCash + amount
        updatedAt = Instant.now()
    }
    
    fun isConsistent(): Boolean {
        return availableCash <= cashBalance && 
               cashBalance >= BigDecimal.ZERO &&
               availableCash >= BigDecimal.ZERO
    }
    
    fun getCashBalance(): BigDecimal = cashBalance
    fun getAvailableCash(): BigDecimal = availableCash
    
    private fun generateReservationId(): String = UUIDv7Generator.generate()
}

sealed class ReservationResult {
    data class Success(val reservationId: String) : ReservationResult()
    data class InsufficientFunds(
        val required: BigDecimal,
        val available: BigDecimal
    ) : ReservationResult()
}
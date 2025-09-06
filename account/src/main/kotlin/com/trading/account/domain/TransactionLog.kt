package com.trading.account.domain

import com.trading.common.util.UUIDv7Generator
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
    name = "transaction_logs",
    indexes = [
        Index(name = "idx_transaction_logs_user_id", columnList = "userId"),
        Index(name = "idx_transaction_logs_trade_id", columnList = "tradeId"),
        Index(name = "idx_transaction_logs_created_at", columnList = "createdAt")
    ]
)
class TransactionLog private constructor(
    @Id
    @Column(length = 50)
    val transactionId: String = UUIDv7Generator.generate(),
    
    @Column(nullable = false, length = 50)
    val userId: String,
    
    @Column(nullable = false, length = 50)
    val tradeId: String,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: TransactionType,
    
    @Column(nullable = false, length = 10)
    val symbol: String,
    
    @Column(nullable = false, precision = 19, scale = 4)
    val quantity: BigDecimal,
    
    @Column(nullable = false, precision = 19, scale = 4)
    val price: BigDecimal,
    
    @Column(nullable = false, precision = 19, scale = 4)
    val amount: BigDecimal,
    
    @Column(nullable = false, precision = 19, scale = 4)
    val balanceBefore: BigDecimal = BigDecimal.ZERO,
    
    @Column(nullable = false, precision = 19, scale = 4)
    val balanceAfter: BigDecimal = BigDecimal.ZERO,
    
    @Column(length = 500)
    val description: String? = null,
    
    @Column(nullable = false)
    val createdAt: Instant = Instant.now()
) {
    companion object {
        fun create(
            userId: String,
            tradeId: String,
            type: TransactionType,
            symbol: String,
            quantity: BigDecimal,
            price: BigDecimal,
            amount: BigDecimal,
            balanceBefore: BigDecimal = BigDecimal.ZERO,
            balanceAfter: BigDecimal = BigDecimal.ZERO,
            description: String? = null
        ): TransactionLog {
            require(userId.isNotBlank()) { "UserId cannot be blank" }
            require(tradeId.isNotBlank()) { "TradeId cannot be blank" }
            require(symbol.isNotBlank()) { "Symbol cannot be blank" }
            require(quantity > BigDecimal.ZERO) { "Quantity must be positive" }
            require(price >= BigDecimal.ZERO) { "Price cannot be negative" }
            require(amount >= BigDecimal.ZERO) { "Amount cannot be negative" }
            
            return TransactionLog(
                userId = userId,
                tradeId = tradeId,
                type = type,
                symbol = symbol,
                quantity = quantity,
                price = price,
                amount = amount,
                balanceBefore = balanceBefore,
                balanceAfter = balanceAfter,
                description = description
            )
        }
    }
}

enum class TransactionType {
    BUY,
    SELL,
    DEPOSIT,
    WITHDRAWAL,
    ROLLBACK
}
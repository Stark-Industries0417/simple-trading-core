package com.trading.account.domain

import com.trading.common.dto.order.OrderSide
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant


@Entity
@Table(
    name = "reservation_info",
    indexes = [
        Index(name = "idx_reservation_order_id", columnList = "order_id"),
        Index(name = "idx_reservation_user_id", columnList = "user_id"),
        Index(name = "idx_reservation_status", columnList = "status"),
        Index(name = "idx_reservation_created", columnList = "created_at")
    ]
)
class ReservationInfo private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(name = "order_id", nullable = false, unique = true, length = 50)
    val orderId: String,
    
    @Column(nullable = false, length = 50)
    val userId: String,
    
    @Column(nullable = false, length = 20)
    val symbol: String,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    val side: OrderSide,
    
    @Column(nullable = false, precision = 19, scale = 8)
    val quantity: BigDecimal,
    
    @Column(precision = 19, scale = 2)
    val price: BigDecimal? = null,
    
    @Column(precision = 19, scale = 4)
    val reservedAmount: BigDecimal? = null, // BUY 주문의 경우 예약 금액
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ReservationStatus = ReservationStatus.ACTIVE,
    
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
    
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    companion object {
        fun createForBuyOrder(
            orderId: String,
            userId: String,
            symbol: String,
            quantity: BigDecimal,
            price: BigDecimal? = null,
            traceId: String = ""
        ): ReservationInfo {
            val reservedAmount = price?.multiply(quantity)
            return ReservationInfo(
                orderId = orderId,
                userId = userId,
                symbol = symbol,
                side = OrderSide.BUY,
                quantity = quantity,
                price = price,
                reservedAmount = reservedAmount,
            )
        }
        
        fun createForSellOrder(
            orderId: String,
            userId: String,
            symbol: String,
            quantity: BigDecimal,
            price: BigDecimal? = null,
            traceId: String
        ): ReservationInfo {
            return ReservationInfo(
                orderId = orderId,
                userId = userId,
                symbol = symbol,
                side = OrderSide.SELL,
                quantity = quantity,
                price = price,
            )
        }
    }
    
    fun release() {
        require(status == ReservationStatus.ACTIVE) { 
            "Cannot release reservation in status: $status" 
        }
        status = ReservationStatus.RELEASED
        updatedAt = Instant.now()
    }
    
    fun confirm() {
        require(status == ReservationStatus.ACTIVE) { 
            "Cannot confirm reservation in status: $status" 
        }
        status = ReservationStatus.CONFIRMED
        updatedAt = Instant.now()
    }
    
    fun expire() {
        require(status == ReservationStatus.ACTIVE) { 
            "Cannot expire reservation in status: $status" 
        }
        status = ReservationStatus.EXPIRED
        updatedAt = Instant.now()
    }
    
    fun isActive(): Boolean = status == ReservationStatus.ACTIVE
}

enum class ReservationStatus {
    ACTIVE,     // 예약 활성 상태
    CONFIRMED,  // 체결되어 확정됨
    RELEASED,   // 예약 해제됨
    EXPIRED     // 만료됨
}
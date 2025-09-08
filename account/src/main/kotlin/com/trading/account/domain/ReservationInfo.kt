package com.trading.account.domain

import com.trading.common.dto.order.OrderSide
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

/**
 * 예약 정보를 저장하는 엔티티
 * 
 * OrderCreatedEvent 처리 시 예약 정보를 저장하고,
 * TradeFailedEvent 발생 시 이 정보를 조회하여 예약을 해제한다.
 * 
 * Simple is Best: 복잡한 cross-module 의존성 대신 로컬 저장소 활용
 */
@Entity
@Table(
    name = "reservation_info",
    indexes = [
        Index(name = "idx_reservation_order_id", columnList = "orderId"),
        Index(name = "idx_reservation_user_id", columnList = "userId"),
        Index(name = "idx_reservation_status", columnList = "status"),
        Index(name = "idx_reservation_created", columnList = "createdAt")
    ]
)
class ReservationInfo private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(nullable = false, unique = true, length = 36)
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
    
    @Column(length = 36)
    val traceId: String
) {
    companion object {
        fun createForBuyOrder(
            orderId: String,
            userId: String,
            symbol: String,
            quantity: BigDecimal,
            price: BigDecimal,
            traceId: String
        ): ReservationInfo {
            val reservedAmount = price * quantity
            return ReservationInfo(
                orderId = orderId,
                userId = userId,
                symbol = symbol,
                side = OrderSide.BUY,
                quantity = quantity,
                price = price,
                reservedAmount = reservedAmount,
                traceId = traceId
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
                traceId = traceId
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
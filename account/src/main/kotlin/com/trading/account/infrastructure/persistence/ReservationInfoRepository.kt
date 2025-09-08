package com.trading.account.infrastructure.persistence

import com.trading.account.domain.ReservationInfo
import com.trading.account.domain.ReservationStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface ReservationInfoRepository : JpaRepository<ReservationInfo, Long> {
    
    fun findByOrderId(orderId: String): ReservationInfo?
    
    fun findByOrderIdAndStatus(orderId: String, status: ReservationStatus): ReservationInfo?
    
    fun findByUserIdAndStatus(userId: String, status: ReservationStatus): List<ReservationInfo>
    
    @Query("""
        SELECT r FROM ReservationInfo r 
        WHERE r.status = :status 
        AND r.createdAt < :expiryTime
    """)
    fun findExpiredReservations(
        @Param("status") status: ReservationStatus = ReservationStatus.ACTIVE,
        @Param("expiryTime") expiryTime: Instant
    ): List<ReservationInfo>
    
    fun deleteByStatusAndCreatedAtBefore(
        status: ReservationStatus,
        cutoffTime: Instant
    ): Int
}
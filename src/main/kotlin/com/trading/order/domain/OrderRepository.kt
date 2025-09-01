package com.trading.order.domain

import com.trading.common.dto.order.OrderStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import jakarta.persistence.LockModeType



interface OrderRepository : JpaRepository<Order, String> {
    
    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<Order>
    
    fun findByIdAndUserId(orderId: String, userId: String): Order?
    
    @Query("""
        SELECT o 
        FROM Order o 
        WHERE o.userId = :userId AND o.status 
        IN :statuses ORDER BY o.createdAt DESC
    """)
    fun findActiveOrdersByUserId(
        @Param("userId") userId: String,
        @Param("statuses") statuses: List<OrderStatus> = listOf(OrderStatus.PENDING, OrderStatus.PARTIALLY_FILLED),
        pageable: Pageable
    ): Page<Order>
    
    @Query("""
        SELECT COUNT(o) 
        FROM Order o 
        WHERE o.userId = :userId 
        AND o.createdAt >= :startOfDay 
        AND o.createdAt < :endOfDay
    """)
    fun countOrdersByUserIdAndDateRange(
        @Param("userId") userId: String,
        @Param("startOfDay") startOfDay: Instant,
        @Param("endOfDay") endOfDay: Instant
    ): Long
}
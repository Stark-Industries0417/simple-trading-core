package com.trading.order.infrastructure.adapter

import com.trading.order.domain.OrderLimitService
import com.trading.order.domain.OrderRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId

@Component
@Transactional(readOnly = true)
class OrderLimitServiceAdapter(
    private val orderRepository: OrderRepository,
) : OrderLimitService {
    
    companion object {
        private val TIMEZONE = ZoneId.of("Asia/Seoul")
    }
    
    override fun getDailyOrderCount(userId: String): Long {
        return try {
            val today = LocalDate.now(TIMEZONE)
            val startOfDay = today.atStartOfDay(TIMEZONE).toInstant()
            val endOfDay = today.plusDays(1).atStartOfDay(TIMEZONE).toInstant()
            
            val dailyCount = orderRepository.countOrdersByUserIdAndDateRange(
                userId = userId,
                startOfDay = startOfDay,
                endOfDay = endOfDay
            )
            
            
            dailyCount
        } catch (ex: Exception) {
            0L
        }
    }
}
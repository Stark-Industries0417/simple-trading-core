package com.trading.order.infrastructure.adapter

import com.trading.common.logging.StructuredLogger
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
    private val structuredLogger: StructuredLogger
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
            
            structuredLogger.info("Daily order count retrieved",
                mapOf(
                    "userId" to userId,
                    "date" to today.toString(),
                    "count" to dailyCount.toString()
                )
            )
            
            dailyCount
        } catch (ex: Exception) {
            structuredLogger.error("Failed to retrieve daily order count",
                mapOf(
                    "userId" to userId,
                    "error" to (ex.message ?: "Unknown error")
                ),
                ex
            )
            0L
        }
    }
}
package com.trading.account.application

import com.trading.common.logging.StructuredLogger
import org.springframework.stereotype.Component

@Component
class OrderServiceClient(
    private val structuredLogger: StructuredLogger
) {
    
    fun revertOrderStatus(tradeId: String): Boolean {
        try {
            structuredLogger.info("Reverting order status",
                mapOf("tradeId" to tradeId)
            )
            
            return true
            
        } catch (ex: Exception) {
            structuredLogger.error("Failed to revert order status",
                mapOf("tradeId" to tradeId),
                ex
            )
            return false
        }
    }
    
    fun cancelOrder(orderId: String, reason: String): Boolean {
        try {
            structuredLogger.info("Requesting order cancellation",
                mapOf(
                    "orderId" to orderId,
                    "reason" to reason
                )
            )
            
            return true
            
        } catch (ex: Exception) {
            structuredLogger.error("Failed to cancel order",
                mapOf("orderId" to orderId),
                ex
            )
            return false
        }
    }
}
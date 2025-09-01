package com.trading.account.application

import com.trading.account.domain.ReservationResult
import com.trading.account.domain.StockReservationResult
import com.trading.common.dto.order.OrderSide
import com.trading.common.event.TradeExecutedEvent
import com.trading.common.event.order.OrderCreatedEvent
import com.trading.common.event.order.OrderCancelledEvent
import com.trading.common.logging.StructuredLogger
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class AccountEventHandler(
    private val accountService: AccountService,
    private val structuredLogger: StructuredLogger
) {
    
    @EventListener
    @Async
    fun handleTradeExecuted(event: TradeExecutedEvent) {
        structuredLogger.info("Received TradeExecutedEvent",
            mapOf(
                "tradeId" to event.tradeId,
                "buyUserId" to event.buyUserId,
                "sellUserId" to event.sellUserId,
                "symbol" to event.symbol,
                "quantity" to event.quantity.toString(),
                "price" to event.price.toString(),
                "traceId" to event.traceId
            )
        )
        
        try {
            val result = accountService.processTradeExecution(event)
            
            when (result) {
                is AccountUpdateResult.Success -> {
                    structuredLogger.info("Account update successful",
                        mapOf(
                            "tradeId" to event.tradeId,
                            "buyerNewBalance" to result.buyerNewBalance.toString(),
                            "sellerNewBalance" to result.sellerNewBalance.toString()
                        )
                    )
                }
                is AccountUpdateResult.Failure -> {
                    structuredLogger.error("Account update failed",
                        mapOf(
                            "tradeId" to event.tradeId,
                            "reason" to result.reason,
                            "shouldRetry" to result.shouldRetry.toString()
                        )
                    )
                    
                    if (result.shouldRetry) {
                        handleRetry(event)
                    }
                }
            }
        } catch (ex: Exception) {
            structuredLogger.error("Unexpected error processing trade",
                mapOf("tradeId" to event.tradeId),
                ex
            )
        }
    }
    
    @EventListener
    @Async
    fun handleOrderCreated(event: OrderCreatedEvent) {
        val order = event.order
        
        structuredLogger.info("Processing order for account reservation",
            mapOf(
                "orderId" to order.orderId,
                "userId" to order.userId,
                "side" to order.side.toString(),
                "quantity" to order.quantity.toString(),
                "price" to (order.price?.toString() ?: "MARKET"),
                "traceId" to event.traceId
            )
        )
        
        try {
            when (order.side) {
                OrderSide.BUY -> {
                    val amount = order.price?.multiply(order.quantity) 
                        ?: estimateMarketOrderAmount(order.symbol, order.quantity)
                    
                    val result = accountService.reserveFundsForOrder(
                        userId = order.userId,
                        amount = amount,
                        traceId = event.traceId
                    )
                    
                    when (result) {
                        is ReservationResult.Success -> {
                            structuredLogger.info("Funds reserved for buy order",
                                mapOf(
                                    "orderId" to order.orderId,
                                    "userId" to order.userId,
                                    "amount" to amount.toString(),
                                    "reservationId" to result.reservationId
                                )
                            )
                        }
                        is ReservationResult.InsufficientFunds -> {
                            structuredLogger.warn("Insufficient funds for buy order",
                                mapOf(
                                    "orderId" to order.orderId,
                                    "userId" to order.userId,
                                    "required" to result.required.toString(),
                                    "available" to result.available.toString()
                                )
                            )
                        }
                    }
                }
                OrderSide.SELL -> {
                    val result = accountService.reserveStocksForOrder(
                        userId = order.userId,
                        symbol = order.symbol,
                        quantity = order.quantity,
                        traceId = event.traceId
                    )
                    
                    when (result) {
                        is StockReservationResult.Success -> {
                            structuredLogger.info("Stocks reserved for sell order",
                                mapOf(
                                    "orderId" to order.orderId,
                                    "userId" to order.userId,
                                    "symbol" to order.symbol,
                                    "quantity" to order.quantity.toString(),
                                    "reservationId" to result.reservationId
                                )
                            )
                        }
                        is StockReservationResult.InsufficientShares -> {
                            structuredLogger.warn("Insufficient shares for sell order",
                                mapOf(
                                    "orderId" to order.orderId,
                                    "userId" to order.userId,
                                    "symbol" to order.symbol,
                                    "required" to result.required.toString(),
                                    "available" to result.available.toString()
                                )
                            )
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            structuredLogger.error("Failed to process order for account",
                mapOf("orderId" to order.orderId),
                ex
            )
        }
    }
    
    @EventListener
    @Async
    fun handleOrderCancelled(event: OrderCancelledEvent) {
        structuredLogger.info("Processing order cancellation for account",
            mapOf(
                "orderId" to event.orderId,
                "userId" to event.userId,
                "reason" to event.reason,
                "traceId" to event.traceId
            )
        )
        
        // TODO: 예약 해제 로직 구현
        // TODO: 예약 ID를 추적하여 정확한 금액/수량을 해제해야 함
    }
    
    private fun handleRetry(event: TradeExecutedEvent) {
        Thread.sleep(1000)
        
        structuredLogger.info("Retrying trade execution",
            mapOf("tradeId" to event.tradeId)
        )
        
        accountService.processTradeExecution(event)
    }
    
    private fun estimateMarketOrderAmount(symbol: String, quantity: BigDecimal): BigDecimal {
        return BigDecimal("100.00") * quantity
    }
}
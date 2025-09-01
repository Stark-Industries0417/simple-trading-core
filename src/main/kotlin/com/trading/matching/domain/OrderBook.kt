package com.trading.matching.domain

import com.trading.common.dto.order.OrderDTO
import com.trading.common.dto.order.OrderSide
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.ConcurrentHashMap



class OrderBook(
    val symbol: String
) {
    private val buyOrders = TreeMap<BigDecimal, Queue<OrderDTO>>(reverseOrder())
    private val sellOrders = TreeMap<BigDecimal, Queue<OrderDTO>>()
    private val orderMap = ConcurrentHashMap<String, OrderDTO>()
    
    fun processMarketOrder(order: OrderDTO): List<Trade> {
        val trades = mutableListOf<Trade>()
        var remainingQuantity = order.quantity
        
        val oppositeBook = if (order.side == OrderSide.BUY) sellOrders else buyOrders
        
        while (remainingQuantity > BigDecimal.ZERO && oppositeBook.isNotEmpty()) {
            val bestPrice = oppositeBook.firstKey()
            val ordersAtPrice = oppositeBook[bestPrice]!!
            
            while (remainingQuantity > BigDecimal.ZERO && ordersAtPrice.isNotEmpty()) {
                val matchingOrder = ordersAtPrice.peek()
                
                val tradeQuantity = minOf(remainingQuantity, matchingOrder.quantity)
                
                trades.add(Trade(
                    tradeId = UUID.randomUUID().toString(),
                    symbol = symbol,
                    buyOrderId = if (order.side == OrderSide.BUY) order.orderId else matchingOrder.orderId,
                    sellOrderId = if (order.side == OrderSide.SELL) order.orderId else matchingOrder.orderId,
                    buyUserId = if (order.side == OrderSide.BUY) order.userId else matchingOrder.userId,
                    sellUserId = if (order.side == OrderSide.SELL) order.userId else matchingOrder.userId,
                    price = bestPrice,
                    quantity = tradeQuantity,
                    timestamp = System.currentTimeMillis()
                ))
                
                remainingQuantity -= tradeQuantity
                val updatedQuantity = matchingOrder.quantity - tradeQuantity

                ordersAtPrice.poll()
                if (updatedQuantity == BigDecimal.ZERO) {
                    orderMap.remove(matchingOrder.orderId)
                } else {
                    
                    val updatedOrder = matchingOrder.copy(quantity = updatedQuantity)
                    ordersAtPrice.offer(updatedOrder)
                    orderMap[matchingOrder.orderId] = updatedOrder
                }
            }
            
            if (ordersAtPrice.isEmpty()) {
                oppositeBook.remove(bestPrice)
            }
        }
        
        return trades
    }
    
    fun processLimitOrder(order: OrderDTO): List<Trade> {
        val trades = mutableListOf<Trade>()
        var remainingQuantity = order.quantity
        
        val oppositeBook = if (order.side == OrderSide.BUY) sellOrders else buyOrders
        val sameBook = if (order.side == OrderSide.BUY) buyOrders else sellOrders
        
        while (remainingQuantity > BigDecimal.ZERO && canMatch(order, oppositeBook)) {
            val bestPrice = oppositeBook.firstKey()
            val ordersAtPrice = oppositeBook[bestPrice]!!
            
            while (remainingQuantity > BigDecimal.ZERO && ordersAtPrice.isNotEmpty()) {
                val matchingOrder = ordersAtPrice.peek()
                
                val tradeQuantity = minOf(remainingQuantity, matchingOrder.quantity)
                
                trades.add(Trade(
                    tradeId = UUID.randomUUID().toString(),
                    symbol = symbol,
                    buyOrderId = if (order.side == OrderSide.BUY) order.orderId else matchingOrder.orderId,
                    sellOrderId = if (order.side == OrderSide.SELL) order.orderId else matchingOrder.orderId,
                    buyUserId = if (order.side == OrderSide.BUY) order.userId else matchingOrder.userId,
                    sellUserId = if (order.side == OrderSide.SELL) order.userId else matchingOrder.userId,
                    price = bestPrice,
                    quantity = tradeQuantity,
                    timestamp = System.currentTimeMillis()
                ))
                
                remainingQuantity -= tradeQuantity
                val updatedQuantity = matchingOrder.quantity - tradeQuantity
                ordersAtPrice.poll()

                if (updatedQuantity == BigDecimal.ZERO) {
                    orderMap.remove(matchingOrder.orderId)
                } else {
                    val updatedOrder = matchingOrder.copy(quantity = updatedQuantity)
                    ordersAtPrice.offer(updatedOrder)
                    orderMap[matchingOrder.orderId] = updatedOrder
                }
            }
            
            if (ordersAtPrice.isEmpty()) {
                oppositeBook.remove(bestPrice)
            }
        }
        
        if (remainingQuantity > BigDecimal.ZERO) {
            val remainingOrder = order.copy(quantity = remainingQuantity)
            addToOrderBook(remainingOrder, sameBook)
        }
        
        return trades
    }
    
    fun cancelOrder(orderId: String): Boolean {
        val order = orderMap.remove(orderId) ?: return false
        
        val book = if (order.side == OrderSide.BUY) buyOrders else sellOrders
        val ordersAtPrice = book[order.price] ?: return false
        
        ordersAtPrice.remove(order)
        if (ordersAtPrice.isEmpty()) {
            book.remove(order.price)
        }
        
        return true
    }
    
    private fun canMatch(order: OrderDTO, oppositeBook: TreeMap<BigDecimal, Queue<OrderDTO>>): Boolean {
        if (oppositeBook.isEmpty()) return false
        
        val bestPrice = oppositeBook.firstKey()
        return if (order.side == OrderSide.BUY) {
            order.price!! >= bestPrice
        } else {
            order.price!! <= bestPrice
        }
    }
    
    private fun addToOrderBook(order: OrderDTO, book: TreeMap<BigDecimal, Queue<OrderDTO>>) {
        val ordersAtPrice = book.computeIfAbsent(order.price!!) { LinkedList() }
        ordersAtPrice.offer(order.copy())
        orderMap[order.orderId] = order
    }
    
    fun getOrderCount(): Int = orderMap.size
    fun getBidLevels(): Int = buyOrders.size
    fun getAskLevels(): Int = sellOrders.size
    fun getBestBid(): BigDecimal? = buyOrders.firstKey()
    fun getBestAsk(): BigDecimal? = sellOrders.firstKey()
    
    fun getOrderBookSnapshot(): OrderBookSnapshot {
        return OrderBookSnapshot(
            symbol = symbol,
            bids = buyOrders.map { (price, orders) ->
                PriceLevel(price, orders.sumOf { it.quantity })
            },
            asks = sellOrders.map { (price, orders) ->
                PriceLevel(price, orders.sumOf { it.quantity })
            },
            timestamp = System.currentTimeMillis()
        )
    }
}

data class Trade(
    val tradeId: String,
    val symbol: String,
    val buyOrderId: String,
    val sellOrderId: String,
    val buyUserId: String,
    val sellUserId: String,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val timestamp: Long
)

data class OrderBookSnapshot(
    val symbol: String,
    val bids: List<PriceLevel>,
    val asks: List<PriceLevel>,
    val timestamp: Long
)

data class PriceLevel(
    val price: BigDecimal,
    val quantity: BigDecimal
)
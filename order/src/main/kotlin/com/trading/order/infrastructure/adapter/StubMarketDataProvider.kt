package com.trading.order.infrastructure.adapter

import com.trading.common.adapter.MarketDataProvider
import java.math.BigDecimal

class StubMarketDataProvider : MarketDataProvider {
    
    private val prices = mapOf(
        "AAPL" to BigDecimal("150.00"),
        "GOOGL" to BigDecimal("2800.00"),
        "TSLA" to BigDecimal("800.00"),
        "MSFT" to BigDecimal("300.00"),
        "AMZN" to BigDecimal("3300.00")
    )
    
    override fun getCurrentPrice(symbol: String): BigDecimal? {
        return prices[symbol]
    }
    
    override fun hasMarketData(symbol: String): Boolean {
        return prices.containsKey(symbol)
    }
}
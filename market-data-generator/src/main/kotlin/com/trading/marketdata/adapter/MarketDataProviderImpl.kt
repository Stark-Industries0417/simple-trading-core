package com.trading.marketdata.adapter

import com.trading.common.adapter.MarketDataProvider
import com.trading.marketdata.generator.MarketDataGenerator
import org.springframework.stereotype.Component
import java.math.BigDecimal



@Component
class MarketDataProviderImpl(
    private val marketDataGenerator: MarketDataGenerator
) : MarketDataProvider {
    
    override fun getCurrentPrice(symbol: String): BigDecimal? {
        return marketDataGenerator.getCurrentPrice(symbol)
    }
    
    override fun hasMarketData(symbol: String): Boolean {
        return marketDataGenerator.getCurrentPrice(symbol) != null
    }
}
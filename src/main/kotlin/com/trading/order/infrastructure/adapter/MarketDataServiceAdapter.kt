package com.trading.order.infrastructure.adapter

import com.trading.common.logging.StructuredLogger
import com.trading.marketdata.generator.MarketDataGenerator
import com.trading.order.domain.MarketDataService
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class MarketDataServiceAdapter(
    private val marketDataGenerator: MarketDataGenerator,
    private val structuredLogger: StructuredLogger
) : MarketDataService {
    
    override fun getCurrentPrice(symbol: String): BigDecimal? {
        return try {
            val price = marketDataGenerator.getCurrentPrice(symbol)
            
            if (price != null) {
                structuredLogger.info("Retrieved current price for symbol",
                    mapOf(
                        "symbol" to symbol,
                        "price" to price.toString()
                    )
                )
            } else {
                structuredLogger.warn("No price available for symbol",
                    mapOf("symbol" to symbol)
                )
            }
            
            price
        } catch (ex: Exception) {
            structuredLogger.error("Failed to retrieve market price",
                mapOf(
                    "symbol" to symbol,
                    "error" to (ex.message ?: "Unknown error")
                ),
                ex
            )
            null
        }
    }
}
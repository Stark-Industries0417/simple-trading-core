package com.trading.marketdata.service

import com.trading.common.dto.market.MarketDataDTO
import com.trading.common.event.market.MarketDataUpdatedEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

@Component
@ConditionalOnProperty(
    prefix = "market.data.listener",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class MarketDataEventListener {
    companion object {
        private val logger = LoggerFactory.getLogger(MarketDataEventListener::class.java)
        private var eventCount = 0L
        private var lastLogTime = System.currentTimeMillis()
        private const val LOG_INTERVAL_MS = 5000
    }

    @EventListener
    fun handleMarketDataUpdated(event: MarketDataUpdatedEvent) {
        eventCount++
        
        if (eventCount <= 10) {
            logIndividualEvent(event)
        }
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastLogTime > LOG_INTERVAL_MS) {
            logSummary(currentTime)
            lastLogTime = currentTime
        }
    }
    
    private fun logIndividualEvent(event: MarketDataUpdatedEvent) {
        val marketData = event.marketData
        val changePercent = calculateChangePercent(marketData)
        
        logger.info(
            "📈 MarketData Event: symbol={}, price={}, volume={}, change={}%, " +
            "bid={}, ask={}, eventId={}, traceId={}",
            marketData.symbol,
            marketData.price.setScale(2, RoundingMode.HALF_UP),
            marketData.volume,
            changePercent,
            marketData.bid?.setScale(2, RoundingMode.HALF_UP),
            marketData.ask?.setScale(2, RoundingMode.HALF_UP),
            event.eventId.take(8),
            event.traceId.take(8)
        )
    }
    
    private fun logSummary(currentTime: Long) {
        val elapsedSeconds = (currentTime - (lastLogTime - LOG_INTERVAL_MS)) / 1000.0
        val eventsPerSecond = (eventCount.toDouble() / elapsedSeconds * 1000.0 / LOG_INTERVAL_MS).let {
            if (it.isNaN()) 0.0 else it
        }
        
        logger.info(
            "📊 MarketData Summary: total_events={}, events_per_second={:.1f}",
            eventCount,
            eventsPerSecond
        )
    }
    
    private fun calculateChangePercent(marketData: MarketDataDTO): String {
        return "0.00"
    }
    
    fun getListenerStats(): Map<String, Any> {
        return mapOf(
            "totalEventsReceived" to eventCount,
            "lastEventTime" to lastLogTime,
            "listenerActive" to true
        )
    }
    
    fun resetStats() {
        eventCount = 0
        lastLogTime = System.currentTimeMillis()
        logger.info("📊 MarketData event listener stats reset")
    }
}
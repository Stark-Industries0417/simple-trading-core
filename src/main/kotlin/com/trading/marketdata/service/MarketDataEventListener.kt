package com.trading.marketdata.service

import com.trading.common.event.market.MarketDataUpdatedEvent
import com.trading.common.logging.StructuredLogger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 테스트용 마켓 데이터 이벤트 리스너
 * 
 * 발행된 MarketDataUpdatedEvent를 구독하여 로그에 출력합니다.
 * 개발 환경에서만 활성화되며, 이벤트 발행 상태를 모니터링할 수 있습니다.
 */
@Component
@ConditionalOnProperty(
    prefix = "market.data.listener",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true  // 기본적으로 활성화
)
class MarketDataEventListener(
    private val structuredLogger: StructuredLogger
) {
    companion object {
        private val logger = LoggerFactory.getLogger(MarketDataEventListener::class.java)
        private var eventCount = 0L
        private var lastLogTime = System.currentTimeMillis()
        private const val LOG_INTERVAL_MS = 5000  // 5초마다 요약 로그
    }

    @EventListener
    fun handleMarketDataUpdated(event: MarketDataUpdatedEvent) {
        eventCount++
        
        // 개별 이벤트 디버그 로그 (첫 10개만)
        if (eventCount <= 10) {
            logIndividualEvent(event)
        }
        
        // 주기적 요약 로그 (5초마다)
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
            event.eventId.take(8),  // 이벤트 ID 앞 8자리만
            event.traceId.take(8)   // 트레이스 ID 앞 8자리만
        )
        
        // 구조화된 로깅
        structuredLogger.logEvent(event)
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
    
    private fun calculateChangePercent(marketData: com.trading.common.dto.MarketDataDTO): String {
        // 간단한 변화율 계산 (이전 가격 정보가 없으므로 임시로 0% 반환)
        return "0.00"
    }
    
    /**
     * 리스너 상태 정보 조회 (모니터링용)
     */
    fun getListenerStats(): Map<String, Any> {
        return mapOf(
            "totalEventsReceived" to eventCount,
            "lastEventTime" to lastLogTime,
            "listenerActive" to true
        )
    }
    
    /**
     * 이벤트 카운터 리셋 (테스트용)
     */
    fun resetStats() {
        eventCount = 0
        lastLogTime = System.currentTimeMillis()
        logger.info("📊 MarketData event listener stats reset")
    }
}
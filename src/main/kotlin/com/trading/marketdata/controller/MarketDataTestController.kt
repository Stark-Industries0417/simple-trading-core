package com.trading.marketdata.controller

import com.trading.marketdata.generator.MarketDataGenerator
import com.trading.marketdata.service.MarketDataEventListener
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 마켓 데이터 테스트용 컨트롤러
 * 
 * 개발 환경에서 마켓 데이터 생성기와 이벤트 리스너의 상태를 
 * 모니터링하고 제어하기 위한 간단한 REST API를 제공합니다.
 */
@RestController
@RequestMapping("/api/test/market-data")
@ConditionalOnProperty(
    prefix = "market.data",
    name = ["enabled"],
    havingValue = "true"
)
class MarketDataTestController(
    private val marketDataGenerator: MarketDataGenerator,
    private val marketDataEventListener: MarketDataEventListener
) {

    /**
     * 마켓 데이터 생성기 상태 조회
     */
    @GetMapping("/generator/status")
    fun getGeneratorStatus(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "isRunning" to marketDataGenerator.isRunning(),
            "currentPrices" to mapOf(
                "AAPL" to marketDataGenerator.getCurrentPrice("AAPL"),
                "GOOGL" to marketDataGenerator.getCurrentPrice("GOOGL"),
                "MSFT" to marketDataGenerator.getCurrentPrice("MSFT")
            ),
            "priceData" to mapOf(
                "AAPL" to marketDataGenerator.getPriceData("AAPL"),
                "GOOGL" to marketDataGenerator.getPriceData("GOOGL"),
                "MSFT" to marketDataGenerator.getPriceData("MSFT")
            )
        ))
    }

    /**
     * 이벤트 리스너 통계 조회
     */
    @GetMapping("/listener/stats")
    fun getListenerStats(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(marketDataEventListener.getListenerStats())
    }

    /**
     * 이벤트 리스너 통계 리셋
     */
    @PostMapping("/listener/reset")
    fun resetListenerStats(): ResponseEntity<Map<String, String>> {
        marketDataEventListener.resetStats()
        return ResponseEntity.ok(mapOf(
            "message" to "Event listener stats reset successfully"
        ))
    }

    /**
     * 특정 심볼의 현재 가격 조회
     */
    @GetMapping("/price/{symbol}")
    fun getCurrentPrice(@PathVariable symbol: String): ResponseEntity<Map<String, Any?>> {
        val price = marketDataGenerator.getCurrentPrice(symbol.uppercase())
        val priceData = marketDataGenerator.getPriceData(symbol.uppercase())
        
        return if (price != null) {
            ResponseEntity.ok(mapOf(
                "symbol" to symbol.uppercase(),
                "currentPrice" to price,
                "priceData" to priceData
            ))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    /**
     * 전체 시스템 상태 요약
     */
    @GetMapping("/summary")
    fun getSystemSummary(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "generator" to mapOf(
                "isRunning" to marketDataGenerator.isRunning(),
                "symbolCount" to 3
            ),
            "listener" to marketDataEventListener.getListenerStats(),
            "prices" to mapOf(
                "AAPL" to marketDataGenerator.getCurrentPrice("AAPL"),
                "GOOGL" to marketDataGenerator.getCurrentPrice("GOOGL"),
                "MSFT" to marketDataGenerator.getCurrentPrice("MSFT")
            )
        ))
    }
}
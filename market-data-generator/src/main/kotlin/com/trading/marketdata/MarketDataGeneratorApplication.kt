package com.trading.marketdata

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Market Data Generator Application
 * 
 * 가상 시세 데이터를 생성하여 Spring Event로 발행하는 독립 실행 모듈
 * - 실시간 시세 데이터 생성 및 발행
 * - 테스트 환경을 위한 시뮬레이션 데이터 제공
 */
@SpringBootApplication(
    scanBasePackages = [
        "com.trading.marketdata",
        "com.trading.common"
    ]
)
class MarketDataGeneratorApplication

fun main(args: Array<String>) {
    runApplication<MarketDataGeneratorApplication>(*args)
}
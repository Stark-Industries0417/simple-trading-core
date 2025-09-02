package com.trading.matching

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Matching Engine Application
 * 
 * 주문 매칭을 담당하는 독립적인 마이크로서비스
 * - Lock-free 매칭 엔진
 * - 이벤트 기반 주문 처리
 * - 체결 이벤트 발행
 */
@SpringBootApplication(
    scanBasePackages = [
        "com.trading.matching",
        "com.trading.common"
    ]
)
@EnableScheduling
class MatchingEngineApplication

fun main(args: Array<String>) {
    runApplication<MatchingEngineApplication>(*args)
}
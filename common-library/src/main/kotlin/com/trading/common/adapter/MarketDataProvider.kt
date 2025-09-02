package com.trading.common.adapter

import java.math.BigDecimal

/**
 * 시세 데이터 제공 인터페이스
 * 
 * 각 모듈은 이 인터페이스를 구현하여 시세 데이터를 제공받을 수 있다.
 * 실제 구현체는 market-data 모듈이나 main 애플리케이션에서 제공한다.
 */
interface MarketDataProvider {
    /**
     * 특정 심볼의 현재 가격을 조회
     * 
     * @param symbol 주식 심볼
     * @return 현재 가격, 시세가 없으면 null
     */
    fun getCurrentPrice(symbol: String): BigDecimal?
    
    /**
     * 특정 심볼의 시세 데이터 존재 여부 확인
     * 
     * @param symbol 주식 심볼
     * @return 시세 데이터 존재 여부
     */
    fun hasMarketData(symbol: String): Boolean = getCurrentPrice(symbol) != null
}
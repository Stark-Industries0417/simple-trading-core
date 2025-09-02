package com.trading.common.adapter

import java.math.BigDecimal

/**
 * 계좌 서비스 제공 인터페이스
 * 
 * Order 모듈에서 계좌 정보를 조회하기 위한 인터페이스.
 * 실제 구현체는 account 모듈이나 main 애플리케이션에서 제공한다.
 */
interface AccountServiceProvider {
    /**
     * 사용자의 현금 잔고가 충분한지 확인
     * 
     * @param userId 사용자 ID
     * @param amount 필요한 금액
     * @return 잔고 충분 여부
     */
    fun hasSufficientCash(userId: String, amount: BigDecimal): Boolean
    
    /**
     * 사용자의 주식 보유량이 충분한지 확인
     * 
     * @param userId 사용자 ID
     * @param symbol 주식 심볼
     * @param quantity 필요한 수량
     * @return 보유량 충분 여부
     */
    fun hasSufficientStock(userId: String, symbol: String, quantity: BigDecimal): Boolean
    
    /**
     * 사용자 계좌 존재 여부 확인
     * 
     * @param userId 사용자 ID
     * @return 계좌 존재 여부
     */
    fun accountExists(userId: String): Boolean
}
package com.trading.common.dto

/**
 * Order Status Enumeration
 * Phase 3 요구사항에 따른 주문 상태 정의
 */
enum class OrderStatus {
    PENDING,          // 대기 중
    PARTIALLY_FILLED, // 부분 체결 (새로 추가)
    FILLED,           // 완전 체결
    CANCELLED,        // 취소
    REJECTED          // 거부
}

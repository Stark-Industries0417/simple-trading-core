package com.trading.common.dto.order



enum class OrderStatus {
    PENDING,          // 대기 중
    PARTIALLY_FILLED, // 부분 체결 (새로 추가)
    FILLED,           // 완전 체결
    CANCELLED,        // 취소
    REJECTED          // 거부
}

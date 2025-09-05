package com.trading.common.dto.order



enum class OrderStatus {
    CREATED,          // 생성됨 (CDC 감지용)
    PENDING,          // 대기 중
    PARTIALLY_FILLED, // 부분 체결 (새로 추가)
    FILLED,           // 완전 체결
    COMPLETED,        // Saga 완료
    CANCELLED,        // 취소
    REJECTED,         // 거부
    TIMEOUT           // 타임아웃
}

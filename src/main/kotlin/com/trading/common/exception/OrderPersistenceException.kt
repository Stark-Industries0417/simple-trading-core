package com.trading.common.exception

/**
 * 주문 저장/조회 시 데이터베이스 오류가 발생했을 때 사용하는 예외
 * Phase 3: Order Management 예외 처리 - 신뢰성 우선
 */
class OrderPersistenceException(
    message: String,
    cause: Throwable? = null,
    context: Map<String, Any> = emptyMap()
) : BusinessException(
    message = message,
    cause = cause,
    errorCode = "ORDER_PERSISTENCE_ERROR",
    context = context.toMutableMap()
)
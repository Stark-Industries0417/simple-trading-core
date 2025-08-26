package com.trading.common.exception

/**
 * 주문 조회 중 오류가 발생했을 때 사용하는 예외
 * Phase 3: Order Management 예외 처리
 */
class OrderRetrievalException(
    message: String,
    cause: Throwable? = null,
    context: Map<String, Any> = emptyMap()
) : BusinessException(
    message = message,
    cause = cause,
    errorCode = "ORDER_RETRIEVAL_ERROR",
    context = context.toMutableMap()
)
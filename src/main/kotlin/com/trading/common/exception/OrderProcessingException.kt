package com.trading.common.exception

/**
 * 주문 처리 중 일반적인 오류가 발생했을 때 사용하는 예외
 * Phase 3: Order Management 예외 처리 - 최상위 래핑 예외
 */
class OrderProcessingException(
    message: String,
    cause: Throwable? = null,
    context: Map<String, Any> = emptyMap()
) : BusinessException(
    message = message,
    cause = cause,
    errorCode = "ORDER_PROCESSING_ERROR",
    context = context.toMutableMap()
)
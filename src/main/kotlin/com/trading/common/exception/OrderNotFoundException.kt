package com.trading.common.exception

/**
 * 주문을 찾을 수 없을 때 발생하는 예외
 * Phase 3: Order Management 예외 처리
 */
class OrderNotFoundException(
    message: String,
    cause: Throwable? = null,
    context: Map<String, Any> = emptyMap()
) : BusinessException(
    message = message,
    cause = cause,
    errorCode = "ORDER_NOT_FOUND",
    context = context.toMutableMap()
)
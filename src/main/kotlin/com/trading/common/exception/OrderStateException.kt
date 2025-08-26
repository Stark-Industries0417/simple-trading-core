package com.trading.common.exception

/**
 * 주문 상태가 잘못되었을 때 발생하는 예외
 * Phase 3: Order Management 예외 처리
 */
class OrderStateException(
    message: String,
    cause: Throwable? = null,
    context: Map<String, Any> = emptyMap()
) : BusinessException(
    message = message,
    cause = cause,
    errorCode = "INVALID_ORDER_STATE",
    context = context.toMutableMap()
)
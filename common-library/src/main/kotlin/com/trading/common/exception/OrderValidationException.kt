package com.trading.common.exception
class OrderValidationException(
    message: String,
    cause: Throwable? = null,
    context: Map<String, Any> = emptyMap()
) : BusinessException(
    message = message,
    cause = cause,
    errorCode = "ORDER_VALIDATION_ERROR",
    context = context.toMutableMap()
)

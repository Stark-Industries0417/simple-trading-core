package com.trading.common.exception.order

import com.trading.common.exception.base.BusinessException


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
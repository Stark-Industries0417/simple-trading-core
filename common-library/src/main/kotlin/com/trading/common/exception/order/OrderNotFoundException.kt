package com.trading.common.exception.order

import com.trading.common.exception.base.BusinessException




class OrderNotFoundException(
    message: String,
    cause: Throwable? = null,
    context: MutableMap<String, Any> = mutableMapOf()
) : BusinessException(
    message = message,
    cause = cause,
    errorCode = "ORDER_NOT_FOUND",
    context = context
)
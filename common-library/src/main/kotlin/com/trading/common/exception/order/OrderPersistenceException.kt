package com.trading.common.exception.order

import com.trading.common.exception.base.BusinessException




class OrderPersistenceException(
    message: String,
    cause: Throwable? = null,
    context: MutableMap<String, Any> = mutableMapOf()
) : BusinessException(
    message = message,
    cause = cause,
    errorCode = "ORDER_PERSISTENCE_ERROR",
    context = context
)
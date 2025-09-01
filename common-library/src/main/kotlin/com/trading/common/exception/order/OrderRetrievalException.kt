package com.trading.common.exception.order

import com.trading.common.exception.base.BusinessException



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
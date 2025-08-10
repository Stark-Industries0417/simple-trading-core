package com.trading.common.exception
class AccountLockException(
    message: String,
    cause: Throwable? = null,
    val userId: String? = null,
    context: Map<String, Any> = emptyMap()
) : BusinessException(
    message = message,
    cause = cause,
    errorCode = "ACCOUNT_LOCK_ERROR",
    context = buildMap {
        putAll(context)
        userId?.let { put("userId", it) }
    }.toMutableMap()
)

package com.trading.common.exception.base


abstract class BusinessException(
    message: String,
    cause: Throwable? = null,
    val errorCode: String = "BUSINESS_ERROR",
    val context: MutableMap<String, Any> = mutableMapOf()
) : RuntimeException(message, cause) {
    fun withContext(key: String, value: Any): BusinessException {
        context[key] = value
        return this
    }
}

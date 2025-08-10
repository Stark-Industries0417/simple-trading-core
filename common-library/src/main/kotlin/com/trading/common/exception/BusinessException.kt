package com.trading.common.exception
abstract class BusinessException(
    message: String,
    cause: Throwable? = null,
    val errorCode: String = "BUSINESS_ERROR",
    val context: Map<String, Any> = emptyMap()
) : RuntimeException(message, cause) {
    fun withContext(key: String, value: Any): BusinessException {
        return this.also {
            (context as MutableMap).put(key, value)
        }
    }
}

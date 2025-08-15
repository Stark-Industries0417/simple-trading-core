package com.trading.common.exception
class MatchingEngineException(
    message: String,
    cause: Throwable? = null,
    context: Map<String, Any> = emptyMap()
) : BusinessException(
    message = message,
    cause = cause,
    errorCode = "MATCHING_ENGINE_ERROR",
    context = context.toMutableMap()
)

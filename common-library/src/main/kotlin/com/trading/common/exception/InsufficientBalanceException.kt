package com.trading.common.exception
import java.math.BigDecimal
class InsufficientBalanceException(
    message: String,
    cause: Throwable? = null,
    val requiredAmount: BigDecimal? = null,
    val availableAmount: BigDecimal? = null,
    context: Map<String, Any> = emptyMap()
) : BusinessException(
    message = message,
    cause = cause,
    errorCode = "INSUFFICIENT_BALANCE",
    context = buildMap {
        putAll(context)
        requiredAmount?.let { put("requiredAmount", it) }
        availableAmount?.let { put("availableAmount", it) }
    }.toMutableMap()
)

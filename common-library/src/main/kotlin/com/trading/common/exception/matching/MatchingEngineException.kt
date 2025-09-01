package com.trading.common.exception.matching

import com.trading.common.exception.base.BusinessException
import java.math.BigDecimal


class MatchingEngineException(
    message: String,
    cause: Throwable? = null,
    val requiredAmount: BigDecimal? = null,
    val availableAmount: BigDecimal? = null,
    context: Map<String, Any> = emptyMap()
) : BusinessException(
    message = message,
    cause = cause,
    errorCode = "MATCHING_ENGINE",
    context = buildMap {
        putAll(context)
        requiredAmount?.let { put("requiredAmount", it) }
        availableAmount?.let { put("availableAmount", it) }
    }.toMutableMap()
)

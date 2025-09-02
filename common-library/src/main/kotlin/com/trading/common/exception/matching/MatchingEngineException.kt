package com.trading.common.exception.matching

import com.trading.common.exception.base.BusinessException
import java.math.BigDecimal


class MatchingEngineException(
    message: String,
    cause: Throwable? = null,
    val requiredAmount: BigDecimal? = null,
    val availableAmount: BigDecimal? = null,
    context: MutableMap<String, Any> = mutableMapOf()
) : BusinessException(
    message = message,
    cause = cause,
    errorCode = "MATCHING_ENGINE",
    context = mutableMapOf<String, Any>().also { map ->
        map.putAll(context)
        requiredAmount?.let { map["requiredAmount"] = it }
        availableAmount?.let { map["availableAmount"] = it }
    }
)

package com.trading.common.exception.account

import com.trading.common.exception.base.BusinessException
import java.math.BigDecimal


class InsufficientBalanceException(
    message: String,
    cause: Throwable? = null,
    val requiredAmount: BigDecimal? = null,
    val availableAmount: BigDecimal? = null,
    context: MutableMap<String, Any> = mutableMapOf()
) : BusinessException(
    message = message,
    cause = cause,
    errorCode = "INSUFFICIENT_BALANCE",
    context = mutableMapOf<String, Any>().also { map ->
        map.putAll(context)
        requiredAmount?.let { map["requiredAmount"] = it }
        availableAmount?.let { map["availableAmount"] = it }
    }
)

package com.trading.common.util
import com.github.f4b6a3.uuid.UuidCreator
import org.springframework.stereotype.Component


@Component
class UUIDv7Generator {

    fun generate(): String = UuidCreator.getTimeOrdered().toString()

    fun generateCompact(): String = UuidCreator.getTimeOrdered().toString().replace("-", "")

    fun generateWithPrefix(prefix: String): String = "${prefix}_${generate()}"

    fun generateOrderId(): String = generateWithPrefix("ORD")

    fun generateTradeId(): String = generateWithPrefix("TRD")

    fun generateEventId(): String = generateWithPrefix("EVT")
}

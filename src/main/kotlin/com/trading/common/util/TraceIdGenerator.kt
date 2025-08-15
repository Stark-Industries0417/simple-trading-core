package com.trading.common.util
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadLocalRandom
@Component
class TraceIdGenerator {

    fun generate(): String {
        val high = ThreadLocalRandom.current().nextLong()
        val low = ThreadLocalRandom.current().nextLong()
        return String.format("%016x%016x", high, low)
    }

    fun generateBytes(): ByteArray {
        val traceId = ByteArray(16)
        ThreadLocalRandom.current().nextBytes(traceId)
        return traceId
    }

    fun generateSpanId(): String {
        val spanId = ThreadLocalRandom.current().nextLong()
        return String.format("%016x", spanId)
    }

    fun getCurrentOrGenerate(): String {
        return MDC.get("traceId") ?: generate().also { newTraceId ->
            MDC.put("traceId", newTraceId)
        }
    }

    fun setTraceId(traceId: String) {
        MDC.put("traceId", traceId)
    }

    fun clearTraceId() {
        MDC.remove("traceId")
    }
}

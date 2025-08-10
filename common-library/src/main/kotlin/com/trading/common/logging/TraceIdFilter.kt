package com.trading.common.logging
import com.trading.common.util.TraceIdGenerator
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
@Component
class TraceIdFilter(
    private val traceIdGenerator: TraceIdGenerator
) : Filter {
    companion object {
        const val TRACE_ID_HEADER = "X-Trace-Id"
        const val TRACE_ID_MDC_KEY = "traceId"
    }
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse
        try {
            val traceId = extractOrGenerateTraceId(httpRequest)
            traceIdGenerator.setTraceId(traceId)
            httpResponse.setHeader(TRACE_ID_HEADER, traceId)
            chain.doFilter(request, response)
        } finally {
            traceIdGenerator.clearTraceId()
        }
    }
    private fun extractOrGenerateTraceId(request: HttpServletRequest): String {
        val headerTraceId = request.getHeader(TRACE_ID_HEADER)
        return if (!headerTraceId.isNullOrBlank()) {
            headerTraceId
        } else {
            traceIdGenerator.generate()
        }
    }
}

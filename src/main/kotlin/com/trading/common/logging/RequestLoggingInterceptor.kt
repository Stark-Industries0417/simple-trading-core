package com.trading.common.logging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.util.*
@Component
class RequestLoggingInterceptor : HandlerInterceptor {
    private val logger = LoggerFactory.getLogger(RequestLoggingInterceptor::class.java)
    companion object {
        private const val REQUEST_START_TIME = "requestStartTime"
    }
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val startTime = System.currentTimeMillis()
        request.setAttribute(REQUEST_START_TIME, startTime)
        logger.info(
            "HTTP Request started: method={}, uri={}, userAgent={}, remoteAddr={}",
            request.method,
            request.requestURI,
            request.getHeader("User-Agent") ?: "unknown",
            getClientIpAddress(request)
        )
        return true
    }
    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        val startTime = request.getAttribute(REQUEST_START_TIME) as? Long ?: System.currentTimeMillis()
        val duration = System.currentTimeMillis() - startTime
        if (ex != null) {
            logger.error(
                "HTTP Request failed: method={}, uri={}, status={}, duration={}ms, error={}",
                request.method,
                request.requestURI,
                response.status,
                duration,
                ex.message,
                ex
            )
        } else {
            logger.info(
                "HTTP Request completed: method={}, uri={}, status={}, duration={}ms",
                request.method,
                request.requestURI,
                response.status,
                duration
            )
        }
    }

    private fun getClientIpAddress(request: HttpServletRequest): String {
        val headers = listOf(
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
        )
        for (header in headers) {
            val value = request.getHeader(header)
            if (!value.isNullOrBlank() && !"unknown".equals(value, ignoreCase = true)) {
                return value.split(",")[0].trim()
            }
        }
        return request.remoteAddr ?: "unknown"
    }
}

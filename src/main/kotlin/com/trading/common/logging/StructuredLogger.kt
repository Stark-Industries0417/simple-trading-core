package com.trading.common.logging
import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.event.DomainEvent
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
@Component
class StructuredLogger(
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(StructuredLogger::class.java)

    fun logEvent(event: DomainEvent) {
        val eventData = mapOf(
            "eventType" to event.javaClass.simpleName,
            "eventId" to event.eventId,
            "aggregateId" to event.aggregateId,
            "occurredAt" to event.occurredAt.toString(),
            "traceId" to event.traceId
        )
        logger.info(
            "Domain event: {}",
            objectMapper.writeValueAsString(eventData)
        )
    }

    fun logError(exception: Exception, context: Map<String, Any> = emptyMap()) {
        val errorData = mutableMapOf<String, Any>(
            "errorType" to exception.javaClass.simpleName,
            "errorMessage" to (exception.message ?: "Unknown error"),
            "timestamp" to java.time.Instant.now().toString()
        )
        errorData.putAll(context)
        MDC.get("traceId")?.let { traceId ->
            errorData["traceId"] = traceId
        }
        logger.error(
            "Application error: {}",
            objectMapper.writeValueAsString(errorData),
            exception
        )
    }

    fun logBusinessMetric(metricName: String, value: Any, tags: Map<String, String> = emptyMap()) {
        val metricData = mutableMapOf<String, Any>(
            "metricName" to metricName,
            "value" to value,
            "timestamp" to java.time.Instant.now().toString()
        )
        if (tags.isNotEmpty()) {
            metricData["tags"] = tags
        }
        MDC.get("traceId")?.let { traceId ->
            metricData["traceId"] = traceId
        }
        logger.info(
            "Business metric: {}",
            objectMapper.writeValueAsString(metricData)
        )
    }

    fun logUserAction(
        userId: String,
        action: String,
        resource: String? = null,
        details: Map<String, Any> = emptyMap()
    ) {
        val actionData = mutableMapOf<String, Any>(
            "userId" to userId,
            "action" to action,
            "timestamp" to java.time.Instant.now().toString()
        )
        resource?.let { actionData["resource"] = it }
        actionData.putAll(details)
        MDC.get("traceId")?.let { traceId ->
            actionData["traceId"] = traceId
        }
        logger.info(
            "User action: {}",
            objectMapper.writeValueAsString(actionData)
        )
    }

    fun logPerformance(
        operation: String,
        durationMs: Long,
        success: Boolean,
        details: Map<String, Any> = emptyMap()
    ) {
        val perfData = mutableMapOf<String, Any>(
            "operation" to operation,
            "durationMs" to durationMs,
            "success" to success,
            "timestamp" to java.time.Instant.now().toString()
        )
        perfData.putAll(details)
        MDC.get("traceId")?.let { traceId ->
            perfData["traceId"] = traceId
        }
        logger.info(
            "Performance metric: {}",
            objectMapper.writeValueAsString(perfData)
        )
    }

    // Order 관련 전용 로깅 메서드들 (Phase 3)
    fun info(message: String, context: Map<String, Any> = emptyMap()) {
        val logData = mutableMapOf<String, Any>(
            "message" to message,
            "timestamp" to java.time.Instant.now().toString()
        )
        logData.putAll(context)
        MDC.get("traceId")?.let { traceId ->
            logData["traceId"] = traceId
        }
        logger.info(
            "Application info: {}",
            objectMapper.writeValueAsString(logData)
        )
    }

    fun warn(message: String, context: Map<String, Any> = emptyMap()) {
        val logData = mutableMapOf<String, Any>(
            "message" to message,
            "timestamp" to java.time.Instant.now().toString()
        )
        logData.putAll(context)
        MDC.get("traceId")?.let { traceId ->
            logData["traceId"] = traceId
        }
        logger.warn(
            "Application warning: {}",
            objectMapper.writeValueAsString(logData)
        )
    }

    fun error(message: String, context: Map<String, Any> = emptyMap(), exception: Exception? = null) {
        val logData = mutableMapOf<String, Any>(
            "message" to message,
            "timestamp" to java.time.Instant.now().toString()
        )
        logData.putAll(context)
        exception?.let {
            logData["errorType"] = it.javaClass.simpleName
            logData["errorMessage"] = it.message ?: "Unknown error"
        }
        MDC.get("traceId")?.let { traceId ->
            logData["traceId"] = traceId
        }
        if (exception != null) {
            logger.error(
                "Application error: {}",
                objectMapper.writeValueAsString(logData),
                exception
            )
        } else {
            logger.error(
                "Application error: {}",
                objectMapper.writeValueAsString(logData)
            )
        }
    }
}

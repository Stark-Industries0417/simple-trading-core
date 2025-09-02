package com.trading.common.event.base

import com.trading.common.util.TraceIdGenerator
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher

class SpringEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val traceIdGenerator: TraceIdGenerator
) : EventPublisher {
    private val logger = LoggerFactory.getLogger(SpringEventPublisher::class.java)
    override fun publish(event: DomainEvent) {
        try {
            traceIdGenerator.setTraceId(event.traceId)
            logger.info(
                "Publishing event: eventType={}, eventId={}, aggregateId={}, traceId={}",
                event.javaClass.simpleName,
                event.eventId,
                event.aggregateId,
                event.traceId
            )
            applicationEventPublisher.publishEvent(event)
        } catch (exception: Exception) {
            logger.error(
                "Failed to publish event: eventType={}, eventId={}, error={}",
                event.javaClass.simpleName,
                event.eventId,
                exception.message,
                exception
            )
            throw exception
        }
    }
}

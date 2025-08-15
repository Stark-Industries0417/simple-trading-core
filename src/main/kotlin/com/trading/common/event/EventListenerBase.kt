package com.trading.common.event
import com.trading.common.util.TraceIdGenerator
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
abstract class EventListenerBase {
    protected val logger = LoggerFactory.getLogger(this.javaClass)
    @Autowired
    protected lateinit var traceIdGenerator: TraceIdGenerator

    protected fun beforeEventHandling(event: DomainEvent, eventHandlerName: String) {
        traceIdGenerator.setTraceId(event.traceId)
        logger.info(
            "Handling event: handler={}, eventType={}, eventId={}, aggregateId={}, traceId={}",
            eventHandlerName,
            event.javaClass.simpleName,
            event.eventId,
            event.aggregateId,
            event.traceId
        )
    }

    protected fun afterEventHandling(event: DomainEvent, eventHandlerName: String) {
        logger.info(
            "Successfully handled event: handler={}, eventType={}, eventId={}",
            eventHandlerName,
            event.javaClass.simpleName,
            event.eventId
        )
    }

    protected fun onEventHandlingFailure(
        event: DomainEvent,
        eventHandlerName: String,
        exception: Exception
    ) {
        logger.error(
            "Failed to handle event: handler={}, eventType={}, eventId={}, error={}",
            eventHandlerName,
            event.javaClass.simpleName,
            event.eventId,
            exception.message,
            exception
        )
    }
}
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
annotation class AfterCommitEventListener
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
annotation class AfterRollbackEventListener

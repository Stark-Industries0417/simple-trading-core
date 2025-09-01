package com.trading.common.monitoring
import com.trading.common.event.base.DomainEvent
import com.trading.common.event.EventListenerBase
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.time.Duration
@Component
class EventMetrics(private val meterRegistry: MeterRegistry) : EventListenerBase() {

    @EventListener
    @Order(0)
    fun handleDomainEvent(event: DomainEvent) {
        val startTime = System.currentTimeMillis()
        val eventType = event.javaClass.simpleName
        try {
            beforeEventHandling(event, "EventMetrics")
            recordEventProcessed(eventType, true)
        } catch (exception: Exception) {
            recordEventProcessed(eventType, false)
            recordEventFailure(eventType, exception)
            onEventHandlingFailure(event, "EventMetrics", exception)
            throw exception
        } finally {
            val processingTime = System.currentTimeMillis() - startTime
            recordEventProcessingTime(eventType, processingTime)
        }
    }
    private fun recordEventProcessed(eventType: String, success: Boolean) {
        Counter.builder("trading.events.processed.total")
            .description("Total number of domain events processed")
            .tags("eventType", eventType, "success", success.toString())
            .register(meterRegistry)
            .increment()
    }
    private fun recordEventProcessingTime(eventType: String, processingTimeMs: Long) {
        Timer.builder("trading.events.processing.duration")
            .description("Time taken to process domain events")
            .tags("eventType", eventType)
            .register(meterRegistry)
            .record(Duration.ofMillis(processingTimeMs))
    }
    private fun recordEventFailure(eventType: String, exception: Exception) {
        Counter.builder("trading.events.failures.total")
            .description("Total number of event processing failures")
            .tags("eventType", eventType, "exceptionType", exception.javaClass.simpleName)
            .register(meterRegistry)
            .increment()
    }
}

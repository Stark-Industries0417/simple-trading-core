package com.trading.order.application

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit




@Component
class OrderMetricsImpl(private val meterRegistry: MeterRegistry) : OrderMetrics {
    
    private val orderCreationTimer = Timer.builder("order.creation.duration")
        .description("Time taken to create an order")
        .tag("operation", "create")
        .register(meterRegistry)
    
    private val validationFailureCounter = Counter.builder("order.validation.failures")
        .description("Number of order validation failures")
        .tag("type", "business_rules")
        .register(meterRegistry)
    
    private val databaseErrorCounter = Counter.builder("order.database.errors")
        .description("Number of database errors during order operations")
        .tag("layer", "persistence")
        .register(meterRegistry)
    
    private val unexpectedErrorCounter = Counter.builder("order.unexpected.errors")
        .description("Number of unexpected errors in order processing")
        .tag("severity", "high")
        .register(meterRegistry)
    
    private val eventPublicationCounter = Counter.builder("order.event.publications")
        .description("Number of successful event publications")
        .tag("type", "success")
        .register(meterRegistry)
    
    private val eventFailureCounter = Counter.builder("order.event.failures")
        .description("Number of failed event publications")
        .tag("type", "failure")
        .register(meterRegistry)
    
    private val orderCreatedCounter = Counter.builder("order.created.total")
        .description("Total number of orders created")
        .register(meterRegistry)
    
    private val orderCancelledCounter = Counter.builder("order.cancelled.total")
        .description("Total number of orders cancelled")
        .register(meterRegistry)
    
    override fun recordOrderCreation(durationMs: Long) {
        orderCreationTimer.record(durationMs, TimeUnit.MILLISECONDS)
        orderCreatedCounter.increment()
    }
    
    override fun incrementValidationFailures() {
        validationFailureCounter.increment()
    }
    
    override fun incrementDatabaseErrors() {
        databaseErrorCounter.increment()
    }
    
    override fun incrementUnexpectedErrors() {
        unexpectedErrorCounter.increment()
    }
    
    override fun incrementEventPublications() {
        eventPublicationCounter.increment()
    }
    
    override fun incrementEventPublicationFailures() {
        eventFailureCounter.increment()
    }
    
    fun recordOrderCancellation() {
        orderCancelledCounter.increment()
    }
    
    fun getMetricsSummary(): Map<String, Number> {
        return mapOf(
            "ordersCreatedTotal" to orderCreatedCounter.count(),
            "ordersCancelledTotal" to orderCancelledCounter.count(),
            "validationFailuresTotal" to validationFailureCounter.count(),
            "databaseErrorsTotal" to databaseErrorCounter.count(),
            "unexpectedErrorsTotal" to unexpectedErrorCounter.count(),
            "eventPublicationsTotal" to eventPublicationCounter.count(),
            "eventFailuresTotal" to eventFailureCounter.count(),
            "averageCreationTimeMs" to orderCreationTimer.mean(TimeUnit.MILLISECONDS)
        )
    }
}
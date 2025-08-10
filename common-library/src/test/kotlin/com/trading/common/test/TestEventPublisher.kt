package com.trading.common.test
import com.trading.common.event.DomainEvent
import com.trading.common.event.EventPublisher
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentLinkedQueue
@Component
@Profile("test")
class TestEventPublisher : EventPublisher {
    private val publishedEvents = ConcurrentLinkedQueue<DomainEvent>()
    override fun publish(event: DomainEvent) {
        publishedEvents.add(event)
    }

    fun getPublishedEvents(): List<DomainEvent> {
        return publishedEvents.toList()
    }

    fun <T : DomainEvent> getPublishedEventsOfType(clazz: Class<T>): List<T> {
        return publishedEvents.filterIsInstance(clazz)
    }

    fun getLastPublishedEvent(): DomainEvent? {
        return publishedEvents.lastOrNull()
    }

    fun <T : DomainEvent> getLastPublishedEventOfType(clazz: Class<T>): T? {
        return publishedEvents.filterIsInstance(clazz).lastOrNull()
    }

    fun getPublishedEventCount(): Int {
        return publishedEvents.size
    }

    fun <T : DomainEvent> getPublishedEventCountOfType(clazz: Class<T>): Int {
        return publishedEvents.count { clazz.isInstance(it) }
    }

    fun clearPublishedEvents() {
        publishedEvents.clear()
    }

    fun wasEventPublished(predicate: (DomainEvent) -> Boolean): Boolean {
        return publishedEvents.any(predicate)
    }
}

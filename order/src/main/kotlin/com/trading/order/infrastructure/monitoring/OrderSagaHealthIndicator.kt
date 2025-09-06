package com.trading.order.infrastructure.monitoring

import com.trading.common.outbox.OutboxStatus
import com.trading.order.infrastructure.outbox.OrderOutboxRepository
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class OrderSagaHealthIndicator(
    private val outboxRepository: OrderOutboxRepository
) : HealthIndicator {
    
    override fun health(): Health {
        return try {
            val pendingEvents = outboxRepository.findByStatus(OutboxStatus.PENDING)
            val staleEvents = outboxRepository.findStaleEvents(
                OutboxStatus.PENDING,
                Instant.now().minusSeconds(300)
            )
            
            val details = mapOf(
                "pendingEvents" to pendingEvents.size,
                "staleEvents" to staleEvents.size,
                "module" to "order"
            )
            
            when {
                staleEvents.size > 10 -> Health.down()
                    .withDetails(details)
                    .withDetail("message", "Too many stale events")
                    .build()
                staleEvents.size > 5 -> Health.status("WARNING")
                    .withDetails(details)
                    .withDetail("message", "Some stale events detected")
                    .build()
                else -> Health.up()
                    .withDetails(details)
                    .build()
            }
        } catch (e: Exception) {
            Health.down()
                .withException(e)
                .withDetail("module", "order")
                .build()
        }
    }
}
package com.trading.matching.infrastructure.scheduler

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.matching.infrastructure.outbox.MatchingOutboxRepository
import com.trading.matching.infrastructure.outbox.MatchingStatus
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class MatchingEventStatusUpdater(
    private val outboxRepository: MatchingOutboxRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(this::class.java)


    @KafkaListener(
        topics = ["#{@kafkaProperties.topics.tradeEvents}"],
        groupId = "#{@kafkaProperties.updateStatus.groupId}"
    )
    fun updateOutboxEventStatus(message: String) {
        try {
            val jsonNode = objectMapper.readTree(message)
            val eventId = jsonNode.get("eventId")?.asText()

            if (eventId == null) {
                logger.warn("Received event without eventId: $message")
                return
            }

            outboxRepository.findById(eventId).ifPresent { outboxEvent ->
                when (outboxEvent.status) {
                    MatchingStatus.PENDING -> {
                        outboxEvent.markAsProcessed()
                        logger.info("Marked outbox event as PROCESSED: $eventId")
                    }
                    MatchingStatus.RETRY -> {
                        outboxEvent.markAsProcessed()
                        logger.info("Retry successful, marked as PROCESSED: $eventId")
                    }
                    MatchingStatus.PROCESSED -> {
                        logger.debug("Event already processed: $eventId")
                    }
                    else -> logger.info("Failed Event eventId: $eventId")
                }
                outboxRepository.save(outboxEvent)
            }

        } catch (e: Exception) {
            logger.error("Failed to update outbox event status", e)
        }
    }

    @KafkaListener(
        topics = ["#{@kafkaProperties.topics.dqlTopic}"],
        groupId = "#{@kafkaProperties.updateStatus.dlqGroupId}"
    )
    fun handleDeadLetterQueue(message: String) {
        try {
            val jsonNode = objectMapper.readTree(message)
            val eventId = jsonNode.get("eventId")?.asText()

            eventId?.let {
                outboxRepository.findById(it).ifPresent { outboxEvent ->
                    val errorMsg = "Event sent to DLQ after processing failure"
                    outboxEvent.markAsFailed(errorMsg)
                    outboxRepository.save(outboxEvent)
                    logger.error("Marked outbox event as FAILED (DLQ): $eventId")
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to handle DLQ message", e)
        }
    }


    @Scheduled(fixedDelay = 60000)
    fun monitorPendingEvents() {
        val pendingEvents = outboxRepository.findByStatus(MatchingStatus.PENDING)
        val staleThreshold = Instant.now().minusSeconds(300) // 5분 이상 PENDING

        val staleEvents = pendingEvents.filter { event ->
            event.createdAt.isBefore(staleThreshold)
        }

        if (staleEvents.isNotEmpty()) {
            logger.warn("Found ${staleEvents.size} stale PENDING events (older than 5 minutes)")
            staleEvents.forEach { event ->
                logger.warn("Stale event: ${event.eventId}, created: ${event.createdAt}")
                event.markForRetry()
                outboxRepository.save(event)
            }
        }
    }
}
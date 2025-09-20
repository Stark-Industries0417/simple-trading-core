package com.trading.matching.infrastructure.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * CDC 이벤트 발행 확인 서비스
 *
 * CDC(Debezium)가 Outbox 테이블의 이벤트를 Kafka로 발행한 후,
 * 해당 이벤트를 구독하여 상태를 PROCESSED로 업데이트
 *
 * 아키텍처:
 * 1. MatchingSagaService -> Outbox Table (PENDING 상태로 INSERT)
 * 2. CDC(Debezium) -> Kafka Topic (trade.events)
 * 3. OutboxEventStatusUpdater -> Outbox Table (PROCESSED로 UPDATE)
 */
@Service
@Transactional
class MatchingEventStatusUpdater(
    private val outboxRepository: MatchingOutboxRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * CDC가 발행한 trade.events 토픽을 구독하여 상태 업데이트
     *
     * CDC 발행 이벤트 포맷:
     * - eventId: Outbox 이벤트 ID
     * - eventType: TradeCreated, TradeFailed 등
     * - sagaId: Saga 트랜잭션 ID
     */
    @KafkaListener(
        topics = ["#{@kafkaProperties.topics.tradeEvents}"],
        groupId = "#{@kafkaProperties.consumer.groupId}-status-updater"
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
                    OutboxStatus.PENDING -> {
                        outboxEvent.markAsProcessed()
                        logger.info("Marked outbox event as PROCESSED: $eventId")
                    }
                    OutboxStatus.RETRY -> {
                        outboxEvent.markAsProcessed()
                        logger.info("Retry successful, marked as PROCESSED: $eventId")
                    }
                    OutboxStatus.PROCESSED -> {
                        logger.debug("Event already processed: $eventId")
                    }
                    OutboxStatus.FAILED -> {
                        logger.warn("Processing previously failed event: $eventId")
                        outboxEvent.markAsProcessed()
                    }
                }
                outboxRepository.save(outboxEvent)
            }

        } catch (e: Exception) {
            logger.error("Failed to update outbox event status", e)
        }
    }

    /**
     * Dead Letter Queue 처리
     * CDC 발행은 성공했지만 downstream 처리가 실패한 경우
     */
    @KafkaListener(
        topics = ["#{@kafkaProperties.topics.tradeEvents}.dlq"],
        groupId = "#{@kafkaProperties.consumer.groupId}-dlq-handler"
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

    /**
     * 주기적으로 오래된 PENDING 이벤트 확인 (모니터링용)
     * CDC가 놓친 이벤트나 장애 상황 감지
     */
    @Scheduled(fixedDelay = 60000)
    fun monitorPendingEvents() {
        val pendingEvents = outboxRepository.findByStatus(OutboxStatus.PENDING)
        val staleThreshold = java.time.Instant.now().minusSeconds(300) // 5분 이상 PENDING

        val staleEvents = pendingEvents.filter { event ->
            event.createdAt.isBefore(staleThreshold)
        }

        if (staleEvents.isNotEmpty()) {
            logger.warn("Found ${staleEvents.size} stale PENDING events (older than 5 minutes)")
            staleEvents.forEach { event ->
                logger.warn("Stale event: ${event.eventId}, created: ${event.createdAt}")
                // 자동 재시도 또는 알림 전송 로직 추가 가능
                event.markForRetry()
                outboxRepository.save(event)
            }
        }
    }
}
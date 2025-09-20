package com.trading.matching.infrastructure.saga

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.dto.cdc.order.OrderCreatedDto
import com.trading.common.dto.cdc.order.OrderCancelledDto
import com.trading.common.dto.order.OrderType
import com.trading.common.dto.order.OrderSide
import com.trading.common.outbox.EventTypes
import com.trading.matching.infrastructure.engine.MatchingEngineManager
import com.trading.matching.infrastructure.outbox.MatchingOutboxEvent
import com.trading.matching.infrastructure.outbox.MatchingOutboxRepository
import io.mockk.*
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.Acknowledgment
import java.math.BigDecimal


class MatchingSagaServiceIdempotencyTest {

    private val matchingEngineManager = mockk<MatchingEngineManager>()
    private val matchingOutboxRepository = mockk<MatchingOutboxRepository>()
    private val objectMapper = ObjectMapper()
    private val acknowledgment = mockk<Acknowledgment>()

    private lateinit var matchingSagaService: MatchingSagaService

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        matchingSagaService = MatchingSagaService(
            matchingEngineManager = matchingEngineManager,
            matchingOutboxRepository = matchingOutboxRepository,
            objectMapper = objectMapper
        )

        // 기본 Mock 설정
        every { acknowledgment.acknowledge() } just runs
        every { matchingOutboxRepository.save(any()) } returns mockk()
    }

    @Test
    fun `새로운 이벤트는 정상 처리되고 커밋된다`() {
        // Given
        val sagaId = "saga-001"
        val eventPayload = createOrderCreatedEvent(sagaId, "order-001")
        val record = ConsumerRecord("order.events", 0, 100L, "key", eventPayload)

        // sagaId로 조회 시 빈 리스트 반환 (처리되지 않은 이벤트)
        every { matchingOutboxRepository.findBySagaId(sagaId) } returns emptyList()
        every { matchingEngineManager.processOrderWithResult(any()) } returns emptyList()

        // When
        matchingSagaService.handleOrderEvent(record, acknowledgment)

        // Then
        verify(exactly = 1) {
            matchingOutboxRepository.findBySagaId(sagaId) // 멱등성 체크
            matchingEngineManager.processOrderWithResult(any()) // 비즈니스 로직 실행
            matchingOutboxRepository.save(any()) // 결과 저장
            acknowledgment.acknowledge() // 커밋
        }
    }

    @Test
    fun `이미 처리된 이벤트는 스킵되고 바로 커밋된다`() {
        // Given
        val sagaId = "saga-002"
        val eventPayload = createOrderCreatedEvent(sagaId, "order-002")
        val record = ConsumerRecord("order.events", 0, 200L, "key", eventPayload)

        // sagaId로 조회 시 기존 이벤트 반환 (이미 처리됨)
        val existingEvent = mockk<MatchingOutboxEvent>()
        every { matchingOutboxRepository.findBySagaId(sagaId) } returns listOf(existingEvent)

        // When
        matchingSagaService.handleOrderEvent(record, acknowledgment)

        // Then
        verify(exactly = 1) {
            matchingOutboxRepository.findBySagaId(sagaId) // 멱등성 체크
            acknowledgment.acknowledge() // 중복 메시지도 커밋
        }

        // 비즈니스 로직은 실행되지 않음
        verify(exactly = 0) {
            matchingEngineManager.processOrderWithResult(any())
            matchingOutboxRepository.save(any())
        }
    }

    @Test
    fun `처리 실패 시 커밋하지 않아 재시도 가능하다`() {
        // Given
        val sagaId = "saga-003"
        val eventPayload = createOrderCreatedEvent(sagaId, "order-003")
        val record = ConsumerRecord("order.events", 0, 300L, "key", eventPayload)

        // 멱등성 체크 통과
        every { matchingOutboxRepository.findBySagaId(sagaId) } returns emptyList()

        // 처리 실패
        every { matchingEngineManager.processOrderWithResult(any()) } throws RuntimeException("Processing failed")

        // 실패 이벤트 저장도 실패
        every { matchingOutboxRepository.save(any()) } throws RuntimeException("Save failed")

        // When
        matchingSagaService.handleOrderEvent(record, acknowledgment)

        // Then
        verify(exactly = 1) {
            matchingOutboxRepository.findBySagaId(sagaId) // 멱등성 체크
            matchingEngineManager.processOrderWithResult(any()) // 처리 시도
        }

        // 커밋하지 않음 (재시도 가능)
        verify(exactly = 0) {
            acknowledgment.acknowledge()
        }
    }

    @Test
    fun `잘못된 형식의 이벤트는 커밋되어 DLQ로 보낸다`() {
        // Given - sagaId가 없는 이벤트
        val invalidPayload = """
            {
                "eventType": "${EventTypes.Order.CREATED}",
                "orderId": "order-004"
            }
        """.trimIndent()
        val record = ConsumerRecord("order.events", 0, 400L, "key", invalidPayload)

        // When
        matchingSagaService.handleOrderEvent(record, acknowledgment)

        // Then
        // 멱등성 체크나 비즈니스 로직 실행 없음
        verify(exactly = 0) {
            matchingOutboxRepository.findBySagaId(any())
            matchingEngineManager.processOrderWithResult(any())
        }

        // 커밋은 수행됨 (DLQ 처리)
        verify(exactly = 1) {
            acknowledgment.acknowledge()
        }
    }

    @Test
    fun `매칭 결과가 없어도 처리 기록을 남긴다`() {
        // Given
        val sagaId = "saga-005"
        val eventPayload = createOrderCreatedEvent(sagaId, "order-005")
        val record = ConsumerRecord("order.events", 0, 500L, "key", eventPayload)

        // 멱등성 체크 통과
        every { matchingOutboxRepository.findBySagaId(sagaId) } returns emptyList()

        // 매칭 결과 없음
        every { matchingEngineManager.processOrderWithResult(any()) } returns emptyList()

        // When
        matchingSagaService.handleOrderEvent(record, acknowledgment)

        // Then
        verify(exactly = 1) {
            matchingOutboxRepository.findBySagaId(sagaId)
            matchingEngineManager.processOrderWithResult(any())
            matchingOutboxRepository.save(any()) // 매칭 없어도 기록 저장
            acknowledgment.acknowledge()
        }
    }

    @Test
    fun `주문 취소 이벤트도 멱등성이 보장된다`() {
        // Given
        val sagaId = "saga-006"
        val eventPayload = createOrderCancelledEvent(sagaId, "order-006")
        val record = ConsumerRecord("order.events", 0, 600L, "key", eventPayload)

        // 멱등성 체크 통과
        every { matchingOutboxRepository.findBySagaId(sagaId) } returns emptyList()
        every { matchingEngineManager.removeOrderFromBook(any(), any()) } returns true

        // When
        matchingSagaService.handleOrderEvent(record, acknowledgment)

        // Then
        verify(exactly = 1) {
            matchingOutboxRepository.findBySagaId(sagaId) // 멱등성 체크
            matchingEngineManager.removeOrderFromBook(any(), any())
            matchingOutboxRepository.save(any())
            acknowledgment.acknowledge()
        }
    }

    private fun createOrderCreatedEvent(sagaId: String, orderId: String): String {
        return """
            {
                "eventType": "${EventTypes.Order.CREATED}",
                "sagaId": "$sagaId",
                "orderId": "$orderId",
                "userId": "user-001",
                "symbol": "AAPL",
                "orderType": "LIMIT",
                "orderSide": "BUY",
                "quantity": 100,
                "price": 150.00
            }
        """.trimIndent()
    }

    private fun createOrderCancelledEvent(sagaId: String, orderId: String): String {
        return """
            {
                "eventType": "${EventTypes.Order.CANCELLED}",
                "sagaId": "$sagaId",
                "orderId": "$orderId",
                "userId": "user-001",
                "symbol": "AAPL",
                "quantity": "100",
                "price": 150.00
            }
        """.trimIndent()
    }
}
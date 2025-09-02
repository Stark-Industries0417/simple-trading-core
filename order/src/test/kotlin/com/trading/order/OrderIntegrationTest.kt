package com.trading.order

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.dto.order.OrderSide
import com.trading.common.dto.order.OrderStatus
import com.trading.common.dto.order.OrderType
import com.trading.common.event.order.OrderCancelledEvent
import com.trading.common.event.order.OrderCreatedEvent
import com.trading.order.domain.OrderRepository
import com.trading.order.infrastructure.web.dto.CreateOrderRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.event.EventListener
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit




@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(OrderIntegrationTestConfig::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var eventCollector: TestEventCollector

    companion object {
        @Container
        @JvmStatic
        val mysqlContainer = MySQLContainer<Nothing>("mysql:8.0").apply {
            withDatabaseName("trading_test")
            withUsername("test")
            withPassword("test")
            withReuse(true) // 컨테이너 재사용
            withInitScript("schema.sql")
        }

        init {
            mysqlContainer.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl)
            registry.add("spring.datasource.username", mysqlContainer::getUsername)
            registry.add("spring.datasource.password", mysqlContainer::getPassword)
            registry.add("spring.datasource.driver-class-name") { "com.mysql.cj.jdbc.Driver" }
        }
    }

    @BeforeEach
    fun setUp() {
        eventCollector.clear()
    }

    @Nested
    @DisplayName("주문 생성 통합 테스트")
    inner class CreateOrderIntegrationTest {

        @Test
        fun `주문 생성 전체 플로우 - API부터 이벤트까지`() {
            // Given
            val request = CreateOrderRequest(
                symbol = "AAPL",
                orderType = OrderType.LIMIT,
                side = OrderSide.BUY,
                quantity = BigDecimal("10"),
                price = BigDecimal("150.00")
            )

            // When - REST API 호출
            val result = mockMvc.perform(
                post("/api/v1/orders")
                    .header("X-User-Id", "testuser")
                    .header("X-Trace-Id", "trace-123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )

            // Then - API 응답 검증
            result
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.orderType").value("LIMIT"))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.price").value(150.00))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.orderId").exists())

            // 응답에서 orderId 추출
            val response = result.andReturn().response.contentAsString
            val orderId = objectMapper.readTree(response).get("orderId").asText()

            // Then - 데이터베이스 저장 검증
            val savedOrder = orderRepository.findById(orderId).orElse(null)
            assertThat(savedOrder).isNotNull
            assertThat(savedOrder.symbol).isEqualTo("AAPL")
            assertThat(savedOrder.status).isEqualTo(OrderStatus.PENDING)

            // Then - 이벤트 발행 검증 (약간의 대기 필요)
            Thread.sleep(100)
            val events = eventCollector.getEvents(OrderCreatedEvent::class.java)
            assertThat(events).hasSize(1)
            assertThat(events[0].order.orderId).isEqualTo(orderId)
            assertThat(events[0].traceId).isEqualTo("trace-123")
        }

        @Test
        fun `잘못된 요청시 400 에러와 상세 메시지`() {
            // Given - 수량이 음수인 잘못된 요청
            val request = """
                {
                    "symbol": "AAPL",
                    "orderType": "LIMIT",
                    "side": "BUY",
                    "quantity": -10,
                    "price": 150.00
                }
            """.trimIndent()

            // When & Then
            mockMvc.perform(
                post("/api/v1/orders")
                    .header("X-User-Id", "testuser")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.details.fieldErrors.quantity").exists())
        }

        @Test
        fun `사용자 인증 헤더 없으면 400 에러`() {
            // Given
            val request = CreateOrderRequest(
                symbol = "AAPL",
                orderType = OrderType.MARKET,
                side = OrderSide.SELL,
                quantity = BigDecimal("5"),
                price = null
            )

            // When & Then - X-User-Id 헤더 없음
            mockMvc.perform(
                post("/api/v1/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("주문 조회 통합 테스트")
    inner class GetOrderIntegrationTest {

        @Test
        fun `사용자별 주문 목록 페이징 조회`() {
            // Given - 테스트 데이터 생성
            val userId = "testuser"
            repeat(15) { i ->
                createTestOrder(userId, "AAPL", BigDecimal(i + 1))
            }

            // When - 첫 페이지 조회
            val result = mockMvc.perform(
                get("/api/v1/orders")
                    .header("X-User-Id", userId)
                    .param("page", "0")
                    .param("size", "10")
                    .param("sort", "createdAt,desc")
            )

            // Then
            result
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content").isArray)
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.totalElements").value(15))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.number").value(0))
        }

        @Test
        fun `다른 사용자의 주문은 조회 불가`() {
            // Given
            val order = createTestOrder("user1", "AAPL", BigDecimal("10"))

            // When & Then - user2로 조회 시도
            mockMvc.perform(
                get("/api/v1/orders/${order.id}")
                    .header("X-User-Id", "user2")
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
        }
    }

    @Nested
    @DisplayName("주문 취소 통합 테스트")
    open inner class CancelOrderIntegrationTest {
        @Test
        @Transactional
        open fun `주문 취소 전체 플로우`() {
            // Given - 주문 생성
            val userId = "testuser"
            val order = createTestOrder(userId, "AAPL", BigDecimal("10"))
            val orderId = order.id

            // When - 주문 취소
            val result = mockMvc.perform(
                post("/api/v1/orders/$orderId/cancel")
                    .header("X-User-Id", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"reason": "Changed my mind"}""")
            )

            // Then - API 응답 검증
            result
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Changed my mind"))

            // Then - DB 상태 검증
            val cancelledOrder = orderRepository.findById(orderId).orElse(null)
            assertThat(cancelledOrder.status).isEqualTo(OrderStatus.CANCELLED)
            assertThat(cancelledOrder.cancellationReason).isEqualTo("Changed my mind")

            // Then - 취소 이벤트 발행 검증
            Thread.sleep(100)
            val events = eventCollector.getEvents(OrderCancelledEvent::class.java)
            assertThat(events).anyMatch { it.orderId == orderId }
        }

        @Test
        fun `이미 체결된 주문은 취소 불가`() {
            // Given - 체결된 주문
            val userId = "testuser"
            val order = createTestOrder(userId, "AAPL", BigDecimal("10")).apply {
                completeFill() // 체결 처리
            }
            orderRepository.save(order)

            // When & Then
            mockMvc.perform(
                post("/api/v1/orders/${order.id}/cancel")
                    .header("X-User-Id", userId)
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("INVALID_STATE"))
        }
    }

    @Nested
    @DisplayName("동시성 시나리오 테스트")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class ConcurrencyTest {

        @Test
        @Order(1)
        fun `동일 사용자가 동시에 여러 주문 생성`() {
            // Given
            val userId = "concurrent-user"
            val threadCount = 10
            val latch = CountDownLatch(threadCount)
            val errors = CopyOnWriteArrayList<Exception>()

            // When - 10개 스레드에서 동시 주문
            repeat(threadCount) { i ->
                Thread {
                    try {
                        val request = CreateOrderRequest(
                            symbol = "AAPL",
                            orderType = OrderType.LIMIT,
                            side = OrderSide.BUY,
                            quantity = BigDecimal(i + 1),
                            price = BigDecimal("150.00")
                        )

                        mockMvc.perform(
                            post("/api/v1/orders")
                                .header("X-User-Id", userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                        ).andExpect(status().isCreated)

                    } catch (e: Exception) {
                        errors.add(e)
                    } finally {
                        latch.countDown()
                    }
                }.start()
            }

            // Then - 모든 스레드 완료 대기
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(errors).isEmpty()

            // 모든 주문이 저장되었는지 확인
            val orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId,
                org.springframework.data.domain.PageRequest.of(0, 20))
            assertThat(orders.totalElements).isEqualTo(threadCount.toLong())
        }

        @Test
        @Order(2)
        fun `동일 주문을 동시에 취소 시도`() {
            // Given
            val userId = "concurrent-cancel-user"
            val order = createTestOrder(userId, "AAPL", BigDecimal("10"))
            val threadCount = 5
            val latch = CountDownLatch(threadCount)
            val successCount = java.util.concurrent.atomic.AtomicInteger(0)
            val conflictCount = java.util.concurrent.atomic.AtomicInteger(0)

            // When - 5개 스레드에서 동시 취소 시도
            repeat(threadCount) {
                Thread {
                    try {
                        val result = mockMvc.perform(
                            post("/api/v1/orders/${order.id}/cancel")
                                .header("X-User-Id", userId)
                        ).andReturn()

                        when (result.response.status) {
                            200 -> successCount.incrementAndGet()
                            409 -> conflictCount.incrementAndGet() // CONFLICT
                        }
                    } finally {
                        latch.countDown()
                    }
                }.start()
            }

            // Then
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(successCount.get()).isEqualTo(1) // 한 번만 성공
            assertThat(conflictCount.get()).isEqualTo(threadCount - 1) // 나머지는 실패
        }
    }

    // 테스트 헬퍼 메서드
    private fun createTestOrder(
        userId: String,
        symbol: String,
        quantity: BigDecimal
    ): com.trading.order.domain.Order {
        val order = com.trading.order.domain.Order.create(
            userId = userId,
            symbol = symbol,
            orderType = OrderType.LIMIT,
            side = OrderSide.BUY,
            quantity = quantity,
            price = BigDecimal("100.00"),
            traceId = "test-trace",
            uuidGenerator = com.trading.common.util.UUIDv7Generator()
        )
        return orderRepository.save(order)
    }
}

@TestConfiguration
class OrderIntegrationTestConfig {

    @Bean
    fun testEventCollector(): TestEventCollector {
        return TestEventCollector()
    }
}

class TestEventCollector {
    private val events = CopyOnWriteArrayList<Any>()

    @EventListener
    fun handleEvent(event: Any) {
        events.add(event)
    }

    fun clear() = events.clear()

    fun <T> getEvents(type: Class<T>): List<T> = events.filterIsInstance(type)
}
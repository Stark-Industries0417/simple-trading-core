package com.trading.order.domain

import com.trading.common.adapter.MarketDataProvider
import com.trading.common.dto.order.OrderSide
import com.trading.common.dto.order.OrderType
import com.trading.common.exception.order.OrderValidationException
import com.trading.common.logging.StructuredLogger
import com.trading.common.util.UUIDv7Generator
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal

@DisplayName("OrderValidator 테스트")
class OrderValidatorTest {

    private lateinit var validator: OrderValidator
    private lateinit var structuredLogger: StructuredLogger
    private lateinit var marketDataProvider: MarketDataProvider
    private lateinit var accountService: AccountService
    private lateinit var orderLimitService: OrderLimitService
    private lateinit var uuidGenerator: UUIDv7Generator

    @BeforeEach
    fun setUp() {
        structuredLogger = mockk(relaxed = true)
        marketDataProvider = mockk()
        accountService = mockk()
        orderLimitService = mockk()
        uuidGenerator = mockk()

        validator = OrderValidator(structuredLogger, marketDataProvider, accountService, orderLimitService)

        // UUID 생성기 기본 동작
        every { uuidGenerator.generateOrderId() } returns "ORDER-TEST-123"
        
        // 기본 모킹 설정 - 모든 검증이 통과하도록
        every { marketDataProvider.getCurrentPrice(any()) } returns BigDecimal("100.00")
        every { accountService.hasSufficientCash(any(), any()) } returns true
        every { accountService.hasSufficientStock(any(), any(), any()) } returns true
        every { accountService.getCurrentPrice(any()) } returns BigDecimal("100.00")
        every { orderLimitService.getDailyOrderCount(any()) } returns 0L
    }

    @Nested
    @DisplayName("도메인 규칙 검증")
    inner class DomainRulesValidation {

        @ParameterizedTest(name = "유효한 심볼: {0}")
        @ValueSource(strings = ["AAPL", "GOOGL", "TSLA", "MSFT", "AMZN"])
        fun `지원되는 심볼은 검증 통과`(symbol: String) {
            // Given
            val order = createTestOrder(symbol = symbol)

            // When & Then - 예외가 발생하지 않음
            validator.validateOrThrow(order)
        }

        @Test
        fun `지원되지 않는 심볼은 검증 실패`() {
            // Given
            val order = createTestOrder(symbol = "INVALID")

            // When & Then
            assertThatThrownBy { validator.validateOrThrow(order) }
                .isInstanceOf(OrderValidationException::class.java)
                .hasMessageContaining("Unsupported symbol")
                .matches { ex ->
                    val orderEx = ex as OrderValidationException
                    orderEx.context["symbol"] == "INVALID" && orderEx.context["supportedSymbols"] != null
                }
        }

        @ParameterizedTest(name = "수량: {0}, 예상결과: {1}")
        @CsvSource(
            "0.001, true",      // 최소값
            "0.01, true",       // 유효
            "100, true",        // 유효
            "9999, true",       // 유효
            "10000, true",      // 최대값
            "0.0009, false",    // 최소값 미만
            "10001, false"      // 최대값 초과
        )
        fun `수량 범위 검증`(quantity: String, shouldPass: Boolean) {
            // Given
            val order = createTestOrder(quantity = BigDecimal(quantity))

            // When & Then
            if (shouldPass) {
                validator.validateOrThrow(order)
            } else {
                assertThatThrownBy { validator.validateOrThrow(order) }
                    .isInstanceOf(OrderValidationException::class.java)
                    .hasMessageContaining("Quantity")
            }
        }

        @Test
        fun `시장가 주문에 가격이 있으면 검증 실패`() {
            // Given
            val order = createTestOrder(
                orderType = OrderType.MARKET,
                price = BigDecimal("150.00")
            )

            // When & Then
            assertThatThrownBy { validator.validateOrThrow(order) }
                .isInstanceOf(OrderValidationException::class.java)
                .hasMessage("Market order should not have a price")
        }
    }

    @Nested
    @DisplayName("시장 데이터 검증")
    inner class MarketDataValidation {

        @Test
        fun `지정가가 현재가 대비 10% 범위 내면 검증 통과`() {
            // Given
            val currentPrice = BigDecimal("100.00")
            val orderPrice = BigDecimal("105.00") // 5% 높음
            val order = createTestOrder(
                orderType = OrderType.LIMIT,
                price = orderPrice
            )

            every { marketDataProvider.getCurrentPrice("AAPL") } returns currentPrice

            // When & Then - 예외 없음
            validator.validateOrThrow(order)
        }

        @Test
        fun `지정가가 현재가 대비 10% 초과하면 검증 실패`() {
            // Given
            val currentPrice = BigDecimal("100.00")
            val orderPrice = BigDecimal("115.00") // 15% 높음
            val order = createTestOrder(
                orderType = OrderType.LIMIT,
                price = orderPrice
            )

            every { marketDataProvider.getCurrentPrice("AAPL") } returns currentPrice

            // When & Then
            assertThatThrownBy {
                validator.validateOrThrow(order)
            }
                .isInstanceOf(OrderValidationException::class.java)
                .hasMessageContaining("outside allowed range")
                .hasMessageContaining("[90.0000 - 110.0000]") // 범위 표시
        }

        @Test
        fun `시장 데이터 서비스 없으면 가격 검증 불가 예외`() {
            // Given
            val order = createTestOrder(
                orderType = OrderType.LIMIT,
                price = BigDecimal("150.00")
            )

            // When & Then
            every { marketDataProvider.getCurrentPrice("AAPL") } returns null

            // When & Then
            assertThatThrownBy {
                validator.validateOrThrow(order)
            }
                .isInstanceOf(OrderValidationException::class.java)
                .hasMessageContaining("market data unavailable")
        }
    }

    @Nested
    @DisplayName("계좌 잔고 검증")
    inner class AccountBalanceValidation {

        @Test
        fun `매수 주문시 충분한 현금이 있으면 검증 통과`() {
            // Given
            val order = createTestOrder(
                side = OrderSide.BUY,
                orderType = OrderType.LIMIT,
                quantity = BigDecimal("10"),
                price = BigDecimal("100.00")
            )

            every {
                accountService.hasSufficientCash("user123", match { it.compareTo(BigDecimal("1000.00")) == 0 })
            } returns true

            // When & Then - 예외 없음
            validator.validateOrThrow(order)
        }

        @Test
        fun `매수 주문시 현금 부족하면 검증 실패`() {
            // Given
            val order = createTestOrder(
                side = OrderSide.BUY,
                orderType = OrderType.LIMIT,
                quantity = BigDecimal("10"),
                price = BigDecimal("100.00")
            )

            every {
                accountService.hasSufficientCash("user123", match { it.compareTo(BigDecimal("1000.00")) == 0 })
            } returns false

            // When & Then
            assertThatThrownBy {
                validator.validateOrThrow(order)
            }
                .isInstanceOf(OrderValidationException::class.java)
                .hasMessageContaining("Insufficient cash balance")
        }

        @Test
        fun `매도 주문시 충분한 주식이 있으면 검증 통과`() {
            // Given
            val order = createTestOrder(
                side = OrderSide.SELL,
                quantity = BigDecimal("10")
            )

            every {
                accountService.hasSufficientStock("user123", "AAPL", BigDecimal("10"))
            } returns true

            // When & Then - 예외 없음
            validator.validateOrThrow(order)
        }

        @Test
        fun `시장가 매수시 버퍼 적용된 금액으로 검증`() {
            // Given
            val order = createTestOrder(
                side = OrderSide.BUY,
                orderType = OrderType.MARKET,
                quantity = BigDecimal("10"),
                price = null
            )

            every { accountService.getCurrentPrice("AAPL") } returns BigDecimal("100.00")
            every {
                accountService.hasSufficientCash("user123", match { it.compareTo(BigDecimal("1100.00")) == 0 }) // 10% 버퍼
            } returns true

            // When & Then - 예외 없음
            validator.validateOrThrow(order)

            verify {
                accountService.hasSufficientCash("user123", match { it.compareTo(BigDecimal("1100.00")) == 0 })
            }
        }
    }

    @Nested
    @DisplayName("사용자 한도 검증")
    inner class UserLimitValidation {

        @Test
        fun `일일 주문 한도 이내면 검증 통과`() {
            // Given
            val order = createTestOrder()
            every { orderLimitService.getDailyOrderCount("user123") } returns 50

            // When & Then - 예외 없음
            validator.validateOrThrow(order)
        }

        @Test
        fun `일일 주문 한도 초과시 검증 실패`() {
            // Given
            val order = createTestOrder()
            every { orderLimitService.getDailyOrderCount("user123") } returns 100

            // When & Then
            assertThatThrownBy {
                validator.validateOrThrow(order)
            }
                .isInstanceOf(OrderValidationException::class.java)
                .hasMessageContaining("Daily order limit")
                .hasMessageContaining("exceeded")
        }
    }

    @Nested
    @DisplayName("통합 시나리오")
    inner class IntegrationScenarios {

        @Test
        fun `모든 검증이 통과하는 정상 주문`() {
            // Given
            val order = createTestOrder(
                side = OrderSide.BUY,
                orderType = OrderType.LIMIT,
                quantity = BigDecimal("10"),
                price = BigDecimal("105.00")
            )

            every { marketDataProvider.getCurrentPrice("AAPL") } returns BigDecimal("100.00")
            every { accountService.hasSufficientCash("user123", match { it.compareTo(BigDecimal("1050.00")) == 0 }) } returns true
            every { orderLimitService.getDailyOrderCount("user123") } returns 10

            // When & Then - 모든 검증 통과
            validator.validateOrThrow(order)

            // 모든 서비스가 호출되었는지 확인
            verify { marketDataProvider.getCurrentPrice("AAPL") }
            verify { accountService.hasSufficientCash(any(), any()) }
            verify { orderLimitService.getDailyOrderCount("user123") }
        }

        @Test
        fun `검증 중 예외 발생시 OrderValidationException으로 래핑`() {
            // Given
            val order = createTestOrder()
            every {
                orderLimitService.getDailyOrderCount(any())
            } throws RuntimeException("Database connection failed")

            // When & Then
            assertThatThrownBy {
                validator.validateOrThrow(order)
            }
                .isInstanceOf(OrderValidationException::class.java)
                .hasMessageContaining("User limits validation failed")
                .hasCause(RuntimeException("Database connection failed"))
        }
    }

    // 테스트용 주문 생성 헬퍼
    private fun createTestOrder(
        userId: String = "user123",
        symbol: String = "AAPL",
        orderType: OrderType = OrderType.LIMIT,
        side: OrderSide = OrderSide.BUY,
        quantity: BigDecimal = BigDecimal("10"),
        price: BigDecimal? = BigDecimal("100.00")
    ): Order {
        return Order.create(
            userId = userId,
            symbol = symbol,
            orderType = orderType,
            side = side,
            quantity = quantity,
            price = price,
            traceId = "trace-test",
            uuidGenerator = uuidGenerator
        )
    }
}
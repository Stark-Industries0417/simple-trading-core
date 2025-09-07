package com.trading.order.domain.saga

import com.trading.common.domain.saga.SagaStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant
import java.util.UUID

class OrderSagaStateTest {

    @Test
    fun `OrderSagaState 생성 테스트`() {
        // given
        val sagaId = UUID.randomUUID().toString()
        val tradeId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()
        val userId = "USER001"
        val symbol = "AAPL"
        val orderType = "LIMIT"
        val timeoutAt = Instant.now().plusSeconds(30)

        // when
        val sagaState = OrderSagaState(
            sagaId = sagaId,
            tradeId = tradeId,
            orderId = orderId,
            userId = userId,
            symbol = symbol,
            orderType = orderType,
            state = SagaStatus.STARTED,
            timeoutAt = timeoutAt,
            metadata = null
        )

        // then
        assertEquals(sagaId, sagaState.sagaId)
        assertEquals(tradeId, sagaState.tradeId)
        assertEquals(orderId, sagaState.orderId)
        assertEquals(userId, sagaState.userId)
        assertEquals(symbol, sagaState.symbol)
        assertEquals(orderType, sagaState.orderType)
        assertEquals(SagaStatus.STARTED, sagaState.state)
        assertFalse(sagaState.isTerminalState())
    }

    @Test
    fun `Saga 완료 상태 변경 테스트`() {
        // given
        val sagaState = createTestSagaState()
        
        // when
        sagaState.markCompleted()
        
        // then
        assertEquals(SagaStatus.COMPLETED, sagaState.state)
        assertNotNull(sagaState.completedAt)
        assertTrue(sagaState.isTerminalState())
    }

    @Test
    fun `Saga 실패 상태 변경 테스트`() {
        // given
        val sagaState = createTestSagaState()
        val failureReason = "Insufficient balance"
        
        // when
        sagaState.markFailed(failureReason)
        
        // then
        assertEquals(SagaStatus.FAILED, sagaState.state)
        assertNotNull(sagaState.completedAt)
        assertEquals(failureReason, sagaState.metadata)
        assertTrue(sagaState.isTerminalState())
    }

    @Test
    fun `Saga 타임아웃 검사 테스트`() {
        // given
        val pastTimeout = Instant.now().minusSeconds(60)
        val sagaState = OrderSagaState(
            sagaId = UUID.randomUUID().toString(),
            tradeId = UUID.randomUUID().toString(),
            orderId = UUID.randomUUID().toString(),
            userId = "USER001",
            symbol = "AAPL",
            orderType = "LIMIT",
            state = SagaStatus.IN_PROGRESS,
            timeoutAt = pastTimeout,
            metadata = null
        )
        
        // when & then
        assertTrue(sagaState.isTimedOut())
        
        // when
        sagaState.markTimeout()
        
        // then
        assertEquals(SagaStatus.TIMEOUT, sagaState.state)
        assertNotNull(sagaState.completedAt)
        assertFalse(sagaState.isTimedOut()) // 타임아웃 처리 후에는 false
    }

    @Test
    fun `Order 관련 메서드 테스트`() {
        // given & when
        val marketOrder = createTestSagaState(orderType = "MARKET")
        val limitOrder = createTestSagaState(orderType = "LIMIT")
        val stopOrder = createTestSagaState(orderType = "STOP")
        val unknownOrder = createTestSagaState(orderType = "UNKNOWN")
        
        // then
        assertTrue(marketOrder.isOrderRelatedOperation())
        assertTrue(limitOrder.isOrderRelatedOperation())
        assertTrue(stopOrder.isOrderRelatedOperation())
        assertFalse(unknownOrder.isOrderRelatedOperation())
        
        assertFalse(marketOrder.requiresMatchingEngine())
        assertTrue(limitOrder.requiresMatchingEngine())
        assertFalse(stopOrder.requiresMatchingEngine())
    }

    @Test
    fun `SagaInfo 문자열 생성 테스트`() {
        // given
        val sagaState = createTestSagaState()
        
        // when
        val info = sagaState.toSagaInfo()
        
        // then
        assertTrue(info.contains(sagaState.sagaId))
        assertTrue(info.contains(sagaState.orderId))
        assertTrue(info.contains(sagaState.userId))
        assertTrue(info.contains(sagaState.symbol))
        assertTrue(info.contains(sagaState.orderType))
        assertTrue(info.contains(sagaState.state.name))
    }

    private fun createTestSagaState(
        orderType: String = "LIMIT"
    ): OrderSagaState {
        return OrderSagaState(
            sagaId = UUID.randomUUID().toString(),
            tradeId = UUID.randomUUID().toString(),
            orderId = UUID.randomUUID().toString(),
            userId = "USER001",
            symbol = "AAPL",
            orderType = orderType,
            state = SagaStatus.STARTED,
            timeoutAt = Instant.now().plusSeconds(30),
            metadata = null
        )
    }
}
package com.trading.order.domain.saga

import com.trading.common.domain.saga.SagaStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import java.util.UUID

@DataJpaTest
@TestPropertySource(properties = [
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:testdb"
])
class OrderSagaRepositoryTest {

    @Autowired
    private lateinit var repository: OrderSagaRepository

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @Test
    fun `SagaState 저장 및 조회 테스트`() {
        // given
        val sagaState = createTestSagaState()
        
        // when
        val saved = repository.save(sagaState)
        entityManager.flush()
        entityManager.clear()
        
        val found = repository.findBySagaId(saved.sagaId)
        
        // then
        assertNotNull(found)
        assertEquals(saved.sagaId, found?.sagaId)
        assertEquals(saved.orderId, found?.orderId)
        assertEquals(saved.userId, found?.userId)
        assertEquals(saved.symbol, found?.symbol)
    }

    @Test
    fun `orderId로 SagaState 조회 테스트`() {
        // given
        val orderId = UUID.randomUUID().toString()
        val saga1 = createTestSagaState(orderId = orderId)
        val saga2 = createTestSagaState(orderId = orderId)
        val saga3 = createTestSagaState() // 다른 orderId
        
        repository.save(saga1)
        repository.save(saga2)
        repository.save(saga3)
        entityManager.flush()
        
        // when
        val found = repository.findByOrderId(orderId)
        
        // then
        assertNotNull(found)
        assertEquals(saga1.sagaId, found.sagaId)
    }

    @Test
    fun `userId로 SagaState 목록 조회 테스트`() {
        // given
        val userId = "USER001"
        val saga1 = createTestSagaState(userId = userId)
        val saga2 = createTestSagaState(userId = userId)
        val saga3 = createTestSagaState(userId = "USER002")
        
        repository.save(saga1)
        repository.save(saga2)
        repository.save(saga3)
        entityManager.flush()
        
        // when
        val found = repository.findByUserId(userId)
        
        // then
        assertEquals(2, found.size)
        assertTrue(found.all { it.userId == userId })
    }

    @Test
    fun `타임아웃된 Saga 조회 테스트`() {
        // given
        val now = Instant.now()
        val timedOutSaga1 = createTestSagaState(
            state = SagaStatus.STARTED,
            timeoutAt = now.minusSeconds(60)
        )
        val timedOutSaga2 = createTestSagaState(
            state = SagaStatus.IN_PROGRESS,
            timeoutAt = now.minusSeconds(30)
        )
        val notTimedOutSaga = createTestSagaState(
            state = SagaStatus.STARTED,
            timeoutAt = now.plusSeconds(60)
        )
        val completedSaga = createTestSagaState(
            state = SagaStatus.COMPLETED,
            timeoutAt = now.minusSeconds(60)
        )
        
        repository.save(timedOutSaga1)
        repository.save(timedOutSaga2)
        repository.save(notTimedOutSaga)
        repository.save(completedSaga)
        entityManager.flush()
        
        // when
        val timedOut = repository.findTimedOutSagas(
            states = listOf(SagaStatus.STARTED, SagaStatus.IN_PROGRESS),
            now = now
        )
        
        // then
        assertEquals(2, timedOut.size)
        assertTrue(timedOut.all { it.timeoutAt.isBefore(now) })
        assertTrue(timedOut.all { 
            it.state == SagaStatus.STARTED || it.state == SagaStatus.IN_PROGRESS 
        })
    }

    @Test
    fun `Symbol과 State로 Saga 조회 테스트`() {
        // given
        val symbol = "AAPL"
        val saga1 = createTestSagaState(
            symbol = symbol,
            state = SagaStatus.IN_PROGRESS
        )
        val saga2 = createTestSagaState(
            symbol = symbol,
            state = SagaStatus.IN_PROGRESS
        )
        val saga3 = createTestSagaState(
            symbol = "GOOGL",
            state = SagaStatus.IN_PROGRESS
        )
        val saga4 = createTestSagaState(
            symbol = symbol,
            state = SagaStatus.COMPLETED
        )
        
        repository.save(saga1)
        repository.save(saga2)
        repository.save(saga3)
        repository.save(saga4)
        entityManager.flush()
        
        // when
        val found = repository.findBySymbolAndState(symbol, SagaStatus.IN_PROGRESS)
        
        // then
        assertEquals(2, found.size)
        assertTrue(found.all { it.symbol == symbol && it.state == SagaStatus.IN_PROGRESS })
    }

    @Test
    fun `활성 주문 수 카운트 테스트`() {
        // given
        val userId = "USER001"
        val activeSaga1 = createTestSagaState(userId = userId, state = SagaStatus.STARTED)
        val activeSaga2 = createTestSagaState(userId = userId, state = SagaStatus.IN_PROGRESS)
        val completedSaga = createTestSagaState(userId = userId, state = SagaStatus.COMPLETED)
        val otherUserSaga = createTestSagaState(userId = "USER002", state = SagaStatus.STARTED)
        
        repository.save(activeSaga1)
        repository.save(activeSaga2)
        repository.save(completedSaga)
        repository.save(otherUserSaga)
        entityManager.flush()
        
        // when
        val count = repository.countActiveOrdersByUser(
            userId = userId,
            activeStates = listOf(SagaStatus.STARTED, SagaStatus.IN_PROGRESS)
        )
        
        // then
        assertEquals(2, count)
    }

    private fun createTestSagaState(
        orderId: String = UUID.randomUUID().toString(),
        userId: String = "USER001",
        symbol: String = "AAPL",
        state: SagaStatus = SagaStatus.STARTED,
        timeoutAt: Instant = Instant.now().plusSeconds(30)
    ): OrderSagaState {
        return OrderSagaState(
            sagaId = UUID.randomUUID().toString(),
            tradeId = UUID.randomUUID().toString(),
            orderId = orderId,
            userId = userId,
            symbol = symbol,
            orderType = "LIMIT",
            state = state,
            timeoutAt = timeoutAt,
            metadata = null
        )
    }
}
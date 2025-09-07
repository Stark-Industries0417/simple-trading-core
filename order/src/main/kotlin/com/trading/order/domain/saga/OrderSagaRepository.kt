package com.trading.order.domain.saga

import com.trading.common.domain.saga.SagaStateRepository
import com.trading.common.domain.saga.SagaStatus
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface OrderSagaRepository : SagaStateRepository<OrderSagaState> {
    
    fun findByUserId(userId: String): List<OrderSagaState>
    
    @Query("SELECT s FROM OrderSagaState s WHERE s.symbol = :symbol AND s.state = :state")
    fun findBySymbolAndState(
        @Param("symbol") symbol: String,
        @Param("state") state: SagaStatus
    ): List<OrderSagaState>
    
    @Query("SELECT COUNT(s) FROM OrderSagaState s WHERE s.userId = :userId AND s.state IN :activeStates")
    fun countActiveOrdersByUser(
        @Param("userId") userId: String,
        @Param("activeStates") activeStates: List<SagaStatus> = listOf(
            SagaStatus.STARTED, 
            SagaStatus.IN_PROGRESS
        )
    ): Long
}
package com.trading.common.domain.saga

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.data.repository.query.Param
import java.time.Instant

@NoRepositoryBean
interface SagaStateRepository<T : SagaState> : JpaRepository<T, String> {
    
    fun findBySagaId(sagaId: String): T?
    
    fun findByOrderId(orderId: String): T
    
    fun findByTradeId(tradeId: String): T?
    
    @Query("SELECT s FROM #{#entityName} s WHERE s.state = :state AND s.timeoutAt < :now")
    fun findByStateAndTimeoutAtBefore(
        @Param("state") state: SagaStatus, 
        @Param("now") now: Instant
    ): List<T>
    
    @Query("SELECT s FROM #{#entityName} s WHERE s.state IN :states AND s.timeoutAt < :now")
    fun findTimedOutSagas(
        @Param("states") states: List<SagaStatus> = listOf(SagaStatus.STARTED, SagaStatus.IN_PROGRESS),
        @Param("now") now: Instant
    ): List<T>
    
    @Query("SELECT COUNT(s) FROM #{#entityName} s WHERE s.state IN :stuckStates AND s.startedAt < :threshold")
    fun countStuckSagas(
        @Param("stuckStates") stuckStates: List<SagaStatus> = listOf(SagaStatus.IN_PROGRESS, SagaStatus.COMPENSATING),
        @Param("threshold") threshold: Instant
    ): Long
    
    fun findByState(state: SagaStatus): List<T>
    
    fun findByStateIn(states: List<SagaStatus>): List<T>
}
package com.trading.matching.domain

import com.trading.matching.infrastructure.outbox.MatchingStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface MatchingRepository : JpaRepository<Matching, String> {

    fun findByTradeId(tradeId: String): Matching?

    fun findByBuyOrderId(buyOrderId: String): List<Matching>

    fun findBySellOrderId(sellOrderId: String): List<Matching>

    @Query("SELECT m FROM Matching m WHERE m.buyOrderId = :orderId OR m.sellOrderId = :orderId")
    fun findByOrderId(@Param("orderId") orderId: String): List<Matching>

    fun findByBuyUserId(buyUserId: String): List<Matching>

    fun findBySellUserId(sellUserId: String): List<Matching>

    @Query("SELECT m FROM Matching m WHERE m.buyUserId = :userId OR m.sellUserId = :userId ORDER BY m.createdAt DESC")
    fun findByUserId(@Param("userId") userId: String): List<Matching>

    fun findBySymbol(symbol: String): List<Matching>

    fun findByStatus(status: MatchingStatus): List<Matching>

    fun findByStatusIn(statuses: List<MatchingStatus>): List<Matching>

    @Query("SELECT m FROM Matching m WHERE m.status = :status AND m.createdAt BETWEEN :startTime AND :endTime")
    fun findByStatusAndCreatedAtBetween(
        @Param("status") status: MatchingStatus,
        @Param("startTime") startTime: Instant,
        @Param("endTime") endTime: Instant
    ): List<Matching>

    @Query("SELECT m FROM Matching m WHERE m.status = :status AND m.retryCount < :maxRetry")
    fun findRetryableMatchings(
        @Param("status") status: MatchingStatus,
        @Param("maxRetry") maxRetry: Int = 3
    ): List<Matching>

    @Query("SELECT COUNT(m) FROM Matching m WHERE m.status = :status")
    fun countByStatus(@Param("status") status: MatchingStatus): Long

    @Query("SELECT m FROM Matching m WHERE m.symbol = :symbol AND m.status = :status ORDER BY m.createdAt DESC")
    fun findBySymbolAndStatus(
        @Param("symbol") symbol: String,
        @Param("status") status: MatchingStatus
    ): List<Matching>

    @Query("SELECT m FROM Matching m WHERE m.processedAt IS NULL AND m.createdAt < :threshold")
    fun findUnprocessedOlderThan(@Param("threshold") threshold: Instant): List<Matching>
}
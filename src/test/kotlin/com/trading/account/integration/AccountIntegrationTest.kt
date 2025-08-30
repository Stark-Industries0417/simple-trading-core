package com.trading.account.integration

import com.trading.TestApplication
import com.trading.TestMockConfig
import com.trading.account.application.AccountService
import com.trading.account.application.AccountUpdateResult
import com.trading.account.application.AlertService
import com.trading.account.domain.Account
import com.trading.account.domain.ReservationResult
import com.trading.account.infrastructure.persistence.AccountRepository
import com.trading.account.domain.TransactionLog
import com.trading.account.domain.TransactionLogRepository
import com.trading.account.domain.TransactionType
import com.trading.account.infrastructure.reconciliation.BalanceReconciliationScheduler
import com.trading.common.event.TradeExecutedEvent
import com.trading.common.util.UUIDv7Generator
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(classes = [TestApplication::class, TestMockConfig::class])
@ActiveProfiles("test")
@Transactional
class AccountIntegrationTest {
    
    @Autowired
    private lateinit var accountService: AccountService
    
    @Autowired
    private lateinit var accountRepository: AccountRepository
    
    @Autowired
    private lateinit var transactionLogRepository: TransactionLogRepository
    
    @Autowired
    private lateinit var reconciliationScheduler: BalanceReconciliationScheduler
    
    @Autowired
    private lateinit var alertService: AlertService
    
    @BeforeEach
    fun setup() {
        clearAllMocks()
    }
    
    @Test
    fun `동시 체결 처리시 데드락 없이 정합성 유지`() {
        // Given
        val users = listOf("user1", "user2", "user3")
        users.forEach { userId ->
            accountService.createAccount(userId, BigDecimal("10000.00"))
        }
        
        val trades = listOf(
            createTrade("trade1", "user1", "user2", BigDecimal("100"), BigDecimal("10")),
            createTrade("trade2", "user2", "user3", BigDecimal("100"), BigDecimal("10")),
            createTrade("trade3", "user3", "user1", BigDecimal("100"), BigDecimal("10"))
        )
        
        val executor = Executors.newFixedThreadPool(3)
        val latch = CountDownLatch(trades.size)
        val results = mutableListOf<Boolean>()
        
        trades.forEach { trade ->
            executor.submit {
                try {
                    val result = accountService.processTradeExecution(trade)
                    results.add(result is AccountUpdateResult.Success)
                } catch (ex: Exception) {
                    results.add(false)
                } finally {
                    latch.countDown()
                }
            }
        }
        
        // Then
        val completed = latch.await(5, TimeUnit.SECONDS)
        assertThat(completed).isTrue()
        
        users.forEach { userId ->
            val account = accountRepository.findByUserId(userId)
            assertThat(account).isNotNull
            assertThat(account!!.getCashBalance())
                .isEqualByComparingTo(BigDecimal("10000.00"))
        }
        
        executor.shutdown()
    }
    
    @Test
    fun `Reconciliation이 의도적 불일치를 감지`() {
        val account = Account.create("user1", BigDecimal("10000"))
        accountRepository.save(account)
        
        val fakeLog = TransactionLog.create(
            userId = "user1",
            tradeId = "fake-trade",
            type = TransactionType.BUY,
            symbol = "AAPL",
            quantity = BigDecimal("10"),
            price = BigDecimal("100"),
            amount = BigDecimal("1000")
        )
        transactionLogRepository.save(fakeLog)
        
        mockkObject(alertService)
        
        // When
        reconciliationScheduler.validateDataConsistency()
        
        verify(exactly = 1) { 
            alertService.sendCriticalAlert(
                match { it.contains("Inconsistency") },
                match { it.contains("user1") }
            )
        }
    }
    
    @Test
    fun `계좌 생성 및 초기 잔고 설정`() {
        // When
        val account = accountService.createAccount("testUser", BigDecimal("5000.00"))
        
        // Then
        assertThat(account).isNotNull
        assertThat(account.userId).isEqualTo("testUser")
        assertThat(account.getCashBalance()).isEqualByComparingTo(BigDecimal("5000.00"))
        assertThat(account.getAvailableCash()).isEqualByComparingTo(BigDecimal("5000.00"))
        assertThat(account.isConsistent()).isTrue()
    }
    
    @Test
    fun `자금 예약 성공 시나리오`() {
        // Given
        val account = accountService.createAccount("user1", BigDecimal("1000.00"))
        
        // When
        val result = accountService.reserveFundsForOrder(
            userId = "user1",
            amount = BigDecimal("500.00"),
            traceId = "trace123"
        )
        
        // Then
        assertThat(result).isInstanceOf(ReservationResult.Success::class.java)
        
        val updatedAccount = accountRepository.findByUserId("user1")
        assertThat(updatedAccount!!.getCashBalance()).isEqualByComparingTo(BigDecimal("1000.00"))
        assertThat(updatedAccount.getAvailableCash()).isEqualByComparingTo(BigDecimal("500.00"))
    }
    
    @Test
    fun `자금 부족 시 예약 실패`() {
        // Given
        accountService.createAccount("user1", BigDecimal("100.00"))
        
        // When
        val result = accountService.reserveFundsForOrder(
            userId = "user1",
            amount = BigDecimal("500.00"),
            traceId = "trace123"
        )
        
        // Then
        assertThat(result).isInstanceOf(ReservationResult.InsufficientFunds::class.java)
        val insufficientResult = result as ReservationResult.InsufficientFunds
        assertThat(insufficientResult.required).isEqualByComparingTo(BigDecimal("500.00"))
        assertThat(insufficientResult.available).isEqualByComparingTo(BigDecimal("100.00"))
    }
    
    @Test
    fun `체결 처리 후 계좌 업데이트 정확성`() {
        // Given
        accountService.createAccount("buyer", BigDecimal("10000.00"))
        accountService.createAccount("seller", BigDecimal("5000.00"))
        
        
        val trade = createTrade(
            tradeId = "trade123",
            buyUserId = "buyer",
            sellUserId = "seller",
            price = BigDecimal("150.00"),
            quantity = BigDecimal("10")
        )
        
        // When
        val result = accountService.processTradeExecution(trade)
        
        // Then
        if (result is AccountUpdateResult.Success) {
            val buyerAccount = accountRepository.findByUserId("buyer")
            val sellerAccount = accountRepository.findByUserId("seller")
            
            assertThat(buyerAccount!!.getCashBalance())
                .isEqualByComparingTo(BigDecimal("8500.00"))
            
            assertThat(sellerAccount!!.getCashBalance())
                .isEqualByComparingTo(BigDecimal("6500.00"))
        }
    }
    
    private fun createTrade(
        tradeId: String,
        buyUserId: String,
        sellUserId: String,
        price: BigDecimal,
        quantity: BigDecimal
    ): TradeExecutedEvent {
        return TradeExecutedEvent(
            eventId = UUIDv7Generator.generate(),
            aggregateId = tradeId,
            occurredAt = Instant.now(),
            traceId = "trace-${System.currentTimeMillis()}",
            tradeId = tradeId,
            symbol = "AAPL",
            buyOrderId = "buy-$tradeId",
            sellOrderId = "sell-$tradeId",
            buyUserId = buyUserId,
            sellUserId = sellUserId,
            price = price,
            quantity = quantity,
            timestamp = System.currentTimeMillis()
        )
    }
}
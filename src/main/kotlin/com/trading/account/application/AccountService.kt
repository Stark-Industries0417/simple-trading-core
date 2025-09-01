package com.trading.account.application

import com.trading.account.domain.*
import com.trading.account.infrastructure.persistence.AccountRepository
import com.trading.account.infrastructure.persistence.StockHoldingRepository
import com.trading.common.dto.account.AccountDTO
import com.trading.common.event.*
import com.trading.common.exception.account.InsufficientBalanceException
import com.trading.common.logging.StructuredLogger
import com.trading.common.util.UUIDv7Generator
import jakarta.persistence.PessimisticLockException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

@Service
@Transactional
class AccountService(
    private val accountRepository: AccountRepository,
    private val stockHoldingRepository: StockHoldingRepository,
    private val transactionLogRepository: TransactionLogRepository,
    private val eventPublisher: EventPublisher,
    private val structuredLogger: StructuredLogger
) {
    
    fun createAccount(userId: String, initialBalance: BigDecimal): Account {
        structuredLogger.info("Creating account",
            mapOf(
                "userId" to userId,
                "initialBalance" to initialBalance.toString()
            )
        )
        
        val account = Account.create(userId, initialBalance)
        return accountRepository.save(account)
    }
    
    fun processTradeExecution(event: TradeExecutedEvent): AccountUpdateResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val sortedUserIds = listOf(event.buyUserId, event.sellUserId).sorted()
            val accounts = sortedUserIds.map { userId ->
                accountRepository.findByUserIdWithLock(userId)
                    ?: throw AccountNotFoundException("Account not found: $userId")
            }
            
            val (buyerAccount, sellerAccount) = if (sortedUserIds[0] == event.buyUserId) {
                accounts[0] to accounts[1]
            } else {
                accounts[1] to accounts[0]
            }
            
            val totalCost = event.price * event.quantity
            buyerAccount.confirmReservation(event.tradeId, totalCost)
            
            val buyerHolding = stockHoldingRepository
                .findByUserIdAndSymbolWithLock(event.buyUserId, event.symbol)
                ?: StockHolding.create(event.buyUserId, event.symbol)
            buyerHolding.addShares(event.quantity, event.price)
            
            val sellerHolding = stockHoldingRepository
                .findByUserIdAndSymbolWithLock(event.sellUserId, event.symbol)
                ?: throw StockNotFoundException("Stock not found for user ${event.sellUserId}, symbol ${event.symbol}")
            sellerHolding.confirmReservation(event.quantity)
            sellerAccount.deposit(totalCost)
            
            val buyLog = TransactionLog.create(
                userId = event.buyUserId,
                tradeId = event.tradeId,
                type = TransactionType.BUY,
                symbol = event.symbol,
                quantity = event.quantity,
                price = event.price,
                amount = totalCost,
                balanceBefore = buyerAccount.getCashBalance() + totalCost,
                balanceAfter = buyerAccount.getCashBalance()
            )
            
            val sellLog = TransactionLog.create(
                userId = event.sellUserId,
                tradeId = event.tradeId,
                type = TransactionType.SELL,
                symbol = event.symbol,
                quantity = event.quantity,
                price = event.price,
                amount = totalCost,
                balanceBefore = sellerAccount.getCashBalance() - totalCost,
                balanceAfter = sellerAccount.getCashBalance()
            )
            
            accountRepository.saveAll(listOf(buyerAccount, sellerAccount))
            stockHoldingRepository.save(buyerHolding)
            stockHoldingRepository.save(sellerHolding)
            transactionLogRepository.saveAll(listOf(buyLog, sellLog))
            
            publishAccountUpdatedEvent(buyerAccount, sellerAccount, event.tradeId, event.traceId)
            
            val duration = System.currentTimeMillis() - startTime
            structuredLogger.info("Trade execution completed",
                mapOf(
                    "tradeId" to event.tradeId,
                    "duration" to duration.toString(),
                    "amount" to totalCost.toString()
                )
            )
            
            AccountUpdateResult.Success(
                buyerNewBalance = buyerAccount.getCashBalance(),
                sellerNewBalance = sellerAccount.getCashBalance()
            )
            
        } catch (ex: InsufficientBalanceException) {
            handleBusinessFailure(event, ex)
        } catch (ex: PessimisticLockException) {
            handleTechnicalFailure(event, ex)
        } catch (ex: Exception) {
            handleSystemFailure(event, ex)
        }
    }
    
    fun reserveFundsForOrder(userId: String, amount: BigDecimal, traceId: String): ReservationResult {
        val account = accountRepository.findByUserIdWithLock(userId)
            ?: throw AccountNotFoundException("Account not found: $userId")
        
        val result = account.reserveCash(amount)
        
        if (result is ReservationResult.Success) {
            accountRepository.save(account)
            structuredLogger.info("Funds reserved",
                mapOf(
                    "userId" to userId,
                    "amount" to amount.toString(),
                    "reservationId" to result.reservationId
                )
            )
        }
        
        return result
    }
    
    fun reserveStocksForOrder(userId: String, symbol: String, quantity: BigDecimal, traceId: String): StockReservationResult {
        val holding = stockHoldingRepository.findByUserIdAndSymbolWithLock(userId, symbol)
            ?: return StockReservationResult.InsufficientShares(
                required = quantity,
                available = BigDecimal.ZERO
            )
        
        val result = holding.reserveShares(quantity)
        
        if (result is StockReservationResult.Success) {
            stockHoldingRepository.save(holding)
            structuredLogger.info("Stocks reserved",
                mapOf(
                    "userId" to userId,
                    "symbol" to symbol,
                    "quantity" to quantity.toString(),
                    "reservationId" to result.reservationId
                )
            )
        }
        
        return result
    }
    
    private fun publishAccountUpdatedEvent(
        buyerAccount: Account, 
        sellerAccount: Account, 
        tradeId: String,
        traceId: String
    ) {
        eventPublisher.publish(
            AccountUpdatedEvent(
                eventId = UUIDv7Generator.generate(),
                aggregateId = tradeId,
                occurredAt = Instant.now(),
                traceId = traceId,
                account = AccountDTO(
                    userId = buyerAccount.userId,
                    cashBalance = buyerAccount.getCashBalance(),
                    availableCash = buyerAccount.getAvailableCash()
                ),
                relatedTradeId = tradeId
            )
        )
        
        eventPublisher.publish(
            AccountUpdatedEvent(
                eventId = UUIDv7Generator.generate(),
                aggregateId = tradeId,
                occurredAt = Instant.now(),
                traceId = traceId,
                account = AccountDTO(
                    userId = sellerAccount.userId,
                    cashBalance = sellerAccount.getCashBalance(),
                    availableCash = sellerAccount.getAvailableCash()
                ),
                relatedTradeId = tradeId
            )
        )
    }
    
    private fun handleBusinessFailure(
        event: TradeExecutedEvent, 
        ex: Exception
    ): AccountUpdateResult {
        structuredLogger.warn("Business rule violation",
            mapOf(
                "tradeId" to event.tradeId,
                "reason" to (ex.message ?: "Unknown")
            )
        )
        
        eventPublisher.publish(
            AccountUpdateFailedEvent(
                eventId = UUIDv7Generator.generate(),
                aggregateId = event.tradeId,
                occurredAt = Instant.now(),
                traceId = event.traceId,
                userId = event.buyUserId,
                relatedTradeId = event.tradeId,
                failureReason = ex.message ?: "Unknown",
                amount = event.price * event.quantity,
                shouldRetry = false
            )
        )
        
        return AccountUpdateResult.Failure(
            reason = ex.message ?: "Business rule violation",
            shouldRetry = false
        )
    }
    
    private fun handleTechnicalFailure(
        event: TradeExecutedEvent,
        ex: Exception
    ): AccountUpdateResult {
        structuredLogger.error("Technical failure - lock timeout",
            mapOf("tradeId" to event.tradeId),
            ex
        )
        
        return AccountUpdateResult.Failure(
            reason = "Lock acquisition timeout",
            shouldRetry = true
        )
    }
    
    private fun handleSystemFailure(
        event: TradeExecutedEvent,
        ex: Exception
    ): AccountUpdateResult {
        structuredLogger.error("System failure",
            mapOf("tradeId" to event.tradeId),
            ex
        )
        
        eventPublisher.publish(
            AccountUpdateFailedEvent(
                eventId = UUIDv7Generator.generate(),
                aggregateId = event.tradeId,
                occurredAt = Instant.now(),
                traceId = event.traceId,
                userId = event.buyUserId,
                relatedTradeId = event.tradeId,
                failureReason = "System failure: ${ex.message}",
                amount = event.price * event.quantity,
                shouldRetry = false
            )
        )
        
        return AccountUpdateResult.Failure(
            reason = "System failure",
            shouldRetry = false
        )
    }
}

sealed class AccountUpdateResult {
    data class Success(
        val buyerNewBalance: BigDecimal,
        val sellerNewBalance: BigDecimal
    ) : AccountUpdateResult()
    
    data class Failure(
        val reason: String,
        val shouldRetry: Boolean
    ) : AccountUpdateResult()
}

class AccountNotFoundException(message: String) : RuntimeException(message)
class StockNotFoundException(message: String) : RuntimeException(message)
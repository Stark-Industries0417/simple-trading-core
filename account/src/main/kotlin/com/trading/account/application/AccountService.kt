package com.trading.account.application

import com.trading.account.domain.*
import com.trading.account.infrastructure.persistence.AccountRepository
import com.trading.account.infrastructure.persistence.ReservationInfoRepository
import com.trading.account.infrastructure.persistence.StockHoldingRepository
import com.trading.account.infrastructure.persistence.TransactionLogRepository

import com.trading.common.dto.order.OrderSide
import com.trading.common.event.matching.TradeExecutedEvent
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
    private val reservationInfoRepository: ReservationInfoRepository,
    private val structuredLogger: StructuredLogger,
    private val uuidGenerator: UUIDv7Generator
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
    
    fun reserveFundsForOrder(
        orderId: String, 
        userId: String, 
        symbol: String,
        quantity: BigDecimal,
        price: BigDecimal,
        amount: BigDecimal, 
        traceId: String
    ): ReservationResult {
        val account = accountRepository.findByUserIdWithLock(userId)
            ?: throw AccountNotFoundException("Account not found: $userId")
        
        val result = account.reserveCash(amount)
        
        if (result is ReservationResult.Success) {
            accountRepository.save(account)
            
            val reservationInfo = ReservationInfo.createForBuyOrder(
                orderId = orderId,
                userId = userId,
                symbol = symbol,
                quantity = quantity,
                price = price,
                traceId = traceId
            )
            reservationInfoRepository.save(reservationInfo)
            
            structuredLogger.info("Funds reserved",
                mapOf(
                    "orderId" to orderId,
                    "userId" to userId,
                    "symbol" to symbol,
                    "amount" to amount.toString(),
                    "reservationId" to result.reservationId,
                    "traceId" to traceId
                )
            )
        }
        
        return result
    }
    
    fun reserveStocksForOrder(
        orderId: String,
        userId: String, 
        symbol: String, 
        quantity: BigDecimal,
        price: BigDecimal? = null,
        traceId: String
    ): StockReservationResult {
        val holding = stockHoldingRepository.findByUserIdAndSymbolWithLock(userId, symbol)
            ?: return StockReservationResult.InsufficientShares(
                required = quantity,
                available = BigDecimal.ZERO
            )
        
        val result = holding.reserveShares(quantity)
        
        if (result is StockReservationResult.Success) {
            stockHoldingRepository.save(holding)
            
            val reservationInfo = ReservationInfo.createForSellOrder(
                orderId = orderId,
                userId = userId,
                symbol = symbol,
                quantity = quantity,
                price = price,
                traceId = traceId
            )
            reservationInfoRepository.save(reservationInfo)
            
            structuredLogger.info("Stocks reserved",
                mapOf(
                    "orderId" to orderId,
                    "userId" to userId,
                    "symbol" to symbol,
                    "quantity" to quantity.toString(),
                    "reservationId" to result.reservationId,
                    "traceId" to traceId
                )
            )
        }
        
        return result
    }

    fun releaseReservationByOrderId(orderId: String, traceId: String): Boolean {
        val startTime = System.currentTimeMillis()
        
        return try {
            val reservationInfo = reservationInfoRepository.findByOrderId(orderId)
            
            if (reservationInfo == null) {
                structuredLogger.warn("No reservation info found for order",
                    mapOf(
                        "orderId" to orderId,
                        "traceId" to traceId
                    )
                )
                // 예약 정보가 없다는 것은 예약이 생성되지 않았거나 이미 처리됨
                return true
            }
            
            if (!reservationInfo.isActive()) {
                structuredLogger.info("Reservation already processed",
                    mapOf(
                        "orderId" to orderId,
                        "status" to reservationInfo.status.name,
                        "traceId" to traceId
                    )
                )
                return true
            }
            
            val success = when (reservationInfo.side) {
                OrderSide.BUY -> {
                    val account = accountRepository.findByUserIdWithLock(reservationInfo.userId)
                    if (account != null && reservationInfo.reservedAmount != null) {
                        account.releaseReservation(reservationInfo.reservedAmount)
                        accountRepository.save(account)
                        
                        structuredLogger.info("Cash reservation released using stored info",
                            mapOf(
                                "orderId" to orderId,
                                "userId" to reservationInfo.userId,
                                "amount" to reservationInfo.reservedAmount.toString(),
                                "duration" to (System.currentTimeMillis() - startTime).toString(),
                                "traceId" to traceId
                            )
                        )
                        true
                    } else {
                        false
                    }
                }
                
                OrderSide.SELL -> {
                    val holding = stockHoldingRepository.findByUserIdAndSymbolWithLock(
                        reservationInfo.userId,
                        reservationInfo.symbol
                    )
                    if (holding != null) {
                        holding.releaseReservation(reservationInfo.quantity)
                        stockHoldingRepository.save(holding)
                        
                        structuredLogger.info("Stock reservation released using stored info",
                            mapOf(
                                "orderId" to orderId,
                                "userId" to reservationInfo.userId,
                                "symbol" to reservationInfo.symbol,
                                "quantity" to reservationInfo.quantity.toString(),
                                "duration" to (System.currentTimeMillis() - startTime).toString(),
                                "traceId" to traceId
                            )
                        )
                        true
                    } else {
                        true // 주식 보유가 없으면 예약도 없었을 것
                    }
                }
            }
            
            if (success) {
                reservationInfo.release()
                reservationInfoRepository.save(reservationInfo)
            }
            
            success
            
        } catch (ex: Exception) {
            structuredLogger.error("Failed to release reservation by orderId",
                mapOf(
                    "orderId" to orderId,
                    "error" to (ex.message ?: "Unknown error"),
                    "traceId" to traceId
                ),
                ex
            )
            false
        }
    }
    
    fun rollbackTradeExecution(
        tradeId: String,
        buyUserId: String,
        sellUserId: String,
        symbol: String,
        quantity: BigDecimal,
        price: BigDecimal,
        traceId: String
    ): RollbackResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            structuredLogger.info("Starting trade rollback",
                mapOf(
                    "tradeId" to tradeId,
                    "buyUserId" to buyUserId,
                    "sellUserId" to sellUserId,
                    "symbol" to symbol,
                    "quantity" to quantity.toString(),
                    "price" to price.toString(),
                    "traceId" to traceId
                )
            )
            
            val sortedUserIds = listOf(buyUserId, sellUserId).sorted()
            val accounts = sortedUserIds.map { userId ->
                accountRepository.findByUserIdWithLock(userId)
                    ?: throw AccountNotFoundException("Account not found during rollback: $userId")
            }
            
            val (buyerAccount, sellerAccount) = if (sortedUserIds[0] == buyUserId) {
                accounts[0] to accounts[1]
            } else {
                accounts[1] to accounts[0]
            }

            val totalCost = price * quantity
            buyerAccount.rollbackWithdrawal(totalCost)
            sellerAccount.rollbackDeposit(totalCost)
            
            val buyerHolding = stockHoldingRepository
                .findByUserIdAndSymbolWithLock(buyUserId, symbol)
            
            if (buyerHolding != null) {
                buyerHolding.rollbackPurchase(quantity, price)
                stockHoldingRepository.save(buyerHolding)
            } else {
                structuredLogger.warn("Buyer holding not found during rollback",
                    mapOf(
                        "tradeId" to tradeId,
                        "buyUserId" to buyUserId,
                        "symbol" to symbol
                    )
                )
            }
            
            val sellerHolding = stockHoldingRepository
                .findByUserIdAndSymbolWithLock(sellUserId, symbol)
                ?: StockHolding.create(sellUserId, symbol)
            
            sellerHolding.rollbackSale(quantity)
            stockHoldingRepository.save(sellerHolding)
            
            val buyerRollbackLog = TransactionLog.create(
                userId = buyUserId,
                tradeId = "$tradeId-rollback",
                type = TransactionType.ROLLBACK,
                symbol = symbol,
                quantity = quantity,
                price = price,
                amount = totalCost,
                balanceBefore = buyerAccount.getCashBalance() - totalCost,
                balanceAfter = buyerAccount.getCashBalance()
            )
            
            val sellerRollbackLog = TransactionLog.create(
                userId = sellUserId,
                tradeId = "$tradeId-rollback",
                type = TransactionType.ROLLBACK,
                symbol = symbol,
                quantity = quantity,
                price = price,
                amount = totalCost,
                balanceBefore = sellerAccount.getCashBalance() + totalCost,
                balanceAfter = sellerAccount.getCashBalance()
            )
            
            accountRepository.saveAll(listOf(buyerAccount, sellerAccount))
            transactionLogRepository.saveAll(listOf(buyerRollbackLog, sellerRollbackLog))
            
            val duration = System.currentTimeMillis() - startTime
            structuredLogger.info("Trade rollback completed",
                mapOf(
                    "tradeId" to tradeId,
                    "duration" to duration.toString(),
                    "buyerNewBalance" to buyerAccount.getCashBalance().toString(),
                    "sellerNewBalance" to sellerAccount.getCashBalance().toString()
                )
            )
            
            RollbackResult.Success(
                buyerNewBalance = buyerAccount.getCashBalance(),
                sellerNewBalance = sellerAccount.getCashBalance()
            )
            
        } catch (ex: Exception) {
            structuredLogger.error("Trade rollback failed",
                mapOf(
                    "tradeId" to tradeId,
                    "error" to (ex.message ?: "Unknown error")
                ),
                ex
            )
            
            RollbackResult.Failure(
                reason = ex.message ?: "Rollback failed",
                exception = ex
            )
        }
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

sealed class RollbackResult {
    data class Success(
        val buyerNewBalance: BigDecimal,
        val sellerNewBalance: BigDecimal
    ) : RollbackResult()
    
    data class Failure(
        val reason: String,
        val exception: Exception
    ) : RollbackResult()
}

class AccountNotFoundException(message: String) : RuntimeException(message)
class StockNotFoundException(message: String) : RuntimeException(message)
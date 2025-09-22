package com.trading.account.application

import com.trading.account.domain.*
import com.trading.account.infrastructure.persistence.AccountRepository
import com.trading.account.infrastructure.persistence.ReservationInfoRepository
import com.trading.account.infrastructure.persistence.StockHoldingRepository
import com.trading.account.infrastructure.persistence.TransactionLogRepository
import com.trading.common.dto.cdc.matching.MatchingCreatedDto

import com.trading.common.dto.order.OrderSide
import com.trading.common.exception.account.InsufficientBalanceException
import jakarta.persistence.PessimisticLockException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal


@Service
@Transactional
class AccountService(
    private val accountRepository: AccountRepository,
    private val stockHoldingRepository: StockHoldingRepository,
    private val transactionLogRepository: TransactionLogRepository,
    private val reservationInfoRepository: ReservationInfoRepository,
) {
    
    fun createAccount(userId: String, initialBalance: BigDecimal): Account {
        val account = Account.create(userId, initialBalance)
        return accountRepository.save(account)
    }
    
    fun processTradeExecution(event: MatchingCreatedDto): AccountUpdateResult {

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

            val buyerReservation = reservationInfoRepository.findByOrderId(event.buyOrderId)
            if (buyerReservation != null && buyerReservation.isActive()) {
                buyerReservation.confirm()
                reservationInfoRepository.save(buyerReservation)
            }

            val sellerReservation = reservationInfoRepository.findByOrderId(event.sellOrderId)
            if (sellerReservation != null && sellerReservation.isActive()) {
                sellerReservation.confirm()
                reservationInfoRepository.save(sellerReservation)
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

            AccountUpdateResult.Success(
                buyerNewBalance = buyerAccount.getCashBalance(),
                sellerNewBalance = sellerAccount.getCashBalance()
            )
            
        } catch (ex: InsufficientBalanceException) {
            handleBusinessFailure(ex)
        } catch (ex: PessimisticLockException) {
            handleTechnicalFailure(ex)
        } catch (ex: Exception) {
            handleSystemFailure(ex)
        }
    }
    
    fun reserveFundsForOrder(
        orderId: String,
        userId: String,
        symbol: String,
        quantity: BigDecimal,
        price: BigDecimal,
        amount: BigDecimal,
    ): ReservationResult {
        // 입력 검증을 먼저 수행하여 트랜잭션 내부에서 예외 발생 방지
        if (amount <= BigDecimal.ZERO) {
            return ReservationResult.InsufficientFunds(
                required = amount,
                available = BigDecimal.ZERO
            )
        }

        val account = accountRepository.findByUserIdWithLock(userId)
            ?: throw AccountNotFoundException("Account not found: $userId")

        val result = try {
            account.reserveCash(amount)
        } catch (e: IllegalArgumentException) {
            // require() 검증 실패를 비즈니스 결과로 변환
            return ReservationResult.InsufficientFunds(
                required = amount,
                available = account.getCashBalance()
            )
        }

        if (result is ReservationResult.Success) {
            accountRepository.save(account)

            val reservationInfo = ReservationInfo.createForBuyOrder(
                orderId = orderId,
                userId = userId,
                symbol = symbol,
                quantity = quantity,
                price = price,
            )
            reservationInfoRepository.save(reservationInfo)
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
        // 입력 검증을 먼저 수행
        if (quantity <= BigDecimal.ZERO) {
            return StockReservationResult.InsufficientShares(
                required = quantity,
                available = BigDecimal.ZERO
            )
        }

        val holding = stockHoldingRepository.findByUserIdAndSymbolWithLock(userId, symbol)
            ?: return StockReservationResult.InsufficientShares(
                required = quantity,
                available = BigDecimal.ZERO
            )

        val result = try {
            holding.reserveShares(quantity)
        } catch (e: IllegalArgumentException) {
            // require() 검증 실패를 비즈니스 결과로 변환
            return StockReservationResult.InsufficientShares(
                required = quantity,
                available = BigDecimal.ZERO  // 예외 발생 시 사용 가능 수량을 0으로 표시
            )
        }

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
        }

        return result
    }

    fun releaseReservationByOrderId(orderId: String): Boolean {
        return try {
            val reservationInfo = reservationInfoRepository.findByOrderId(orderId)
            
            if (reservationInfo == null) return true
            if (!reservationInfo.isActive()) return true

            val success = when (reservationInfo.side) {
                OrderSide.BUY -> {
                    val account = accountRepository.findByUserIdWithLock(reservationInfo.userId)
                    if (account != null && reservationInfo.reservedAmount != null) {
                        account.releaseReservation(reservationInfo.reservedAmount)
                        accountRepository.save(account)
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
    ): RollbackResult {

        return try {
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

            RollbackResult.Success(
                buyerNewBalance = buyerAccount.getCashBalance(),
                sellerNewBalance = sellerAccount.getCashBalance()
            )
            
        } catch (ex: Exception) {
            RollbackResult.Failure(
                reason = ex.message ?: "Rollback failed",
                exception = ex
            )
        }
    }
    
    private fun handleBusinessFailure(
        ex: Exception
    ): AccountUpdateResult {
        return AccountUpdateResult.Failure(
            reason = ex.message ?: "Business rule violation",
            shouldRetry = false
        )
    }
    
    private fun handleTechnicalFailure(
        ex: Exception
    ): AccountUpdateResult {
        return AccountUpdateResult.Failure(
            reason = "Lock acquisition timeout",
            shouldRetry = true
        )
    }
    
    private fun handleSystemFailure(
        ex: Exception
    ): AccountUpdateResult {
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
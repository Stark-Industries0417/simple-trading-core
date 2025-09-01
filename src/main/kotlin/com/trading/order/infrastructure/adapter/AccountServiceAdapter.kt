package com.trading.order.infrastructure.adapter

import com.trading.account.infrastructure.persistence.AccountRepository
import com.trading.account.infrastructure.persistence.StockHoldingRepository
import com.trading.common.logging.StructuredLogger
import com.trading.marketdata.generator.MarketDataGenerator
import com.trading.order.domain.AccountService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
@Transactional(readOnly = true)
class AccountServiceAdapter(
    private val accountRepository: AccountRepository,
    private val stockHoldingRepository: StockHoldingRepository,
    private val marketDataGenerator: MarketDataGenerator,
    private val structuredLogger: StructuredLogger
) : AccountService {
    
    override fun hasSufficientCash(userId: String, amount: BigDecimal): Boolean {
        return try {
            val account = accountRepository.findByUserId(userId)
            
            if (account == null) {
                structuredLogger.warn("Account not found for user",
                    mapOf("userId" to userId)
                )
                return false
            }
            
            val hasSufficient = account.getAvailableCash() >= amount
            
            structuredLogger.info("Cash balance check",
                mapOf(
                    "userId" to userId,
                    "requiredAmount" to amount.toString(),
                    "availableCash" to account.getAvailableCash().toString(),
                    "hasSufficient" to hasSufficient.toString()
                )
            )
            
            hasSufficient
        } catch (ex: Exception) {
            structuredLogger.error("Failed to check cash balance",
                mapOf(
                    "userId" to userId,
                    "amount" to amount.toString(),
                    "error" to (ex.message ?: "Unknown error")
                ),
                ex
            )
            false
        }
    }
    
    override fun hasSufficientStock(userId: String, symbol: String, quantity: BigDecimal): Boolean {
        return try {
            val holding = stockHoldingRepository.findByUserIdAndSymbol(userId, symbol)
            
            if (holding == null) {
                structuredLogger.warn("Stock holding not found",
                    mapOf(
                        "userId" to userId,
                        "symbol" to symbol
                    )
                )
                return false
            }
            
            val hasSufficient = holding.getAvailableQuantity() >= quantity
            
            structuredLogger.info("Stock balance check",
                mapOf(
                    "userId" to userId,
                    "symbol" to symbol,
                    "requiredQuantity" to quantity.toString(),
                    "availableShares" to holding.getAvailableQuantity().toString(),
                    "hasSufficient" to hasSufficient.toString()
                )
            )
            
            hasSufficient
        } catch (ex: Exception) {
            structuredLogger.error("Failed to check stock balance",
                mapOf(
                    "userId" to userId,
                    "symbol" to symbol,
                    "quantity" to quantity.toString(),
                    "error" to (ex.message ?: "Unknown error")
                ),
                ex
            )
            false
        }
    }
    
    override fun getCurrentPrice(symbol: String): BigDecimal? {
        return try {
            val price = marketDataGenerator.getCurrentPrice(symbol)
            
            if (price != null) {
                structuredLogger.info("Retrieved current price for balance calculation",
                    mapOf(
                        "symbol" to symbol,
                        "price" to price.toString()
                    )
                )
            } else {
                structuredLogger.warn("No price available for balance calculation",
                    mapOf("symbol" to symbol)
                )
            }
            
            price
        } catch (ex: Exception) {
            structuredLogger.error("Failed to retrieve price for balance calculation",
                mapOf(
                    "symbol" to symbol,
                    "error" to (ex.message ?: "Unknown error")
                ),
                ex
            )
            null
        }
    }
}
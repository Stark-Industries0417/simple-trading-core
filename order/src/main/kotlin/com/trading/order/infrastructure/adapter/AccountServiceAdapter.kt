package com.trading.order.infrastructure.adapter

import com.trading.common.adapter.AccountServiceProvider
import com.trading.common.adapter.MarketDataProvider
import com.trading.common.logging.StructuredLogger
import com.trading.order.domain.AccountService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
@Transactional(readOnly = true)
class AccountServiceAdapter(
    private val accountServiceProvider: AccountServiceProvider,
    private val marketDataProvider: MarketDataProvider,
    private val structuredLogger: StructuredLogger
) : AccountService {
    
    override fun hasSufficientCash(userId: String, amount: BigDecimal): Boolean {
        return try {
            if (!accountServiceProvider.accountExists(userId)) {
                structuredLogger.warn("Account not found for user",
                    mapOf("userId" to userId)
                )
                return false
            }
            
            val hasSufficient = accountServiceProvider.hasSufficientCash(userId, amount)
            
            structuredLogger.info("Cash balance check",
                mapOf(
                    "userId" to userId,
                    "requiredAmount" to amount.toString(),
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
            val hasSufficient = accountServiceProvider.hasSufficientStock(userId, symbol, quantity)
            
            if (!hasSufficient) {
                structuredLogger.warn("Insufficient stock balance",
                    mapOf(
                        "userId" to userId,
                        "symbol" to symbol,
                        "requiredQuantity" to quantity.toString()
                    )
                )
            } else {
                structuredLogger.info("Stock balance check passed",
                    mapOf(
                        "userId" to userId,
                        "symbol" to symbol,
                        "requiredQuantity" to quantity.toString(),
                        "hasSufficient" to hasSufficient.toString()
                    )
                )
            }
            
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
            val price = marketDataProvider.getCurrentPrice(symbol)
            
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
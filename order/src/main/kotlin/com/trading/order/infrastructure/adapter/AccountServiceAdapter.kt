package com.trading.order.infrastructure.adapter

import com.trading.common.adapter.AccountServiceProvider
import com.trading.common.adapter.MarketDataProvider
import com.trading.order.domain.AccountService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
@Transactional(readOnly = true)
class AccountServiceAdapter(
    private val accountServiceProvider: AccountServiceProvider,
    private val marketDataProvider: MarketDataProvider,
) : AccountService {
    
    override fun hasSufficientCash(userId: String, amount: BigDecimal): Boolean {
        return try {
            if (!accountServiceProvider.accountExists(userId)) {
                return false
            }
            accountServiceProvider.hasSufficientCash(userId, amount)
        } catch (ex: Exception) {
            false
        }
    }
    
    override fun hasSufficientStock(userId: String, symbol: String, quantity: BigDecimal): Boolean {
        return try {
            val hasSufficient = accountServiceProvider.hasSufficientStock(userId, symbol, quantity)
            
            if (!hasSufficient) {
            } else {
            }
            
            hasSufficient
        } catch (ex: Exception) {
            false
        }
    }
    
    override fun getCurrentPrice(symbol: String): BigDecimal? {
        return try {
            val price = marketDataProvider.getCurrentPrice(symbol)
            
            if (price != null) {
            } else {
            }
            
            price
        } catch (ex: Exception) {
            null
        }
    }
}
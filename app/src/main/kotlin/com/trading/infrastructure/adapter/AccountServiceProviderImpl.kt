package com.trading.infrastructure.adapter

import com.trading.account.infrastructure.persistence.AccountRepository
import com.trading.account.infrastructure.persistence.StockHoldingRepository
import com.trading.common.adapter.AccountServiceProvider
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal



@Component
@Transactional(readOnly = true)
class AccountServiceProviderImpl(
    private val accountRepository: AccountRepository,
    private val stockHoldingRepository: StockHoldingRepository
) : AccountServiceProvider {
    
    override fun hasSufficientCash(userId: String, amount: BigDecimal): Boolean {
        val account = accountRepository.findByUserId(userId) ?: return false
        return account.getAvailableCash() >= amount
    }
    
    override fun hasSufficientStock(userId: String, symbol: String, quantity: BigDecimal): Boolean {
        val holding = stockHoldingRepository.findByUserIdAndSymbol(userId, symbol) ?: return false
        return holding.getAvailableQuantity() >= quantity
    }
    
    override fun accountExists(userId: String): Boolean {
        return accountRepository.findByUserId(userId) != null
    }
}
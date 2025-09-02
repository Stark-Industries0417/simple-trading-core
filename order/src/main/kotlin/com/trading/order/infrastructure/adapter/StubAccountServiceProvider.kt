package com.trading.order.infrastructure.adapter

import com.trading.common.adapter.AccountServiceProvider
import java.math.BigDecimal

class StubAccountServiceProvider : AccountServiceProvider {
    
    override fun hasSufficientCash(userId: String, amount: BigDecimal): Boolean {
        return true
    }
    
    override fun hasSufficientStock(userId: String, symbol: String, quantity: BigDecimal): Boolean {
        return true
    }
    
    override fun accountExists(userId: String): Boolean {
        return true
    }
}
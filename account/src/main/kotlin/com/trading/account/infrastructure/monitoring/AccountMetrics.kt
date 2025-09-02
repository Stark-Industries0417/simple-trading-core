package com.trading.account.infrastructure.monitoring

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class AccountMetrics(private val meterRegistry: MeterRegistry) {
    
    fun recordAccountUpdate(duration: Long, success: Boolean) {
        Timer.builder("account.update.duration")
            .tag("success", success.toString())
            .description("Time taken to update account")
            .register(meterRegistry)
            .record(duration, TimeUnit.MILLISECONDS)
        
        Counter.builder("account.update.total")
            .tag("status", if (success) "success" else "failure")
            .description("Total account updates")
            .register(meterRegistry)
            .increment()
    }
    
    fun recordLockWaitTime(duration: Long) {
        Timer.builder("account.lock.wait.duration")
            .description("Time spent waiting for pessimistic lock")
            .register(meterRegistry)
            .record(duration, TimeUnit.MILLISECONDS)
        
        if (duration > 1000) {
            Counter.builder("account.lock.wait.slow")
                .description("Number of slow lock acquisitions")
                .register(meterRegistry)
                .increment()
        }
    }
    
    fun recordDeadlockPrevented() {
        Counter.builder("account.deadlock.prevented")
            .description("Number of potential deadlocks prevented")
            .register(meterRegistry)
            .increment()
    }
    
    fun recordTransactionProcessed(type: String, success: Boolean) {
        Counter.builder("account.transaction.processed")
            .tag("type", type)
            .tag("success", success.toString())
            .description("Number of transactions processed")
            .register(meterRegistry)
            .increment()
    }
    
    fun recordReservation(type: String, success: Boolean) {
        Counter.builder("account.reservation")
            .tag("type", type)
            .tag("success", success.toString())
            .description("Number of reservation attempts")
            .register(meterRegistry)
            .increment()
    }
    
    fun recordCompensation(reason: String) {
        Counter.builder("account.compensation")
            .tag("reason", reason)
            .description("Number of compensation transactions")
            .register(meterRegistry)
            .increment()
    }
    
    fun recordSagaExecution(step: String, success: Boolean, duration: Long) {
        Timer.builder("account.saga.execution")
            .tag("step", step)
            .tag("success", success.toString())
            .description("Saga execution time by step")
            .register(meterRegistry)
            .record(duration, TimeUnit.MILLISECONDS)
    }
    
    fun recordAccountCreation() {
        Counter.builder("account.created")
            .description("Number of accounts created")
            .register(meterRegistry)
            .increment()
    }
    
    fun recordTradeExecution(duration: Long, success: Boolean) {
        Timer.builder("account.trade.execution")
            .tag("success", success.toString())
            .description("Trade execution processing time")
            .register(meterRegistry)
            .record(duration, TimeUnit.MILLISECONDS)
        
        if (success) {
            Counter.builder("account.trade.executed")
                .description("Number of trades successfully executed")
                .register(meterRegistry)
                .increment()
        } else {
            Counter.builder("account.trade.failed")
                .description("Number of failed trade executions")
                .register(meterRegistry)
                .increment()
        }
    }
}
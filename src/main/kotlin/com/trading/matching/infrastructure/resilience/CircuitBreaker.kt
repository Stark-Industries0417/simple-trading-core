package com.trading.matching.infrastructure.resilience

import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong



class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val resetTimeoutMs: Long = 30_000,
    private val halfOpenRequests: Int = 3
) {
    private enum class State { CLOSED, OPEN, HALF_OPEN }
    
    private var state = State.CLOSED
    private val failureCount = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0)
    private val halfOpenSuccesses = AtomicInteger(0)
    
    companion object {
        private val logger = LoggerFactory.getLogger(CircuitBreaker::class.java)
    }
    
    fun <T> execute(action: () -> T): T {
        return when (state) {
            State.OPEN -> {
                if (shouldAttemptReset()) {
                    transitionToHalfOpen()
                    tryExecute(action)
                } else {
                    throw CircuitBreakerOpenException("Circuit breaker is open")
                }
            }
            State.HALF_OPEN -> {
                tryExecute(action)
            }
            State.CLOSED -> {
                tryExecute(action)
            }
        }
    }
    
    private fun <T> tryExecute(action: () -> T): T {
        return try {
            val result = action()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure(e)
            throw e
        }
    }
    
    private fun onSuccess() {
        when (state) {
            State.HALF_OPEN -> {
                if (halfOpenSuccesses.incrementAndGet() >= halfOpenRequests) {
                    transitionToClosed()
                }
            }
            State.CLOSED -> {
                failureCount.set(0)
            }
            State.OPEN -> {}

        }
    }
    
    private fun onFailure(e: Exception) {
        when (state) {
            State.HALF_OPEN -> {
                transitionToOpen()
            }
            State.CLOSED -> {
                if (failureCount.incrementAndGet() >= failureThreshold) {
                    transitionToOpen()
                }
            }
            State.OPEN -> {}

        }
        lastFailureTime.set(System.currentTimeMillis())
    }
    
    private fun shouldAttemptReset(): Boolean {
        return System.currentTimeMillis() - lastFailureTime.get() > resetTimeoutMs
    }
    
    private fun transitionToOpen() {
        state = State.OPEN
        failureCount.set(0)
        logger.warn(
            "Circuit breaker transitioned to OPEN",
            mapOf(
                "previousState" to State.CLOSED.name,
                "failureCount" to failureThreshold,
                "resetTimeoutMs" to resetTimeoutMs
            )
        )
    }
    
    private fun transitionToHalfOpen() {
        state = State.HALF_OPEN
        halfOpenSuccesses.set(0)
        logger.info(
            "Circuit breaker transitioned to HALF_OPEN",
            mapOf(
                "previousState" to State.OPEN.name,
                "requiredSuccesses" to halfOpenRequests
            )
        )
    }
    
    private fun transitionToClosed() {
        state = State.CLOSED
        failureCount.set(0)
        halfOpenSuccesses.set(0)
        logger.info(
            "Circuit breaker transitioned to CLOSED",
            mapOf(
                "previousState" to State.HALF_OPEN.name
            )
        )
    }
    
    fun getState(): String = state.name
    fun getFailureCount(): Int = failureCount.get()
    fun isOpen(): Boolean = state == State.OPEN
    fun isClosed(): Boolean = state == State.CLOSED
    fun isHalfOpen(): Boolean = state == State.HALF_OPEN
}

class CircuitBreakerOpenException(message: String) : RuntimeException(message)
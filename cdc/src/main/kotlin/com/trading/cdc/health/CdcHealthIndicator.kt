package com.trading.cdc.health

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Component
class CdcHealthIndicator {

    @Volatile
    private var isRunning = false
    private val eventsProcessed = AtomicLong(0)
    private val lastEventTime = AtomicLong(0)
    
    fun getHealthStatus(): Map<String, Any> {
        val details = mutableMapOf<String, Any>()
        details["running"] = isRunning
        details["eventsProcessed"] = eventsProcessed.get()
        
        if (lastEventTime.get() > 0) {
            details["lastEventTime"] = lastEventTime.get()
            val timeSinceLastEvent = System.currentTimeMillis() - lastEventTime.get()
            details["timeSinceLastEventMs"] = timeSinceLastEvent
            
            if (timeSinceLastEvent > 300000) {
                details["healthy"] = false
                details["reason"] = "No events processed in the last 5 minutes"
            } else {
                details["healthy"] = true
            }
        } else {
            details["healthy"] = isRunning
            if (!isRunning) {
                details["reason"] = "CDC engine is not running"
            }
        }
        
        return details
    }
    
    fun markRunning() {
        isRunning = true
    }
    
    fun markStopped() {
        isRunning = false
    }
    
    fun incrementEventsProcessed() {
        eventsProcessed.incrementAndGet()
        lastEventTime.set(System.currentTimeMillis())
    }
}
package com.trading.cdc.health

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CdcHealthIndicatorTest {
    
    private lateinit var healthIndicator: CdcHealthIndicator
    
    @BeforeEach
    fun setUp() {
        healthIndicator = CdcHealthIndicator()
    }
    
    @Test
    fun `초기 상태는 실행 중이 아니다`() {
        val health = healthIndicator.getHealthStatus()
        assertEquals(false, health["healthy"])
        assertEquals(false, health["running"])
        assertEquals(0L, health["eventsProcessed"])
        assertEquals("CDC engine is not running", health["reason"])
    }
    
    @Test
    fun `이벤트 처리시 상태 표시기가 업데이트된다`() {
        healthIndicator.markRunning()
        healthIndicator.incrementEventsProcessed()
        val health = healthIndicator.getHealthStatus()
        assertEquals(true, health["healthy"])
        assertEquals(true, health["running"])
        assertEquals(1L, health["eventsProcessed"])
        assertNotNull(health["lastEventTime"])
    }
    
    @Test
    fun `실행 중지시 상태가 비정상으로 표시된다`() {
        healthIndicator.markRunning()
        healthIndicator.incrementEventsProcessed()
        healthIndicator.markStopped()
        val health = healthIndicator.getHealthStatus()
        assertEquals(false, health["running"])
        assertNotNull(health["eventsProcessed"])
    }
    
    @Test
    fun `여러 이벤트 처리가 가능하다`() {
        healthIndicator.markRunning()
        
        repeat(5) {
            healthIndicator.incrementEventsProcessed()
        }
        val health = healthIndicator.getHealthStatus()
        assertEquals(true, health["healthy"])
        assertEquals(5L, health["eventsProcessed"])
    }
    
    @Test
    fun `마지막 이벤트 시간을 추적한다`() {
        healthIndicator.markRunning()
        val timeBefore = System.currentTimeMillis()
        
        healthIndicator.incrementEventsProcessed()
        val timeAfter = System.currentTimeMillis()
        val health = healthIndicator.getHealthStatus()
        val lastEventTime = health["lastEventTime"] as Long
        assertTrue(lastEventTime >= timeBefore)
        assertTrue(lastEventTime <= timeAfter)
    }
    
    @Test
    fun `마지막 이벤트 이후 경과 시간을 추적한다`() {
        healthIndicator.markRunning()
        healthIndicator.incrementEventsProcessed()
        
        Thread.sleep(100)
        val health = healthIndicator.getHealthStatus()
        val timeSinceLastEvent = health["timeSinceLastEventMs"] as Long
        assertTrue(timeSinceLastEvent >= 100)
        assertTrue(timeSinceLastEvent < 200)
    }
}
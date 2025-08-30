package com.trading

import com.trading.account.application.AlertService
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class TestMockConfig {
    
    @Bean
    @Primary
    fun testAlertService(): AlertService {
        return mockk(relaxed = true)
    }
}
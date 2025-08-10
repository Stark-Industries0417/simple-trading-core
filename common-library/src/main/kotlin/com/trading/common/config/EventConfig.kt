package com.trading.common.config
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.ApplicationEventMulticaster
import org.springframework.context.event.SimpleApplicationEventMulticaster
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
@Configuration
@EnableAsync
class EventConfig {

    @Bean
    fun applicationEventMulticaster(): ApplicationEventMulticaster =
        SimpleApplicationEventMulticaster().apply {
            this.setTaskExecutor { eventTaskExecutor() }
        }
    }

    @Bean("eventTaskExecutor")
    fun eventTaskExecutor(): Executor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 4
            queueCapacity = 100
            threadNamePrefix = "event-"
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(60)
            initialize()
        }

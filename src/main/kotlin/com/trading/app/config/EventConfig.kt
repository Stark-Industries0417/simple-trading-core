package com.trading.app.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.ApplicationEventMulticaster
import org.springframework.context.event.SimpleApplicationEventMulticaster
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
@EnableAsync
class EventConfig(
    private val tradingProperties: TradingProperties
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun applicationEventMulticaster(): ApplicationEventMulticaster {
        val multicaster = SimpleApplicationEventMulticaster()
        multicaster.setTaskExecutor { eventTaskExecutor() }

        multicaster.setErrorHandler { throwable ->
            logger.error("Event processing failed", throwable)
        }

        logger.info(
            "Event multicaster initialized | " +
                    "async=${tradingProperties.event.async.enabled}"
        )

        return multicaster
    }

    @Bean("eventTaskExecutor")
    fun eventTaskExecutor(): Executor {
        val asyncConfig = tradingProperties.event.async

        return ThreadPoolTaskExecutor().apply {
            corePoolSize = asyncConfig.corePoolSize
            maxPoolSize = asyncConfig.maxPoolSize
            queueCapacity = 100
            setThreadNamePrefix("event-")
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(60)
            initialize()

            logger.info(
                "Event executor configured | " +
                        "core=${corePoolSize} | " +
                        "max=${maxPoolSize}"
            )
        }
    }
}
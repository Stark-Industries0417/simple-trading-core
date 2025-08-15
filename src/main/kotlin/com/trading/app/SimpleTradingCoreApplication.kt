package com.trading

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
class SimpleTradingCoreApplication

fun main(args: Array<String>) {
    runApplication<SimpleTradingCoreApplication>(*args)
}
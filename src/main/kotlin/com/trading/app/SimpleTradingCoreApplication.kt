package com.trading

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
@EnableJpaRepositories(basePackages = ["com.trading"])
@EntityScan(basePackages = ["com.trading"])
class SimpleTradingCoreApplication

fun main(args: Array<String>) {
    runApplication<SimpleTradingCoreApplication>(*args)
}
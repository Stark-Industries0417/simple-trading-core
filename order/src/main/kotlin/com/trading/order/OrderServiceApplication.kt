package com.trading.order

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication


@SpringBootApplication(
    scanBasePackages = [
        "com.trading.order",
        "com.trading.common"
    ]
)
class OrderServiceApplication

fun main(args: Array<String>) {
    runApplication<OrderServiceApplication>(*args)
}
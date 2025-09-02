package com.trading.account

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication



@SpringBootApplication(
    scanBasePackages = [
        "com.trading.account",
        "com.trading.common"
    ]
)
class AccountServiceApplication

fun main(args: Array<String>) {
    runApplication<AccountServiceApplication>(*args)
}
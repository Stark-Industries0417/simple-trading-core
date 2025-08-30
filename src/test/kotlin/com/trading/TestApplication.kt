package com.trading

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@ComponentScan(basePackages = ["com.trading"])
@EnableJpaRepositories(basePackages = ["com.trading"])
@EntityScan(basePackages = ["com.trading"])
@EnableAsync
class TestApplication
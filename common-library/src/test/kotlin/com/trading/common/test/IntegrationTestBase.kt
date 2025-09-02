package com.trading.common.test

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
abstract class IntegrationTestBase {
    @Autowired
    protected lateinit var testEventPublisher: TestEventPublisher
    @BeforeEach
    fun setUpBase() {
        testEventPublisher.clearPublishedEvents()
        setUp()
    }

    protected open fun setUp() {
    }
}

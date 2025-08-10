package com.trading.common.test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@AutoConfigureRestDocs(
    outputDir = "build/generated-snippets",
    uriScheme = "https",
    uriHost = "api.simple-trading.com"
)
@ExtendWith(RestDocumentationExtension::class)
@ActiveProfiles("test")
@Transactional
abstract class RestDocsTestBase {
    @Autowired
    protected lateinit var mockMvc: MockMvc
    @Autowired
    protected lateinit var webApplicationContext: WebApplicationContext
    @Autowired
    protected lateinit var testEventPublisher: TestEventPublisher
    @BeforeEach
    fun setUpRestDocs(restDocumentation: RestDocumentationContextProvider) {
        testEventPublisher.clearPublishedEvents()
        setUpRestDocsTest()
    }

    protected open fun setUpRestDocsTest() {
    }
}


plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // 내부 모듈 의존성
    implementation(project(":common-library"))

    // Spring Boot Core (Web, Validation, Actuator는 루트로 이동)
    implementation("org.springframework.boot:spring-boot-starter")
    
    // Guava (for ThreadFactoryBuilder)
    implementation("com.google.guava:guava:32.1.2-jre")
    
    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    
    // Configuration Processor (선택적)
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    
    // Testing (spring-boot-starter-test, mockk는 루트로 이동)
    testImplementation("io.kotest:kotest-runner-junit5:5.7.2")
    testImplementation("io.kotest:kotest-assertions-core:5.7.2")
    testImplementation("io.kotest.extensions:kotest-extensions-spring:1.1.3")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// 이 모듈은 독립 실행 가능한 Spring Boot 애플리케이션
tasks.bootJar {
    enabled = true
}

tasks.jar {
    enabled = true
}
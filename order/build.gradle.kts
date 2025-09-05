import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot")
}

dependencies {
    // 내부 모듈 의존성
    implementation(project(":common-library"))

    // Spring Boot Data (Web, Validation, Actuator는 루트로 이동)
    implementation(Dependencies.springBootStarterDataJpa)
    
    // Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // DB
    runtimeOnly(Dependencies.h2Database)
    runtimeOnly(Dependencies.mysqlConnector)

    // 테스트
    testImplementation(Dependencies.testcontainersMysql)
    testImplementation(Dependencies.testcontainersJupiter)
}

tasks.jar {
    enabled = true
}

tasks.withType<BootJar> {
    enabled = true
    archiveBaseName.set("order-service")
    archiveVersion.set("1.0.0")
    mainClass.set("com.trading.order.OrderServiceApplicationKt")
}
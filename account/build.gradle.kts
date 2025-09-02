import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":common-library"))

    // Spring Boot Data (Web, Actuator는 루트로 이동)
    implementation(Dependencies.springBootStarterDataJpa)

    // DB
    runtimeOnly(Dependencies.h2Database)
    runtimeOnly(Dependencies.mysqlConnector)

    // 테스트
    testImplementation(Dependencies.testcontainersMysql)
}

tasks.jar {
    enabled = true
}

tasks.withType<BootJar> {
    enabled = true
    archiveBaseName.set("account-service")
    archiveVersion.set("1.0.0")
    mainClass.set("com.trading.account.AccountServiceApplicationKt")
}
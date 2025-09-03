import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":common-library"))

    implementation(Dependencies.springBootStarterDataJpa)
    
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.apache.kafka:kafka-clients:3.8.0")

    runtimeOnly(Dependencies.h2Database)
    runtimeOnly(Dependencies.mysqlConnector)

    testImplementation(Dependencies.testcontainersMysql)
    testImplementation("org.springframework.kafka:spring-kafka-test")
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
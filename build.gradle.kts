import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.spring") version "2.0.0"
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
}

group = "com.trading"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Database
    runtimeOnly("com.h2database:h2")
    runtimeOnly("mysql:mysql-connector-java:8.0.33")

    // Configuration
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Logging
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    
    // UUID v7 Generation
    implementation("com.github.f4b6a3:uuid-creator:5.3.2")
    
    // Guava for ThreadFactoryBuilder
    implementation("com.google.guava:guava:32.1.3-jre")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.assertj:assertj-core")
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

springBoot {
    mainClass.set("com.trading.SimpleTradingCoreApplicationKt")
}

tasks.bootJar {
    enabled = true
    archiveBaseName.set("simple-trading-core")
}
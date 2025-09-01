import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    kotlin("jvm") version Versions.kotlin
    kotlin("plugin.spring") version Versions.kotlin
    kotlin("plugin.jpa") version Versions.kotlin
    id("org.springframework.boot") version Versions.springBoot
    id("io.spring.dependency-management") version Versions.springDependencyManagement
}

allprojects {
    group = "com.trading"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

// 서브프로젝트 공통 설정
subprojects {
    apply(plugin = "kotlin")
    apply(plugin = "io.spring.dependency-management")
    
    // Spring Boot BOM 적용
    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom(SpringBootPlugin.BOM_COORDINATES)
            mavenBom("org.testcontainers:testcontainers-bom:${Versions.testcontainers}")
        }
    }
    
    // Kotlin 설정
    configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(17)
    }
    
    // 테스트 설정
    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
    
    // 공통 의존성
    dependencies {
        val implementation by configurations
        val testImplementation by configurations
        
        // Kotlin
        implementation(Dependencies.kotlinReflect)
        implementation(Dependencies.kotlinStdlib)
        
        // 테스트
        testImplementation(Dependencies.springBootStarterTest)
        testImplementation(Dependencies.mockk)
        testImplementation(Dependencies.assertjCore)
    }
}

// Spring Boot BOM 적용 (루트 프로젝트에도 필요)
the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
    imports {
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
        mavenBom("org.testcontainers:testcontainers-bom:${Versions.testcontainers}")
    }
}

dependencies {
    // Module dependencies
    implementation(project(":common-library"))
    
    // Spring Boot Starters
    implementation(Dependencies.springBootStarterWeb)
    implementation(Dependencies.springBootStarterDataJpa)
    implementation(Dependencies.springBootStarterValidation)
    implementation(Dependencies.springBootStarterActuator)
    implementation(Dependencies.springBootStarterAop)
    implementation(Dependencies.springBootStarterSecurity)

    // Kotlin
    implementation(Dependencies.jacksonModuleKotlin)

    // Database
    runtimeOnly(Dependencies.h2Database)
    runtimeOnly(Dependencies.mysqlConnector)

    // Configuration
    annotationProcessor(Dependencies.springBootConfigurationProcessor)

    // Logging
    implementation(Dependencies.logstashLogbackEncoder)

    // UUID v7 Generation
    implementation(Dependencies.uuidCreator)

    // Guava for ThreadFactoryBuilder
    implementation(Dependencies.guava)

    // ===== Test Dependencies =====
    testImplementation(Dependencies.springBootStarterTest) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }

    // TestContainers
    testImplementation(Dependencies.testcontainers)
    testImplementation(Dependencies.testcontainersJupiter)
    testImplementation(Dependencies.testcontainersMysql)

    // Kotlin Test Support
    testImplementation(Dependencies.springMockk)
}

// Root 프로젝트에도 Kotlin 설정
configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
    jvmToolchain(17)
}

// Root 프로젝트 테스트 설정
tasks.withType<Test> {
    useJUnitPlatform()

    // TestContainers 관련 설정
    environment("TESTCONTAINERS_REUSE_ENABLE", "true")
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

configure<org.springframework.boot.gradle.dsl.SpringBootExtension> {
    mainClass.set("com.trading.SimpleTradingCoreApplicationKt")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = true
    archiveBaseName.set("simple-trading-core")
}
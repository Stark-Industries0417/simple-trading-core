import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    kotlin("jvm") version Versions.kotlin apply false
    kotlin("plugin.spring") version Versions.kotlin apply false
    kotlin("plugin.jpa") version Versions.kotlin apply false
    id("org.springframework.boot") version Versions.springBoot apply false
    id("io.spring.dependency-management") version Versions.springDependencyManagement apply false
}

allprojects {
    group = "com.trading"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "kotlin")
    apply(plugin = "io.spring.dependency-management")
    
    // Spring Boot BOM 적용
    the<DependencyManagementExtension>().apply {
        imports {
            mavenBom(SpringBootPlugin.BOM_COORDINATES)
            mavenBom("org.testcontainers:testcontainers-bom:${Versions.testcontainers}")
        }
    }
    
    configure<KotlinJvmProjectExtension> {
        jvmToolchain(17)
    }
    
    // 테스트 설정
    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
    
    // 공통 의존성
    dependencies {
        val implementation by configurations
        val testImplementation by configurations
        
        // Kotlin
        implementation(Dependencies.kotlinReflect)
        implementation(Dependencies.kotlinStdlib)
        
        // Jackson - 모든 모듈에서 사용
        implementation(Dependencies.jacksonModuleKotlin)
        
        // Spring Boot Core - 대부분 모듈에서 사용
        implementation(Dependencies.springBootStarterWeb)
        implementation(Dependencies.springBootStarterValidation)
        implementation(Dependencies.springBootStarterActuator)
        
        // Metrics - 여러 모듈에서 사용
        implementation("io.micrometer:micrometer-core")
        
        // 테스트
        testImplementation(Dependencies.springBootStarterTest) {
            exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        }
        testImplementation(Dependencies.mockk)
        testImplementation(Dependencies.assertjCore)
    }
}

tasks.register("build") {
    dependsOn(subprojects.map { it.tasks.named("build") })
}

tasks.register("clean") {
    dependsOn(subprojects.map { it.tasks.named("clean") })
}
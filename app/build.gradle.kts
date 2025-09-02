plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // 내부 모듈 의존성
    implementation(project(":common-library"))
    implementation(project(":market-data-generator"))
    implementation(project(":order"))
    implementation(project(":matching"))
    implementation(project(":account"))
    
    // Spring Boot Data (Web, Validation, Actuator는 루트로 이동)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    
    // Database
    runtimeOnly("com.h2database:h2")
}

tasks.bootJar {
    enabled = true
    archiveFileName.set("simple-trading-core.jar")
}

tasks.jar {
    enabled = false
}
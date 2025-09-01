plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
}

dependencies {
    // Spring Boot Core (Event, Validation만)
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    
    // JPA Annotations (구현체 X)
    compileOnly("jakarta.persistence:jakarta.persistence-api")
    
    // Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    
    // UUID v7
    implementation("com.github.f4b6a3:uuid-creator:${Versions.uuidCreator}")
    
    // Logging
    implementation("net.logstash.logback:logstash-logback-encoder:${Versions.logstashEncoder}")
    
    // Metrics
    implementation("io.micrometer:micrometer-core")
}

// 다른 모듈에서 사용할 수 있도록 JAR 생성
tasks.jar {
    enabled = true
}

// common-library는 라이브러리 모듈이므로 bootJar 생성하지 않음
tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    enabled = false
}
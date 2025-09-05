import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
}

dependencies {
    // Spring Context for ApplicationEventPublisher only
    compileOnly("org.springframework:spring-context")
    
    // Jackson for StructuredLogger
    compileOnly("com.fasterxml.jackson.core:jackson-databind")
    
    // SLF4J for logging
    implementation("org.slf4j:slf4j-api")
    
    // JPA Annotations (구현체 X)
    compileOnly("jakarta.persistence:jakarta.persistence-api")
    
    // Spring Data JPA for Repository interfaces
    compileOnly("org.springframework.data:spring-data-jpa")
    
    // UUID v7
    implementation(Dependencies.uuidCreator)
    
    // Logging
    implementation(Dependencies.logstashLogbackEncoder)
    
    // Test Dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework:spring-tx")
}

tasks.withType<BootJar> {
    enabled = false
}

tasks.jar {
    enabled = true
}
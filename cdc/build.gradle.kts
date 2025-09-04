plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":common-library"))
    
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.kafka:spring-kafka")
    
    // Debezium Embedded Engine
    implementation("io.debezium:debezium-embedded:2.4.0.Final")
    implementation("io.debezium:debezium-connector-mysql:2.4.0.Final")
    implementation("io.debezium:debezium-core:2.4.0.Final")
    
    // Database
    implementation("mysql:mysql-connector-java:8.0.33")
    
    // JSON Processing
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    
    // Logging
    implementation("net.logstash.logback:logstash-logback-encoder:7.3")
    
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.testcontainers:mysql")
}

tasks.bootJar {
    enabled = true
    archiveBaseName.set("cdc")
}

tasks.jar {
    enabled = true
}
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":common-library"))

    // Guava for ThreadFactory
    implementation(Dependencies.guava)
    
    // Kafka
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.apache.kafka:kafka-clients")
    implementation("org.apache.kafka:kafka-streams")
    
    // JSON Processing
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}

tasks.jar {
    enabled = true
}

tasks.withType<BootJar> {
    enabled = true
    archiveBaseName.set("matching-engine-service")
    archiveVersion.set("1.0.0")
    mainClass.set("com.trading.matching.MatchingEngineApplicationKt")
}
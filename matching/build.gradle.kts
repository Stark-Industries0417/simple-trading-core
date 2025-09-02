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
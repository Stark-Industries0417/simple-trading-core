import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.spring") version "2.0.0" apply false
    id("org.springframework.boot") version "3.2.0" apply false
    id("io.spring.dependency-management") version "1.1.4"
    id("org.asciidoctor.jvm.convert") version "3.3.2" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.4" apply false
    id("org.owasp.dependencycheck") version "8.4.0" apply false
}

group = "sk.moon"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.owasp.dependencycheck")
    apply(plugin = "jacoco")

    dependencyManagement {
        imports {
            mavenBom(SpringBootPlugin.BOM_COORDINATES)
        }
    }

    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
        
        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("io.mockk:mockk:1.13.8")
        testImplementation("org.assertj:assertj-core")
        testImplementation("org.junit.jupiter:junit-jupiter")
        testImplementation("org.testcontainers:junit-jupiter")
        testImplementation("org.testcontainers:postgresql")
        testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
        testImplementation("org.springframework.restdocs:spring-restdocs-asciidoctor")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    kotlin {
        jvmToolchain(17)
    }

    // Spring REST Docs 설정
    val snippetsDir by extra { file("build/generated-snippets") }

    // 모든 프로젝트에 Asciidoctor 설정 적용
    configure(subprojects.filter { it.name in listOf("order-module", "matching-module", "account-module") }) {
        apply(plugin = "org.asciidoctor.jvm.convert")

        tasks.withType<org.asciidoctor.gradle.jvm.AsciidoctorTask> {
            inputs.dir(snippetsDir)
            dependsOn(tasks.test)
        }
    }

    tasks.withType<Test> {
        outputs.dir(snippetsDir)
    }
}
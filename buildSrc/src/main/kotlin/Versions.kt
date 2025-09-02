object Versions {
    const val kotlin = "2.0.0"
    const val springBoot = "3.2.0"
    const val springDependencyManagement = "1.1.4"
    
    const val uuidCreator = "5.3.2"
    const val guava = "32.1.3-jre"
    const val logstashEncoder = "7.4"
    const val testcontainers = "1.19.3"
    const val mockk = "1.13.8"
    const val mysqlConnector = "8.0.33"
    const val springMockk = "4.0.2"
}

object Dependencies {
    const val kotlinReflect = "org.jetbrains.kotlin:kotlin-reflect"
    const val kotlinStdlib = "org.jetbrains.kotlin:kotlin-stdlib-jdk8"
    
    const val springBootStarterWeb = "org.springframework.boot:spring-boot-starter-web"
    const val springBootStarterDataJpa = "org.springframework.boot:spring-boot-starter-data-jpa"
    const val springBootStarterValidation = "org.springframework.boot:spring-boot-starter-validation"
    const val springBootStarterActuator = "org.springframework.boot:spring-boot-starter-actuator"
    const val springBootStarterAop = "org.springframework.boot:spring-boot-starter-aop"
    const val springBootStarterSecurity = "org.springframework.boot:spring-boot-starter-security"
    const val springBootStarterTest = "org.springframework.boot:spring-boot-starter-test"
    const val springBootConfigurationProcessor = "org.springframework.boot:spring-boot-configuration-processor"
    
    const val h2Database = "com.h2database:h2"
    const val mysqlConnector = "mysql:mysql-connector-java:${Versions.mysqlConnector}"
    
    const val jacksonModuleKotlin = "com.fasterxml.jackson.module:jackson-module-kotlin"
    const val logstashLogbackEncoder = "net.logstash.logback:logstash-logback-encoder:${Versions.logstashEncoder}"
    const val uuidCreator = "com.github.f4b6a3:uuid-creator:${Versions.uuidCreator}"
    const val guava = "com.google.guava:guava:${Versions.guava}"
    
    const val testcontainers = "org.testcontainers:testcontainers"
    const val testcontainersJupiter = "org.testcontainers:junit-jupiter"
    const val testcontainersMysql = "org.testcontainers:mysql"
    const val mockk = "io.mockk:mockk:${Versions.mockk}"
    const val assertjCore = "org.assertj:assertj-core"
    const val springMockk = "com.ninja-squad:springmockk:${Versions.springMockk}"
}
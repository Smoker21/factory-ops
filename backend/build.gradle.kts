import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.allopen") version "2.0.21"
    id("io.quarkus") version "3.17.4"
    jacoco
}

group = "com.factoryops"
version = "1.0.0-SNAPSHOT"

val quarkusPlatformVersion = "3.17.4"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:${quarkusPlatformVersion}"))

    // Core Quarkus
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")

    // MongoDB (synchronous Panache for simpler code path)
    implementation("io.quarkus:quarkus-mongodb-panache-kotlin")

    // Security / JWT
    implementation("io.quarkus:quarkus-smallrye-jwt")
    implementation("io.quarkus:quarkus-smallrye-jwt-build")

    // Validation
    implementation("io.quarkus:quarkus-hibernate-validator")

    // OpenAPI / Swagger
    implementation("io.quarkus:quarkus-smallrye-openapi")

    // Health
    implementation("io.quarkus:quarkus-smallrye-health")

    // Scheduler (outbox poller)
    implementation("io.quarkus:quarkus-scheduler")

    // REST Client (for HR mock)
    implementation("io.quarkus:quarkus-rest-client-jackson")

    // Logging
    implementation("io.quarkus:quarkus-logging-json")

    // Kotlin stdlib
    implementation(kotlin("stdlib"))
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    // Password hashing (bcrypt via Spring Security Crypto - no Spring context needed)
    implementation("at.favre.lib:bcrypt:0.10.2")

    // ULID generation for event IDs
    implementation("com.github.f4b6a3:ulid-creator:5.2.3")

    // Jackson Kotlin module (auto-registered by quarkus-rest-jackson but explicit for safety)
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Test
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.rest-assured:kotlin-extensions")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

allOpen {
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.enterprise.context.RequestScoped")
    annotation("io.quarkus.test.junit.QuarkusTest")
    annotation("jakarta.inject.Singleton")
    annotation("io.quarkus.mongodb.panache.common.MongoEntity")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs = listOf("-Xjvm-default=all")
    }
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    // Exclude generated/framework code from coverage
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/FactoryOpsApplication*",
                    "**/*_ClientProxy*",
                    "**/*_Bean*",
                    "**/*ScheduledInvoker*",
                    "**/quarkus-generated*"
                )
            }
        })
    )
}

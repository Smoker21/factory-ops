plugins {
    kotlin("jvm") version "1.9.23"
}

group = "com.factoryops"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
}

kotlin {
    jvmToolchain(21)
}

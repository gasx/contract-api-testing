import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.JavaExec

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}

group = "ru.course.apitesting"
version = "2.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.ktor:ktor-server-core-jvm:2.3.12")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.12")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.12")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.2")
    implementation("org.apache.commons:commons-text:1.12.0")
    implementation("org.apache.kafka:kafka-clients:4.3.1")

    // чтобы не было предупреждений SLF4J NOP
    runtimeOnly("org.slf4j:slf4j-simple:2.0.13")
}

// Переносимость: без toolchain, байткод 1.8 (работает на любой JDK 8+)
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_1_8)
}

application {
    mainClass.set("ru.course.apitesting.MainKt")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}

// Не валим сборку из‑за exit code и всегда печатаем stdout/stderr
tasks.withType<JavaExec>().configureEach {
    isIgnoreExitValue = true
    standardOutput = System.out
    errorOutput = System.err
}

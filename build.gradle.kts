plugins {
    kotlin("jvm") version "2.2.21"
    id("org.jetbrains.dokka") version "2.1.0"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("io.ktor:ktor-network:2.3.7")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

application {
    // Default main class
    mainClass.set("org.example.CoroutineServerKt")
}

// Create separate run tasks
tasks.register<JavaExec>("runCoroutines") {
    group = "application"
    description = "Run the coroutines server"
    mainClass.set("org.example.CoroutineServerKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("runVirtualThreads") {
    group = "application"
    description = "Run the original virtual threads server (port 8000)"
    mainClass.set("org.example.VirtualThreadServerOriginalKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("runVirtualThreadsNIO") {
    group = "application"
    description = "Run the virtual threads server with NIO optimization (port 8001)"
    mainClass.set("org.example.VirtualThreadServerKt")
    classpath = sourceSets["main"].runtimeClasspath
}

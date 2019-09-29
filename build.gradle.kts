import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.0"
    application
}

group = "co.edu.uniandes"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    compile(kotlin("stdlib-jdk8"))
}

application {
    mainClassName = "Runner"
}

val run by tasks.getting(JavaExec::class) {
    standardInput = System.`in`
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version Versions.kotlinVs
    kotlin("plugin.serialization") version Versions.kotlinVs apply false
    kotlin("plugin.spring") version Versions.kotlinVs apply false
    id("com.google.protobuf") version Versions.protobufGradlePlugin apply false
}

allprojects {

    group = "metaticket"
    version = "3.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            //freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget.set(JvmTarget.JVM_18)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

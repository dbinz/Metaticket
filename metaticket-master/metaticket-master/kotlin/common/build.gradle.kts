import Script.creatingArtifactsConf
import Script.exportSourceSet

plugins {
    id("java")
    kotlin("jvm")
}

version = "1.0.0"

sourceSets {
    val logger by creating
    val coroutine by creating
    val redis by creating
    val lang by creating
    val mongodb by creating
    val checksum by creating
    val utils by creating
}

val coroutine by creatingArtifactsConf("coroutine")
val logger by creatingArtifactsConf("logger")
val redis by creatingArtifactsConf("redis")
val checksum by creatingArtifactsConf("checksum")
val utils by creatingArtifactsConf("utils")

exportSourceSet(checksum.name)
exportSourceSet(coroutine.name)
exportSourceSet(logger.name)
exportSourceSet(redis.name)
exportSourceSet(utils.name)

dependencies {
    // dependencies codec

    "mongodbImplementation"("org.mongodb:bson:${Versions.mongoDriverVS}")

    "coroutineImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutineVs}")

    "redisImplementation"("io.lettuce:lettuce-core:${Versions.lettuceCoreVs}")
    "redisImplementation"("com.fasterxml.jackson.module:jackson-module-kotlin:${Versions.jacksonVs}")
    "redisImplementation"(project(":common", "logger"))

    "langImplementation"("org.mongodb:bson:${Versions.mongoDriverVS}")

    // dependencies for logback
    "loggerImplementation"("org.slf4j:slf4j-api:2.0.7")
    "loggerImplementation"("ch.qos.logback:logback-classic:${Versions.logbackVs}")
    "loggerImplementation"("ch.qos.logback:logback-core:${Versions.logbackVs}")
}


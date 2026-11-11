import Script.creatingArtifactsConf
import Script.exportSourceSet
import Script.gettingArtifactsConf

plugins {
    kotlin("plugin.serialization")
    kotlin("jvm")
    id("application")
    id("com.google.protobuf")
}

version = "3.1.0"

sourceSets {
    val api by creating
    val grpc by creating
    main {
        resources {
            srcDirs(files("conf"))
        }
    }
}

val api by gettingArtifactsConf("api")
val grpc by creatingArtifactsConf("grpc")
exportSourceSet(api.name)
exportSourceSet(grpc.name)

application {
    mainClass.set("metaticket.order.Launcher")
}

dependencies {

    "apiImplementation"(project(":common", "logger"))
    implementation(project(":common", "coroutine"))
    implementation(project(":common", "checksum"))
    implementation(project(":service.order", "api"))
    implementation(project(":service.order", "grpc"))
    implementation(project(":service.payment", "api"))
    implementation(project(":service.payment", "grpc"))
    implementation(project(":service.order.datacollector", "api"))
    implementation(project(":service.order.datacollector", "grpc"))
    "apiImplementation"(project(":service.ticket", "api"))
    implementation(project(":service.ticket", "grpc"))
    implementation(project(":service.show", "api"))
    implementation(project(":service.show", "grpc"))
    implementation(project(":service.jobrunr", "api"))
    implementation(project(":service.jobrunr", "grpc"))
    implementation(project(":service.notification", "api"))
    implementation(project(":service.notification", "grpc"))

    "apiImplementation"("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.jacksonVs}")

    implementation("com.google.guava:guava:${Versions.guavaVs}")

    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    implementation("io.insert-koin:koin-core:${Versions.koinVs}")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutineVs}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:${Versions.coroutineVs}")

    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:${Versions.mongoDriverVS}")
    implementation("org.mongodb:bson:${Versions.mongoDriverVS}")
    implementation("org.mongodb:bson-kotlinx:${Versions.mongoDriverVS}")

    implementation("org.apache.kafka:kafka-clients:4.0.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.5.1")

    // --- GENERATE PROTO
    implementation("com.hubspot.jackson:jackson-datatype-protobuf:${Versions.jacksonProtobufVs}")
    "grpcImplementation"("io.grpc:grpc-protobuf:${Versions.grpcVs}")
    "grpcImplementation"("io.grpc:grpc-kotlin-stub:${Versions.grpcKotlinVs}")
    "grpcImplementation"("com.google.protobuf:protobuf-kotlin:${Versions.protobufVs}")
    "grpcImplementation"("io.grpc:protoc-gen-grpc-kotlin:${Versions.grpcKotlinVs}")
    "grpcImplementation"("com.google.protobuf:protobuf-java:${Versions.protobufVs}")
    "grpcImplementation"("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")

    // GRPC server
    implementation("io.grpc:grpc-netty-shaded:${Versions.grpcVs}")
    implementation("io.grpc:grpc-stub:${Versions.grpcVs}")

    // --- KTOR Server
    implementation("io.ktor:ktor-server-request-validation:${Versions.requestValidationVs}")
    implementation("io.ktor:ktor-server-core:${Versions.ktorVs}")
    implementation("io.ktor:ktor-server-content-negotiation:${Versions.ktorVs}")
    implementation("io.ktor:ktor-serialization-jackson:${Versions.ktorVs}")
    implementation("io.ktor:ktor-server-netty:${Versions.ktorVs}")
    implementation("io.ktor:ktor-server-cors:${Versions.ktorVs}")
    implementation("io.ktor:ktor-server-status-pages:${Versions.ktorVs}")
    implementation("io.ktor:ktor-server-jvm:${Versions.ktorVs}")

    // --- KTOR Client
    implementation("io.ktor:ktor-client-core:${Versions.ktorVs}")
    implementation("io.ktor:ktor-client-cio:${Versions.ktorVs}")
    implementation("io.ktor:ktor-client-content-negotiation:${Versions.ktorVs}")

    // --- Test
    testImplementation("org.jetbrains.kotlin:kotlin-test:${Versions.kotlinVs}")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:${Versions.kotlinVs}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutineVs}")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:${Versions.jacksonVs}")
    testImplementation("io.ktor:ktor-server-test-host:${Versions.ktorVs}")
    testImplementation("io.ktor:ktor-server-content-negotiation:${Versions.ktorVs}")
    testImplementation("io.ktor:ktor-serialization-jackson:${Versions.ktorVs}")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${Versions.protobufVs}"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${Versions.grpcVs}"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:${Versions.grpcKotlinVs}:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
//            task.builtins {
//                create("kotlin")
//            }
            task.plugins {
                create("grpc") {
                    option("generate_kotlin")
                }
                create("grpckt")
//                create("kotlin")
            }
        }
    }
}

tasks.getByName("build") {
    dependsOn("generateProto")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

plugins {
	id("application")
	kotlin("jvm")
}

version = "3.0.0"

sourceSets {
	main {
		resources {
			if (gradle.startParameter.taskNames.contains("${project.path}:run")) {
				srcDirs(files("conf"))
			}
		}
	}
}

application {
	mainClass.set("metaticket.editor.seatplanner.Launcher")
}

dependencies {

	implementation(project(":common", "logger"))

	implementation("io.insert-koin:koin-core:${Versions.koinVs}")

	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutineVs}")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:${Versions.coroutineVs}")

	implementation("org.mongodb:mongodb-driver-kotlin-coroutine:${Versions.mongoDriverVS}")
	implementation("org.mongodb:bson:${Versions.mongoDriverVS}")
	implementation("org.mongodb:bson-kotlinx:${Versions.mongoDriverVS}")


	implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.5.1")

	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.14.2")

	implementation("com.google.protobuf:protobuf-java:${Versions.protobufVs}")

	// --- KTOR Server
	implementation("io.ktor:ktor-server-core:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-content-negotiation:${Versions.ktorVs}")
	implementation("io.ktor:ktor-serialization-jackson:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-netty:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-cors:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-status-pages:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-jvm:${Versions.ktorVs}")
}


plugins {
	id("application")
	kotlin("jvm")
}

version = "1.1.0"

sourceSets {
	main {
		resources {
			srcDirs(files("conf"))
		}
	}
}

application {
	mainClass.set("metaticket.editor.policydesc.Launcher")
}

dependencies {

	implementation(project(":common", "logger"))

	implementation("io.insert-koin:koin-core:${Versions.koinVs}")

	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutineVs}")

	implementation("org.mongodb:mongodb-driver-kotlin-coroutine:${Versions.mongoDriverVS}")
	implementation("org.mongodb:bson:${Versions.mongoDriverVS}")
	implementation("org.mongodb:bson-kotlinx:${Versions.mongoDriverVS}")

	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.14.2")

	// --- KTOR Server
	implementation("io.ktor:ktor-server-core:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-content-negotiation:${Versions.ktorVs}")
	implementation("io.ktor:ktor-serialization-jackson:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-netty:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-cors:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-status-pages:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-jvm:${Versions.ktorVs}")
}

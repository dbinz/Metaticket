import Script.exportSourceSet
import Script.gettingArtifactsConf

plugins {
	id("application")
	kotlin("jvm")
}

version = "1.0.0"

repositories {
	maven {
		url = uri("https://jitpack.io")
	}
}

sourceSets {
	val api by creating
	main {
		resources {
			srcDirs(files("conf"))
		}
	}
}

val api by gettingArtifactsConf("api")
exportSourceSet("api")


application {
	mainClass.set("metaticket.authorization.Launcher")
}

dependencies {

	implementation(project(":common", "logger"))
	implementation(project(":service.authorization", "api"))

	implementation("org.casbin:jcasbin:${Versions.casbinVs}")
	implementation("com.github.zzl221000:jcasbin-mongo-adapter:v1.0")

	implementation("io.insert-koin:koin-core:${Versions.koinVs}")

	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutineVs}")

	implementation("org.mongodb:mongodb-driver-sync:${Versions.mongoDriverVS}")
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

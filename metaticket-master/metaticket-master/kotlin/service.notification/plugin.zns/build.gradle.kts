plugins {
	kotlin("plugin.serialization")
	kotlin("jvm")
}

version = "1.0.0"

dependencies {
	compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")
	// --- KTOR Client
	compileOnly("io.ktor:ktor-client-core:${Versions.ktorVs}")
	compileOnly("io.ktor:ktor-client-cio:${Versions.ktorVs}")
	compileOnly("io.ktor:ktor-client-content-negotiation:${Versions.ktorVs}")
}

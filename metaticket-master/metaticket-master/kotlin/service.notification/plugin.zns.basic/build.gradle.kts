plugins {
	id("application")
	kotlin("jvm")
}

version = "1.0.0"

dependencies {
	compileOnly(project(":service.notification:plugin.zns"))
	compileOnly(project(":service.notification", "grpc"))
	compileOnly(project(":common", "logger"))
    compileOnly("com.fasterxml.jackson.core:jackson-databind:${Versions.jacksonVs}")
    compileOnly("com.fasterxml.jackson.module:jackson-module-kotlin:${Versions.jacksonVs}")
}

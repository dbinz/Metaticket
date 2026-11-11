plugins {
	id("application")
	kotlin("jvm")
}

version = "1.0.0"

dependencies {
	compileOnly(project(":service.notification:plugin.mailtemplate"))
	compileOnly(project(":service.notification", "grpc"))
}

plugins {
	id("application")
	kotlin("jvm")
}

version = "1.0.0"

dependencies {
	compileOnly(project(":service.notification:plugin.mailtemplate"))
	compileOnly(project(":service.notification", "grpc"))
	compileOnly(project(":service.qrcode", "api"))
	compileOnly(project(":service.qrcode", "grpc"))

	implementation("jakarta.activation:jakarta.activation-api:2.1.3")
	implementation("com.itextpdf:itext-core:8.0.5")
	implementation("com.itextpdf:itextpdf:5.5.13.4")
	implementation("com.itextpdf:html2pdf:5.0.5")

	compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutineVs}")

	compileOnly("io.insert-koin:koin-core:${Versions.koinVs}")
}
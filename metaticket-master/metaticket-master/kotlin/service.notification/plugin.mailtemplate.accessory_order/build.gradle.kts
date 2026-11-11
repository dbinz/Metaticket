plugins {
	id("application")
	kotlin("jvm")
}

version = "1.0.0"

dependencies {
	compileOnly(project(":service.notification:plugin.mailtemplate"))
	compileOnly(project(":service.notification", "grpc"))

	// Provided at runtime by service.notification's application classloader
	// (URLClassLoader parent), which already bundles jackson-module-kotlin.
	compileOnly("com.fasterxml.jackson.core:jackson-databind:${Versions.jacksonVs}")
	compileOnly("com.fasterxml.jackson.module:jackson-module-kotlin:${Versions.jacksonVs}")

	// --- Test
	testImplementation("org.jetbrains.kotlin:kotlin-test:${Versions.kotlinVs}")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:${Versions.kotlinVs}")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutineVs}")
	// The plugin contract (PluginMailTemplate, AccessoryMailPayload, MailTemplateDocument, ...)
	testImplementation(project(":service.notification:plugin.mailtemplate"))
	// Jackson is used by the plugin's payload(...) coercion and is compileOnly for main,
	// so it must be present on the test runtime classpath.
	testImplementation("com.fasterxml.jackson.core:jackson-databind:${Versions.jacksonVs}")
	testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:${Versions.jacksonVs}")
}

package metaticket.notification.plugin.template.service

import kotlinx.coroutines.CompletableDeferred
import metaticket.common.logger.Logging
import metaticket.plugin.zns.PluginZNS
import java.io.File
import java.net.URLClassLoader
import java.util.*


/**
 *
 * @author dongcatchay
 */
class PluginZNSTemplateService : Logging {
    private var loader: CompletableDeferred<ServiceLoader<PluginZNS>> = CompletableDeferred()

    fun loadJar() {
        File("./plugin/zns_template").listFiles()
            ?.asSequence()
            ?.mapNotNull { it.listFiles()?.toList() }?.flatten()
            ?.filter {it.name in listOf("lib", "libs")}
            ?.mapNotNull { it.listFiles()?.toList() }?.flatten()
            ?.mapNotNull { it.toURI().toURL() }
            ?.toList()
            ?.takeIf { it.isNotEmpty() } // Ensure the list is not empty before creating URLClassLoader
            ?.let { urls ->
                // Use Thread.currentThread().contextClassLoader as the parent classloader
                // This classloader is more likely to have visibility to the Koin context
                // started in the main application.
                val classLoader = URLClassLoader(urls.toTypedArray(), Thread.currentThread().contextClassLoader)
                val s = ServiceLoader.load(PluginZNS::class.java, classLoader)
                loader.complete(s)
                logger.info("loaded plugin zns $urls, $s")
            } ?: run {
                // ServiceLoader.load(Class) uses Thread.currentThread().contextClassLoader by default
                // Explicitly passing it here for consistency if preferred, though not strictly necessary.
                val s = ServiceLoader.load(PluginZNS::class.java, Thread.currentThread().contextClassLoader)
                loader.complete(s)
                logger.info("loaded plugin zns $s")
            }
    }

    suspend fun getPlugin(name: String): PluginZNS? {
        return loader.await().firstOrNull { it::class.java.simpleName == name }
    }

    suspend fun getPlugins(): List<String> {
        return loader.await().toList().map { it::class.java.simpleName }
    }
}
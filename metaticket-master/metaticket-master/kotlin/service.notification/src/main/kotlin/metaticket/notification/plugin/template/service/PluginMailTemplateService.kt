package metaticket.notification.plugin.template.service

import kotlinx.coroutines.CompletableDeferred
import metaticket.common.logger.Logging
import metaticket.plugin.mailtemplate.PluginMailTemplate
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.ServiceConfigurationError
import java.util.ServiceLoader


/**
 *
 * @author dongcatchay
 */
class PluginMailTemplateService : Logging {
    private var loader: CompletableDeferred<ServiceLoader<PluginMailTemplate>> = CompletableDeferred()

    /** Scans plugin folders and completes [loader] with a ServiceLoader over those jars. */
    fun loadJar() {
        val urls = collectPluginClasspathUrls()
        val classLoader = if (urls.isNotEmpty()) {
            URLClassLoader(urls.toTypedArray(), Thread.currentThread().contextClassLoader)
        } else {
            Thread.currentThread().contextClassLoader
        }
        val s = ServiceLoader.load(PluginMailTemplate::class.java, classLoader)
        loader.complete(s)
        logger.info("loaded plugin mail template jars={} loader={}", urls, s)
    }

    /**
     * Collects jars for the plugin classloader: each plugin jar plus its private deps
     * (e.g. itext), excluding jars that must stay unique on the parent classloader.
     */
    private fun collectPluginClasspathUrls(): List<URL> {
        val root = File("./plugin/mail_template")
        if (!root.isDirectory) {
            logger.warn("mail template plugin root missing: {}", root.absolutePath)
            return emptyList()
        }
        return root.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory }
            ?.flatMap { pluginDir ->
                listOf("lib", "libs").asSequence()
                    .map { File(pluginDir, it) }
                    .filter { it.isDirectory }
                    .flatMap { libDir -> libDir.listFiles()?.asSequence() ?: emptySequence() }
            }
            ?.filter { it.isFile && shouldLoadOnPluginClassLoader(it.name) }
            ?.map { it.toURI().toURL() }
            ?.toList()
            .orEmpty()
    }

    /**
     * Returns false for jars that collide with the application classloader when duplicated
     * in the shared plugin URLClassLoader (stdlib / logging / API contract jar).
     */
    private fun shouldLoadOnPluginClassLoader(fileName: String): Boolean {
        if (!fileName.endsWith(".jar", ignoreCase = true)) return false
        val n = fileName.lowercase()
        if (n.startsWith("kotlin-stdlib")) return false
        if (n.startsWith("kotlin-logging")) return false
        if (n.startsWith("annotations-")) return false
        if (n.startsWith("slf4j-")) return false
        // API module only: plugin.mailtemplate-1.0.0.jar (not plugin.mailtemplate.basic-…)
        if (n.matches(Regex("""plugin\.mailtemplate-\d.*\.jar"""))) return false
        return true
    }

    /** Returns the provider whose simple class name matches [name], or null. */
    suspend fun getPlugin(name: String): PluginMailTemplate? {
        return safeProviders().firstOrNull { it::class.java.simpleName == name }
    }

    /** Lists simple names of providers that instantiate successfully. */
    suspend fun getPlugins(): List<String> {
        return safeProviders().map { it::class.java.simpleName }
    }

    /**
     * Enumerates ServiceLoader providers and skips ones that fail to instantiate so a
     * single broken plugin does not empty the admin plugin list.
     */
    private suspend fun safeProviders(): List<PluginMailTemplate> {
        val out = mutableListOf<PluginMailTemplate>()
        try {
            loader.await().stream().forEach { provider ->
                try {
                    out.add(provider.get())
                } catch (e: ServiceConfigurationError) {
                    logger.error("skip mail plugin {}", provider.type().name, e)
                } catch (e: Throwable) {
                    logger.error("skip mail plugin {}", provider.type().name, e)
                }
            }
        } catch (e: Throwable) {
            logger.error("failed enumerating mail plugins", e)
        }
        return out
    }
}

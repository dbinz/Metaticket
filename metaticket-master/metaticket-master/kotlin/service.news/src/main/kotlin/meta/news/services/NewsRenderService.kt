package meta.news.services

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import meta.news.config.NewsProperties
import meta.news.models.NewsDetail
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets

@JsonIgnoreProperties(ignoreUnknown = true)
private data class WpScript(
	/** WP mu-plugin emits `handle`; older BFF payloads used `id`. */
	@JsonAlias("handle")
	val id: String = "",
	val src: String = "",
	val deps: List<String> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class WpInlineScript(
	@JsonAlias("handle")
	val id: String = "",
	val position: String = "after",
	/** WP emits `code` (`wp_localize_script` + extra before/after). */
	@JsonAlias("code")
	val data: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class WpScriptsManifest(
	val scripts: List<WpScript> = emptyList(),
	val inline: List<WpInlineScript> = emptyList(),
)

@Service
class NewsRenderService(
	private val newsService: NewsService,
	private val newsProperties: NewsProperties,
) {
	private val mapper = jacksonObjectMapper()
	private val template: String = loadResource("/templates/news-article.html")
	private val hydrateJs: String = loadResource("/mt-chrome.js")

	/**
	 * Renders a full HTML page: static SEO shell from the classpath template,
	 * dynamic islands left as skeletons for client hydrate.
	 */
	@Cacheable(cacheNames = ["articleHtml"], key = "#slug")
	fun renderArticle(slug: String): String {
		val detail = newsService.getNewsBySlug(slug)
		val wpCss = newsService.getContentStyles()
		val manifest = parseManifest(newsService.getContentScriptsJson())
		return fillTemplate(detail, wpCss, manifest)
	}

	/** Reads a UTF-8 classpath resource; fails fast if the template is missing from the JAR. */
	private fun loadResource(path: String): String {
		val stream = javaClass.getResourceAsStream(path)
			?: error("Missing classpath resource: $path")
		return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
	}

	private fun parseManifest(json: String): WpScriptsManifest {
		return runCatching { mapper.readValue<WpScriptsManifest>(json) }
			.getOrElse { WpScriptsManifest() }
	}

	/**
	 * Fills SEO tokens in the static shell. Article HTML is applied last so WP
	 * body cannot collide with placeholder names.
	 */
	private fun fillTemplate(
		detail: NewsDetail,
		wpCss: String,
		manifest: WpScriptsManifest,
	): String {
		val frontend = newsProperties.frontendBaseUrl.trimEnd('/')
		val canonicalUrl = "$frontend/news/${detail.slug}.html"
		val title = escHtml(detail.title)
		val pageTitle = "$title | META Ticket"
		val desc = escHtml((detail.summary.takeIf { it.isNotBlank() } ?: detail.title).take(200))
		val categoryName = escHtml(detail.categoryName.ifBlank { "Tin tức" })
		val displayDate = escHtml(detail.displayDate)
		val wpCssBlock = if (wpCss.isNotBlank()) {
			"""<style id="wp-plugin-styles">$wpCss</style>"""
		} else {
			""
		}
		val boot = """
  <script>
    window.__MT_CHROME__ = {
      frontend: ${escJs(frontend)},
      api: "/api",
      slug: ${escJs(detail.slug)},
      categoryCode: ${escJs(detail.categoryCode)},
      loginHref: ${escJs("$frontend/dang-nhap")},
      iconBase: ${escJs("$frontend/assets/homepage/icons")}
    };
  </script>
  <script>
$hydrateJs
  </script>""".trimIndent()

		var html = template
			.replace("__PAGE_TITLE__", pageTitle)
			.replace("__META_DESC__", desc)
			.replace("__CANONICAL__", canonicalUrl)
			.replace("__OG_IMAGE__", detail.thumbnail)
			.replace("__JSON_LD__", buildJsonLd(detail, canonicalUrl))
			.replace("__FRONTEND__", frontend)
			.replace("__CATEGORY_NAME__", categoryName)
			.replace("__DISPLAY_DATE__", displayDate)
			.replace("__H1_TITLE__", title)
			.replace("__HERO_SRC__", detail.thumbnail)
			.replace("__WP_CSS__", wpCssBlock)
			.replace("__WP_SCRIPTS__", buildScriptTags(manifest))
			.replace("__HYDRATE_BOOT__", boot)
			.replace("__ARTICLE_TAGS__", buildTagLine(detail.tags))
		html = html.replace("__ARTICLE_HTML__", detail.content)
		return html
	}

	/**
	 * Emit scripts in WP print order: localize/`before` → src → `after`.
	 * Manifest handles are already topologically sorted by `WP_Scripts::all_deps`.
	 */
	private fun buildScriptTags(manifest: WpScriptsManifest): String {
		val before = manifest.inline.filter { it.position.equals("before", ignoreCase = true) }.groupBy { it.id }
		val after = manifest.inline.filter { !it.position.equals("before", ignoreCase = true) }.groupBy { it.id }
		val sb = StringBuilder()
		for (script in manifest.scripts) {
			val handle = script.id.ifBlank { "wp-script" }
			before[script.id]?.forEach { inline ->
				appendInlineScript(sb, "$handle-before", inline.data)
			}
			if (script.src.isNotBlank()) {
				sb.append("  <script src=\"${escHtml(script.src)}\" id=\"${escHtml(handle)}\"></script>\n")
			}
			after[script.id]?.forEach { inline ->
				appendInlineScript(sb, "$handle-after", inline.data)
			}
		}
		val handledIds = manifest.scripts.map { it.id }.toSet()
		(before + after).forEach { (id, inlines) ->
			if (id !in handledIds) {
				inlines.forEach { inline ->
					appendInlineScript(sb, "${id.ifBlank { "wp" }}-inline", inline.data)
				}
			}
		}
		return sb.toString().trimEnd()
	}

	private fun buildJsonLd(detail: NewsDetail, canonicalUrl: String): String {
		val title = escJson(detail.title)
		val desc = escJson(detail.summary.take(200))
		val author = escJson(detail.author.ifBlank { "META Ticket" })
		val logo = "${newsProperties.frontendBaseUrl.trimEnd('/')}/assets/logo.png"
		return """{
  "@context": "https://schema.org",
  "@type": "NewsArticle",
  "headline": "$title",
  "description": "$desc",
  "image": ["${detail.thumbnail}"],
  "author": { "@type": "Person", "name": "$author" },
  "publisher": {
    "@type": "Organization",
    "name": "META Ticket",
    "logo": { "@type": "ImageObject", "url": "$logo" }
  },
  "datePublished": "${detail.createdAt}",
  "mainEntityOfPage": { "@type": "WebPage", "@id": "$canonicalUrl" }
}"""
	}

	/** Emits a WP inline / `wp_localize_script` block; skips empty payloads. */
	private fun appendInlineScript(sb: StringBuilder, id: String, body: String) {
		if (body.isBlank()) return
		sb.append("  <script id=\"${escHtml(id)}\">$body</script>\n")
	}

	/** Italic `#tag` line after the article body; omitted when the post has no tags. */
	private fun buildTagLine(tags: List<String>): String {
		if (tags.isEmpty()) return ""
		val line = tags.joinToString(" ") { "#${escHtml(it)}" }
		return """<p class="art-tags">$line</p>"""
	}

	private fun escHtml(s: String): String = s
		.replace("&", "&amp;")
		.replace("<", "&lt;")
		.replace(">", "&gt;")
		.replace("\"", "&quot;")

	private fun escJson(s: String): String = s
		.replace("\\", "\\\\")
		.replace("\"", "\\\"")
		.replace("\n", "\\n")
		.replace("\r", "")

	/** JSON string literal for inline JS config. */
	private fun escJs(s: String): String = "\"" + escJson(s) + "\""
}

package meta.news.services

import meta.news.client.WpPost
import meta.news.client.WpTerm
import meta.news.config.NewsProperties
import meta.news.models.NewsCategoryItem
import meta.news.models.NewsItem
import meta.news.models.NewsPinSummary
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

@Component
class NewsMapper(
    private val newsProperties: NewsProperties,
) {
    private val zoneVn: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    private val dayNames = mapOf(
        1 to "Thứ Hai",
        2 to "Thứ Ba",
        3 to "Thứ Tư",
        4 to "Thứ Năm",
        5 to "Thứ Sáu",
        6 to "Thứ Bảy",
        7 to "Chủ Nhật",
    )
    private val displayTimeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private val displayDateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val isoOutFmt = DateTimeFormatter.ISO_INSTANT

    // Chuẩn hoá: luôn trả về base KHÔNG có dấu "/" cuối, 
    // để mọi chỗ gọi tự thêm đúng 1 dấu "/" khi ghép path.
    private fun mediaBaseNormalized(): String = mediaBase().trimEnd('/')

    fun toCategoryItem(id: Long, slug: String, name: String): NewsCategoryItem {
        val code = slug.ifBlank { id.toString() }
        return NewsCategoryItem(
            id = "cat-$code",
            name = name,
            code = code,
            slug = code,
        )
    }

    fun toNewsItem(post: WpPost): NewsItem {
        val category = primaryCategory(post)
        val created = resolveInstant(post)
        return NewsItem(
            id = post.id.toString(),
            thumbnail = thumbnailUrl(post),
            createdAt = isoOutFmt.format(created),
            displayDate = formatDisplayDate(created),
            title = stripHtml(post.title.rendered),
            summary = stripHtml(post.excerpt.rendered),
            slug = post.slug,
            categoryId = category?.let { "cat-${it.slug}" } ?: "",
            categoryCode = category?.slug ?: "",
            categoryName = category?.name ?: "",
            author = authorName(post),
        )
    }

    /** Compact card for the admin pin board / picker. */
    fun toPinSummary(post: WpPost): NewsPinSummary {
        return NewsPinSummary(
            id = post.id,
            title = stripHtml(post.title.rendered),
            slug = post.slug,
            status = post.status,
            thumbnailUrl = thumbnailUrl(post),
            modifiedAt = post.modified,
        )
    }

    fun authorName(post: WpPost): String {
        val name = post.embedded?.author?.firstOrNull()?.name?.trim().orEmpty()
        return name.ifBlank { "MetaTicket" }
    }

    fun readTime(html: String): String {
        val text = stripHtml(html)
        val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
        val minutes = max(1, (words + 199) / 200)
        return "$minutes min read"
    }

    fun rewriteContentUrls(html: String, slug: String = ""): String {
        if (html.isBlank()) return html
        var out = html
        val base = mediaBaseNormalized()
        out = out.replace(
            Regex("https?://(?:admin|news)\\.[^/\"'\\s]+/"),
            "$base/",
        )
        out = out.replace(
            Regex("https?://(?:admin|news)\\.[^/\"'\\s]+/(wp-content|wp-includes)/"),
            "$base/$1/",
        )
        out = out.replace(
            Regex("(src|href|srcset)=([\"'])/(wp-content|wp-includes)/"),
            "$1=$2$base/$3/",
        )
        out = out.replace(Regex("https?://localhost:8081/"), "$base/")
        out = out.replace(Regex("https?://127\\.0\\.0\\.1:8081/"), "$base/")
        out = rewriteWpInPageAnchorsForFrontend(out, slug)
        return out
    }

    /**
     * Convert WP permalink + fragment (Easy TOC) to frontend path + fragment.
     * Bare `#id` alone breaks under `<base href="/">` (becomes origin/#id).
     */
    private fun rewriteWpInPageAnchorsForFrontend(html: String, slug: String): String {
        val path = frontendArticlePath(slug)
        val absolutePattern = Regex(
            "href=([\"'])(https?://[^\"']+)#([^\"']+)\\1",
            RegexOption.IGNORE_CASE,
        )
        var out = html.replace(absolutePattern) { match ->
            val quote = match.groupValues[1]
            val url = match.groupValues[2]
            val fragment = match.groupValues[3]
            if (isWordpressHostedUrl(url) && fragment.isNotBlank()) {
                "href=$quote$path#$fragment$quote"
            } else {
                match.value
            }
        }
        if (path.isNotEmpty()) {
            val bareHash = Regex("href=([\"'])#([^\"']+)\\1", RegexOption.IGNORE_CASE)
            out = out.replace(bareHash) { match ->
                val quote = match.groupValues[1]
                val fragment = match.groupValues[2]
                "href=$quote$path#$fragment$quote"
            }
        }
        return out
    }

    /** Path matching MetaTicket-Home canonical article URL `/news/:slug.html`. */
    private fun frontendArticlePath(slug: String): String {
        val clean = slug.trim().trim('/')
        if (clean.isEmpty()) return ""
        return "/news/$clean.html"
    }

    private fun isWordpressHostedUrl(url: String): Boolean {
        val media = mediaBase()
        if (url.startsWith(media)) return true
        if (Regex("https?://(?:admin|news)\\.", RegexOption.IGNORE_CASE).containsMatchIn(url)) return true
        if (url.contains("localhost:8081") || url.contains("127.0.0.1:8081")) return true
        return false
    }

    fun stripHtml(html: String): String {
        if (html.isBlank()) return ""
        return html
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun mediaBase(): String = newsProperties.mediaBaseUrl.trimEnd('/')

    private fun rewriteMediaUrl(url: String): String {
        if (url.isBlank()) return url
        var out = url
        val base = mediaBaseNormalized()

        // Handle correctly slashed paths first
        val wpContentIdx = out.indexOf("/wp-content/")
        if (wpContentIdx != -1) {
            return base + out.substring(wpContentIdx)
        }
        val wpIncludesIdx = out.indexOf("/wp-includes/")
        if (wpIncludesIdx != -1) {
            return base + out.substring(wpIncludesIdx)
        }

        // Handle malformed paths missing the slash
        val badWpContentIdx = out.indexOf("wp-content/")
        if (badWpContentIdx != -1) {
            return base + "/" + out.substring(badWpContentIdx)
        }
        val badWpIncludesIdx = out.indexOf("wp-includes/")
        if (badWpIncludesIdx != -1) {
            return base + "/" + out.substring(badWpIncludesIdx)
        }

        return out
    }

    private fun thumbnailUrl(post: WpPost): String {
        val media = post.embedded?.featuredMedia?.firstOrNull() ?: return ""
        if (media.sourceUrl.isBlank()) return ""
        return rewriteMediaUrl(media.sourceUrl)
    }

    private fun primaryCategory(post: WpPost): WpTerm? {
        return post.embedded?.terms
            ?.flatten()
            ?.firstOrNull { it.taxonomy == "category" && it.slug != "uncategorized" }
            ?: post.embedded?.terms?.flatten()?.firstOrNull { it.taxonomy == "category" }
    }

    /** Display names of WP `post_tag` terms on the post. */
    fun tagNames(post: WpPost): List<String> {
        return post.embedded?.terms
            ?.flatten()
            ?.filter { it.taxonomy == "post_tag" }
            ?.map { it.name.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            .orEmpty()
    }

    /** Prefer GMT from WP; fall back to local date treated as Vietnam wall time. */
    private fun resolveInstant(post: WpPost): Instant {
        val gmt = post.dateGmt.trim()
        if (gmt.isNotEmpty()) {
            return runCatching {
                LocalDateTime.parse(gmt.replace(" ", "T").take(19)).toInstant(ZoneOffset.UTC)
            }.getOrElse { Instant.now() }
        }
        val local = post.date.trim()
        if (local.isNotEmpty()) {
            return runCatching {
                LocalDateTime.parse(local.replace(" ", "T").take(19)).atZone(zoneVn).toInstant()
            }.getOrElse { Instant.now() }
        }
        return Instant.now()
    }

    private fun formatDisplayDate(instant: Instant): String {
        val zdt = instant.atZone(zoneVn)
        val dayName = dayNames[zdt.dayOfWeek.value] ?: ""
        return "${displayTimeFmt.format(zdt)} $dayName, ${displayDateFmt.format(zdt)}"
    }
}

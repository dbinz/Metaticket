package meta.news.services

import meta.news.client.WpPost
import meta.news.client.WpPostWrite
import meta.news.models.AdminArticleForm
import meta.news.models.AdminArticleRow
import meta.news.models.SaveArticleRequest
import org.springframework.stereotype.Component

@Component
class AdminNewsMapper {

	fun toRow(post: WpPost): AdminArticleRow {
		return AdminArticleRow(
			id = post.id,
			title = stripHtml(post.title.rendered),
			slug = post.slug,
			visible = post.status == "publish",
			pinOrder = ArticleSort.effectiveFeaturedOrder(post),
			categories = categoryNames(post),
			modifiedAt = post.modified,
		)
	}

	fun toForm(post: WpPost): AdminArticleForm {
		return AdminArticleForm(
			id = post.id,
			title = stripHtml(post.title.rendered),
			slug = post.slug,
			excerpt = stripHtml(post.excerpt.rendered),
			contentHtml = post.content.rendered,
			visible = post.status == "publish",
			pinOrder = ArticleSort.effectiveFeaturedOrder(post),
			categoryIds = post.categories,
		)
	}

	/** Maps the admin save payload; featured pin is 1..3 only. */
	fun toWrite(request: SaveArticleRequest): WpPostWrite {
		return WpPostWrite(
			title = request.title.trim(),
			content = request.contentHtml,
			excerpt = request.excerpt,
			status = if (request.visible) "publish" else "draft",
			slug = request.slug.trim().ifBlank { null },
			categories = request.categoryIds,
			pinOrder = request.pinOrder?.takeIf { it in 1..NewsPins.FEATURED_MAX },
		)
	}

	private fun categoryNames(post: WpPost): List<String> {
		return post.embedded?.terms
			?.flatten()
			?.filter { it.taxonomy == "category" }
			?.map { it.name }
			?: emptyList()
	}

	private fun stripHtml(html: String): String {
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
}

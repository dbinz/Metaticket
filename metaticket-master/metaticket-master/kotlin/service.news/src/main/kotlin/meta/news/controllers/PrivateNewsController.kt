package meta.news.controllers

import meta.news.models.AdminArticleForm
import meta.news.models.AdminArticleRow
import meta.news.models.AdminCategoryOption
import meta.news.models.ConfirmNewsPinsRequest
import meta.news.models.NewsPinBoard
import meta.news.models.NewsPinCandidatePage
import meta.news.models.PageResponse
import meta.news.models.SaveArticleRequest
import meta.news.models.SetPinRequest
import meta.news.models.SetVisibilityRequest
import meta.news.services.AdminNewsService
import meta.news.services.NewsPinService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/private/news")
class PrivateNewsController(
	private val adminNewsService: AdminNewsService,
	private val newsPinService: NewsPinService,
) {

	@GetMapping("/articles")
	fun listArticles(
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
		@RequestParam(required = false) search: String?,
		@RequestParam(required = false) category: String?,
	): PageResponse<AdminArticleRow> {
		return adminNewsService.listArticles(page, size.coerceIn(1, 100), search, category)
	}

	@GetMapping("/articles/{id}")
	fun getArticle(@PathVariable id: Long): AdminArticleForm {
		return adminNewsService.getArticle(id)
	}

	@PostMapping("/articles")
	fun createArticle(@RequestBody request: SaveArticleRequest): AdminArticleForm {
		return adminNewsService.createArticle(request)
	}

	@PutMapping("/articles/{id}")
	fun updateArticle(
		@PathVariable id: Long,
		@RequestBody request: SaveArticleRequest,
	): AdminArticleForm {
		return adminNewsService.updateArticle(id, request)
	}

	@PatchMapping("/articles/{id}/visibility")
	fun setVisibility(
		@PathVariable id: Long,
		@RequestBody request: SetVisibilityRequest,
	): AdminArticleRow {
		return adminNewsService.setVisibility(id, request.visible)
	}

	@PatchMapping("/articles/{id}/pin")
	fun setPin(
		@PathVariable id: Long,
		@RequestBody request: SetPinRequest,
	): AdminArticleRow {
		return adminNewsService.setPin(id, request.pinOrder)
	}

	@GetMapping("/categories")
	fun categories(): List<AdminCategoryOption> {
		return adminNewsService.listCategories()
	}

	@GetMapping("/pin/featured")
	fun getFeaturedPins(): NewsPinBoard {
		return newsPinService.getFeaturedBoard()
	}

	@PostMapping("/pin/featured/refresh")
	fun refreshFeaturedPins(): NewsPinBoard {
		return newsPinService.refreshFeaturedBoard()
	}

	@PostMapping("/pin/featured/confirm")
	fun confirmFeaturedPins(@RequestBody request: ConfirmNewsPinsRequest): NewsPinBoard {
		return newsPinService.confirmFeaturedBoard(request)
	}

	@GetMapping("/pin/category/{code}")
	fun getCategoryPins(@PathVariable code: String): NewsPinBoard {
		return newsPinService.getCategoryBoard(code)
	}

	@PostMapping("/pin/category/{code}/refresh")
	fun refreshCategoryPins(@PathVariable code: String): NewsPinBoard {
		return newsPinService.refreshCategoryBoard(code)
	}

	@PostMapping("/pin/category/{code}/confirm")
	fun confirmCategoryPins(
		@PathVariable code: String,
		@RequestBody request: ConfirmNewsPinsRequest,
	): NewsPinBoard {
		return newsPinService.confirmCategoryBoard(code, request)
	}

	@GetMapping("/pin/candidates")
	fun pinCandidates(
		@RequestParam(defaultValue = "1") page: Int,
		@RequestParam(defaultValue = "10") size: Int,
		@RequestParam(required = false) keyword: String?,
		@RequestParam(required = false) categoryCode: String?,
	): NewsPinCandidatePage {
		return newsPinService.searchCandidates(page, size.coerceIn(1, 50), keyword, categoryCode)
	}
}

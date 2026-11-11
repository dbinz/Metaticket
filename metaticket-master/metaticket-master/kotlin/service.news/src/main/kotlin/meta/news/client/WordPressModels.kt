package meta.news.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class WpRendered(
	val rendered: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WpPost(
	val id: Long = 0,
	val slug: String = "",
	val status: String = "",
	val date: String = "",
	@JsonProperty("date_gmt")
	val dateGmt: String = "",
	val modified: String = "",
	val link: String = "",
	val title: WpRendered = WpRendered(),
	val content: WpRendered = WpRendered(),
	val excerpt: WpRendered = WpRendered(),
	@JsonProperty("featured_media")
	val featuredMedia: Long = 0,
	val categories: List<Long> = emptyList(),
	val tags: List<Long> = emptyList(),
	val author: Long = 0,
	val sticky: Boolean = false,
	@JsonProperty("mt_pin_order")
	val pinOrder: Int? = null,
	@JsonProperty("mt_category_pins")
	val categoryPins: Map<String, Int> = emptyMap(),
	@JsonProperty("_embedded")
	val embedded: WpEmbedded? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WpPostWrite(
	val title: String,
	val content: String,
	val excerpt: String = "",
	val status: String = "draft",
	val slug: String? = null,
	val categories: List<Long> = emptyList(),
	// null = omit from JSON so WP keeps existing featured image (0 would clear it).
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonProperty("featured_media")
	val featuredMedia: Long? = null,
	@JsonInclude(JsonInclude.Include.ALWAYS)
	@JsonProperty("mt_pin_order")
	val pinOrder: Int? = null,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonProperty("mt_category_pins")
	val categoryPins: Map<String, Int>? = null,
)

/** PUT extra field only — omit `mt_category_pins` so category ranks stay put. */
data class WpFeaturedPinWrite(
	@JsonInclude(JsonInclude.Include.ALWAYS)
	@JsonProperty("mt_pin_order")
	val pinOrder: Int?,
)

/** PUT extra field only — omit `mt_pin_order` so featured rank stays put. */
data class WpCategoryPinsWrite(
	@JsonInclude(JsonInclude.Include.ALWAYS)
	@JsonProperty("mt_category_pins")
	val categoryPins: Map<String, Int>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WpEmbedded(
	@JsonProperty("wp:featuredmedia")
	val featuredMedia: List<WpMedia>? = null,
	@JsonProperty("wp:term")
	val terms: List<List<WpTerm>>? = null,
	val author: List<WpAuthor>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WpAuthor(
	val id: Long = 0,
	val name: String = "",
	val slug: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WpMedia(
	val id: Long = 0,
	@JsonProperty("source_url")
	val sourceUrl: String = "",
	@JsonProperty("alt_text")
	val altText: String = "",
	@JsonProperty("media_details")
	val mediaDetails: JsonNode? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WpTerm(
	val id: Long = 0,
	val slug: String = "",
	val name: String = "",
	val taxonomy: String = "",
	val count: Int = 0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WpCategory(
	val id: Long = 0,
	val slug: String = "",
	val name: String = "",
	val count: Int = 0,
)

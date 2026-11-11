package meta.metasearch.models

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import org.springframework.data.elasticsearch.annotations.Setting

/**
 * Search projection; analyzer definitions live in `/static/es-settings.json`.
 * Normalize accents and combining marks before generating prefixes so NFC/NFD text matches alike.
 * The standard tokenizer keeps code digits; preserve_original keeps complete tokens longer than 20 characters.
 * Keyword fields share one search analyzer so cross_fields can apply AND across different fields.
 * Analyzer changes require reindexing: @Setting does not migrate an index that already exists.
 */
@Document(indexName = "#{@environment.getProperty('elasticsearch.show.index.name')}")
@Setting(settingPath = "/static/es-settings.json")
data class Show constructor(
        @Id
        var id: String = "",

        @Field(type = FieldType.Text, name = "showName", analyzer = "autocomplete", searchAnalyzer = "autocomplete_search")
        var showName: String = "",

        @Field(type = FieldType.Text, name = "code",analyzer = "autocomplete",searchAnalyzer = "autocomplete_search")
        var code: String = "",

        @Field(type = FieldType.Keyword, name = "slug")
        var slug: String = "",

        @Field(type = FieldType.Text, name = "status")
        var status: String = "",

        @Field(type = FieldType.Text, name = "eventCategoryCode")
        var eventCategoryCode: String = "",

        // Match full category words; prefix expansion here would pull in loosely related categories.
        @Field(type = FieldType.Text, name = "eventCategory", analyzer = "folded_text", searchAnalyzer = "autocomplete_search")
        var eventCategory: String = "",

        @Field(type = FieldType.Text, name = "detailImageUrl")
        var detailImageUrl: String? = null,

        @Field(type = FieldType.Text, name = "thumbnailUrl")
        var thumbnailUrl: String = "",

        @Field(type = FieldType.Text, name = "bannerImageUrl")
        var bannerImageUrl: String? = null,

        @Field(type = FieldType.Text, name = "logoHostUrl")
        var logoHostUrl: String? = null,

        @Field(type = FieldType.Text, name = "logoShowUrl")
        var logoShowUrl: String? = null,

        @Field(type = FieldType.Long, name = "createdAt")
        var createdAt: Long,

        @Field(type = FieldType.Text, name = "description",analyzer = "autocomplete",searchAnalyzer = "autocomplete_search")
        var description: String? = null,

        // HTML-stripped, length-capped plain text of `description`, used for keyword search so
        // markup/CSS noise (e.g. "style", "font", "margin") never enters the search index. Index
        // analyzer folds accents without edge-ngram prefixes to avoid re-introducing that noise;
        // search analyzer must stay `autocomplete_search` so it groups into the same
        // cross_fields blend as showName/venue/cityName for AND-across-fields matching.
        @Field(type = FieldType.Text, name = "descriptionText", analyzer = "folded_text", searchAnalyzer = "autocomplete_search")
        var descriptionText: String? = null,

        @Field(type = FieldType.Integer, name = "ranking")
        var ranking: Int = 0,

        @Field(type = FieldType.Boolean, name = "pinned")
        var pinned: Boolean = false,

        @Field(type = FieldType.Integer, name = "pinOrder")
        var pinOrder: Int? = null,

        @Field(type = FieldType.Long, name = "soldTicket")
        var soldTicket: Long = 0,

        // Lowest ticket base price of the show (VND), pushed by service.show (the owner of show data).
        // Null when no session has a priced ticket — free shows, or tickets not created yet — so the
        // storefront can tell "no price to advertise" apart from a genuine 0.
        @Field(type = FieldType.Long, name = "minPrice")
        var minPrice: Long? = null,

        @Field(type = FieldType.Boolean, name = "showInHome")
        val showInHome: Boolean = true,

        @Field(type = FieldType.Nested, includeInParent = true, name = "sessions")
        var sessions: List<ShowSession> = listOf(),

        @Field(type = FieldType.Long, name = "waitingTime")
        var waitingTime: Long = 0,

        @Field(type = FieldType.Long, name = "startTime")
        var startTime: Long = 0,

        /**
         * Ngày cuối của các session vé theo lịch còn bán; null khi show không có. Show có giá trị này >= now được xếp
         * như đang diễn ra dù startTime (ngày đầu của lịch) đã qua.
         */
        @Field(type = FieldType.Long, name = "ongoingUntil")
        var ongoingUntil: Long? = null,

        @Field(type = FieldType.Long, name = "startYear")
        var startYear: Int = 0,

        @Field(type = FieldType.Long, name = "startMonth")
        var startMonth: Int = 0,

        @Field(type = FieldType.Long, name = "startDay")
        var startDay: Int = 0,

        @Field(type = FieldType.Boolean, name = "hasAllSessionsStopped")
        val hasAllSessionsStopped: Boolean = false,
)

data class ShowSession constructor(
        @Id
        var id: String = "",

        @Field(type = FieldType.Text, name = "venue",analyzer = "autocomplete",searchAnalyzer = "autocomplete_search")
        var venue: String = "",

        @Field(type = FieldType.Text, name = "code")
        val code: String? = null,

        @Field(type = FieldType.Keyword, name = "slug")
        var slug: String = "",

        @Field(type = FieldType.Text, name = "status")
        val status: String = "",

        @Field(type = FieldType.Long, name = "startTime")
        var startTime: Long = 0,

        @Field(type = FieldType.Long, name = "endTime")
        var endTime: Long = 0,

        @Field(type = FieldType.Text, name = "sessionName", analyzer = "autocomplete", searchAnalyzer = "autocomplete_search")
        val sessionName: String? = null,

        @Field(type = FieldType.Text, name = "cityName",analyzer = "autocomplete",searchAnalyzer = "autocomplete_search")
        val cityName: String? = null,

        @Field(type = FieldType.Text, name = "cityCode",analyzer = "autocomplete",searchAnalyzer = "autocomplete_search")
        val cityCode: String? = null,

        @Field(type = FieldType.Boolean, name = "isFree")
        val isFree: Boolean,

        @Field(type = FieldType.Boolean, name = "showInHome")
        val showInHome: Boolean = true,

        /** SINGLE | CALENDAR (vé theo lịch: bán theo ngày trong [startTime, endTime]); service.show cũ không gửi → SINGLE. */
        @Field(type = FieldType.Keyword, name = "sessionType")
        val sessionType: String = "SINGLE",
        )

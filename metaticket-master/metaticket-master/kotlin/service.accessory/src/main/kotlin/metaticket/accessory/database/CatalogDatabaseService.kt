package metaticket.accessory.database

import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import metaticket.accessory.logger
import metaticket.accessory.pojo.db.AccessoryDocument
import metaticket.accessory.pojo.db.AccessoryShowStatus
import metaticket.accessory.pojo.db.AccessoryShowDocument
import metaticket.accessory.pojo.db.UNLIMITED_STOCK
import org.bson.BsonType
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import java.util.Date

/** Upper bound on regex matches ranked in-process by [CatalogDatabaseService.searchShowsForStorefront]. */
private const val SEARCH_MATCH_CAP = 200

/** Base Latin letter → every Vietnamese precomposed accented form (lowercase; the Mongo regex "i" flag covers case). */
private val VIETNAMESE_LETTER_VARIANTS = mapOf(
    'a' to "aàáạảãâầấậẩẫăằắặẳẵ",
    'e' to "eèéẹẻẽêềếệểễ",
    'i' to "iìíịỉĩ",
    'o' to "oòóọỏõôồốộổỗơờớợởỡ",
    'u' to "uùúụủũưừứựửữ",
    'y' to "yỳýỵỷỹ",
    'd' to "dđ",
)

/** Strips Vietnamese diacritics (NFD decompose + drop combining marks; `đ`/`Đ` don't decompose, handled separately) and lowercases. */
private fun normalizeVietnamese(s: String): String {
    val decomposed = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
    val stripped = decomposed.replace(Regex("\\p{Mn}+"), "")
    return stripped.replace('đ', 'd').replace('Đ', 'D').lowercase()
}

/**
 * Builds a Mongo/Java regex pattern that matches [raw] ignoring case and Vietnamese diacritics, by
 * expanding each letter into a character class of its accented variants (e.g. `a` → `[aàáạảã...]`).
 * Runs entirely at the Mongo level — unlike loading candidates and filtering in-process, this keeps
 * the query index-eligible-shaped and bounded by [SEARCH_MATCH_CAP] regardless of catalog size.
 */
private fun vietnameseInsensitivePattern(raw: String): String {
    return raw.map { ch ->
        val variants = VIETNAMESE_LETTER_VARIANTS[ch.lowercaseChar()]
        if (variants != null) "[$variants]" else java.util.regex.Pattern.quote(ch.toString())
    }.joinToString("")
}

/**
 * Owns the accessory catalog collections (`accessory-show`, `accessory`) and declares their MongoDB
 * indexes, following the same convention as `service.show`'s `DatabaseService` (indexes created in
 * an `init` block when the service is instantiated at start-up).
 *
 * Indexes (design "Data Models → Validation Rules"):
 * - Unique index on `AccessoryShowDocument.code`.
 * - Partial unique index on `AccessoryShowDocument.slug` (string values only).
 * - Compound unique index on `(AccessoryDocument.accessoryShowId, AccessoryDocument.sku)`.
 */
class CatalogDatabaseService(
    private val accessoryShowCol: MongoCollection<AccessoryShowDocument>,
    private val accessoryCol: MongoCollection<AccessoryDocument>,
) {

    init {
        CoroutineScope(Dispatchers.Unconfined).launch {
            // Unique, human-readable accessory-show code.
            val showCodeIdx = accessoryShowCol.createIndex(
                Indexes.ascending(AccessoryShowDocument::code.name),
                IndexOptions().unique(true)
            )
            logger.info("created accessory-show index {}", showCodeIdx)

            // Partial unique Canonical_Slug — null/absent docs do not collide.
            val showSlugIdx = accessoryShowCol.createIndex(
                Indexes.ascending(AccessoryShowDocument::slug.name),
                IndexOptions().unique(true).partialFilterExpression(
                    Filters.type(AccessoryShowDocument::slug.name, BsonType.STRING),
                ),
            )
            logger.info("created accessory-show slug index {}", showSlugIdx)

            // A SKU is unique within its owning accessory show.
            val skuIdx = accessoryCol.createIndex(
                Indexes.ascending(
                    AccessoryDocument::accessoryShowId.name,
                    AccessoryDocument::sku.name
                ),
                IndexOptions().unique(true)
            )
            logger.info("created accessory (accessoryShowId, sku) index {}", skuIdx)
        }
    }

    // -------------------------------------------------------------------------
    // Public storefront read queries (task 12.2)
    // -------------------------------------------------------------------------

    /**
     * Rank storefront listing: pinned 1..[pinMax] first (board order, not repeated later), then
     * Waiting (soonest [AccessoryShowDocument.saleOpenAt], undated newest [createdAt]), then Open,
     * then Closed. Open/Closed: higher paid-unit count first, then newest created.
     */
    fun rankShowsForStorefront(
        shows: List<AccessoryShowDocument>,
        soldQtyByShowId: Map<String, Int>,
        pinMax: Int,
    ): List<AccessoryShowDocument> {
        val pinned = shows
            .filter { show -> show.pinOrder?.let { it in 1..pinMax } == true }
            .sortedBy { it.pinOrder }
        val pinnedIds = pinned.map { it.id }.toSet()
        val rest = shows.filter { it.id !in pinnedIds }
        val waitingCmp = compareBy<AccessoryShowDocument> { it.saleOpenAt == null }
            .thenBy { it.saleOpenAt?.time ?: Long.MAX_VALUE }
            .thenByDescending { it.createdAt.time }
        val soldCmp = compareByDescending<AccessoryShowDocument> { soldQtyByShowId[it.id] ?: 0 }
            .thenByDescending { it.createdAt.time }
        val waiting = rest.filter { it.status == AccessoryShowStatus.Waiting }.sortedWith(waitingCmp)
        val open = rest.filter { it.status == AccessoryShowStatus.Open }.sortedWith(soldCmp)
        val closed = rest.filter { it.status == AccessoryShowStatus.Closed }.sortedWith(soldCmp)
        return pinned + waiting + open + closed
    }

    /**
     * Load every accessory show regardless of [AccessoryShowStatus] (admin catalog).
     * Sorted by `lastModified` descending so the newest edits appear first in the portal list.
     */
    suspend fun loadAllShows(): List<AccessoryShowDocument> {
        return withContext(Dispatchers.IO) {
            accessoryShowCol.find().toList()
                .sortedByDescending { it.lastModified.time }
        }
    }

    /**
     * Count all accessories (active + inactive) per show id for the admin list `productCount` column.
     */
    suspend fun countAccessoriesByShowIds(showRawIds: Collection<String>): Map<String, Int> {
        if (showRawIds.isEmpty()) return emptyMap()
        return showRawIds.associateWith { showId ->
            withContext(Dispatchers.IO) {
                accessoryCol.countDocuments(
                    Filters.eq(AccessoryDocument::accessoryShowId.name, ObjectId(showId)),
                ).toInt()
            }
        }
    }

    /**
     * Lowest price among the **active** accessories of each show, backing the storefront
     * "Từ …đ" card label.
     *
     * Deliberately one query for the whole page rather than one per show: the listing endpoints
     * render a full page of shows at a time, so a per-show round trip here would be an N+1 on the
     * storefront's hottest path. Shows with no active, priced product are simply absent from the
     * map and the card renders without a price.
     */
    suspend fun minPriceByShowIds(showRawIds: Collection<String>): Map<String, Long> {
        if (showRawIds.isEmpty()) return emptyMap()
        val filter = Filters.and(
            Filters.`in`(AccessoryDocument::accessoryShowId.name, showRawIds.map { ObjectId(it) }),
            Filters.eq(AccessoryDocument::active.name, true),
        )
        val minPrices = mutableMapOf<String, Long>()
        withContext(Dispatchers.IO) { accessoryCol.find(filter) }.toList().forEach { accessory ->
            if (accessory.price <= 0) return@forEach
            val current = minPrices[accessory.accessoryShowId]
            if (current == null || accessory.price < current) {
                minPrices[accessory.accessoryShowId] = accessory.price
            }
        }
        return minPrices
    }

    /**
     * Load a single accessory show by its raw MongoDB id **only when it is `Open`**.
     *
     * Backs `GET /public/accessory/show/fetch?id=` (Req 1.3). A missing or `Closed` show resolves to
     * `null` so the caller can return a not-found response (Req 1.6). `rawId` is the decoded, already
     * validated `ObjectId` hex string produced by [metaticket.accessory.security.PublicId.decode].
     */
    suspend fun loadOpenShow(rawId: String): AccessoryShowDocument? {
        val filter = Filters.and(
            Filters.eq("_id", ObjectId(rawId)),
            Filters.eq(AccessoryShowDocument::status.name, AccessoryShowStatus.Open.name),
        )
        return withContext(Dispatchers.IO) { accessoryShowCol.find(filter) }.firstOrNull()
    }

    /**
     * Load an accessory show by Canonical_Slug (any status).
     * Backs `GET /public/accessory/show/fetch/slug?slug=`. Missing → null.
     */
    suspend fun loadShowBySlug(canonicalSlug: String): AccessoryShowDocument? {
        if (canonicalSlug.isBlank()) return null
        val filter = Filters.eq(AccessoryShowDocument::slug.name, canonicalSlug)
        return withContext(Dispatchers.IO) { accessoryShowCol.find(filter) }.firstOrNull()
    }

    /**
     * Load the **active** accessories (`active == true`) belonging to the given accessory show,
     * including each accessory's variants (variants are embedded on the document).
     *
     * Backs the accessory portion of `GET /public/accessory/show/fetch?id=` (Req 1.3, 1.4). Inactive
     * accessories are excluded from the public catalog. `showRawId` is the raw `ObjectId` hex string
     * of the owning show.
     */
    suspend fun loadActiveAccessories(showRawId: String): List<AccessoryDocument> {
        val filter = Filters.and(
            Filters.eq(AccessoryDocument::accessoryShowId.name, ObjectId(showRawId)),
            Filters.eq(AccessoryDocument::active.name, true),
        )
        return withContext(Dispatchers.IO) { accessoryCol.find(filter) }.toList()
    }

    /**
     * Load **all** accessories for a show (active + inactive) for the admin product list.
     * Sorted by `lastModified` descending.
     */
    suspend fun loadAccessoriesByShowId(showRawId: String): List<AccessoryDocument> {
        val filter = Filters.eq(AccessoryDocument::accessoryShowId.name, ObjectId(showRawId))
        return withContext(Dispatchers.IO) {
            accessoryCol.find(filter).toList()
                .sortedByDescending { it.lastModified.time }
        }
    }

    /**
     * Load a single **active** accessory by its raw `ObjectId`, including its embedded variants.
     *
     * Backs `GET /public/accessory/fetch?id=`, the storefront's product-detail popup poll: the popup
     * refreshes one product's live stock (top-level and per-variant) so a sold-out product/variant is
     * reflected before the customer adds it to the cart. Returns `null` when the id matches no
     * accessory or the accessory is inactive (hidden from the public storefront). `rawId` is the
     * decoded, already validated `ObjectId` hex string.
     */
    suspend fun loadActiveAccessoryById(rawId: String): AccessoryDocument? {
        val filter = Filters.and(
            Filters.eq("_id", ObjectId(rawId)),
            Filters.eq(AccessoryDocument::active.name, true),
        )
        return withContext(Dispatchers.IO) { accessoryCol.find(filter) }.firstOrNull()
    }

    // -------------------------------------------------------------------------
    // Checkout catalog re-validation (task 6.2)
    // -------------------------------------------------------------------------

    /**
     * Load every accessory whose raw `ObjectId` is in [rawIds], **regardless of `active` state or
     * owning show**.
     *
     * Backs the catalog re-validation step of `checkout` (Req 6.3, 6.4): loading without the
     * `active` filter lets `checkout` distinguish a genuinely non-existent id (⇒ `404`, Req 6.3)
     * from an existing-but-inactive accessory (⇒ validation error, Req 6.4). Each `rawId` is a
     * decoded, already validated `ObjectId` hex string (the payload gate rejects malformed ids
     * before this is reached). Duplicate ids in [rawIds] are de-duplicated by the query; the caller
     * indexes the result by id.
     */
    suspend fun loadAccessoriesByIds(rawIds: Collection<String>): List<AccessoryDocument> {
        if (rawIds.isEmpty()) return emptyList()
        val objectIds = rawIds.toSet().map { ObjectId(it) }
        val filter = Filters.`in`("_id", objectIds)
        return withContext(Dispatchers.IO) { accessoryCol.find(filter) }.toList()
    }

    /**
     * Load a single accessory show by its raw MongoDB id, **regardless of status**.
     *
     * Backs the single-Open-show check of `checkout` (Req 6.4): `checkout` loads the show referenced
     * by its accessories and asserts `status == Open` itself, so it can emit the offending-lines
     * validation error rather than a bare not-found. `rawId` is the decoded, already validated
     * `ObjectId` hex string of the owning show.
     */
    suspend fun loadShowById(rawId: String): AccessoryShowDocument? {
        val filter = Filters.eq("_id", ObjectId(rawId))
        return withContext(Dispatchers.IO) { accessoryShowCol.find(filter) }.firstOrNull()
    }

    /**
     * Load a show by unique human-readable [code] (any status). Used to return a clear 409 on create
     * when the code is already taken.
     */
    suspend fun loadShowByCode(code: String): AccessoryShowDocument? {
        val filter = Filters.eq(AccessoryShowDocument::code.name, code)
        return withContext(Dispatchers.IO) { accessoryShowCol.find(filter) }.firstOrNull()
    }

    /**
     * Insert a new accessory show document. Caller must validate and ensure [code] uniqueness.
     * Returns the inserted document (same instance).
     */
    suspend fun insertShow(doc: AccessoryShowDocument): AccessoryShowDocument {
        withContext(Dispatchers.IO) { accessoryShowCol.insertOne(doc) }
        return doc
    }

    /**
     * Replace an existing accessory show by `_id`. Returns null when no document matched.
     */
    suspend fun replaceShow(doc: AccessoryShowDocument): AccessoryShowDocument? {
        val result = withContext(Dispatchers.IO) {
            accessoryShowCol.replaceOne(Filters.eq("_id", ObjectId(doc.id)), doc)
        }
        return if (result.matchedCount == 1L) doc else null
    }

    /**
     * Update only [AccessoryShowDocument.status] (+ lastModified) and return the document after
     * update, or null when the id does not exist.
     */
    suspend fun updateShowStatus(rawId: String, status: AccessoryShowStatus): AccessoryShowDocument? {
        return withContext(Dispatchers.IO) {
            accessoryShowCol.findOneAndUpdate(
                Filters.eq("_id", ObjectId(rawId)),
                Updates.combine(
                    Updates.set(AccessoryShowDocument::status.name, status),
                    Updates.set(AccessoryShowDocument::lastModified.name, Date()),
                ),
                FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
            )
        }
    }

    /**
     * CAS: Open a Waiting show whose `saleOpenAt` is already due (or slightly early — caller
     * may pass a slack `now`). Returns the updated document, or null when missing / not Waiting /
     * not yet due (stale job or admin already changed status).
     */
    suspend fun openShowIfWaitingAndDue(rawId: String, now: Date = Date()): AccessoryShowDocument? {
        return withContext(Dispatchers.IO) {
            accessoryShowCol.findOneAndUpdate(
                Filters.and(
                    Filters.eq("_id", ObjectId(rawId)),
                    Filters.eq(AccessoryShowDocument::status.name, AccessoryShowStatus.Waiting.name),
                    Filters.lte(AccessoryShowDocument::saleOpenAt.name, now),
                ),
                Updates.combine(
                    Updates.set(AccessoryShowDocument::status.name, AccessoryShowStatus.Open.name),
                    Updates.set(AccessoryShowDocument::lastModified.name, now),
                ),
                FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
            )
        }
    }

    /**
     * Waiting shows whose `saleOpenAt` is already due. Used by the in-process sweep so a missed
     * JobRunr fire (restart, callback fail) still Open.
     */
    suspend fun findWaitingShowsDue(now: Date = Date()): List<AccessoryShowDocument> {
        val filter = Filters.and(
            Filters.eq(AccessoryShowDocument::status.name, AccessoryShowStatus.Waiting.name),
            Filters.lte(AccessoryShowDocument::saleOpenAt.name, now),
        )
        return withContext(Dispatchers.IO) { accessoryShowCol.find(filter).toList() }
    }

    /**
     * Update only [AccessoryShowDocument.pinOrder] (+ lastModified) and return the document after
     * update, or null when the id does not exist. Pass null to clear the pin (`$unset` — BSON null
     * breaks Kotlin DataClassCodec for `Int?`).
     */
    suspend fun updateShowPinOrder(rawId: String, pinOrder: Int?): AccessoryShowDocument? {
        val pinUpdate = if (pinOrder == null) {
            Updates.unset(AccessoryShowDocument::pinOrder.name)
        } else {
            Updates.set(AccessoryShowDocument::pinOrder.name, pinOrder)
        }
        return withContext(Dispatchers.IO) {
            accessoryShowCol.findOneAndUpdate(
                Filters.eq("_id", ObjectId(rawId)),
                Updates.combine(
                    pinUpdate,
                    Updates.set(AccessoryShowDocument::lastModified.name, Date()),
                ),
                FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
            )
        }
    }

    /**
     * Accessory shows occupying pin ranks 1..[maxOrder], sorted by pinOrder.
     * Does not bump lastModified — pin rank is editorial.
     */
    suspend fun loadPinnedShows(maxOrder: Int): List<AccessoryShowDocument> {
        val filter = Filters.and(
            Filters.exists(AccessoryShowDocument::pinOrder.name),
            Filters.gte(AccessoryShowDocument::pinOrder.name, 1),
            Filters.lte(AccessoryShowDocument::pinOrder.name, maxOrder),
        )
        return withContext(Dispatchers.IO) {
            accessoryShowCol.find(filter)
                .sort(Sorts.ascending(AccessoryShowDocument::pinOrder.name))
                .toList()
        }
    }

    /** Newest [limit] accessory shows by lastModified — used by “Làm mới danh sách ghim”. */
    suspend fun loadMostRecentlyModifiedShows(limit: Int): List<AccessoryShowDocument> {
        return withContext(Dispatchers.IO) {
            accessoryShowCol.find()
                .sort(Sorts.descending(AccessoryShowDocument::lastModified.name))
                .limit(limit)
                .toList()
        }
    }

    data class AccessoryShowSearchPage(
        val items: List<AccessoryShowDocument>,
        val total: Long,
    )

    /** Paginated picker (1-based [page]); optional name/code keyword. */
    suspend fun searchShowsForPinPicker(
        page: Int,
        size: Int,
        keyword: String?,
    ): AccessoryShowSearchPage {
        val safePage = page.coerceAtLeast(1)
        val safeSize = size.coerceIn(1, 50)
        val q = keyword?.trim().orEmpty()
        val filter = if (q.isEmpty()) {
            Filters.empty()
        } else {
            val escaped = java.util.regex.Pattern.quote(q)
            Filters.or(
                Filters.regex(AccessoryShowDocument::name.name, escaped, "i"),
                Filters.regex(AccessoryShowDocument::code.name, escaped, "i"),
            )
        }
        val skip = (safePage - 1) * safeSize
        return withContext(Dispatchers.IO) {
            val total = accessoryShowCol.countDocuments(filter)
            val items = accessoryShowCol.find(filter)
                .sort(Sorts.descending(AccessoryShowDocument::lastModified.name))
                .skip(skip)
                .limit(safeSize)
                .toList()
            AccessoryShowSearchPage(items, total)
        }
    }

    /**
     * Storefront keyword search (0-based [page]) for the "Sản phẩm" column of unified search.
     * Unlike [rankShowsForStorefront] (browse order: pins → Waiting → Open → Closed), this ranks by
     * match quality first — exact name/code match, then prefix match, then the rest, then a
     * description-only match — so a strong match always outranks an editorially-pinned but unrelated
     * show. Ignore-case and ignore-diacritics both happen at the Mongo level (via
     * [vietnameseInsensitivePattern] expanding each letter into a character class of its accented
     * variants, plus the regex "i" flag).
     *
     * Name/code and description are queried as two separate, capped buckets rather than one combined
     * `OR` filter: `find().limit()` has no `sort`, so a single query could let enough description-only
     * noise into the first [SEARCH_MATCH_CAP] docs to push an exact name/code match out of the result
     * entirely. Querying the name/code bucket first and only spending the remaining budget on
     * description matches guarantees a name/code hit is never dropped by description noise.
     */
    suspend fun searchShowsForStorefront(
        keyword: String,
        page: Int,
        size: Int,
    ): AccessoryShowSearchPage {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 50)
        val q = keyword.trim().take(64)
        if (q.isEmpty()) {
            return AccessoryShowSearchPage(emptyList(), 0)
        }
        val normalizedQ = normalizeVietnamese(q)
        val pattern = vietnameseInsensitivePattern(q)
        val nameFilter = Filters.or(
            Filters.regex(AccessoryShowDocument::name.name, pattern, "i"),
            Filters.regex(AccessoryShowDocument::code.name, pattern, "i"),
        )
        val descFilter = Filters.and(
            Filters.regex(AccessoryShowDocument::description.name, pattern, "i"),
            Filters.nor(nameFilter),
        )
        return withContext(Dispatchers.IO) {
            val total = accessoryShowCol.countDocuments(Filters.or(nameFilter, descFilter))
            val nameMatches = accessoryShowCol.find(nameFilter)
                .limit(SEARCH_MATCH_CAP)
                .toList()
            val descBudget = SEARCH_MATCH_CAP - nameMatches.size
            val descMatches = if (descBudget > 0) {
                accessoryShowCol.find(descFilter)
                    .limit(descBudget)
                    .toList()
            } else {
                emptyList()
            }
            val rankedName = nameMatches.sortedWith(
                compareBy<AccessoryShowDocument> { matchRank(it, normalizedQ) }
                    .thenBy { statusRank(it.status) }
                    .thenByDescending { it.createdAt.time }
            )
            val rankedDesc = descMatches.sortedWith(
                compareBy<AccessoryShowDocument> { statusRank(it.status) }
                    .thenByDescending { it.createdAt.time }
            )
            val ranked = rankedName + rankedDesc
            val slice = ranked.drop(safePage * safeSize).take(safeSize)
            AccessoryShowSearchPage(slice, minOf(total, SEARCH_MATCH_CAP.toLong()))
        }
    }

    /** 0 = exact name/code match, 1 = name/code starts with the keyword, 2 = matched name/code elsewhere. */
    private fun matchRank(show: AccessoryShowDocument, normalizedKeyword: String): Int {
        val name = normalizeVietnamese(show.name)
        val code = normalizeVietnamese(show.code)
        return when {
            name == normalizedKeyword || code == normalizedKeyword -> 0
            name.startsWith(normalizedKeyword) || code.startsWith(normalizedKeyword) -> 1
            else -> 2
        }
    }

    private fun statusRank(status: AccessoryShowStatus): Int = when (status) {
        AccessoryShowStatus.Open -> 0
        AccessoryShowStatus.Waiting -> 1
        AccessoryShowStatus.Closed -> 2
    }

    /**
     * Rewrite accessory pins: clear previous pinOrder, then assign [orderedShowIds] as 1..n.
     * Empty ids are skipped; later duplicates overwrite. Does not touch lastModified.
     */
    suspend fun replaceAccessoryPins(orderedShowIds: List<String>): Unit {
        withContext(Dispatchers.IO) {
            accessoryShowCol.updateMany(
                Filters.exists(AccessoryShowDocument::pinOrder.name),
                Updates.unset(AccessoryShowDocument::pinOrder.name),
            )
        }
        orderedShowIds.forEachIndexed { index, rawId ->
            val id = rawId.trim()
            if (id.isEmpty() || !ObjectId.isValid(id)) return@forEachIndexed
            withContext(Dispatchers.IO) {
                accessoryShowCol.updateOne(
                    Filters.eq("_id", ObjectId(id)),
                    Updates.set(AccessoryShowDocument::pinOrder.name, index + 1),
                )
            }
        }
    }

    /**
     * Load a single accessory by raw id **regardless of active** (admin get/edit).
     */
    suspend fun loadAccessoryById(rawId: String): AccessoryDocument? {
        val filter = Filters.eq("_id", ObjectId(rawId))
        return withContext(Dispatchers.IO) { accessoryCol.find(filter) }.firstOrNull()
    }

    /**
     * Find an accessory by (showId, sku), optionally excluding [excludeId] (update uniqueness check).
     */
    suspend fun loadAccessoryByShowIdAndSku(
        showRawId: String,
        sku: String,
        excludeId: String? = null,
    ): AccessoryDocument? {
        val filters = mutableListOf(
            Filters.eq(AccessoryDocument::accessoryShowId.name, ObjectId(showRawId)),
            Filters.eq(AccessoryDocument::sku.name, sku),
        )
        if (!excludeId.isNullOrBlank()) {
            filters += Filters.ne("_id", ObjectId(excludeId))
        }
        return withContext(Dispatchers.IO) {
            accessoryCol.find(Filters.and(filters)).firstOrNull()
        }
    }

    /**
     * Insert a new accessory. Strips optional BSON-null fields that break Kotlin DataClassCodec.
     */
    suspend fun insertAccessory(doc: AccessoryDocument): AccessoryDocument {
        withContext(Dispatchers.IO) { accessoryCol.insertOne(doc) }
        stripAccessoryOptionalNulls(doc.id, doc)
        return loadAccessoryById(doc.id) ?: doc
    }

    /**
     * Replace an accessory by `_id`. Returns null when no document matched.
     * Strips optional BSON-null fields after replace.
     */
    suspend fun replaceAccessory(doc: AccessoryDocument): AccessoryDocument? {
        val result = withContext(Dispatchers.IO) {
            accessoryCol.replaceOne(Filters.eq("_id", ObjectId(doc.id)), doc)
        }
        if (result.matchedCount != 1L) return null
        stripAccessoryOptionalNulls(doc.id, doc)
        return loadAccessoryById(doc.id) ?: doc
    }

    /**
     * Update only [AccessoryDocument.active] (+ lastModified). Returns null when id missing.
     */
    suspend fun updateAccessoryActive(rawId: String, active: Boolean): AccessoryDocument? {
        return withContext(Dispatchers.IO) {
            accessoryCol.findOneAndUpdate(
                Filters.eq("_id", ObjectId(rawId)),
                Updates.combine(
                    Updates.set(AccessoryDocument::active.name, active),
                    Updates.set(AccessoryDocument::lastModified.name, Date()),
                ),
                FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
            )
        }
    }

    /**
     * Admin catalog update that never overwrites existing variant stock cells.
     * - Patches label/active on existing keys via positional `$`.
     * - `$push`es newly added variants (with their initial stock) without rewriting the array.
     */
    suspend fun updateAccessoryCatalog(
        doc: AccessoryDocument,
        existingBefore: AccessoryDocument,
    ): AccessoryDocument? {
        val oid = ObjectId(doc.id)
        val topLevel = mutableListOf<Bson>(
            Updates.set(AccessoryDocument::sku.name, doc.sku),
            Updates.set(AccessoryDocument::name.name, doc.name),
            Updates.set(AccessoryDocument::imageUrls.name, doc.imageUrls),
            Updates.set(AccessoryDocument::price.name, doc.price),
            Updates.set(AccessoryDocument::currency.name, doc.currency),
            Updates.set(AccessoryDocument::active.name, doc.active),
            Updates.set(AccessoryDocument::version.name, doc.version),
            Updates.set(AccessoryDocument::lastModified.name, doc.lastModified),
        )
        if (doc.description != null) {
            topLevel += Updates.set(AccessoryDocument::description.name, doc.description)
        } else {
            topLevel += Updates.unset(AccessoryDocument::description.name)
        }
        if (doc.imageUrl != null) {
            topLevel += Updates.set(AccessoryDocument::imageUrl.name, doc.imageUrl)
        } else {
            topLevel += Updates.unset(AccessoryDocument::imageUrl.name)
        }
        if (doc.attr1Name != null) {
            topLevel += Updates.set(AccessoryDocument::attr1Name.name, doc.attr1Name)
        } else {
            topLevel += Updates.unset(AccessoryDocument::attr1Name.name)
        }
        if (doc.attr2Name != null) {
            topLevel += Updates.set(AccessoryDocument::attr2Name.name, doc.attr2Name)
        } else {
            topLevel += Updates.unset(AccessoryDocument::attr2Name.name)
        }
        if (doc.minOrderQty != null) {
            topLevel += Updates.set(AccessoryDocument::minOrderQty.name, doc.minOrderQty)
        } else {
            topLevel += Updates.unset(AccessoryDocument::minOrderQty.name)
        }
        if (doc.maxOrderQty != null) {
            topLevel += Updates.set(AccessoryDocument::maxOrderQty.name, doc.maxOrderQty)
        } else {
            topLevel += Updates.unset(AccessoryDocument::maxOrderQty.name)
        }
        if (doc.maxPerUserQty != null) {
            topLevel += Updates.set(AccessoryDocument::maxPerUserQty.name, doc.maxPerUserQty)
        } else {
            topLevel += Updates.unset(AccessoryDocument::maxPerUserQty.name)
        }

        val updated = withContext(Dispatchers.IO) {
            accessoryCol.findOneAndUpdate(
                Filters.eq("_id", oid),
                Updates.combine(topLevel),
                FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
            )
        } ?: return null

        val existingKeys = existingBefore.variants.map { it.key }.toSet()
        for (v in doc.variants) {
            if (v.key in existingKeys) {
                withContext(Dispatchers.IO) {
                    accessoryCol.updateOne(
                        Filters.and(
                            Filters.eq("_id", oid),
                            Filters.elemMatch(
                                AccessoryDocument::variants.name,
                                Filters.eq("key", v.key),
                            ),
                        ),
                        Updates.combine(
                            Updates.set("variants.$.label", v.label),
                            Updates.set("variants.$.active", v.active),
                            Updates.set("variants.$.imageUrl", v.imageUrl),
                        ),
                    )
                }
            } else {
                val variantDoc = org.bson.Document().apply {
                    put("key", v.key)
                    put("label", v.label)
                    put("stock", v.stock.coerceAtLeast(0))
                    if (v.attr1 != null) put("attr1", v.attr1)
                    if (v.attr2 != null) put("attr2", v.attr2)
                    if (v.imageUrl != null) put("imageUrl", v.imageUrl)
                    put("active", v.active)
                }
                withContext(Dispatchers.IO) {
                    accessoryCol.updateOne(
                        Filters.and(
                            Filters.eq("_id", oid),
                            Filters.not(
                                Filters.elemMatch(
                                    AccessoryDocument::variants.name,
                                    Filters.eq("key", v.key),
                                ),
                            ),
                        ),
                        Updates.push(AccessoryDocument::variants.name, variantDoc),
                    )
                }
            }
        }
        return loadAccessoryById(doc.id) ?: updated
    }

    /** Outcome of an admin stock adjustment. */
    enum class StockAdjustOutcome {
        OK,
        NOT_FOUND,
        CELL_NOT_FOUND,
        UNLIMITED_BLOCKED,
        INSUFFICIENT,
        INVALID,
    }

    /**
     * Atomically adjust one sellable cell (top-level or variant) for admin restock/reduce.
     * - [delta]: guarded `$inc` (blocked when cell is unlimited).
     * - [setTo]: absolute finite sellable (`>= 0`). Setting unlimited (-1) is not supported here.
     */
    suspend fun adjustAccessoryStock(
        rawId: String,
        variantKey: String?,
        delta: Int?,
        setTo: Int?,
    ): Pair<StockAdjustOutcome, AccessoryDocument?> {
        val existing = loadAccessoryById(rawId) ?: return StockAdjustOutcome.NOT_FOUND to null
        val key = variantKey?.trim()?.takeIf { it.isNotEmpty() }

        if (existing.hasVariants) {
            if (key == null) return StockAdjustOutcome.INVALID to existing
            if (existing.variants.none { it.key == key }) return StockAdjustOutcome.CELL_NOT_FOUND to existing
        } else if (key != null) {
            return StockAdjustOutcome.INVALID to existing
        }

        val outcome = when {
            setTo != null -> {
                if (setTo < 0) StockAdjustOutcome.INVALID
                else setStockCell(rawId, key, setTo)
            }
            delta != null && delta != 0 -> incStockCell(rawId, key, delta)
            else -> StockAdjustOutcome.INVALID
        }
        if (outcome != StockAdjustOutcome.OK) return outcome to existing
        return StockAdjustOutcome.OK to (loadAccessoryById(rawId) ?: existing)
    }

    /** Sets top-level or variant stock to an absolute value. */
    private suspend fun setStockCell(rawId: String, variantKey: String?, value: Int): StockAdjustOutcome {
        val oid = ObjectId(rawId)
        val modified = withContext(Dispatchers.IO) {
            if (variantKey == null) {
                accessoryCol.updateOne(
                    Filters.eq("_id", oid),
                    Updates.combine(
                        Updates.set(AccessoryDocument::stock.name, value),
                        Updates.set(AccessoryDocument::lastModified.name, Date()),
                    ),
                ).modifiedCount == 1L
            } else {
                accessoryCol.updateOne(
                    Filters.and(
                        Filters.eq("_id", oid),
                        Filters.elemMatch(
                            AccessoryDocument::variants.name,
                            Filters.eq("key", variantKey),
                        ),
                    ),
                    Updates.combine(
                        Updates.set("variants.$.stock", value),
                        Updates.set(AccessoryDocument::lastModified.name, Date()),
                    ),
                ).modifiedCount == 1L
            }
        }
        return if (modified) StockAdjustOutcome.OK else StockAdjustOutcome.CELL_NOT_FOUND
    }

    /**
     * Atomic `$inc` on one cell. Rejects unlimited cells and negative overshoot (stock would go &lt; 0).
     */
    private suspend fun incStockCell(rawId: String, variantKey: String?, delta: Int): StockAdjustOutcome {
        val oid = ObjectId(rawId)
        // Unlimited cells must not be `$inc`'d — detect first.
        val current = loadAccessoryById(rawId) ?: return StockAdjustOutcome.NOT_FOUND
        val cell = if (variantKey == null) {
            current.stock
        } else {
            current.variants.find { it.key == variantKey }?.stock ?: return StockAdjustOutcome.CELL_NOT_FOUND
        }
        if (cell == UNLIMITED_STOCK) return StockAdjustOutcome.UNLIMITED_BLOCKED

        val modified = withContext(Dispatchers.IO) {
            if (variantKey == null) {
                val filter = if (delta < 0) {
                    Filters.and(
                        Filters.eq("_id", oid),
                        Filters.gte(AccessoryDocument::stock.name, -delta),
                    )
                } else {
                    Filters.and(
                        Filters.eq("_id", oid),
                        Filters.gte(AccessoryDocument::stock.name, 0),
                    )
                }
                accessoryCol.updateOne(
                    filter,
                    Updates.combine(
                        Updates.inc(AccessoryDocument::stock.name, delta),
                        Updates.set(AccessoryDocument::lastModified.name, Date()),
                    ),
                ).modifiedCount == 1L
            } else {
                val stockGuard = if (delta < 0) {
                    Filters.and(Filters.eq("key", variantKey), Filters.gte("stock", -delta))
                } else {
                    Filters.and(Filters.eq("key", variantKey), Filters.gte("stock", 0))
                }
                accessoryCol.updateOne(
                    Filters.and(
                        Filters.eq("_id", oid),
                        Filters.elemMatch(AccessoryDocument::variants.name, stockGuard),
                    ),
                    Updates.combine(
                        Updates.inc("variants.$.stock", delta),
                        Updates.set(AccessoryDocument::lastModified.name, Date()),
                    ),
                ).modifiedCount == 1L
            }
        }
        return if (modified) StockAdjustOutcome.OK else StockAdjustOutcome.INSUFFICIENT
    }

    /**
     * Removes optional string fields stored as BSON null so DataClassCodec can decode later.
     * Absent field ⇒ Kotlin null via default; BSON null ⇒ decode failure.
     */
    private suspend fun stripAccessoryOptionalNulls(rawId: String, doc: AccessoryDocument) {
        val unsets = mutableListOf<Bson>()
        if (doc.description == null) unsets += Updates.unset(AccessoryDocument::description.name)
        if (doc.imageUrl == null) unsets += Updates.unset(AccessoryDocument::imageUrl.name)
        if (doc.attr1Name == null) unsets += Updates.unset(AccessoryDocument::attr1Name.name)
        if (doc.attr2Name == null) unsets += Updates.unset(AccessoryDocument::attr2Name.name)
        if (doc.minOrderQty == null) unsets += Updates.unset(AccessoryDocument::minOrderQty.name)
        if (doc.maxOrderQty == null) unsets += Updates.unset(AccessoryDocument::maxOrderQty.name)
        if (doc.maxPerUserQty == null) unsets += Updates.unset(AccessoryDocument::maxPerUserQty.name)
        // Nested variant optional string nulls — rewrite variants without BSON-null keys.
        if (doc.variants.any { it.attr1 == null || it.attr2 == null || it.imageUrl == null }) {
            normalizeAccessoryVariantsBson(rawId, doc)
        }
        if (unsets.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                accessoryCol.updateOne(Filters.eq("_id", ObjectId(rawId)), Updates.combine(unsets))
            }
        }
    }

    /**
     * Rewrites the `variants` array without null attr1/attr2/imageUrl keys (BSON-null-safe for DataClassCodec).
     */
    private suspend fun normalizeAccessoryVariantsBson(rawId: String, doc: AccessoryDocument) {
        if (doc.variants.isEmpty()) return
        val variantDocs = doc.variants.map { v ->
            org.bson.Document().apply {
                put("key", v.key)
                put("label", v.label)
                put("stock", v.stock)
                if (v.attr1 != null) put("attr1", v.attr1)
                if (v.attr2 != null) put("attr2", v.attr2)
                if (v.imageUrl != null) put("imageUrl", v.imageUrl)
                put("active", v.active)
            }
        }
        withContext(Dispatchers.IO) {
            accessoryCol.updateOne(
                Filters.eq("_id", ObjectId(rawId)),
                Updates.set(AccessoryDocument::variants.name, variantDocs),
            )
        }
    }

    // -------------------------------------------------------------------------
    // Stock decrement on payment success (task 7.1 — design "Handling on the accessory side")
    // -------------------------------------------------------------------------

    /**
     * Outcome of a guarded stock decrement (design "Handling on the accessory side", Req 9.2, 9.3).
     *
     * - [DECREMENTED]        — the full line quantity was available and was atomically subtracted.
     * - [UNLIMITED_SKIPPED]  — the applicable stock cell is unlimited (`-1`); no decrement is done
     *   and the line is not counted as a shortfall (Req 9.2 "skipping lines whose stock is
     *   unlimited").
     * - [FLOORED_SHORTFALL]  — the cell held less than the line quantity; it was floored at `0` and
     *   the caller must record a `STOCK_SHORTFALL` reconciliation entry (Req 9.3).
     */
    enum class StockDecrementOutcome { DECREMENTED, UNLIMITED_SKIPPED, FLOORED_SHORTFALL }

    /**
     * Atomically decrement a **variant's** stock (`variants.$.stock`) by [qty], never below zero
     * (Req 9.2, 9.3).
     *
     * Strategy (design guarded `$inc`):
     * 1. Fast path — decrement only if the matched variant has `stock >= qty`. A guarded `$inc`
     *    matched on `(key, stock >= qty)` is atomic and safe against concurrent callbacks. Because a
     *    line quantity is always `>= 1`, the unlimited sentinel (`-1`) never satisfies `stock >= qty`,
     *    so the fast path never touches an unlimited cell.
     * 2. Unlimited — if the matched variant is unlimited (`stock == -1`), skip (no decrement).
     * 3. Shortfall — otherwise the cell held `0..qty-1`; floor it at `0` and report a shortfall.
     *
     * @return the [StockDecrementOutcome] so the caller can record a shortfall reconciliation entry.
     */
    suspend fun decrementVariantStock(accessoryId: String, variantKey: String, qty: Int): StockDecrementOutcome {
        val oid = ObjectId(accessoryId)

        // 1. Fast path: guarded $inc when the variant has enough stock (and is not unlimited).
        val decremented = withContext(Dispatchers.IO) {
            accessoryCol.updateOne(
                Filters.and(
                    Filters.eq("_id", oid),
                    Filters.elemMatch(
                        AccessoryDocument::variants.name,
                        Filters.and(Filters.eq("key", variantKey), Filters.gte("stock", qty)),
                    ),
                ),
                Updates.inc("variants.$.stock", -qty),
            )
        }.modifiedCount == 1L
        if (decremented) return StockDecrementOutcome.DECREMENTED

        // 2. Unlimited variant → skip (Req 9.2).
        val unlimited = withContext(Dispatchers.IO) {
            accessoryCol.find(
                Filters.and(
                    Filters.eq("_id", oid),
                    Filters.elemMatch(
                        AccessoryDocument::variants.name,
                        Filters.and(Filters.eq("key", variantKey), Filters.eq("stock", UNLIMITED_STOCK)),
                    ),
                )
            )
        }.firstOrNull() != null
        if (unlimited) return StockDecrementOutcome.UNLIMITED_SKIPPED

        // 3. Shortfall → floor the matched variant at 0 (Req 9.3). Guard `stock >= 0` so an
        //    already-floored / concurrently-updated cell is left untouched.
        withContext(Dispatchers.IO) {
            accessoryCol.updateOne(
                Filters.and(
                    Filters.eq("_id", oid),
                    Filters.elemMatch(
                        AccessoryDocument::variants.name,
                        Filters.and(Filters.eq("key", variantKey), Filters.gte("stock", 0)),
                    ),
                ),
                Updates.set("variants.$.stock", 0),
            )
        }
        return StockDecrementOutcome.FLOORED_SHORTFALL
    }

    /**
     * Atomically decrement a **single-SKU** accessory's top-level `stock` by [qty], never below zero
     * (Req 9.2, 9.3). Same guarded strategy as [decrementVariantStock] applied to the top-level cell.
     *
     * @return the [StockDecrementOutcome] so the caller can record a shortfall reconciliation entry.
     */
    suspend fun decrementStock(accessoryId: String, qty: Int): StockDecrementOutcome {
        val oid = ObjectId(accessoryId)

        // 1. Fast path: guarded $inc when there is enough stock (excludes unlimited, since qty >= 1).
        val decremented = withContext(Dispatchers.IO) {
            accessoryCol.updateOne(
                Filters.and(
                    Filters.eq("_id", oid),
                    Filters.gte(AccessoryDocument::stock.name, qty),
                ),
                Updates.inc(AccessoryDocument::stock.name, -qty),
            )
        }.modifiedCount == 1L
        if (decremented) return StockDecrementOutcome.DECREMENTED

        // 2. Unlimited → skip (Req 9.2).
        val unlimited = withContext(Dispatchers.IO) {
            accessoryCol.find(
                Filters.and(
                    Filters.eq("_id", oid),
                    Filters.eq(AccessoryDocument::stock.name, UNLIMITED_STOCK),
                )
            )
        }.firstOrNull() != null
        if (unlimited) return StockDecrementOutcome.UNLIMITED_SKIPPED

        // 3. Shortfall → floor at 0 (Req 9.3), guarded `stock >= 0` so it never goes negative.
        withContext(Dispatchers.IO) {
            accessoryCol.updateOne(
                Filters.and(
                    Filters.eq("_id", oid),
                    Filters.gte(AccessoryDocument::stock.name, 0),
                ),
                Updates.set(AccessoryDocument::stock.name, 0),
            )
        }
        return StockDecrementOutcome.FLOORED_SHORTFALL
    }

    // -------------------------------------------------------------------------
    // Reserve-on-create inventory (overselling fix): reserve stock atomically at order creation,
    // release it exactly once on a non-success terminal state (fail / cancel / expiry).
    // -------------------------------------------------------------------------

    /**
     * Outcome of a strict stock **reservation** attempt (reserve-on-create model).
     *
     * - [RESERVED]          — the full quantity was available and was atomically subtracted (held).
     * - [UNLIMITED_SKIPPED] — the cell is unlimited (`-1`); nothing is held and the line never blocks.
     * - [INSUFFICIENT]      — the cell held fewer than the requested units; nothing is changed (unlike
     *   [StockDecrementOutcome.FLOORED_SHORTFALL], a reservation never floors — the caller must roll
     *   back and reject the checkout so two concurrent buyers can never both hold the same last unit).
     *
     * `RESERVED` is first so a relaxed test mock defaults to the success outcome.
     */
    enum class StockReserveOutcome { RESERVED, UNLIMITED_SKIPPED, INSUFFICIENT }

    /**
     * Atomically reserve [qty] units of a **variant** (`variants.$.stock`) at order creation. The
     * guarded `$inc` matched on `(key, stock >= qty)` is the serialization point that prevents
     * overselling: only one of two concurrent checkouts can win the last units; the loser sees
     * [StockReserveOutcome.INSUFFICIENT] and is rejected. Unlimited variants (`stock == -1`) are never
     * decremented and report [StockReserveOutcome.UNLIMITED_SKIPPED].
     */
    suspend fun reserveVariantStock(accessoryId: String, variantKey: String, qty: Int): StockReserveOutcome {
        val oid = ObjectId(accessoryId)
        val reserved = withContext(Dispatchers.IO) {
            accessoryCol.updateOne(
                Filters.and(
                    Filters.eq("_id", oid),
                    Filters.elemMatch(
                        AccessoryDocument::variants.name,
                        Filters.and(Filters.eq("key", variantKey), Filters.gte("stock", qty)),
                    ),
                ),
                Updates.inc("variants.$.stock", -qty),
            )
        }.modifiedCount == 1L
        if (reserved) return StockReserveOutcome.RESERVED

        val unlimited = withContext(Dispatchers.IO) {
            accessoryCol.find(
                Filters.and(
                    Filters.eq("_id", oid),
                    Filters.elemMatch(
                        AccessoryDocument::variants.name,
                        Filters.and(Filters.eq("key", variantKey), Filters.eq("stock", UNLIMITED_STOCK)),
                    ),
                )
            )
        }.firstOrNull() != null
        return if (unlimited) StockReserveOutcome.UNLIMITED_SKIPPED else StockReserveOutcome.INSUFFICIENT
    }

    /**
     * Atomically reserve [qty] units of a **single-SKU** accessory's top-level `stock` at order
     * creation. Same guarded, non-flooring strategy as [reserveVariantStock].
     */
    suspend fun reserveStock(accessoryId: String, qty: Int): StockReserveOutcome {
        val oid = ObjectId(accessoryId)
        val reserved = withContext(Dispatchers.IO) {
            accessoryCol.updateOne(
                Filters.and(
                    Filters.eq("_id", oid),
                    Filters.gte(AccessoryDocument::stock.name, qty),
                ),
                Updates.inc(AccessoryDocument::stock.name, -qty),
            )
        }.modifiedCount == 1L
        if (reserved) return StockReserveOutcome.RESERVED

        val unlimited = withContext(Dispatchers.IO) {
            accessoryCol.find(
                Filters.and(
                    Filters.eq("_id", oid),
                    Filters.eq(AccessoryDocument::stock.name, UNLIMITED_STOCK),
                )
            )
        }.firstOrNull() != null
        return if (unlimited) StockReserveOutcome.UNLIMITED_SKIPPED else StockReserveOutcome.INSUFFICIENT
    }

    /**
     * Release (add back) [qty] units to a **variant's** stock when a reserved order fails / is
     * cancelled / expires. Guarded on `stock >= 0` so an unlimited cell (`-1`) is never touched — an
     * unlimited variant was never decremented at reservation, so it must not be inflated on release.
     */
    suspend fun releaseVariantStock(accessoryId: String, variantKey: String, qty: Int) {
        withContext(Dispatchers.IO) {
            accessoryCol.updateOne(
                Filters.and(
                    Filters.eq("_id", ObjectId(accessoryId)),
                    Filters.elemMatch(
                        AccessoryDocument::variants.name,
                        Filters.and(Filters.eq("key", variantKey), Filters.gte("stock", 0)),
                    ),
                ),
                Updates.inc("variants.$.stock", qty),
            )
        }
    }

    /**
     * Release (add back) [qty] units to a **single-SKU** accessory's top-level stock. Guarded on
     * `stock >= 0` so an unlimited cell (`-1`) is never touched.
     */
    suspend fun releaseStock(accessoryId: String, qty: Int) {
        withContext(Dispatchers.IO) {
            accessoryCol.updateOne(
                Filters.and(
                    Filters.eq("_id", ObjectId(accessoryId)),
                    Filters.gte(AccessoryDocument::stock.name, 0),
                ),
                Updates.inc(AccessoryDocument::stock.name, qty),
            )
        }
    }
}

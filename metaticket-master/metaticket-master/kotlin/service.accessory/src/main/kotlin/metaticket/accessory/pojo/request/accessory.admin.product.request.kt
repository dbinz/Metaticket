package metaticket.accessory.pojo.request

import metaticket.accessory.pojo.db.AccessoryDocument
import metaticket.accessory.pojo.db.AccessoryVariant
import metaticket.accessory.pojo.db.normalizeAttributeNames
import org.bson.types.ObjectId
import java.util.Date

/**
 * Admin upsert body for create/update accessory product
 * (`POST /private/accessory/create|update`).
 */
data class AdminAccessoryUpsertRequest(
    val accessoryShowId: String? = null,
    val sku: String? = null,
    val name: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val imageUrls: List<String> = emptyList(),
    val price: Long? = null,
    val currency: String? = null,
    val stock: Int? = null,
    val variants: List<AdminAccessoryVariantRequest> = emptyList(),
    /** Display name for attr1. Max 2 attributes per product with [attr2Name]. */
    val attr1Name: String? = null,
    /** Display name for attr2. */
    val attr2Name: String? = null,
    /** Min units of this product per checkout; null = unlimited. */
    val minOrderQty: Int? = null,
    /** Max units of this product per checkout; null = unlimited. */
    val maxOrderQty: Int? = null,
    /** Max units of this product per customer (Paid + open holds); null = unlimited. */
    val maxPerUserQty: Int? = null,
    val active: Boolean? = null,
)

/** One variant row from the admin product form. */
data class AdminAccessoryVariantRequest(
    val key: String? = null,
    val label: String? = null,
    val stock: Int? = null,
    val attr1: String? = null,
    val attr2: String? = null,
    /** Required when the product has variants. */
    val imageUrl: String? = null,
    val active: Boolean? = null,
)

/** Body for `POST /private/accessory/active`. */
data class AdminAccessoryActiveRequest(
    val active: Boolean? = null,
)

/**
 * Body for `POST /private/accessory/update?id=` — catalog fields.
 * Existing variant keys cannot be removed; new keys may be appended (attr1/attr2/stock for adds only).
 * Stock of existing keys is never overwritten here — use `/stock/adjust`.
 */
data class AdminAccessoryCatalogUpdateRequest(
    val sku: String? = null,
    val name: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val imageUrls: List<String> = emptyList(),
    val price: Long? = null,
    val currency: String? = null,
    val variants: List<AdminAccessoryVariantCatalogRequest> = emptyList(),
    val attr1Name: String? = null,
    val attr2Name: String? = null,
    val minOrderQty: Int? = null,
    val maxOrderQty: Int? = null,
    val maxPerUserQty: Int? = null,
    val active: Boolean? = null,
)

/**
 * Variant on catalog update.
 * Existing key → [label]/[active]/[imageUrl] applied; stock unchanged.
 * New key → created with [attr1]/[attr2]/[imageUrl]/[stock] (default 0).
 */
data class AdminAccessoryVariantCatalogRequest(
    val key: String? = null,
    val label: String? = null,
    val active: Boolean? = null,
    val attr1: String? = null,
    val attr2: String? = null,
    /** Required for every variant row (existing + new). */
    val imageUrl: String? = null,
    /** Initial sellable for **new** keys only (`>= 0`). Ignored for existing keys. */
    val stock: Int? = null,
)

/**
 * Body for `POST /private/accessory/stock/adjust?id=`.
 * Exactly one of: non-zero [delta], or [setTo] (finite; e.g. leave an existing -1 cell).
 * Setting unlimited (-1) via this API is intentionally not supported (too easy to misclick).
 */
data class AdminAccessoryStockAdjustRequest(
    /** Omit for single-SKU top-level stock; required when the product has variants. */
    val variantKey: String? = null,
    /** Atomic sellable change (`$inc`). Rejected when the cell is currently unlimited (-1). */
    val delta: Int? = null,
    /**
     * Absolute finite sellable for this cell. Prefer [delta] for restock/reduce.
     * Use to leave an existing unlimited cell. Must be `>= 0`.
     */
    val setTo: Int? = null,
)

/**
 * Collect validation errors for an admin product **create** before Mongo write.
 * [requireShowId] is true on create. Initial stock must be finite (`>= 0`) — no admin -1.
 */
fun AdminAccessoryUpsertRequest.productValidationErrors(requireShowId: Boolean): List<String> {
    val errors = mutableListOf<String>()
    if (requireShowId && (accessoryShowId.isNullOrBlank() || !ObjectId.isValid(accessoryShowId))) {
        errors += "`accessoryShowId` is required"
    }
    if (sku.isNullOrBlank()) errors += "`sku` is required"
    if (name.isNullOrBlank()) errors += "`name` is required"
    if (imageUrl.isNullOrBlank()) errors += "`imageUrl` is required"
    if (price == null || price <= 0L) errors += "`price` must be > 0"
    val variantDocs = variants.mapNotNull { it.takeIf { v -> !v.key.isNullOrBlank() || !v.label.isNullOrBlank() } }
    if (variantDocs.isEmpty()) {
        val stockVal = stock ?: 0
        if (stockVal < 0) errors += "`stock` must be >= 0 (unlimited via admin create is not allowed)"
    } else {
        val seen = mutableSetOf<String>()
        variantDocs.forEachIndexed { index, v ->
            val key = v.key?.trim().orEmpty()
            if (key.isBlank()) errors += "variants[$index].key is required"
            else if (!seen.add(key)) errors += "variants[$index].key must be unique"
            if (v.label.isNullOrBlank()) errors += "variants[$index].label is required"
            if (v.imageUrl.isNullOrBlank()) errors += "variants[$index].imageUrl is required"
            val vs = v.stock ?: 0
            if (vs < 0) errors += "variants[$index].stock must be >= 0 (unlimited via admin create is not allowed)"
        }
    }
    errors += purchaseLimitRequestErrors(minOrderQty, maxOrderQty, maxPerUserQty)
    return errors
}

/** Catalog-update shape validation. */
fun AdminAccessoryCatalogUpdateRequest.catalogUpdateValidationErrors(): List<String> {
    val errors = mutableListOf<String>()
    if (sku.isNullOrBlank()) errors += "`sku` is required"
    if (name.isNullOrBlank()) errors += "`name` is required"
    if (imageUrl.isNullOrBlank()) errors += "`imageUrl` is required"
    if (price == null || price <= 0L) errors += "`price` must be > 0"
    val variantDocs = variants.mapNotNull { it.takeIf { v -> !v.key.isNullOrBlank() || !v.label.isNullOrBlank() } }
    val seen = mutableSetOf<String>()
    variantDocs.forEachIndexed { index, v ->
        val key = v.key?.trim().orEmpty()
        if (key.isBlank()) errors += "variants[$index].key is required"
        else if (!seen.add(key)) errors += "variants[$index].key must be unique"
        if (v.label.isNullOrBlank()) errors += "variants[$index].label is required"
        if (v.imageUrl.isNullOrBlank()) errors += "variants[$index].imageUrl is required"
        val vs = v.stock
        if (vs != null && vs < 0) errors += "variants[$index].stock must be >= 0"
    }
    errors += purchaseLimitRequestErrors(minOrderQty, maxOrderQty, maxPerUserQty)
    return errors
}

/** Shared checks for optional purchase-limit fields on admin create/update bodies. */
private fun purchaseLimitRequestErrors(min: Int?, max: Int?, perUser: Int?): List<String> {
    val errors = mutableListOf<String>()
    if (min != null && min < 1) errors += "`minOrderQty` must be >= 1 when set"
    if (max != null && max < 1) errors += "`maxOrderQty` must be >= 1 when set"
    if (perUser != null && perUser < 1) errors += "`maxPerUserQty` must be >= 1 when set"
    if (min != null && max != null && min > max) errors += "`minOrderQty` must be <= `maxOrderQty`"
    if (max != null && perUser != null && max > perUser) errors += "`maxOrderQty` must be <= `maxPerUserQty`"
    if (min != null && perUser != null && min > perUser) errors += "`minOrderQty` must be <= `maxPerUserQty`"
    return errors
}

/**
 * Existing keys must all remain (no delete). New keys may be added.
 * Single-SKU products cannot grow a variants array via update (recreate instead).
 */
fun AdminAccessoryCatalogUpdateRequest.variantKeyLockErrors(existing: AccessoryDocument): List<String> {
    val requestKeys = variants.mapNotNull { v ->
        val key = v.key?.trim().orEmpty()
        val label = v.label?.trim().orEmpty()
        if (key.isBlank() && label.isBlank()) null else key
    }
    val existingKeys = existing.variants.map { it.key }
    if (existingKeys.isEmpty() && requestKeys.isNotEmpty()) {
        return listOf("cannot add variants to a single-SKU product — recreate the product")
    }
    if (existingKeys.isNotEmpty() && requestKeys.isEmpty()) {
        return listOf("cannot remove all variants")
    }
    val requestSet = requestKeys.toSet()
    val missing = existingKeys.filter { it !in requestSet }
    if (missing.isNotEmpty()) {
        return listOf("cannot remove variants (missing keys: ${missing.joinToString(", ")})")
    }
    return emptyList()
}

/** Validation for stock-adjust payload (shape only; cell existence checked later). */
fun AdminAccessoryStockAdjustRequest.stockAdjustValidationErrors(): List<String> {
    val errors = mutableListOf<String>()
    val modes = listOfNotNull(
        delta?.takeIf { it != 0 }?.let { "delta" },
        setTo?.let { "setTo" },
    )
    if (modes.isEmpty()) {
        errors += "provide non-zero `delta` or `setTo` (>= 0)"
    } else if (modes.size > 1) {
        errors += "provide only one of `delta` or `setTo`"
    }
    if (setTo != null && setTo < 0) errors += "`setTo` must be >= 0 (setting unlimited via adjust is not allowed)"
    if (delta == 0) errors += "`delta` must be non-zero"
    return errors
}

/** Maps admin upsert JSON into a new [AccessoryDocument] (initial stock finite only). */
fun AdminAccessoryUpsertRequest.toNewAccessoryDocument(): AccessoryDocument {
    val now = Date()
    val variantDocs = toVariantDocuments()
    val (n1, n2) = normalizeAttributeNames(attr1Name, attr2Name, variantDocs)
    // Product-level gallery only for single-SKU; variant products use each variant's imageUrl.
    val productGallery = if (variantDocs.isEmpty()) {
        imageUrls.map { it.trim() }.filter { it.isNotEmpty() }
    } else {
        emptyList()
    }
    return AccessoryDocument(
        id = ObjectId().toHexString(),
        accessoryShowId = accessoryShowId!!.trim(),
        sku = sku!!.trim(),
        name = name!!.trim(),
        description = description?.trim()?.takeIf { it.isNotEmpty() },
        imageUrl = imageUrl!!.trim(),
        imageUrls = productGallery,
        price = price!!,
        currency = currency?.trim()?.takeIf { it.isNotEmpty() } ?: "VND",
        stock = if (variantDocs.isEmpty()) (stock ?: 0).coerceAtLeast(0) else 0,
        variants = variantDocs,
        attr1Name = n1,
        attr2Name = n2,
        minOrderQty = minOrderQty?.takeIf { it >= 1 },
        maxOrderQty = maxOrderQty?.takeIf { it >= 1 },
        maxPerUserQty = maxPerUserQty?.takeIf { it >= 1 },
        active = active ?: true,
        version = 1,
        createdAt = now,
        lastModified = now,
    )
}

/**
 * Maps catalog-update onto an existing accessory.
 * Existing variants: preserve stock/attr1/attr2; patch label/active/imageUrl.
 * New request keys: appended with attr1/attr2/imageUrl and initial stock (>= 0).
 * Call [variantKeyLockErrors] first.
 */
fun AdminAccessoryCatalogUpdateRequest.toUpdatedAccessoryDocument(existing: AccessoryDocument): AccessoryDocument {
    val byKey = variants.mapNotNull { v ->
        val key = v.key?.trim().orEmpty()
        if (key.isBlank()) null else key to v
    }.toMap()
    val existingKeys = existing.variants.map { it.key }.toSet()
    val mergedExisting = existing.variants.map { ev ->
        val patch = byKey[ev.key]
        ev.copy(
            label = patch?.label?.trim()?.takeIf { it.isNotEmpty() } ?: ev.label,
            active = patch?.active ?: ev.active,
            imageUrl = patch?.imageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: ev.imageUrl,
        )
    }
    val added = byKey.values
        .filter { (it.key?.trim().orEmpty()) !in existingKeys }
        .map { v ->
            val key = v.key!!.trim()
            AccessoryVariant(
                key = key,
                label = v.label!!.trim(),
                stock = (v.stock ?: 0).coerceAtLeast(0),
                attr1 = v.attr1?.trim()?.takeIf { it.isNotEmpty() },
                attr2 = v.attr2?.trim()?.takeIf { it.isNotEmpty() },
                imageUrl = v.imageUrl?.trim()?.takeIf { it.isNotEmpty() },
                active = v.active ?: true,
            )
        }
    val mergedVariants = mergedExisting + added
    val (n1, n2) = normalizeAttributeNames(
        attr1Name = attr1Name ?: existing.attr1Name,
        attr2Name = attr2Name ?: existing.attr2Name,
        variants = mergedVariants,
    )
    val productGallery = if (mergedVariants.isEmpty()) {
        imageUrls.map { it.trim() }.filter { it.isNotEmpty() }
    } else {
        emptyList()
    }
    return existing.copy(
        sku = sku!!.trim(),
        name = name!!.trim(),
        description = description?.trim()?.takeIf { it.isNotEmpty() },
        imageUrl = imageUrl!!.trim(),
        imageUrls = productGallery,
        price = price!!,
        currency = currency?.trim()?.takeIf { it.isNotEmpty() } ?: existing.currency,
        stock = existing.stock,
        variants = mergedVariants,
        attr1Name = n1,
        attr2Name = n2,
        minOrderQty = minOrderQty?.takeIf { it >= 1 },
        maxOrderQty = maxOrderQty?.takeIf { it >= 1 },
        maxPerUserQty = maxPerUserQty?.takeIf { it >= 1 },
        active = active ?: existing.active,
        version = existing.version + 1,
        lastModified = Date(),
    )
}

/** Builds variant documents from the create request; blanks-only rows are skipped. */
fun AdminAccessoryUpsertRequest.toVariantDocuments(): List<AccessoryVariant> =
    variants.mapNotNull { v ->
        val key = v.key?.trim().orEmpty()
        val label = v.label?.trim().orEmpty()
        if (key.isBlank() && label.isBlank()) return@mapNotNull null
        AccessoryVariant(
            key = key,
            label = label,
            stock = (v.stock ?: 0).coerceAtLeast(0),
            attr1 = v.attr1?.trim()?.takeIf { it.isNotEmpty() },
            attr2 = v.attr2?.trim()?.takeIf { it.isNotEmpty() },
            imageUrl = v.imageUrl?.trim()?.takeIf { it.isNotEmpty() },
            active = v.active ?: true,
        )
    }

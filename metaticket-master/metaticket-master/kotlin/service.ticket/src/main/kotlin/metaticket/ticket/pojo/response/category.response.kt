package metaticket.ticket.pojo.response

import metaticket.ticket.pojo.db.CategoryItemDocument
import metaticket.ticket.pojo.db.CategoryMappingDocument

data class CategoryItemResponse(
    val category: String, // category on seat map, it is difference with ticket category
    val displayName: String, //  this is ticket category, it's being show on the ticket
    val price: Long,
    val color: String?,
    val openDate: Long?,
    val closeDate: Long?,
)

data class CategoryMappingResponse(
    val id: String,
    val showId: String,
    val sessionId: String,
    val mapId: String? = null,
    val kindMap: String,
    val catMapping: Map<String, CategoryItemResponse>,

    val version: Int,
    val createdAt: Long,
    val lastModified: Long,
)

fun CategoryMappingDocument.toResponse(): CategoryMappingResponse {
    return CategoryMappingResponse(
        id = this.id,
        showId = this.showId,
        sessionId = this.sessionId,
        mapId = this.mapId,
        kindMap = this.kindMap,
        catMapping = this.catMapping.map { it.key to it.value.toResponse() }.associate { it },
        version = this.version,
        createdAt = this.createdAt.time,
        lastModified = this.lastModified.time,
    )
}

fun CategoryItemDocument.toResponse(): CategoryItemResponse {
    return CategoryItemResponse(
        category = this.category,
        displayName = this.displayName,
        price = this.price,
        color = this.color,
        openDate = this.openDate?.time,
        closeDate = this.closeDate?.time,
    )
}
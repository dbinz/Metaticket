package metaticket.ticket.pojo.request


/**
 *
 * @author dongcatchay
 */
data class CreateCategoryMappingRequest(
    val showId: String,
    val sessionId: String,
    val kindMap: String,
    val mapId: String?,
    val category: CategoryItemDocumentRequest,
)

data class AddOrUpdateCategoryMappingRequest(
    val showId: String,
    val sessionId: String,
    val oldCat: String?,
    val category: CategoryItemDocumentRequest
)

data class DeleteCategoryMappingRequest(
    val showId: String,
    val sessionId: String,
    val category: String,
)

data class ClearTicketByCategoryRequest(
    val showId: String,
    val sessionId: String,
    val category: String
)

data class CategoryItemDocumentRequest(
    val category: String,
    val price: Long,
    val displayName: String,
    val color: String?,
    val openDate: Long?,
    val closeDate: Long?,
)

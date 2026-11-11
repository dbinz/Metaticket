package metaticket.notification.pojo.request

data class CreateMailTemplateRequest(
    var id: String,
    var root: String,
    val priceItem: String,
    val promotionItem: String,
    val customFieldItem: String?,
    val ticketPdfTemplate: String?,
    val ticketPdfFileName: String?,
)

data class UpdateMailTemplateRequest(
    var id: String,
    var root: String,
    val priceItem: String,
    val promotionItem: String,
    val customFieldItem: String?,
    val ticketPdfTemplate: String?,
    val ticketPdfFileName: String?,
)

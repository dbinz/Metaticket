package metaticket.show.datastructure


/**
 *
 * Created - vừa được tạo,
 * ApprovalWait - chờ duyệt,
 * Rejected - Không duyệt, từ chối,
 * OpeningWait - Chờ mở bán,
 * Open - mở bán,
 * Paused - tạm dừng mở bán,
 * Stopped - Dừng mở bán,
 * Terminated - Chấm dứt hoàn toàn
 */
enum class ShowSessionStatus {
    Created,
    ApprovalWait,
    Rejected,
    OpeningWait,
    Open,
    Paused,
    Stopped,
    Terminated
}

data class MailPluginTemplate(
    val orderStatus: String,
    val plugin: String,
    val templateId: String
)

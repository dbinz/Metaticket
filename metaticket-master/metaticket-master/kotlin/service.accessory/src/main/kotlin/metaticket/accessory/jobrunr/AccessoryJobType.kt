package metaticket.accessory.jobrunr

import metaticket.accessory.pojo.db.AccessoryShowStatus
import java.util.Date

/** One-shot JobRunr job names executed back into `service.accessory`. */
enum class AccessoryJobType {
    /** Flip a Waiting accessory show to Open when Mongo still says the sale is due. */
    OpenShow,
}

/**
 * Whether to schedule a new [AccessoryJobType.OpenShow] job after persist.
 *
 * Skip when the show stays Waiting with the same `saleOpenAt` — leftover jobs no-op on fire,
 * so rescheduling the same instant only creates junk. Schedule on create, on first Waiting,
 * or when the planned instant actually changes.
 */
fun shouldScheduleOpenShowJob(
    previousStatus: AccessoryShowStatus?,
    previousSaleOpenAt: Date?,
    newStatus: AccessoryShowStatus,
    newSaleOpenAt: Date?,
    nowMillis: Long = System.currentTimeMillis(),
): Boolean {
    if (newStatus != AccessoryShowStatus.Waiting) return false
    val openAt = newSaleOpenAt?.time ?: return false
    if (openAt <= nowMillis) return false
    val unchangedWhileWaiting =
        previousStatus == AccessoryShowStatus.Waiting &&
            previousSaleOpenAt != null &&
            previousSaleOpenAt.time == openAt
    return !unchangedWhileWaiting
}

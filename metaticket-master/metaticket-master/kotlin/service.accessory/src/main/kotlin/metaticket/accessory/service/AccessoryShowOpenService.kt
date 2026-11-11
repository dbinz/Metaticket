package metaticket.accessory.service

import metaticket.accessory.database.CatalogDatabaseService
import metaticket.accessory.gateway.JobrunrGateway
import metaticket.accessory.jobrunr.shouldScheduleOpenShowJob
import metaticket.accessory.pojo.db.AccessoryShowDocument
import metaticket.accessory.pojo.db.AccessoryShowStatus
import metaticket.common.logger.Logging
import java.util.Date

/**
 * Opens a Waiting accessory show at `saleOpenAt`.
 *
 * Staging/prod: schedule a one-shot JobRunr job (`AccessoryService` → this process's gRPC).
 * A periodic sweep CAS-opens any due Waiting show if the job was missed (restart, callback fail).
 */
class AccessoryShowOpenService(
    private val catalogDb: CatalogDatabaseService,
    private val jobrunrGateway: JobrunrGateway,
    private val scheduleOnJobrunr: Boolean,
) : Logging {

    /**
     * After create/update/status persist: enqueue JobRunr only when the planned instant changed
     * (or the show newly became Waiting). Unchanged date → no extra job.
     */
    suspend fun afterShowPersisted(
        previousStatus: AccessoryShowStatus?,
        previousSaleOpenAt: Date?,
        saved: AccessoryShowDocument,
    ) {
        if (!shouldScheduleOpenShowJob(previousStatus, previousSaleOpenAt, saved.status, saved.saleOpenAt)) {
            if (previousStatus == AccessoryShowStatus.Waiting && saved.status == AccessoryShowStatus.Waiting) {
                logger.info(
                    "accessory show {} skip OpenShow job; saleOpenAt unchanged ({})",
                    saved.id, saved.saleOpenAt?.time,
                )
            }
            return
        }
        val openAt = saved.saleOpenAt ?: return
        if (!scheduleOnJobrunr) {
            logger.info(
                "accessory show {} OpenShow JobRunr skipped (scheduleOnJobrunr=false) saleOpenAt={}",
                saved.id, openAt.time,
            )
            return
        }
        if (!jobrunrGateway.scheduleOpenShow(saved.id, openAt)) {
            logger.error(
                "accessory show {} persist ok but OpenShow JobRunr job was not scheduled saleOpenAt={}",
                saved.id, openAt.time,
            )
        }
    }

    /**
     * Open every Waiting show whose `saleOpenAt` is already due. Safe to run often; CAS no-ops.
     */
    suspend fun sweepDueShows(): Int {
        val due = catalogDb.findWaitingShowsDue()
        if (due.isEmpty()) return 0
        var opened = 0
        for (show in due) {
            try {
                val saved = catalogDb.openShowIfWaitingAndDue(show.id) ?: continue
                opened++
                logger.info("sale-open sweep: opened accessory show {} code={}", saved.id, saved.code)
            } catch (t: Throwable) {
                logger.error("sale-open sweep: failed to open accessory show {}", show.id, t)
            }
        }
        if (opened > 0) {
            logger.info("sale-open sweep: {} due, {} opened", due.size, opened)
        }
        return opened
    }
}

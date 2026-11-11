package metaticket.accessory.jobrunr

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import metaticket.accessory.pojo.db.AccessoryShowStatus
import java.util.Date

/**
 * Guards the skip-if-unchanged rule: editing a Waiting show without changing saleOpenAt
 * must not enqueue another OpenShow job.
 */
class AccessoryShowOpenJobTest : FunSpec({
    val now = 1_700_000_000_000L
    val future = Date(now + 86_400_000L)
    val later = Date(now + 172_800_000L)

    test("create Waiting with future saleOpenAt schedules") {
        shouldScheduleOpenShowJob(null, null, AccessoryShowStatus.Waiting, future, now) shouldBe true
    }

    test("edit Waiting without changing saleOpenAt does not schedule") {
        shouldScheduleOpenShowJob(
            AccessoryShowStatus.Waiting, future,
            AccessoryShowStatus.Waiting, future,
            now,
        ) shouldBe false
    }

    test("edit Waiting and change saleOpenAt schedules") {
        shouldScheduleOpenShowJob(
            AccessoryShowStatus.Waiting, future,
            AccessoryShowStatus.Waiting, later,
            now,
        ) shouldBe true
    }

    test("Open to Waiting with existing future date schedules") {
        shouldScheduleOpenShowJob(
            AccessoryShowStatus.Open, future,
            AccessoryShowStatus.Waiting, future,
            now,
        ) shouldBe true
    }

    test("Waiting with saleOpenAt cleared does not schedule") {
        shouldScheduleOpenShowJob(
            AccessoryShowStatus.Waiting, future,
            AccessoryShowStatus.Waiting, null,
            now,
        ) shouldBe false
    }

    test("Open status does not schedule") {
        shouldScheduleOpenShowJob(
            AccessoryShowStatus.Waiting, future,
            AccessoryShowStatus.Open, future,
            now,
        ) shouldBe false
    }
})

package metaticket.accessory.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import metaticket.accessory.pojo.db.DiscountApplyMode
import metaticket.accessory.pojo.db.DiscountCodeDocument
import metaticket.accessory.pojo.db.DiscountTier
import metaticket.accessory.pojo.db.DiscountType
import metaticket.accessory.pojo.db.validationErrors
import org.bson.types.ObjectId
import java.util.Date

/**
 * Unit tests for discount `evaluate` (task 3.5) plus Tiered / EarlyBird auto campaigns.
 *
 * **Validates: Requirements 4.2, 4.4, 4.5** and auto-apply selection rules.
 */
class DiscountEvaluateTest : FunSpec({

    val showId = "SHOW_A"
    val now = Date(1_700_000_000_000L)
    val dayMs = 24L * 60 * 60 * 1000

    fun code(
        type: DiscountType = DiscountType.Percentage,
        value: Long = 10,
        maxDiscountAmount: Long? = null,
        minOrderAmount: Long = 0,
        accessoryShowId: String? = null,
        usageLimit: Int? = null,
        usedCount: Int = 0,
        startAt: Date? = null,
        endAt: Date? = null,
        active: Boolean = true,
        codeStr: String = "THEFLOB10",
        rewardType: DiscountType? = null,
        tiers: List<DiscountTier> = emptyList(),
        applyMode: DiscountApplyMode = DiscountApplyMode.Manual,
    ) = DiscountCodeDocument(
        id = ObjectId().toHexString(),
        code = codeStr,
        accessoryShowId = accessoryShowId,
        type = type,
        value = value,
        rewardType = rewardType,
        maxDiscountAmount = maxDiscountAmount,
        minOrderAmount = minOrderAmount,
        tiers = tiers,
        applyMode = applyMode,
        usageLimit = usageLimit,
        usedCount = usedCount,
        startAt = startAt,
        endAt = endAt,
        active = active,
    )

    fun eval(doc: DiscountCodeDocument, subtotal: Long) =
        DiscountCodeServiceImpl.evaluatePure(doc, showId, subtotal, now)

    // --- Percentage cap incl. THEFLOB10 (Req 4.4) ---

    test("THEFLOB10 applies 10% of subtotal") {
        val result = eval(code(value = 10), subtotal = 200_000)
        result.shouldNotBeNull()
        result.amount shouldBe 20_000
        result.code shouldBe "THEFLOB10"
        result.type shouldBe DiscountType.Percentage
    }

    test("percentage discount is capped at maxDiscountAmount") {
        val result = eval(code(value = 10, maxDiscountAmount = 15_000), subtotal = 1_000_000)
        result.shouldNotBeNull()
        result.amount shouldBe 15_000
    }

    test("percentage discount uncapped when below the cap") {
        val result = eval(code(value = 25, maxDiscountAmount = 100_000), subtotal = 200_000)
        result.shouldNotBeNull()
        result.amount shouldBe 50_000
    }

    // --- Fixed-amount clamp (Req 4.5) ---

    test("fixed-amount discount is clamped to the subtotal") {
        val result = eval(code(type = DiscountType.FixedAmount, value = 50_000, codeStr = "FIX50"), subtotal = 30_000)
        result.shouldNotBeNull()
        result.amount shouldBe 30_000
    }

    test("fixed-amount discount applies its value when below the subtotal") {
        val result = eval(code(type = DiscountType.FixedAmount, value = 50_000, codeStr = "FIX50"), subtotal = 100_000)
        result.shouldNotBeNull()
        result.amount shouldBe 50_000
    }

    // --- Min-order gate (Req 4.2) ---

    test("code below the minimum order amount is not applicable") {
        eval(code(minOrderAmount = 100_000), subtotal = 50_000).shouldBeNull()
    }

    test("code exactly at the minimum order amount is applicable") {
        eval(code(minOrderAmount = 100_000), subtotal = 100_000).shouldNotBeNull()
    }

    // --- Date window (Req 4.2) ---

    test("code before its start date is not applicable") {
        eval(code(startAt = Date(now.time + dayMs)), subtotal = 200_000).shouldBeNull()
    }

    test("code after its end date is not applicable") {
        eval(code(endAt = Date(now.time - dayMs)), subtotal = 200_000).shouldBeNull()
    }

    test("code within its inclusive window is applicable") {
        val doc = code(startAt = Date(now.time - dayMs), endAt = Date(now.time + dayMs))
        eval(doc, subtotal = 200_000).shouldNotBeNull()
    }

    // --- Show scoping (Req 4.2) ---

    test("code scoped to another show is not applicable") {
        eval(code(accessoryShowId = "SHOW_B"), subtotal = 200_000).shouldBeNull()
    }

    test("code scoped to this show is applicable") {
        eval(code(accessoryShowId = showId), subtotal = 200_000).shouldNotBeNull()
    }

    test("global code (null show scope) is applicable to this show") {
        eval(code(accessoryShowId = null), subtotal = 200_000).shouldNotBeNull()
    }

    // --- Exhausted usage (Req 4.2) ---

    test("code with usedCount at the usage limit is not applicable") {
        eval(code(usageLimit = 5, usedCount = 5), subtotal = 200_000).shouldBeNull()
    }

    test("code with usage remaining is applicable") {
        eval(code(usageLimit = 5, usedCount = 4), subtotal = 200_000).shouldNotBeNull()
    }

    test("unlimited usage (null usageLimit) is applicable") {
        eval(code(usageLimit = null, usedCount = 999), subtotal = 200_000).shouldNotBeNull()
    }

    // --- Inactive / non-positive subtotal ---

    test("inactive code is not applicable") {
        eval(code(active = false), subtotal = 200_000).shouldBeNull()
    }

    test("non-positive subtotal is never applicable") {
        eval(code(), subtotal = 0).shouldBeNull()
        eval(code(), subtotal = -1).shouldBeNull()
    }

    // --- Tiered (highest eligible tier only) ---

    fun tiered(
        usageLimit: Int? = null,
        usedCount: Int = 0,
        active: Boolean = true,
    ) = code(
        type = DiscountType.Tiered,
        value = 0,
        codeStr = "TIER_SHOW_A",
        accessoryShowId = showId,
        applyMode = DiscountApplyMode.Auto,
        usageLimit = usageLimit,
        usedCount = usedCount,
        active = active,
        tiers = listOf(
            DiscountTier(minOrderAmount = 200_000, type = DiscountType.FixedAmount, value = 20_000),
            DiscountTier(minOrderAmount = 500_000, type = DiscountType.Percentage, value = 10),
        ),
    )

    test("Tiered below lowest threshold is not applicable") {
        eval(tiered(), subtotal = 199_999).shouldBeNull()
    }

    test("Tiered at 200K uses fixed 20K tier") {
        val result = eval(tiered(), subtotal = 200_000)
        result.shouldNotBeNull()
        result.type shouldBe DiscountType.Tiered
        result.amount shouldBe 20_000
    }

    test("Tiered at 500K uses highest eligible 10% tier only") {
        val result = eval(tiered(), subtotal = 500_000)
        result.shouldNotBeNull()
        result.amount shouldBe 50_000
    }

    test("Tiered between thresholds keeps the lower tier") {
        val result = eval(tiered(), subtotal = 499_999)
        result.shouldNotBeNull()
        result.amount shouldBe 20_000
    }

    test("Tiered respects usageLimit") {
        eval(tiered(usageLimit = 10, usedCount = 10), subtotal = 500_000).shouldBeNull()
    }

    test("Tiered document validation accepts a well-formed campaign") {
        tiered().validationErrors().shouldBe(emptyList())
    }

    // --- EarlyBird ---

    fun earlyBird(
        rewardType: DiscountType = DiscountType.Percentage,
        value: Long = 15,
        startAt: Date? = Date(now.time - dayMs),
        endAt: Date? = Date(now.time + dayMs),
        usageLimit: Int? = null,
        usedCount: Int = 0,
    ) = code(
        type = DiscountType.EarlyBird,
        value = value,
        rewardType = rewardType,
        codeStr = "EARLY_SHOW_A",
        accessoryShowId = showId,
        applyMode = DiscountApplyMode.Auto,
        startAt = startAt,
        endAt = endAt,
        usageLimit = usageLimit,
        usedCount = usedCount,
    )

    test("EarlyBird percentage applies inside the window") {
        val result = eval(earlyBird(rewardType = DiscountType.Percentage, value = 15), subtotal = 200_000)
        result.shouldNotBeNull()
        result.type shouldBe DiscountType.EarlyBird
        result.amount shouldBe 30_000
    }

    test("EarlyBird fixed amount applies inside the window") {
        val result = eval(
            earlyBird(rewardType = DiscountType.FixedAmount, value = 25_000),
            subtotal = 200_000,
        )
        result.shouldNotBeNull()
        result.amount shouldBe 25_000
    }

    test("EarlyBird outside the window is not applicable") {
        eval(earlyBird(endAt = Date(now.time - dayMs)), subtotal = 200_000).shouldBeNull()
        eval(earlyBird(startAt = Date(now.time + dayMs)), subtotal = 200_000).shouldBeNull()
    }

    test("EarlyBird for another show is not applicable") {
        eval(earlyBird().copy(accessoryShowId = "SHOW_B"), subtotal = 200_000).shouldBeNull()
    }

    test("EarlyBird unlimited usage still applies") {
        eval(earlyBird(usageLimit = null, usedCount = 1_000), subtotal = 200_000).shouldNotBeNull()
    }

    test("EarlyBird document validation rejects missing show or window") {
        val bad = earlyBird(startAt = null, endAt = null).copy(accessoryShowId = null)
        val errors = bad.validationErrors()
        errors.any { it.contains("accessoryShowId") } shouldBe true
        errors.any { it.contains("startAt") || it.contains("endAt") } shouldBe true
    }
})

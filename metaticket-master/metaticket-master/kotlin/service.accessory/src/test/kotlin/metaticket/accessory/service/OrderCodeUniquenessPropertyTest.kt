package metaticket.accessory.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.set
import io.kotest.property.checkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.atomic.AtomicLong

/**
 * Property 18: Order code uniqueness (task 4.2).
 *
 * **Validates: Requirements 6.10, 6.15**
 *
 * `∀ o1, o2 ∈ O with o1 ≠ o2: o1.code ≠ o2.code`. The atomic per-bucket sequence and the unique
 * index guarantee uniqueness even under concurrent checkouts.
 *
 * A live MongoDB is not available in this environment, so the test exercises the deterministic core:
 * the atomic sequence source (an [AtomicLong], the exact contract the Mongo `$inc` provides) feeding
 * the real production formatter [OrderCodeService.formatOrderCode]. It asserts that N concurrent
 * allocations against the same `(prefix, year)` bucket all yield distinct codes, and that distinct
 * sequence values always map to distinct codes.
 */
class OrderCodeUniquenessPropertyTest : FunSpec({

    test("N concurrent allocations against one bucket produce N distinct order codes") {
        checkAll(50, Arb.int(2..500)) { n ->
            val seq = AtomicLong(0) // models the atomic per-bucket counter
            val codes = coroutineScope {
                (1..n).map {
                    async(Dispatchers.Default) {
                        OrderCodeService.formatOrderCode("TF", 2026, seq.incrementAndGet())
                    }
                }.awaitAll()
            }
            codes.size shouldBe n
            codes.toSet().size shouldBe n // all distinct
        }
    }

    test("distinct sequence values map to distinct codes within a bucket") {
        checkAll(100, Arb.set(Arb.long(1L..1_000_000L), 1..200)) { seqs ->
            val codes = seqs.map { OrderCodeService.formatOrderCode("TF", 2026, it) }
            codes.toSet().size shouldBe seqs.size
        }
    }

    test("formatted code matches <PREFIX><YEAR>-<SEQ4+> and pads to at least 4 digits") {
        OrderCodeService.formatOrderCode("TF", 2026, 148) shouldBe "TF2026-0148"
        OrderCodeService.formatOrderCode("TF", 2026, 1) shouldBe "TF2026-0001"
        // grows naturally beyond 4 digits, staying unique
        OrderCodeService.formatOrderCode("TF", 2026, 12345) shouldBe "TF2026-12345"

        checkAll(100, Arb.long(1L..9_999_999L)) { s ->
            OrderCodeService.formatOrderCode("AC", 2026, s).shouldMatch(Regex("""AC2026-\d{4,}"""))
        }
    }
})

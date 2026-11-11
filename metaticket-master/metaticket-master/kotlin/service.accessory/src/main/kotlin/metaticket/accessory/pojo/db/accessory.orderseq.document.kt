package metaticket.accessory.pojo.db

import org.bson.codecs.pojo.annotations.BsonId

/**
 * One sequence bucket per `(prefix, year)` in the `accessory-order-seq` collection, backing atomic
 * order-code generation (`<PREFIX><YEAR>-<SEQ4>`, e.g. `TF2026-0148`).
 *
 * The [id] is the bucket key `"<PREFIX><YEAR>"` (e.g. `"TF2026"`). Sequences are allocated with a
 * single atomic `findOneAndUpdate($inc)`, so concurrent checkouts never receive the same value
 * (design "Order Code Generation", task 4). Modelled exactly per the design.
 */
data class OrderSeqDocument(
    @BsonId val id: String,   // "<PREFIX><YEAR>", e.g. "TF2026"
    var seq: Long = 0,
)

package metaticket.accessory.pojo.request

/**
 * Request / response DTOs for `POST /accessory/discount/validate` (design
 * "Components and Interfaces → AccessoryController"; Req 4.1, 4.2, 4.3).
 *
 * The discount-preview endpoint is a **read-only** calculation: it re-derives the subtotal for the
 * submitted cart lines from the stored catalog (never a client-supplied price — Req 6.9), then
 * evaluates the supplied code against that server-derived subtotal via
 * [metaticket.accessory.service.DiscountCodeService.evaluate], **without** reserving any usage
 * (Req 4.7). It never creates an order.
 */

/**
 * The discount-preview payload: a candidate code plus the full set of cart lines to price.
 *
 * @property discountCode the code to preview. A blank/absent code is treated as "does not exist"
 *   and rejected with `422` (Req 4.3).
 * @property lines the entire cart (reusing [CheckoutLine]); zero lines is rejected with `422`
 *   (Req 4.3).
 */
data class DiscountPreviewRequest(
    val discountCode: String? = null,
    val lines: List<CheckoutLine> = emptyList(),
)

/**
 * The discount-preview success result (Req 4.1): the resolved discount amount and the resulting
 * order total (`subtotal - discountAmount`), alongside the server-derived subtotal for display.
 *
 * @property code the canonical (uppercase) discount code that was applied.
 * @property subtotal the catalog-derived subtotal in whole VND.
 * @property discountAmount the resolved discount in whole VND (`0 <= discountAmount <= subtotal`).
 * @property total the resulting order total in whole VND (`subtotal - discountAmount`).
 */
data class DiscountPreviewResponse(
    val code: String,
    val type: String,           // DiscountType name, or "None" when no promo applies
    val value: Long,            // percent / fixed VND / winning-tier value — for display
    val amount: Long,           // resolved discount in whole VND (0 <= amount <= subtotal)
    val subtotal: Long,
    val total: Long,
)

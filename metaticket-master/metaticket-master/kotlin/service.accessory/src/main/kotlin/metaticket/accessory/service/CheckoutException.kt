package metaticket.accessory.service

/**
 * Domain-level checkout failures raised by [AccessoryOrderService.checkout] **after** the pure
 * payload gate ([metaticket.accessory.pojo.request.CheckoutValidator]) has passed but while the
 * catalog is re-validated and the order is priced/created (task 6.2).
 *
 * Each subtype carries the HTTP [status] the controller (task 12.3) should surface, so the wiring
 * layer maps a failure to the exact status the requirements mandate without re-deriving it:
 * - unknown accessory id → `404` (Req 6.3),
 * - not a single `Open` show / inactive accessory → `422` (Req 6.4),
 * - variant selection inconsistent with the catalog → `400` (Req 6.5),
 * - a line exceeding its applicable stock → `409` (Req 6.6),
 * - empty cart → `400` (Req 6.2; normally caught by the payload gate, re-asserted defensively),
 * - a zero/negative order total → `422` (Req 7.5),
 * - payment creation failure/timeout/no-id → `502` (Req 7.4).
 *
 * The catalog-validation subtypes are all thrown **before** [OrderDatabaseService.insertOrder], so
 * no order is created. The two payment-phase subtypes ([InvalidOrderTotal], [PaymentCreationFailed])
 * are raised during payment wiring (task 6.3): [InvalidOrderTotal] is thrown before any order is
 * inserted, while [PaymentCreationFailed] is thrown only after the failed order has been moved to a
 * terminal state and any reserved discount reverted (Req 7.4).
 */
sealed class CheckoutException(
    val status: Int,
    message: String,
) : RuntimeException(message) {

    /** The cart is empty (Req 6.2). Defensive re-assert of the payload gate; maps to `400`. */
    class EmptyCart :
        CheckoutException(400, "Giỏ hàng không được trống")

    /**
     * One or more submitted `accessoryId`s do not reference an existing accessory (Req 6.3). Maps to
     * `404`. [encodedAccessoryIds] lists the offending public ids so the client can highlight them.
     */
    class UnknownAccessory(val encodedAccessoryIds: List<String>) :
        CheckoutException(404, "Sản phẩm không tồn tại: ${encodedAccessoryIds.joinToString(", ")}")

    /**
     * The submitted lines do not all reference **active** accessories belonging to a **single**
     * accessory show whose status is `Open` (Req 6.4). Maps to `422`. [offendingEncodedIds`] names
     * the accessory ids that broke the rule (inactive, or belonging to a different/closed show).
     */
    class NotSingleOpenShow(val reason: String, val offendingEncodedIds: List<String> = emptyList()) :
        CheckoutException(422, "Vui lòng check lại sản phẩm trong giỏ hàng: $reason")

    /**
     * Owning show is `Waiting` or `Closed` — catalog may be browsed but checkout / discount
     * preview must not create a sale. Maps to `422`.
     */
    class ShowNotOnSale(val showStatus: String) :
        CheckoutException(
            422,
            when (showStatus) {
                "Waiting" -> "Sự kiện chưa mở bán. Vui lòng quay lại khi đến giờ mở bán."
                "Closed" -> "Sự kiện đã đóng, không thể đặt hàng."
                else -> "Sự kiện hiện không mở bán."
            },
        )

    /**
     * A line's variant selection is inconsistent with the catalog (Req 6.5): a has-variants
     * accessory with a missing/unknown `variantKey`, or a single-SKU accessory carrying a
     * `variantKey`. Maps to `400`.
     */
    class InvalidVariant(val encodedAccessoryId: String, reason: String) :
        CheckoutException(400, "Lựa chọn phụ kiện $encodedAccessoryId không hợp lệ: $reason")

    /**
     * The selected variant exists on the catalog but is **inactive** (withdrawn from sale). Mirrors
     * the inactive-accessory rule (Req 6.4): an inactive purchasable unit must never be ordered, so
     * checkout is rejected before any order/stock/discount work. Maps to `422`. [variantKey] names
     * the offending combination so the storefront can flag it.
     */
    class InactiveVariant(val encodedAccessoryId: String, val variantKey: String) :
        CheckoutException(422, "Phụ kiện $encodedAccessoryId không khả dụng: $variantKey")

    /**
     * A line's `quantity` exceeds its applicable stock and that stock is not unlimited (Req 6.6).
     * Maps to `409`. [detail] carries the human-readable "Out of stock: <sku><variant?>" message.
     */
    class OutOfStock(val detail: String) :
        CheckoutException(409, "Hết hàng: $detail")

    /**
     * Cart quantity for a product violates that product's purchase limits
     * (min/max per order, or max per user including prior Paid + open holds). Maps to `422`.
     */
    class ProductQuantityLimit(message: String) :
        CheckoutException(422, message)

    /**
     * The owning show has sessions but the submitted `showSessionId` is missing or does not match any
     * of them (session-selection feature). Maps to `400`. The customer must pick which show session
     * they are buying the products for.
     */
    class InvalidShowSession(val showSessionId: String?) :
        CheckoutException(400, "Lựa chọn phiên sự kiện không hợp lệ: ${showSessionId ?: "<none>"}")

    /** Unknown fulfillment string that already passed the pure gate (defensive). Maps to `400`. */
    class InvalidFulfillment(val fulfillment: String?) :
        CheckoutException(400, "Lựa chọn giao hàng không hợp lệ: ${fulfillment ?: "<none>"}")

    /** Chosen mode is not enabled on the owning show. Maps to `422`. */
    class FulfillmentNotEnabled(val fulfillment: String) :
        CheckoutException(422, "Giao hàng '$fulfillment' không khả dụng cho show này")

    /** Missing/unknown META Ticket pickup point for MetaTicketPickup. Maps to `400`. */
    class InvalidPickupPoint(val pickupPointId: String?) :
        CheckoutException(400, "Điểm nhận hàng không hợp lệ: ${pickupPointId ?: "<none>"}")

    /** Delivery address missing or incomplete after the pure gate. Maps to `400`. */
    class InvalidDeliveryAddress(reason: String) :
        CheckoutException(400, "Địa chỉ giao hàng không hợp lệ: $reason")

    /**
     * The computed order total is `<= 0` VND, so it cannot be charged (Req 7.5). No payment creation
     * request is sent to `service.payment`. Maps to `422`. This is raised before any order is
     * inserted, and any discount reserved during pricing is reverted before it is thrown, so no order
     * is created and no discount usage is leaked.
     */
    class InvalidOrderTotal(val total: Long) :
        CheckoutException(
            422,
            "Tổng đơn sau giảm giá phải lớn hơn 0. Vui lòng bỏ mã giảm giá hoặc thêm sản phẩm.",
        )

    /**
     * A typed discount code was submitted but is not applicable (expired, wrong show, min order,
     * inactive, unknown, …). Maps to `422` so the storefront can clear the preview and ask the
     * customer to re-apply — never silently drop the code and charge full price.
     */
    class DiscountNotApplicable(val code: String) :
        CheckoutException(
            422,
            "Mã giảm giá '$code' không còn áp dụng được cho đơn này. Vui lòng bỏ mã hoặc áp dụng lại.",
        )

    /**
     * A typed discount code was applicable but could not be reserved (usage exhausted / raced).
     * Maps to `409`.
     */
    class DiscountExhausted(val code: String) :
        CheckoutException(
            409,
            "Mã giảm giá '$code' đã hết lượt sử dụng. Vui lòng bỏ mã hoặc thử mã khác.",
        )

    /**
     * Payment creation against `service.payment` failed: it returned an error, returned no payment
     * id, or did not respond within the 30s deadline (Req 7.4). Maps to `502`. Before this is thrown,
     * the order has already been moved to the `PaymentFailed` terminal state (via the guarded
     * transition) and any reserved discount reverted exactly once.
     */
    class PaymentCreationFailed(reason: String) :
        CheckoutException(502, "Tạo thanh toán thất bại: $reason")
}

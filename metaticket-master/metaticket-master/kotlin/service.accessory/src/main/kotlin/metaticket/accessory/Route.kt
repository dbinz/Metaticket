package metaticket.accessory

import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import metaticket.accessory.controller.AccessoryController
import metaticket.accessory.security.AccessoryPublicRateLimit

/**
 * HTTP routing scaffolding for `service.accessory`.
 *
 * Routes follow the platform-wide `/public` (unauthenticated) and `/private` (authenticated /
 * internal) prefix convention and are reached by `metaticket-home` through the nginx
 * `public/accessory/...` path prefix.
 *
 * Endpoint handlers are wired to [metaticket.accessory.controller.AccessoryController] in a later
 * task; this scaffolding only establishes the route groups so the module builds and the wiring
 * points are explicit.
 */
fun Route.accessoryRoutes(di: ApplicationDI) {
    publicAccessoryRoutes(di)
    privateAccessoryRoutes(di)
    apiAccessoryRoutes(di)
}

/**
 * Public, unauthenticated storefront surface under `/public/accessory`.
 *
 * Storefront reads:
 *  - GET  /public/accessory/show/pin/banner
 *  - GET  /public/accessory/show/fetch/all
 *  - GET  /public/accessory/show/search
 *  - GET  /public/accessory/show/fetch
 *  - GET  /public/accessory/show/fetch/slug
 *  - GET  /public/accessory/order/fetch
 */
private fun Route.publicAccessoryRoutes(di: ApplicationDI) {
    val accessoryController: AccessoryController = di.accessoryController

    route("/public/accessory") {
        // Unthrottled browse/read endpoints (task 12.2):
        //  - GET /show/pin/banner → pinned shows in pinOrder (`/san-pham` slider)
        //  - GET /show/fetch/all  → paginated listing (pins, Waiting, Open, Closed)
        //  - GET /show/search     → keyword search, ranked by match quality (unified search "Sản phẩm")
        //  - GET /show/fetch      → one show + active accessories incl. variants (Req 1.3, 1.4, 1.6, 1.7)
        //  - GET /show/fetch/slug → one show by Canonical_Slug (public share links)
        get("/show/pin/banner") { accessoryController.fetchPinnedBanner(call) }
        get("/show/fetch/all") { accessoryController.fetchAllShowsForStorefront(call) }
        get("/show/search") { accessoryController.searchShowsForStorefront(call) }
        get("/show/fetch/slug") { accessoryController.fetchShowBySlug(call) }
        get("/show/fetch") { accessoryController.fetchShowById(call) }
        //  - GET /fetch           → one active accessory with live stock (product-detail popup poll)
        get("/fetch") { accessoryController.fetchAccessoryById(call) }
        //  - GET /order/fetch     → single-order review view (Req 13.3, 13.4, 13.5, 14.5).
        //    Unthrottled (a read endpoint, like the browse endpoints); rate limiting is scoped to
        //    the sensitive write endpoints only (Req 16.5).
        get("/order/fetch") { accessoryController.fetchOrder(call) }
        // Partner shareable report (Active filter only) — no auth.
        get("/report/{reportKey}") { accessoryController.fetchPublicSalesReport(call) }
        // Partner Excel source: paid orders (+ lines) for the Active filter.
        get("/report/{reportKey}/orders") { accessoryController.fetchPublicReportOrders(call) }
    }
}

/**
 * Private, internal-only surface under `/private/accessory`.
 *
 * Planned endpoints:
 *  - POST /private/accessory/order/notify/resend
 */
private fun Route.privateAccessoryRoutes(di: ApplicationDI) {
    val accessoryController: AccessoryController = di.accessoryController

    route("/private/accessory") {
        // Admin catalog (Open + Closed, raw ids). Local hybrid: no JWT gate yet — network-scoped.
        get("/show/fetch/all") { accessoryController.fetchAllShowsForAdmin(call) }
        get("/show/fetch") { accessoryController.fetchShowForAdmin(call) }
        post("/show/create") { accessoryController.createShowForAdmin(call) }
        post("/show/update") { accessoryController.updateShowForAdmin(call) }
        post("/show/status") { accessoryController.setShowStatusForAdmin(call) }
        post("/show/pin") { accessoryController.setShowPinForAdmin(call) }
        get("/show/pin/board") { accessoryController.getAccessoryPinBoard(call) }
        post("/show/pin/refresh") { accessoryController.refreshAccessoryPins(call) }
        post("/show/pin/confirm") { accessoryController.confirmAccessoryPins(call) }
        get("/show/pin/candidates") { accessoryController.searchAccessoryPinCandidates(call) }
        // Admin products for one show (active + inactive).
        //  - GET  /fetch/all?showId=     list
        //  - GET  /fetch?id=            detail
        //  - POST /create               create (initial stock finite >= 0)
        //  - POST /update?id=           catalog only (no stock write)
        //  - POST /active?id=           active flag
        //  - POST /stock/adjust?id=     atomic delta | setTo (>= 0)
        get("/fetch/all") { accessoryController.fetchAccessoriesForAdmin(call) }
        get("/fetch") { accessoryController.fetchAccessoryForAdmin(call) }
        post("/create") { accessoryController.createAccessoryForAdmin(call) }
        post("/update") { accessoryController.updateAccessoryForAdmin(call) }
        post("/active") { accessoryController.setAccessoryActiveForAdmin(call) }
        post("/stock/adjust") { accessoryController.adjustAccessoryStockForAdmin(call) }
        // Admin promotions (discounts) for one show.
        get("/discount/fetch/all") { accessoryController.fetchDiscountsForAdmin(call) }
        get("/discount/fetch") { accessoryController.fetchDiscountForAdmin(call) }
        post("/discount/create") { accessoryController.createDiscountForAdmin(call) }
        post("/discount/create/batch") { accessoryController.createDiscountsBatchForAdmin(call) }
        post("/discount/update") { accessoryController.updateDiscountForAdmin(call) }
        post("/discount/active") { accessoryController.setDiscountActiveForAdmin(call) }
        post("/discount/delete") { accessoryController.deleteDiscountForAdmin(call) }
        // Admin orders for one show (list + paid-only revenue stats).
        get("/order/fetch/all") { accessoryController.fetchOrdersForAdmin(call) }
        get("/order/stats") { accessoryController.fetchOrderStatsForAdmin(call) }
        // Admin Excel source: orders (+ lines + paymentId) in optional date window.
        get("/order/export") { accessoryController.exportOrdersForAdmin(call) }
        // Saved statistic filters (shareable reportKey).
        get("/report-filter/fetch/all") { accessoryController.fetchReportFiltersForAdmin(call) }
        get("/report-filter/fetch") { accessoryController.fetchReportFilterByKeyForAdmin(call) }
        post("/report-filter/create") { accessoryController.createReportFilterForAdmin(call) }
        post("/report-filter/enable") { accessoryController.enableReportFilterForAdmin(call) }
        post("/report-filter/disable") { accessoryController.disableReportFilterForAdmin(call) }
        post("/report-filter/delete") { accessoryController.deleteReportFilterForAdmin(call) }
        // POST /order/notify/resend?id= → operational resend of one order's confirmation email.
        //   Resend for PaymentSucceed dispatches exactly one email (Req 15.1); malformed/unknown id
        //   → 404 (Req 15.3); non-PaymentSucceed order → 409 (Req 15.4).
        post("/order/notify/resend") { accessoryController.resendConfirmation(call) }
    }
}

/**
 * require login api `/accessory`.
 *
 * Planned endpoints:
 *  - POST /accessory/checkout
 *  - POST /accessory/discount/validate
 */
private fun Route.apiAccessoryRoutes(di: ApplicationDI) {
    val accessoryController: AccessoryController = di.accessoryController

    route("/accessory") {
        // Sensitive endpoints are protected by the per-client-IP rate limiter
        // (20 req / 60s window; over-limit -> 429 + Retry-After) — Req 16.5, 16.6.
        rateLimit(AccessoryPublicRateLimit) {
            // POST /checkout          → validate cart, create order + payment (Req 6, 7, 14, 16)
            // POST /discount/validate → preview a discount code against submitted lines (Req 4)
            post("/checkout") { accessoryController.checkout(call) }
            post("/discount/validate") { accessoryController.validateDiscount(call) }
            // POST /order/cancel?id=  → customer cancels their own pre-payment order (releases the
            //   reserved stock + discount); past the cancellable window → 409.
            post("/order/cancel") { accessoryController.cancelOrder(call) }
        }
        // Authenticated customer reads (gateway injects X-Customer-User-Id).
        get("/order/fetch/mine") { accessoryController.fetchMyPaidOrders(call) }
        // GET /order/pending → newest unpaid hold for resume on the product page.
        get("/order/pending") { accessoryController.fetchPendingOrder(call) }
    }
}

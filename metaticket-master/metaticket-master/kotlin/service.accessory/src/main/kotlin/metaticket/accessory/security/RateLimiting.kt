package metaticket.accessory.security

import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import kotlin.time.Duration.Companion.seconds

/**
 * Per-client-IP rate limiter for the sensitive public accessory endpoints
 * (`/accessory/checkout` and `/accessory/discount/validate`). Browse/read endpoints
 * are intentionally left unthrottled.
 *
 * The window/limit are HOCON-configurable via `accessory.rateLimit.limit` /
 * `accessory.rateLimit.windowSeconds`, defaulting to **20 requests / 60-second window** (Req 16.5).
 * When a client exceeds the limit, Ktor's [RateLimit] plugin rejects the request with
 * `429 Too Many Requests` and a `Retry-After` header before any handler runs, so no order is
 * created and no discount is evaluated (Req 16.6).
 */

/** Named limiter applied to the checkout and discount-validate routes (see [Route.accessoryRoutes]). */
val AccessoryPublicRateLimit = RateLimitName("accessory-public")

private const val DEFAULT_RATE_LIMIT = 20
private const val DEFAULT_WINDOW_SECONDS = 60

/**
 * Install and configure the [RateLimit] plugin with the [AccessoryPublicRateLimit] limiter, keyed
 * by the real client IP (resolved behind nginx). Reads the limit/window from HOCON, falling back to
 * the 20/60 defaults when the keys are absent or malformed.
 */
fun Application.configureRateLimiting() {
    val cfg = environment.config
    val limit = cfg.propertyOrNull("accessory.rateLimit.limit")?.getString()?.toIntOrNull()
        ?: DEFAULT_RATE_LIMIT
    val windowSeconds = cfg.propertyOrNull("accessory.rateLimit.windowSeconds")?.getString()?.toIntOrNull()
        ?: DEFAULT_WINDOW_SECONDS

    install(RateLimit) {
        register(AccessoryPublicRateLimit) {
            rateLimiter(limit = limit, refillPeriod = windowSeconds.seconds)
            // Bucket by the true client IP so the limit is per-client, not per-connection.
            requestKey { call -> call.clientIpBehindNginx() }
        }
    }
}

/**
 * Resolve the real client IP for a request that reaches `service.accessory` through the nginx
 * gateway. nginx sets `X-Forwarded-For` / `X-Real-IP` on the accessory public location, so the
 * left-most `X-Forwarded-For` entry (the original client) is preferred, then `X-Real-IP`, and
 * finally the socket remote address as a fallback when the service is reached directly.
 */
fun ApplicationCall.clientIpBehindNginx(): String {
    request.headers["X-Forwarded-For"]
        ?.split(",")
        ?.firstOrNull()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }

    request.headers["X-Real-IP"]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }

    return request.local.remoteAddress
}

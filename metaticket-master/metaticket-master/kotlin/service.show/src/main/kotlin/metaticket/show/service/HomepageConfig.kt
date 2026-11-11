package metaticket.show.service

import io.ktor.server.application.*

/**
 * Reads and validates the homepage origin (the Homepage_Base_URL) from the `service.show` HOCON
 * application configuration at startup.
 *
 * The value is read once, when this class is constructed during service start-up. If
 * `homepage.base_url` is missing, empty, or whitespace-only, construction throws and aborts
 * service start-up with a clear error message, rather than allowing the service to run and emit
 * malformed share links at request time.
 *
 * Trailing slashes are intentionally **not** stripped here; slash normalization between the origin
 * and the first path segment is handled by [ShareLinkBuilder.join], so both a base URL that ends
 * with a single trailing slash and one without are accepted as-is.
 */
class HomepageConfig(env: ApplicationEnvironment) {

    /** The configured Homepage_Base_URL used as the origin of every generated share link. */
    val baseUrl: String =
        env.config.propertyOrNull("homepage.base_url")?.getString()
            ?.takeIf { it.isNotBlank() }
            ?: error("homepage.base_url configuration is required")
}

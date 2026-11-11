package metaticket.admin.exception

import java.io.IOException

/**
 * Define a custom exception for this client
 */
class PeerServiceException(message: String, cause: Throwable? = null) : IOException(message, cause)
package metaticket.ticket.exception

// --- Helper Exceptions for HTTP Layer ---
class HttpUnauthorizedException(message: String) : RuntimeException(message)
class HttpBadRequestException(message: String) : RuntimeException(message)
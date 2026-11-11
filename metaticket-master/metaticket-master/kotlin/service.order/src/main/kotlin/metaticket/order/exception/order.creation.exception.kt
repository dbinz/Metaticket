package metaticket.order.exception

class OrderCreationException(message: String, cause: Exception): Exception(message, cause)
class OrderValidationException(message: String, cause: Exception): Exception(message, cause)
class ResourceNotFoundException(message: String, cause: Exception): Exception(message, cause)
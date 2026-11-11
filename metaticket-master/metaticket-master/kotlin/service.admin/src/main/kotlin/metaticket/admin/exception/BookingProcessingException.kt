package metaticket.admin.exception


// Custom exception for better error reporting from processSingleBooking
class BookingProcessingException constructor(val bookingId: String, message: String, cause: Throwable) : Exception(message, cause)
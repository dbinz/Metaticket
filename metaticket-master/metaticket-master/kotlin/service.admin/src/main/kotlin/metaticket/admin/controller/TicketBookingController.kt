package metaticket.admin.controller

import io.grpc.Status
import io.grpc.StatusException
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.io.readByteArray
import metaticket.admin.database.service.TicketBookingDBService
import metaticket.admin.exception.BookingProcessingException
import metaticket.admin.exception.PeerServiceException
import metaticket.admin.gateway.CustomerUserGateway
import metaticket.admin.gateway.OrderGateway
import metaticket.admin.gateway.ReservationGateway
import metaticket.admin.gateway.TicketGateway
import metaticket.admin.pojo.db.TicketBookingDocument
import metaticket.admin.pojo.request.BookingTicketDTO
import metaticket.admin.pojo.request.BookingTicketRequest
import metaticket.admin.pojo.response.toResponse
import metaticket.admin.utils.TicketBookingHelper.ALLOWED_EXTENSIONS
import metaticket.admin.utils.TicketBookingHelper.CSV_EXTENSION
import metaticket.admin.utils.TicketBookingHelper.EXCEL_EXTENSIONS
import metaticket.admin.utils.TicketBookingHelper.parseCsvToBookingTicketList
import metaticket.admin.utils.TicketBookingHelper.parseExcelToBookingTicketList
import metaticket.common.logger.Logging
import metaticket.order.datastructure.OrderStatus
import metaticket.order.notification.grpc.OrderMessage
import metaticket.ticket.datastructure.TicketReservationStatus
import metaticket.ticket.grpc.TicketMessage
import org.bson.types.ObjectId
import kotlin.coroutines.cancellation.CancellationException

class TicketBookingController(
    private val environment: ApplicationEnvironment,
    private val ticketGateway: TicketGateway,
    private val reservationGateway: ReservationGateway,
    private val orderClient: OrderGateway,
    private val ticketBookingDBService: TicketBookingDBService,
    private val customerUserGateway: CustomerUserGateway,
) : Logging {
    private val defaultCustomerUserId = environment.config.property("ticketBooking.defaultCustomerUserId").getString()

    suspend fun fetchBookingTicketByShowAndSession(call: ApplicationCall) {
        val showId: String = call.parameters["ShowId"] ?: return call.respond(HttpStatusCode.BadRequest, "Missing ShowId parameter")
        val sessionId: String = call.parameters["SessionId"] ?: return call.respond(HttpStatusCode.BadRequest, "Missing SessionId parameter")
        // Consider adding opUserId/customerUserId logging here if needed for audit
        val bookingDocument = ticketBookingDBService.findByShowAndSession(showId, sessionId)
        call.respond(HttpStatusCode.OK, bookingDocument.map { it.toResponse() })
    }

    /**
     * 1.  Use `multipart/form-data` as the request `Content-Type`.
     * 2.  Include the CSV or Excel file as a file part (the API expects the part name to be `file`).
     * 3.  Include the `showId` as a form field part (e.g., `showId=SHOW123`).
     * 4.  Include the `sessionId` as a form field part (e.g., `sessionId=SESSION456`).
     *
     * The API will then:
     * 1.  Receive and validate the request parts.
     * 2.  Parse the file (CSV or Excel) based on its extension.
     * 3.  Validate headers and row data according to the rules above.
     * 4.  If parsing and initial validation succeed, attempt to process each valid row concurrently:
     *     *   Create a ticket reservation.
     *     *   Create an order linked to the reservation.
     *     *   Handle potential errors and compensation (e.g., releasing reservation if order fails).
     * 5.  Respond with the overall status (success, failure, or multi-status if some
     */
    suspend fun bookingTicketFromFile(call: ApplicationCall) {
        val opUserId = call.request.headers.get("X-OP-User-Id") ?: kotlin.run {
            // No user IDs available yet for this log message
            logger.error("Get op user profile but missing user id in header, maybe user haven't logged in")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }

        handleFileUploadAndBooking(
            call = call,
            allowedExtensions = ALLOWED_EXTENSIONS,
            opUserId = opUserId,
        )
    }

    // --- Modified Generic File Upload Handler ---
    private suspend fun handleFileUploadAndBooking(
        call: ApplicationCall,
        allowedExtensions: List<String>,
        opUserId: String,
    ) {
        var fileBytes: ByteArray? = null
        var originalFileName: String? = null
        var detectedExtension: String? = null
        var showId: String? = null
        var customerUserId: String? = null
        var sessionId: String? = null
        var fileTypeDescription = "CSV or Excel"

        try {
            // 1. Receive Multipart Data (Simplified for brevity - assumes previous logic is correct)
            val multipart = call.receiveMultipart()
            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        val fileName = part.originalFileName
                        val currentExtension = allowedExtensions.find { ext -> fileName?.endsWith(ext, ignoreCase = true) == true }
                        if (fileBytes == null && currentExtension != null) {
                            originalFileName = fileName
                            detectedExtension = currentExtension
                            // Read bytes immediately to avoid stream lifecycle issues
                            fileBytes = part.provider().readRemaining().readByteArray()
                            fileTypeDescription = if (currentExtension == CSV_EXTENSION) "CSV" else "Excel"
                            logger.info("[Op:{}] Received {} file: {}, size: {} bytes", opUserId, fileTypeDescription, originalFileName,
                                fileBytes.size
                            )
                        } else {
                            // Dispose part immediately if not used or already found a file
                            part.dispose()
                            if (currentExtension == null) {
                                logger.warn("[Op:{}] Received unsupported file type: {}", opUserId, fileName)
                            }
                        }
                    }
                    is PartData.FormItem -> {
                        when (part.name) {
                            "showId" -> showId = part.value.trim().takeIf { it.isNotEmpty() }
                            "sessionId" -> sessionId = part.value.trim().takeIf { it.isNotEmpty() }
                            "customerUserId" -> customerUserId = part.value.trim().takeIf { it.isNotEmpty() }
                            else -> logger.debug("[Op:{}] Received unused form item: {}={}", opUserId, part.name, part.value)
                        }
                        part.dispose()
                    }
                    else -> part.dispose() // Dispose other parts
                }
            }

            // 2. Validate Received Data (Simplified for brevity - assumes previous logic is correct)
            val errorMessages = mutableListOf<String>()
            if (fileBytes == null || detectedExtension == null) errorMessages.add("No valid file (${allowedExtensions.joinToString(" or ")}) provided.")
            if (showId == null) errorMessages.add("Form field 'showId' is missing or empty.")
            if (sessionId == null) errorMessages.add("Form field 'sessionId' is missing or empty.")
            if (errorMessages.isNotEmpty()) {
                logger.warn("[Op:{}, Cust:{}] Invalid file upload request for Show ID: {}, Session ID: {}. Errors: {}", opUserId, customerUserId, showId ?: "null", sessionId ?: "null", errorMessages.joinToString())
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid request.", "details" to errorMessages))
                return
            }

            logger.info("[Op:{}, Cust:{}] Processing {} upload for Show ID: {}, Session ID: {}, File: {}", opUserId, customerUserId, fileTypeDescription, showId, sessionId, originalFileName)

            // 3. Parse File based on the detected extension
            val parseResult: Result<List<BookingTicketDTO>> = withContext(Dispatchers.IO) {
                // Create a fresh stream from the bytes
                val stream = java.io.ByteArrayInputStream(fileBytes)
                stream.use {
                    when {
                        detectedExtension.equals(CSV_EXTENSION, ignoreCase = true) -> {
                            logger.debug("[Op:{}, Cust:{}] Using CSV parser for file: {}", opUserId, customerUserId, originalFileName)
                            parseCsvToBookingTicketList(logger, it)
                        }
                        EXCEL_EXTENSIONS.any { ext -> ext.equals(detectedExtension, ignoreCase = true) } -> {
                            logger.debug("[Op:{}, Cust:{}] Using Excel parser for file: {}", opUserId, customerUserId, originalFileName)
                            parseExcelToBookingTicketList(logger, it)
                        }
                        else -> {
                            logger.error("[Op:{}, Cust:{}] Internal error: Detected extension '{}' is not a recognized type for parsing.", opUserId, customerUserId, detectedExtension)
                            Result.failure(IllegalStateException("Unsupported file type detected after initial check: $detectedExtension"))
                        }
                    }
                }
            }


            if (parseResult.isFailure) {
                val errorDetails = parseResult.exceptionOrNull()?.message ?: "Unknown parsing error"
                logger.warn("[Op:{}, Cust:{}] Failed to parse {} file '{}' for Show ID: {}, Session ID: {}: {}", opUserId, customerUserId, fileTypeDescription, originalFileName, showId, sessionId, errorDetails)
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Failed to parse $fileTypeDescription file.", "details" to errorDetails))
                return
            }

            val bookingTickets: List<BookingTicketDTO> = parseResult.getOrThrow()
            if (bookingTickets.isEmpty()) {
                logger.warn("[Op:{}, Cust:{}] {} file '{}' for Show ID: {}, Session ID: {} contained no valid booking data rows.", opUserId, customerUserId, fileTypeDescription, originalFileName, showId, sessionId)
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "No valid booking data found in the provided $fileTypeDescription file."))
                return
            }

            // Kiểm tra giới hạn số lượng record tối đa 10
            if (bookingTickets.size > 10) {
                logger.warn("[Op:{}, Cust:{}] {} file '{}' for Show ID: {}, Session ID: {} contains {} records, exceeds maximum limit of 10.", opUserId, customerUserId, fileTypeDescription, originalFileName, showId, sessionId, bookingTickets.size)
                call.respond(HttpStatusCode.BadRequest, mapOf(
                    "message" to "Số lượng record vượt quá giới hạn cho phép", 
                    "details" to "File chứa ${bookingTickets.size} record, tối đa chỉ được phép xử lý 10 record."
                ))
                return
            }

            if (customerUserId == null) {
                logger.info("Booking ticket using default customer user {}", defaultCustomerUserId)
                customerUserId = defaultCustomerUserId
            }

            try {
                val customerUser = customerUserGateway.findUserByIdOrPhone(customerUserId!!)
                customerUserId = customerUser.id
                logger.info("user customer user {}, phone {}, name {} to booking ticket", customerUserId, customerUser.phone, customerUser.name)
            } catch (e: StatusException) {
                val description = e.status.description ?: "Lỗi không xác định"
                logger.error("gRPC error during get customer user: ${e.status.code} - $description", e.cause)
                val errorMessage = when (e.status.code) {
                    Status.Code.NOT_FOUND -> "Không tồn tại: $description"
                    Status.Code.FAILED_PRECONDITION -> "Không hợp lệ : $description"
                    Status.Code.UNAVAILABLE -> "Hệ thống chưa sẵn sàng. Please try again later."
                    Status.Code.INTERNAL -> "Lỗi hệ thống: $description"
                    Status.Code.PERMISSION_DENIED -> "Không có quyền truy cập: $description"
                    // Add other specific mappings as needed
                    else -> "Lỗi chưa xác định: ${e.status.code} - $description"
                }
                return call.respond(HttpStatusCode.InternalServerError, errorMessage)
            } catch (e: Throwable) {
                // Catch any other unexpected exceptions
                logger.error("Unexpected error during: ${e.message}", e)
                // Avoid wrapping PeerServiceException again if it was somehow thrown inside grpcCall
                throw PeerServiceException("Lỗi không xác định: ${e.message}", e)
            }

            // 4. Construct BookingTicketRequest
            val req = BookingTicketRequest(
                showId = showId!!,
                sessionId = sessionId!!,
                bookingTickets = bookingTickets
            )

            // 5. Execute Core Booking Logic
            executeBookingFlow(opUserId, customerUserId!!, req, call, "$fileTypeDescription file '$originalFileName'")

        } catch (e: Throwable) {
            // Log with user IDs if available, otherwise log generally
            val logPrefix = "[Op:$opUserId, Cust:$customerUserId]"
            logger.error("$logPrefix Error processing $fileTypeDescription booking request for Show ID: $showId, Session ID: $sessionId", e)
            call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "Failed to process the uploaded file.", "details" to e.message))
        } finally {
            // fileInputStream should be closed by the .use block or the earlier error handling
            // No explicit close needed here unless there's a path where .use isn't reached
        }
    }

    // --- Shared Core Booking Logic ---
    private suspend fun executeBookingFlow(opUserId: String, customerUserId: String, req: BookingTicketRequest, call: ApplicationCall, sourceDescription: String) {
        val logPrefix = "[Op:$opUserId, Cust:$customerUserId]"
        logger.info("{} Executing booking flow from {}. Processing {} booking items for Show ID: {}, Session ID: {}", logPrefix, sourceDescription, req.bookingTickets.size, req.showId, req.sessionId)

        // --- Fetch Ticket Details ---
        val tickets: List<TicketMessage.TicketProto>
        try {
            logger.debug("{} Fetching ticket details for show: {}, session: {}", logPrefix, req.showId, req.sessionId)
            tickets = ticketGateway.loadTicketByShowAndSession(req.showId, req.sessionId)
        } catch (e: PeerServiceException) {
            logger.warn("{} Ticket service request failed during ticket fetch for show {}, session {}: {}", logPrefix, req.showId, req.sessionId, e.message, e.cause)
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("message" to "Could not connect to or retrieve data from the ticket service.", "details" to e.message))
            return
        } catch (e: CancellationException) {
            logger.warn("{} Ticket info request cancelled/timed out for show {}, session {}", logPrefix, req.showId, req.sessionId, e)
            call.respond(HttpStatusCode.GatewayTimeout, mapOf("message" to "Request to fetch ticket information timed out."))
            return
        } catch (e: Throwable) {
            logger.error("{} Unexpected error loading ticket info for show {}, session {}", logPrefix, req.showId, req.sessionId, e)
            call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "System error while fetching ticket information."))
            return
        }
        logger.info("{} Fetched {} ticket details for show: {}, session: {}", logPrefix, tickets.size, req.showId, req.sessionId)

        // --- Prepare Documents ---
        val ticketMap = tickets.associateBy { it.sid }
        val documentsToInsert = mutableListOf<TicketBookingDocument>()
        val preparationErrors = mutableListOf<String>()

        req.bookingTickets.forEach { bookingTicketDto ->
            val ticketDetail = ticketMap[bookingTicketDto.sid]
            if (ticketDetail == null) {
                preparationErrors.add("Ticket details not found for SID: ${bookingTicketDto.sid} (from $sourceDescription)")
            } else {
                if (bookingTicketDto.quantity <= 0) {
                    preparationErrors.add("Invalid quantity (${bookingTicketDto.quantity}) for SID: ${bookingTicketDto.sid} (from $sourceDescription)")
                } else {
                    documentsToInsert.add(
                        TicketBookingDocument(
                            id = ObjectId().toHexString(),
                            showId = req.showId,
                            sessionId = req.sessionId,
                            ticketId = ticketDetail.id, // Use id from TicketProto
                            capacityId = ticketDetail.capacityId.takeIf { it.isNotEmpty() }, // Use capacityId from TicketProto
                            priceCat = ticketDetail.priceCat.takeIf { it.isNotEmpty() }, // Use priceCat from TicketProto
                            sid = bookingTicketDto.sid,
                            quantity = bookingTicketDto.quantity,
                            customerUserId = customerUserId  ,
                            customerName = bookingTicketDto.name,
                            customerPhone = bookingTicketDto.phone,
                            customerEmail = bookingTicketDto.email,
                            deliveryName = bookingTicketDto.delivery?.deliveryName,
                            deliveryPhone = bookingTicketDto.delivery?.deliveryPhone,
                            provinceName = bookingTicketDto.delivery?.provinceName,
                            districtName = bookingTicketDto.delivery?.districtName,
                            wardName = bookingTicketDto.delivery?.wardName,
                            address = bookingTicketDto.delivery?.address,
                            promotionCode = bookingTicketDto.promotionCode,
                            customFields = bookingTicketDto.customFields
                        )
                    )
                }
            }
        }

        if (preparationErrors.isNotEmpty()) {
            logger.warn("{} Data validation failed during booking preparation (from {}): {}", logPrefix, sourceDescription, preparationErrors)
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid booking data derived from file.", "details" to preparationErrors))
            return
        }

        if (documentsToInsert.isEmpty()) {
            logger.warn("{} No valid tickets found after processing {}.", logPrefix, sourceDescription)
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "No valid tickets provided for booking in the file."))
            return
        }

        // --- Insert Documents into DB ---
        val insertedDocsMap: Map<String, TicketBookingDocument>
        try {
            logger.info("{} Attempting to insert {} booking documents into DB (from {}).", logPrefix, documentsToInsert.size, sourceDescription)
            val (success, ids) = ticketBookingDBService.insert(documentsToInsert)
            if (!success || ids.size != documentsToInsert.size) {
                logger.error("{} Database insertion failed or was partial. Expected {}, got {} IDs.", logPrefix, documentsToInsert.size, ids.size)
                call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "Failed to save initial booking information."))
                return
            }
            insertedDocsMap = ids.zip(documentsToInsert).toMap()
            logger.info("{} Successfully inserted {} booking documents. IDs: {}", logPrefix, insertedDocsMap.size, insertedDocsMap.keys)

        } catch (e: Throwable) {
            logger.error("{} Failed to insert booking documents into DB.", logPrefix, e)
            call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "System error while saving booking information.", "details" to e.message))
            return
        }

        // --- Process Reservations and Orders Concurrently ---
        val results = mutableListOf<Result<String>>()
        val semaphore = Semaphore(MAX_CONCURRENCY)
        try {
            coroutineScope {
                logger.info("{} Starting concurrent processing for {} bookings (from {}). Max Concurrency: {}", logPrefix, insertedDocsMap.size, sourceDescription, MAX_CONCURRENCY)
                insertedDocsMap.values.map { doc ->
                    async {
                        semaphore.withPermit {
                            processSingleBooking(opUserId, customerUserId, doc, req)
                        }
                    } // Pass the constructed req
                }.awaitAll().forEach { result ->
                    results.add(result)
                }
            }
            logger.info("{} Finished concurrent processing (from {}).", logPrefix, sourceDescription)

            // --- Handle Results ---
            val failures = results.filter { it.isFailure }
            val successes = results.filter { it.isSuccess }

            if (failures.isNotEmpty()) {
                val failedBookings = failures.mapNotNull { failureResult ->
                    when (val exception = failureResult.exceptionOrNull()) {
                        is BookingProcessingException -> exception.bookingId to (exception.message ?: "Unknown processing error")
                        else -> null // Should not happen if processSingleBooking always wraps errors
                    }
                }.toMap()
                val failedIds = failedBookings.keys.toList()
                val failureMessages = failedBookings.values.joinToString("; ")
                logger.error("{} Booking processing failed for {} out of {} documents (from {}). Failed IDs: {}. Errors: {}", logPrefix, failures.size, insertedDocsMap.size, sourceDescription, failedIds, failureMessages)
                val responseStatus = if (successes.isNotEmpty()) HttpStatusCode.MultiStatus else HttpStatusCode.InternalServerError
                call.respond(
                    responseStatus, mapOf(
                        "message" to "One or more booking steps failed. $failureMessages",
                        "successfulBookingIds" to successes.mapNotNull { it.getOrNull() },
                        "failedBookings" to failedBookings,
                    )
                )
            } else {
                val successfulDocIds = successes.mapNotNull { it.getOrNull() }
                logger.info("{} Successfully processed reservations and orders for all {} booking documents (from {}): {}", logPrefix, successfulDocIds.size, sourceDescription, successfulDocIds)
                call.respond(HttpStatusCode.OK, mapOf("message" to "Booking successful.", "bookingIds" to successfulDocIds))
            }

        } catch (e: CancellationException) {
            logger.error("{} Concurrent booking processing scope was cancelled (from {}).", logPrefix, sourceDescription, e)
            call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "Booking processing was interrupted."))
        } catch (e: Throwable) {
            logger.error("{} Unexpected error during concurrent booking processing scope (from {}).", logPrefix, sourceDescription, e)
            call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "System error during booking processing."))
        }
    }

    // --- Single Booking Processing (with Compensation Logic) ---
    private suspend fun processSingleBooking(opUserId: String, customerUserId: String, doc: TicketBookingDocument, req: BookingTicketRequest): Result<String> {
        var reservationId: String? = null
        var orderId: String? = null
        var reservationCreated = false
        var reservationConfirmed = false
        val docId = doc.id
        val logPrefix = "[Op:$opUserId, Cust:$customerUserId], [Booking:$docId]"

        try {
            // Step 1: Create Reservation
            reservationId = stepCreateReservation(logPrefix, opUserId, customerUserId, req, doc)
            reservationCreated = true
            doc.reservationId = reservationId

            // Step 2: Update Booking Document with Reservation ID
            stepUpdateDocWithReservationId(logPrefix, docId, reservationId)

            // Step 3: Reserve Ticket (Confirm Reservation) & Poll for Status
            stepConfirmAndPollReservation(logPrefix, opUserId, customerUserId, reservationId)
            reservationConfirmed = true

            // Step 4: Create Order
            orderId = stepCreateOrder(logPrefix, doc, reservationId)
            doc.orderId = orderId

            // Step 5: Update Booking Document with Order ID
            stepUpdateDocWithOrderId(logPrefix, docId, orderId)

            // Step 6: Verify Order
            stepVerifyOrder(logPrefix, docId, orderId)

            logger.info("{} Processing completed successfully.", logPrefix)
            return Result.success(docId)

        } catch (t: Throwable) {
            val errorMessage = when (t) {
                is BookingProcessingException -> t.message ?: "Processing failed"
                is PeerServiceException -> "Ticket service error: ${t.message}"
                is CancellationException -> "Processing cancelled"
                is OrderStatusValidationException -> "Order validation failed: ${t.message}"
                else -> "System error: ${t.message}"
            }
            logger.error("{} Processing failed. Error: {}", logPrefix, errorMessage, t)

            // Compensation Logic
            // 1. Cancel Order if created
            if (orderId != null) {
                val knownStatus = if (t is OrderStatusValidationException) t.status else null
                safeCancelOrder(logPrefix, orderId, knownStatus)
            } else if (reservationConfirmed || reservationCreated) {
                reservationId?.let { resId ->
                    logger.warn("{} Attempting compensation: Releasing reservation {}...", logPrefix, resId)
                    try {
                        reservationGateway.releaseTicket(opUserId, customerUserId, resId)
                        logger.info("{} Compensation: Ticket service acknowledged release for reservation {}.", logPrefix, resId)
                    } catch (cancelError: Throwable) {
                        logger.error("{} Compensation FAILED: Could not release reservation {}. Error: {}", logPrefix, resId, cancelError.message, cancelError)
                    }
                }
            }
            val finalException = t as? BookingProcessingException ?: BookingProcessingException(docId, errorMessage, t)
            return Result.failure(finalException)
        }
    }

    // --- Sub-steps for clarity and structure ---

    private suspend fun stepCreateReservation(
        logPrefix: String, opUserId: String, customerUserId: String, req: BookingTicketRequest, doc: TicketBookingDocument
    ): String {
        logger.debug("{} -> Creating reservation...", logPrefix)
        val reservationDetail = TicketMessage.ReservationDetailProto.newBuilder().apply {
            setTicketId(doc.ticketId)
            setQuantity(doc.quantity)
            setSid(doc.sid)
            doc.priceCat?.let { setPriceCat(it) }
            doc.capacityId?.let { setCapacityId(it) }
        }.build()

        val resId = reservationGateway.createReservation(opUserId, customerUserId, req.showId, req.sessionId, reservationDetail)
        logger.info("{} Reservation created: {}", logPrefix, resId)
        return resId
    }

    private suspend fun stepUpdateDocWithReservationId(logPrefix: String, docId: String, reservationId: String) {
        logger.debug("{} Updating document with reservationId: {}", logPrefix, reservationId)
        ticketBookingDBService.updateReservationId(docId, reservationId)
                ?: throw BookingProcessingException(docId, "Failed to update booking document with reservationId.", RuntimeException("DB update returned false")
            )
        logger.info("{} Document updated with reservationId: {}", logPrefix, reservationId)
    }

    private suspend fun stepConfirmAndPollReservation(logPrefix: String, opUserId: String, customerUserId: String, reservationId: String) {
        // Trigger reserve ticket
        logger.debug("{} -> Confirming reservation: {}", logPrefix, reservationId)
        reservationGateway.reserveTicket(opUserId, customerUserId, reservationId)

        // Poll for status
        logger.debug("{} -> Polling reservation status...", logPrefix)
        val startTime = System.currentTimeMillis()
        var lastStatus = "Unknown"

        while (System.currentTimeMillis() - startTime < RESERVATION_CHECK_TIMEOUT_MS) {
            val reservation = reservationGateway.getReservation(reservationId)
            lastStatus = reservation.status

            when (lastStatus) {
                TicketReservationStatus.Reserved.name -> {
                    logger.info("{} Reservation confirmed (Reserved).", logPrefix)
                    return // Success
                }
                TicketReservationStatus.Created.name,
                TicketReservationStatus.Reserving.name, -> {
                    // Still Created, Processing, etc.
                    delay(RESERVATION_CHECK_INTERVAL_MS)
                }
                else -> {
                    throw BookingProcessingException(reservationId, "Reservation failed with status: $lastStatus", RuntimeException("Reservation failed"))
                }
            }
        }
        throw BookingProcessingException(reservationId, "Timeout waiting for reservation confirmation. Last status: $lastStatus", java.util.concurrent.TimeoutException())
    }

    private suspend fun stepCreateOrder(logPrefix: String, doc: TicketBookingDocument, resId: String): String {
        logger.debug("{} -> Creating order for reservation: {}", logPrefix, resId)
        val createOrderRequest = OrderMessage.CreateOrderRequest.newBuilder().apply {
            setReservationId(doc.reservationId)
            setCustomerUserId(doc.customerUserId)
            setName(doc.customerName)
            setPhone(doc.customerPhone)
            setEmail(doc.customerEmail)
            doc.promotionCode?.let { setPromotionCode(it) }
            doc.customFields?.let { cf -> addAllCustomFields(cf.map { OrderMessage.CustomFieldProto.newBuilder().setId(it.id).setValue(it.value).build() }) }

            val hasAllDeliveryInfo = listOf(doc.deliveryName, doc.deliveryPhone, doc.provinceName, doc.districtName, doc.wardName, doc.address)
                .all { !it.isNullOrBlank() }
            val hasPartialDeliveryInfo = !hasAllDeliveryInfo && listOf(doc.deliveryName, doc.deliveryPhone, doc.provinceName, doc.districtName, doc.wardName, doc.address)
                .any { !it.isNullOrBlank() }

            if (hasAllDeliveryInfo) {
                this.delivery = OrderMessage.DeliveryProto.newBuilder().apply {
                    setDeliveryName(doc.deliveryName)
                    setDeliveryPhone(doc.deliveryPhone)
                    setProvinceName(doc.provinceName)
                    setDistrictName(doc.districtName)
                    setWardName(doc.wardName)
                    setAddress(doc.address)
                }.build()
            } else if (hasPartialDeliveryInfo) {
                logger.warn("{} Incomplete delivery information provided, skipping.", logPrefix)
                throw BookingProcessingException(doc.id, "Incomplete delivery information provided.", IllegalArgumentException())
            }
        }
        val orderId = orderClient.createOrder(createOrderRequest.build())
        logger.info("{} Order created: {}", logPrefix, orderId)
        return orderId
    }

    private suspend fun stepUpdateDocWithOrderId(logPrefix: String, docId: String, orderId: String) {
        logger.debug("{} -> Updating document with orderId: {}", logPrefix, orderId)
        val orderUpdated = ticketBookingDBService.updateOrderId(docId, orderId)
        
        if (orderUpdated == null) {
            logger.error("{} CRITICAL: Order {} created, but FAILED to update booking document!", logPrefix, orderId)
            throw BookingProcessingException(docId, "Failed to update booking document with orderId.", RuntimeException("DB update returned false"))
        }
        logger.info("{} Document updated with orderId {}.", logPrefix, orderId)
    }

    private suspend fun stepVerifyOrder(logPrefix: String, docId: String, orderId: String) {
        try {
            val orderDetails = orderClient.getOrder(orderId)
            
            // Check status
            // Note: OrderStatus.Canceled (one 'l') is the correct enum value based on datastructure.kt
            if (orderDetails.status == OrderStatus.Canceled.name || 
                orderDetails.status == OrderStatus.Failed.name || 
                orderDetails.status == OrderStatus.Expired.name) {
                 logger.warn("{} Order {} has invalid status: {}. Cancelling...", logPrefix, orderId, orderDetails.status)
                 throw OrderStatusValidationException(orderDetails.status, "Order has invalid status: ${orderDetails.status}.")
            }

            if (orderDetails.total > 0) {
                logger.warn("{} Order {} has total > 0. Cancelling...", logPrefix, orderId)
                throw OrderStatusValidationException(orderDetails.status, "Order total is ${orderDetails.total} (must be 0).")
            }
        } catch (e: Throwable) {
            logger.error("{} Order verification failed.", logPrefix)
            if (e is OrderStatusValidationException) throw e
            throw BookingProcessingException(docId, "Order verification failed: ${e.message}", e)
        }
        logger.info("{} Order verified successfully.", logPrefix)
    }

    private suspend fun safeCancelOrder(logPrefix: String, orderId: String, knownStatus: String? = null) {
        try {
            val status = knownStatus ?: orderClient.getOrder(orderId).status

            // Check if order is already in a resolved state (Success, Failed, Cancelled, Expired, etc.)
            // If resolved, we do NOT cancel again.
            val isResolved = when (status) {
                OrderStatus.PaymentSucceed.name,
                OrderStatus.PaymentFailed.name,
                OrderStatus.PaymentCancelled.name,
                OrderStatus.PaymentRefunded.name,
                OrderStatus.Expired.name,
                OrderStatus.Failed.name,
                OrderStatus.Canceled.name -> true // Canceled has one 'l' in OrderStatus enum
                else -> false
            }

            if (isResolved) {
                logger.warn("{} Order {} is already in resolved state ({}). Skipping cancellation.", logPrefix, orderId, status)
                return
            }

            // Only cancel if status is unresolved (Created, WaitingForPayment, PaymentProcessing, etc.)
            logger.info("{} Order {} is in state {}. Proceeding to cancel...", logPrefix, orderId, status)
            orderClient.cancelOrder(orderId)
            logger.info("{} Order {} cancelled.", logPrefix, orderId)
        } catch (e: Throwable) {
            logger.error("{} FAILED to safe-cancel order {}: {}", logPrefix, orderId, e.message)
        }
    }

    private class OrderStatusValidationException(val status: String, message: String) : RuntimeException(message)

    companion object {
        private const val MAX_CONCURRENCY = 5
        private const val RESERVATION_CHECK_TIMEOUT_MS = 30000L
        private const val RESERVATION_CHECK_INTERVAL_MS = 1000L
    }
}
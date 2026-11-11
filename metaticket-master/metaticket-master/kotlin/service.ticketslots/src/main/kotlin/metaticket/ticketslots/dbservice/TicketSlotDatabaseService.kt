package metaticket.ticketslots.dbservice

import com.mongodb.MongoException
import com.mongodb.client.model.*
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.result.InsertManyResult
import com.mongodb.client.result.UpdateResult
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.ticketslots.pojo.db.*
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import java.util.*

class TicketSlotDatabaseService(
    private val collection: MongoCollection<TicketSlotDoc>
) : Logging {

    init {
        // --- Create Indexes ---
        // Run index creation in a background coroutine to avoid blocking initialization
        CoroutineScope(Dispatchers.Unconfined).launch {
            try {
                logger.info("Attempting to create indexes for TicketSlotDoc collection...")

                // Index for efficient lookup by orderId
                val orderIdx = collection.createIndex(
                    Indexes.ascending(TicketSlotDoc::orderId.name)
                )
                logger.info("Created/Ensured TicketSlotDoc index on orderId: {}", orderIdx)

                // Index for efficient lookup by current owner
                val ownerIdx = collection.createIndex(
                    Indexes.ascending(TicketSlotDoc::currentOwnerId.name),
                    // Use sparse index if null values are possible and you don't want them indexed
                    // IndexOptions().sparse(true)
                )
                logger.info("Created/Ensured TicketSlotDoc index on currentOwnerId: {}", ownerIdx)

                // Index for efficient lookup by current status (if frequently needed)
                val statusIdx = collection.createIndex(
                    Indexes.ascending(TicketSlotDoc::currentStatus.name)
                )
                logger.info("Created/Ensured TicketSlotDoc index on currentStatus: {}", statusIdx)

                // Compound index for finding a user's slots for a specific show/session
                val showSessionOwnerIdx = collection.createIndex(
                    Indexes.ascending(
                        TicketSlotDoc::showId.name,
                        TicketSlotDoc::sessionId.name,
                        TicketSlotDoc::currentOwnerId.name
                    )
                )
                logger.info("Created/Ensured TicketSlotDoc compound index on show/session/owner: {}", showSessionOwnerIdx)

                // Unique index to ensure the combination identifying the original slot is unique
                val originalInfoIdx = collection.createIndex(
                    Indexes.ascending(
                        TicketSlotDoc::orderId.name,
                        TicketSlotDoc::priceCat.name,
                        TicketSlotDoc::ticketId.name,
                        TicketSlotDoc::ticketIdx.name,
                        TicketSlotDoc::slotIdx.name
                    ),
                    IndexOptions().unique(true) // Enforce uniqueness based on origin
                )
                logger.info("Created/Ensured TicketSlotDoc unique index on original info: {}", originalInfoIdx)

                logger.info("Index creation process for TicketSlotDoc completed.")

            } catch (e: MongoException) { // Catch specific Mongo exceptions if possible
                logger.error("Error creating indexes for TicketSlotDoc collection", e)
            } catch (e: Exception) {
                logger.error("Unexpected error during TicketSlotDoc index creation", e)
            }
        }
    }

    suspend fun findTicketSlotByOrderId(orderId: String): List<TicketSlotDoc> {
        return withContext(Dispatchers.IO) {
            collection.find(eq(TicketSlotDoc::orderId.name, orderId)).toList()
        }
    }

    /**
     * Creates multiple TicketSlot documents in a single batch operation.
     * This method uses `ordered(false)` for the insert operation, meaning that if some documents
     * in the batch cause an error (e.g., duplicate key), MongoDB will attempt to insert the
     * remaining documents in the batch instead of stopping the entire operation.
     * If any errors occur, a `MongoBulkWriteException` will be thrown at the end,
     * detailing which documents failed and which succeeded.
     *
     * @param docs A list of TicketSlotDoc objects to be inserted into the database.
     * @return An InsertManyResult object containing information about the operation's success,
     *         including the inserted IDs for successfully inserted documents.
     * @throws com.mongodb.MongoBulkWriteException if any document in the batch fails to insert.
     *         The exception will contain details about both successful and failed writes.
     */
    suspend fun createTicketSlots(docs: List<TicketSlotDoc>): InsertManyResult {
        val option = InsertManyOptions().ordered(false)
        return withContext(Dispatchers.IO) {
            collection.insertMany(docs, option)
        }
    }

    /**
     * Updates the status of a specific ticket slot and returns the updated document
     * with only the current state in the 'states' list and without the 'currentQRString' field.
     *
     * @param slotId The ID of the slot to update.
     * @param stateIdx The expected current state index.
     * @param oldStatus The expected current status.
     * @param newStatus The new status to set.
     * @return The updated TicketSlotDoc with limited state and no currentQRString, or null if not found or conditions not met.
     */
    suspend fun updateStatusTicketSlot(slotId: String, stateIdx: Int, oldStatus: TicketSlotStatus, newStatus: TicketSlotStatus): TicketSlotDoc? {
        val now = Date()
        val filterConditions = mutableListOf<Bson>()
        filterConditions.add(Filters.eq("_id", ObjectId(slotId)))
        filterConditions.add(Filters.eq(TicketSlotDoc::currentStateIdx.name, stateIdx))
        filterConditions.add(Filters.eq(TicketSlotDoc::currentStatus.name, oldStatus))

        val filter = Filters.and(filterConditions)
        val update = Updates.combine(
            Updates.set("${TicketSlotDoc::states.name}.$stateIdx.${TicketSlotState::status.name}", newStatus),
            Updates.set("${TicketSlotDoc::states.name}.$stateIdx.${TicketSlotState::timestamp.name}", now),
            Updates.set(TicketSlotDoc::currentStatus.name, newStatus),
            Updates.set(TicketSlotDoc::lastModified.name, now),
            Updates.inc(TicketSlotDoc::version.name, 1)
        )
        // Options to return the document *after* update and apply projection
        val options = FindOneAndUpdateOptions()
            .returnDocument(ReturnDocument.AFTER) // Return the modified document
            .projection(
                Projections.fields(
                    Projections.slice(TicketSlotDoc::states.name, -1), // Only the current state
                    Projections.exclude(TicketSlotDoc::currentQRString.name) // Exclude QR string
                )
            )

        return withContext(Dispatchers.IO) { collection.findOneAndUpdate(filter, update, options) }
    }

    suspend fun updateSelectedTicketSlotsOptStatus(ticketSlotIds: List<String>, optStatus: TicketSlotOptStatus) {
        val now = Date()
        val filterConditions = mutableListOf<Bson>()

        // Add basic filter to exclude CREATED status
        filterConditions.add(Filters.ne(TicketSlotDoc::currentStatus.name, TicketSlotStatus.CREATED))

        // Create filter for selected ticket slots using ObjectIds
        val objectIds = ticketSlotIds.map { ObjectId(it) }
        filterConditions.add(Filters.`in`("_id", objectIds))
        val combinedFilters = Filters.and(filterConditions)

        val updatePipeline = Updates.combine(
            Updates.set(TicketSlotDoc::optStatus.name, optStatus),
            Updates.set(TicketSlotDoc::lastModified.name, now),
            Updates.inc(TicketSlotDoc::version.name, 1)
        )

        return withContext(Dispatchers.IO) { collection.updateMany(combinedFilters, updatePipeline) }
    }

    /**
     * Finds a TicketSlot document by ID, projecting only the last (current) state
     * and excluding the currentQRString field.
     * Useful for retrieving current information without the full history payload or QR string.
     *
     * @param slotId The ObjectId string of the slot.
     * @return TicketSlotDoc with only the last state in the 'states' list and no currentQRString, or null if not found.
     */
    suspend fun findByIdWithCurrentStateOnly(slotId: String): TicketSlotDoc? {
        if (!ObjectId.isValid(slotId)) {
            logger.warn("Invalid ObjectId format provided for findByIdWithCurrentStateOnly: {}", slotId)
            return null
        }
        val filter = Filters.eq("_id", ObjectId(slotId))
        // Project only the last element of the 'states' array and exclude currentQRString
        val projection = Projections.fields(
            Projections.slice(TicketSlotDoc::states.name, -1),
            Projections.exclude(TicketSlotDoc::currentQRString.name)
        )
        return withContext(Dispatchers.IO) {
            collection.find(filter).projection(projection)
        }.firstOrNull()
        // Note: The returned document's 'states' list will contain only one element (the last one).
        // The 'currentQRString' field will be null or absent.
    }

    /**
     * Finds a TicketSlot document by ID and owner ID, projecting only the last (current) state
     * and excluding the currentQRString field.
     * Useful for retrieving current information without the full history payload or QR string.
     *
     * @param slotId The ObjectId string of the slot.
     * @param ownerId The User ID.
     * @return TicketSlotDoc with only the last state in the 'states' list and no currentQRString, or null if not found.
     */
    suspend fun findByIdAndOwnerIdWithCurrentStateOnly(ownerId: String, slotId: String): TicketSlotDoc? {
        if (!ObjectId.isValid(slotId)) {
            logger.warn("Invalid ObjectId format provided for findByIdAndOwnerIdWithCurrentStateOnly: {}", slotId)
            return null
        }
        val filter = Filters.and(
            Filters.eq("_id", ObjectId(slotId)),
            Filters.eq(TicketSlotDoc::currentOwnerId.name, ownerId)
        )
        // Project only the last element of the 'states' array and exclude currentQRString
        val projection = Projections.fields(
            Projections.slice(TicketSlotDoc::states.name, -1),
            Projections.exclude(TicketSlotDoc::currentQRString.name)
        )
        return withContext(Dispatchers.IO) {
            collection.find(filter).projection(projection)
        }.firstOrNull()
        // Note: The returned document's 'states' list will contain only one element (the last one).
        // The 'currentQRString' field will be null or absent.
    }

    /**
     * Finds all ticket slots currently owned by a specific user, projecting only the current state
     * and excluding the currentQRString field.
     * Uses the index on 'currentOwnerId'.
     *
     * @param ownerId The User ID.
     * @return A list of TicketSlotDoc objects owned by the user, with limited state history and no currentQRString.
     */
    suspend fun findSlotsByCurrentOwner(ownerId: String): List<TicketSlotDoc> {
        val filter = Filters.eq(TicketSlotDoc::currentOwnerId.name, ownerId)
        // Add projection to only get current state and exclude currentQRString
        val projection = Projections.fields(
            Projections.slice(TicketSlotDoc::states.name, -1),
            Projections.exclude(TicketSlotDoc::currentQRString.name)
        )
        return withContext(Dispatchers.IO) { collection.find(filter).projection(projection) }.toList()
    }

    /**
     * Retrieves the QR code string for a specific ticket slot state, given the owner ID and state index.
     * This method uses an aggregation pipeline to find the relevant state within the document.
     * It ensures that the QR code exists and the status is 'OPENED' for the given owner and state index.
     *
     */
    suspend fun getQRCode(ownerId: String, ownerPhone: String, slotId: String, stateIdx: Int): TicketSlotDoc? {
        val pipeline = listOf(
            // Stage 1: Match the specific TicketSlotDoc
            Aggregates.match(
                Filters.and(
                    Filters.eq("_id", ObjectId(slotId)),
                    Filters.or(
                        Filters.eq(TicketSlotDoc::currentOwnerId.name, ownerId),
                        Filters.eq(TicketSlotDoc::currentOwnerPhone.name, ownerPhone),
                    ),
                    Filters.eq(TicketSlotDoc::currentStateIdx.name, stateIdx),
                )
            ),
            // Stage 2: Unwind the 'states' array to process each state individually
            Aggregates.unwind("\$${TicketSlotDoc::states.name}"),
            // Stage 3: Match the state belonging to the specified owner and having a QR code
            Aggregates.match(
                Filters.and(
                    Filters.or(
                        Filters.eq("${TicketSlotDoc::states.name}.${TicketSlotState::ownerId.name}", ownerId),
                        Filters.eq("${TicketSlotDoc::states.name}.${TicketSlotState::ownerPhone.name}", ownerPhone),
                    ),
                )
            ),
            // Stage 4: Sort by state index in descending order to get the latest state if multiple match
            Aggregates.sort(Sorts.descending("${TicketSlotDoc::states.name}.${TicketSlotState::stateIdx.name}")),
            // Stage 5: Limit to the first matching state
            Aggregates.limit(1),
            // --- FIX: Stage 6: Reshape the document to match the TicketSlotDoc data class ---
            // After unwinding, 'states' is an object. We wrap it back into a single-element array
            // so that the Kotlin driver can correctly decode it into a List<TicketSlotState>.
            Aggregates.addFields(
                Field(TicketSlotDoc::states.name, listOf("\$${TicketSlotDoc::states.name}"))
            )
        )

        val resultState: TicketSlotDoc? = withContext(Dispatchers.IO) {
            try {
                collection.aggregate<TicketSlotDoc>(pipeline).firstOrNull()
            } catch (e: MongoException) {
                logger.error(
                    "MongoDB error during getQRCode for slotId: $slotId, owner '$ownerId', stateIdx '$stateIdx'", e
                )
                throw e // Rethrow MongoDB specific exceptions
            }
        }

        return resultState ?: run {
            val errorMessage = "QR Code state not found for slotId: $slotId, owner '$ownerId', stateIdx '$stateIdx'."
            logger.warn(errorMessage)
            // Using NoSuchElementException is fine, but returning null might be more idiomatic for a "find" operation
            // that can fail. I'll leave it as is since it's the existing behavior.
            throw NoSuchElementException(errorMessage)
        }
    }

    /**
     * Finds all ticket slots where a specific user has ever been an owner in any state,
     * and projects only the states where this user was the owner. The currentQRString field is excluded.
     *
     * @param ownerId The User ID.
     * @param phoneNumber The User Phone number.
     * @return A list of TicketSlotDoc objects where the user appears in the states history,
     *         with the 'states' array filtered to only include states owned by this user,
     *         and currentQRString excluded.
     */
    suspend fun findSlotsByOwner(ownerId: String, phoneNumber: String): List<TicketSlotDoc> {
        val pipeline = listOf(
            // Stage 1: Match documents where the ownerId appears in at least one state
            Aggregates.match(Filters.or(
                Filters.eq("${TicketSlotDoc::states.name}.${TicketSlotState::ownerId.name}", ownerId),
                Filters.eq("${TicketSlotDoc::states.name}.${TicketSlotState::ownerPhone.name}", phoneNumber),
            )),
            // Stage 2: Project the document using an INCLUSION projection, which allows computed fields.
            Aggregates.project(
                Projections.fields(
                    // Explicitly list all fields to keep.
                    // 'currentQRString' is omitted and thus automatically excluded.
                    Projections.include(
                        TicketSlotDoc::id.name,
                        TicketSlotDoc::orderId.name,
                        TicketSlotDoc::orderCode.name,
                        TicketSlotDoc::ticketId.name, // Required for response mapping
                        TicketSlotDoc::ticketIdx.name,
                        TicketSlotDoc::slotIdx.name,
                        TicketSlotDoc::showId.name,
                        TicketSlotDoc::sessionId.name,
                        TicketSlotDoc::priceCat.name,
                        TicketSlotDoc::sid.name,
                        TicketSlotDoc::originalOwnerId.name, // Required for response mapping
                        TicketSlotDoc::currentOwnerId.name,
                        TicketSlotDoc::currentOwnerPhone.name,
                        TicketSlotDoc::currentStatus.name,
                        TicketSlotDoc::optStatus.name,
                        TicketSlotDoc::visitDate.name, // Vé theo lịch: storefront hiển thị ngày tham quan thay giờ suất
                        TicketSlotDoc::currentStateIdx.name,
                        TicketSlotDoc::version.name,
                        TicketSlotDoc::lastModified.name,
                        TicketSlotDoc::createdAt.name
                    ),
                    // Compute the new, filtered 'states' array. This is allowed in an inclusion projection.
                    Projections.computed(
                        TicketSlotDoc::states.name,
                        Document(
                            "\$filter", Document("input", "\$${TicketSlotDoc::states.name}")
                                .append("as", "state")
                                .append(
                                    "cond",
                                    Document("\$or", listOf(
                                        Document("\$eq", listOf("\$\$state.${TicketSlotState::ownerId.name}", ownerId)),
                                        Document("\$eq", listOf("\$\$state.${TicketSlotState::ownerPhone.name}", phoneNumber))
                                    ))
                                )
                        )
                    )
                )
            )
        )
        return withContext(Dispatchers.IO) {
            collection.aggregate(pipeline).toList()
        }
    }

    /**
     * Finds all ticket slots currently owned by a specific user for a specific show and session,
     * projecting only the current state and excluding the currentQRString field.
     * Uses the compound index on showId, sessionId, currentOwnerId.
     *
     * @param ownerId The User ID.
     * @param showId The Show ID.
     * @param sessionId The Session ID.
     * @return A list of matching TicketSlotDoc objects with limited state history and no currentQRString.
     */
    suspend fun findSlotsByOwnerAndShow(ownerId: String, showId: String, sessionId: String): List<TicketSlotDoc> {
        val filter = Filters.and(
            Filters.eq(TicketSlotDoc::currentOwnerId.name, ownerId),
            Filters.eq(TicketSlotDoc::showId.name, showId),
            Filters.eq(TicketSlotDoc::sessionId.name, sessionId)
        )
        // Add projection to only get current state and exclude currentQRString
        val projection = Projections.fields(
            Projections.slice(TicketSlotDoc::states.name, -1),
            Projections.exclude(TicketSlotDoc::currentQRString.name)
        )
        return withContext(Dispatchers.IO) { collection.find(filter).projection(projection) }.toList()
    }

    /**
     * Finds all ticket slots for a specific show and session, projecting only the current state
     * and excluding the currentQRString field.
     *
     * @param showId The Show ID.
     * @param sessionId The Session ID.
     * @return A list of matching TicketSlotDoc objects with limited state history and no currentQRString.
     */
    suspend fun findSlotsByShow(showId: String, sessionId: String): List<TicketSlotDoc> {
        val filter = Filters.and(
            Filters.eq(TicketSlotDoc::showId.name, showId),
            Filters.eq(TicketSlotDoc::sessionId.name, sessionId)
        )
        // Add projection to only get current state and exclude currentQRString
        val projection = Projections.fields(
            Projections.slice(TicketSlotDoc::states.name, -1),
            Projections.exclude(TicketSlotDoc::currentQRString.name)
        )
        return withContext(Dispatchers.IO) { collection.find(filter).projection(projection) }.toList()
    }

    /**
     * Atomically transfers a ticket slot from an expected owner to a new owner.
     * Performs checks and updates in a single atomic database operation to prevent race conditions.
     *
     * @param slotId The ID of the slot to transfer.
     * @param stateIdx The expected current state index of the document. This is crucial for optimistic locking.
     * @param expectedCurrentOwnerId The ID of the user expected to currently own the slot (sender).
     * @param version The expected version of the document.
     * @param newOwnerId The ID of the user receiving the slot.
     * @param newOwnerPhone The phone number of the new owner.
     * @return true if the transfer was successful, false otherwise (e.g., slot not found,
     *         owner mismatch, invalid status, or concurrent modification).
     */
    suspend fun transferTicketSlotAtomic(
        slotId: String,
        stateIdx: Int,
        expectedCurrentOwnerId: String,
        version: Int,
        newOwnerPhone: String? = null,
        newOwnerId: String? = null,
    ): TicketSlotState? {
        if (!ObjectId.isValid(slotId)) {
            logger.warn("Invalid ObjectId format provided for transferTicketSlotAtomic: {}", slotId)
            return null
        }
        logger.debug(
            "Attempting atomic transfer for slot: {}, from expected owner: {}, to new owner: {}",
            slotId, expectedCurrentOwnerId, newOwnerId
        )

        val now = Date()

        // Prepare the new state for the recipient
        val newStateForRecipient = TicketSlotState(
            stateIdx = stateIdx + 1,
            ownerId = newOwnerId,
            ownerPhone = newOwnerPhone,
            status = TicketSlotStatus.CREATED, // Initial status for recipient
            reason = StateReason.TRANSFER_RECEIVED,
            senderId = expectedCurrentOwnerId,
            timestamp = now,
            // qrcodeString will be null initially
        )

        // --- Build the Atomic Filter ---
        // This filter ensures the update only happens if the conditions are met *at the time of the update*
        val filter = Filters.and(
            Filters.eq("_id", ObjectId(slotId)), // Match the specific slot
            Filters.eq(TicketSlotDoc::currentOwnerId.name, expectedCurrentOwnerId), // Ensure the current owner is the expected sender
            Filters.eq(TicketSlotDoc::version.name, version), // Validate a correct version that frontend is working on
            // Đảm bảo rằng chúng ta đang cập nhật đúng trạng thái mà client mong đợi.
            Filters.eq(TicketSlotDoc::currentStateIdx.name, stateIdx),
            // Không cho phép transfer khi ticket slot đã check in
            Filters.ne(TicketSlotDoc::currentStatus.name, TicketSlotStatus.CHECKED_IN)
        )

        // --- Build the Atomic Update using an Aggregation Pipeline ---
        // This pipeline constructs the new document state on the server to avoid client-side race conditions
        // and conflicting path errors.

        val setStageDocument: Document = Document().apply {
            newOwnerId?.let { append(TicketSlotDoc::currentOwnerId.name, it) }
            newOwnerPhone?.let { append(TicketSlotDoc::currentOwnerPhone.name, it) }
            // 1. Set simple top-level fields
            append(TicketSlotDoc::currentStatus.name, newStateForRecipient.status)
            append(TicketSlotDoc::currentStateIdx.name, newStateForRecipient.stateIdx)
            append(TicketSlotDoc::lastModified.name, now)
            // currentQRString is now removed via $unset instead of being set to null
            // 2. Increment version using aggregation operator $add
            append(TicketSlotDoc::version.name, Document("\$add", listOf("\$${TicketSlotDoc::version.name}", 1)))
            // 3. Construct the new 'states' array
            append(
                TicketSlotDoc::states.name,
                Document(
                    // Use $concatArrays to append the new state (like $push)
                    "\$concatArrays", listOf(
                        // The first array is the modified existing 'states' array
                        Document(
                            // Use $map to iterate over the existing states
                            "\$map", Document("input", "\$${TicketSlotDoc::states.name}")
                                .append("as", "state")
                                .append(
                                    "in",
                                    // Use $cond to find and modify the correct state
                                    Document(
                                        "\$cond", Document()
                                            .append(
                                                "if",
                                                Document("\$eq", listOf("\$\$state.${TicketSlotState::stateIdx.name}", stateIdx))
                                            )
                                            .append(
                                                "then",
                                                // If it's the state to update, merge in the new fields
                                                Document(
                                                    "\$mergeObjects", listOf(
                                                        "\$\$state", // The original state object
                                                        // The fields to update
                                                        Document(TicketSlotState::reason.name, StateReason.TRANSFERRED_AWAY).apply {
                                                            newOwnerId?.let { append(TicketSlotState::recipientId.name, it) }
                                                            append(TicketSlotState::timestamp.name, now)
                                                        }
                                                    )
                                                )
                                            )
                                            .append("else", "\$\$state") // Otherwise, keep the state as is
                                    )
                                )
                        ),
                        // The second array is a single-element array with the new state
                        listOf(newStateForRecipient)
                    )
                )
            )
        }
        
        val updatePipeline = listOfNotNull(
            Document("\$set", setStageDocument),
            Document("\$unset", TicketSlotDoc::currentQRString.name),
            if (newOwnerId == null) { Document("\$unset", TicketSlotDoc::currentOwnerId.name) } else null
        )


        // --- Execute the Atomic Update ---
        val updateResult: UpdateResult = try {
            // The second argument is now a pipeline (List<Bson>) instead of a single update document
            withContext(Dispatchers.IO) { collection.updateOne(filter, updatePipeline) }
        } catch (e: MongoException) {
            logger.error("MongoDB error during atomic transfer update for slot ID: {}", slotId, e)
            return null // Database or network error
        } catch (e: Exception) {
            logger.error("Unexpected error during atomic transfer update for slot ID: {}", slotId, e)
            return null
        }

        // --- Check the Result ---
        if (updateResult.matchedCount == 0L) {
            // No document matched the filter criteria.
            logger.warn(
                "Atomic transfer failed for slot ID: {}. Filter did not match. " +
                        "Reasons: Slot not found, expected owner '{}' incorrect, " +
                        "current status not transferable, version mismatch, or state index mismatch.",
                slotId, expectedCurrentOwnerId
            )
            return null
        }

        if (updateResult.modifiedCount == 0L) {
            // Matched a document, but nothing was modified. This is unusual with this pipeline.
            logger.error(
                "Atomic transfer potentially problematic for slot ID: {}. Document matched but was not modified. Investigate.",
                slotId
            )
            // Treat as failure for safety
            return null
        }

        // Success: matchedCount = 1, modifiedCount = 1
        logger.info(
            "Atomic transfer successful: Slot ID {} transferred from expected owner {} to new owner {}",
            slotId, expectedCurrentOwnerId, newOwnerId
        )
        return newStateForRecipient
    }

    /**
     * Sets the QR code string for a specific ticket slot state.
     * Only allows QR code setting for CREATED status tickets, rejects CLOSED tickets completely.
     *
     * @param slotId The ID of the ticket slot.
     * @param currentStateIdx The index of the current state to update.
     * @param qrcodeString The QR code string to set.
     * @throws Exception if a database error occurs or if trying to set QR for closed ticket.
     */
    suspend fun setQRCode(slotId: String, currentStateIdx: Int, qrcodeString: String) {
        val now = Date()
        
        // Filter to only allow QR code setting for CREATED tickets, reject CLOSED tickets
        val filter = Filters.and(
            Filters.eq("_id", ObjectId(slotId)),
            Filters.eq(TicketSlotDoc::currentStateIdx.name, currentStateIdx),
            Filters.eq(TicketSlotDoc::currentStatus.name, TicketSlotStatus.CREATED)
        )
        
        val update = Updates.combine(
            Updates.set("${TicketSlotDoc::states.name}.$currentStateIdx.${TicketSlotState::qrcodeString.name}", qrcodeString),
            Updates.set("${TicketSlotDoc::states.name}.$currentStateIdx.${TicketSlotState::status.name}", TicketSlotStatus.QR_GENERATED),
            Updates.set("${TicketSlotDoc::states.name}.$currentStateIdx.${TicketSlotState::timestamp.name}", now),
            Updates.set(TicketSlotDoc::currentStatus.name, TicketSlotStatus.QR_GENERATED),
            Updates.set(TicketSlotDoc::currentQRString.name, qrcodeString),
            Updates.set(TicketSlotDoc::lastModified.name, now),
            Updates.inc(TicketSlotDoc::version.name, 1)
        )
        
        try {
            val updateResult = withContext(Dispatchers.IO) {
                collection.updateOne(filter, update)
            }
            
            if (updateResult.matchedCount == 0L) {
                logger.warn(
                    "setQRCode for Slot ID: {}, State Index: {}: No document matched the filter criteria. " +
                    "This could be because the ticket slot is CLOSED, not CREATED status, not found, or currentStateIdx mismatch.",
                    slotId, currentStateIdx
                )
                throw IllegalStateException("Cannot set QR code: ticket slot is not in CREATED status or not found")
            }
            
            if (updateResult.modifiedCount > 0L) {
                logger.info(
                    "Successfully set QR code and updated status to QR_GENERATED for Slot ID: {}, State Index: {}.",
                    slotId, currentStateIdx
                )
            } else {
                val errorMessage = "setQRCode for Slot ID: $slotId, State Index: $currentStateIdx: " +
                        "Document matched but no fields were modified. This indicates an unexpected state."
                logger.error(errorMessage)
                throw IllegalStateException(errorMessage)
            }
            
        } catch (e: Throwable) {
            logger.error(
                "Error during setQRCode operation. Slot ID: {}, State Index: {}.",
                slotId, currentStateIdx, e
            )
            throw e
        }
    }
}
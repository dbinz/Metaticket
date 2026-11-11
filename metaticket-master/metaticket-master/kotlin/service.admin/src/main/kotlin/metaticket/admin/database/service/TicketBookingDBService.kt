package metaticket.admin.database.service

import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import metaticket.admin.pojo.db.TicketBookingDocument
import metaticket.common.logger.Logging
import org.bson.types.ObjectId
import java.util.*

class TicketBookingDBService constructor(
    private val col: MongoCollection<TicketBookingDocument>,
) : Logging {

    /**
     * Inserts multiple documents efficiently.
     * @param documents The list of documents to insert.
     * @return Pair<Boolean, List<String>> where Boolean is true if ALL documents were inserted successfully,
     *         and List<String> contains the hex string IDs of the successfully inserted documents.
     * @throws MongoWriteException or other exceptions if the underlying MongoDB operation fails.
     */
    suspend fun insert(documents: List<TicketBookingDocument>): Pair<Boolean, List<String>> {
        if (documents.isEmpty()) {
            logger.info("Insert called with empty document list.")
            return Pair(true, emptyList()) // Nothing to insert, technically successful
        }
        logger.debug("Attempting to insert ${documents.size} documents.")
        return try {
            val result = withContext(Dispatchers.IO) { col.insertMany(documents) }
            // result.insertedIds is Map<Int, BsonValue>
            val insertedIdsList = result.insertedIds.values // Get only the BsonValues
                .mapNotNull { it?.asObjectId()?.value?.toHexString() } // Safely map to hex string

            val allInserted = insertedIdsList.size == documents.size
            if (!allInserted) {
                // This condition is more likely with unordered inserts where some might fail while others succeed.
                // With default ordered inserts, an exception is usually thrown on the first failure.
                logger.warn(
                    "InsertMany attempted for ${documents.size} documents, but only ${insertedIdsList.size} IDs were returned in the result. This might indicate partial success (e.g., with unordered inserts) or an unexpected driver behavior."
                )
            } else {
                logger.info("Successfully inserted ${insertedIdsList.size} documents.")
            }
            Pair(allInserted, insertedIdsList)
        } catch (e: Exception) { // Catch specific exceptions like MongoBulkWriteException if needed
            logger.error("Error during insertMany operation for ${documents.size} documents.", e)
            // Re-throw to allow the caller to handle the failure context
            throw e
        }
    }

    /**
     * Updates a single booking document identified by its ID, setting the reservationId.
     * Also updates the lastUpdated timestamp and increments the version.
     *
     * @param id The hex string representation of the document's ObjectId.
     * @param reservationId The reservation ID to set.
     * @return A TicketBookingDocument object. Returns null if none are found.
     * @throws Exception Propagates underlying database exceptions during the update operation.
     */
    suspend fun updateReservationId(id: String, reservationId: String): TicketBookingDocument? {
        val objectId: ObjectId = try {
            ObjectId(id)
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid ObjectId format provided for updateReservationId: '$id'")
            return null // Invalid ID format, cannot match
        }

        val filter = Filters.eq("_id", objectId)
        val update = Updates.combine(
            Updates.set(TicketBookingDocument::reservationId.name, reservationId),
            Updates.set(TicketBookingDocument::lastUpdated.name, Date()),
            Updates.inc(TicketBookingDocument::version.name, 1)
        )
        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)

        logger.debug("Attempting to update reservationId for document ID: $id")
        return try {
            val result = withContext(Dispatchers.IO) { col.findOneAndUpdate(filter, update, options) }
            if (result != null) {
                logger.info("Successfully updated reservationId for document ID: $id.")
            } else {
                logger.warn("Update reservationId attempt for document ID: $id found no matching document.")
            }
            result // Return true if at least one document was matched
        } catch (e: Throwable) {
            logger.error("Error updating reservationId for document ID: $id", e)
            throw e // Re-throw DB exceptions
        }
    }

    /**
     * Updates a single booking document identified by its ID, setting the orderId.
     * Also updates the lastUpdated timestamp and increments the version.
     *
     * @param id The hex string representation of the document's ObjectId.
     * @param orderId The order ID to set.
     * @return A TicketBookingDocument object. Returns null if none are found.
     * @throws Exception Propagates underlying database exceptions during the update operation.
     */
    suspend fun updateOrderId(id: String, orderId: String): TicketBookingDocument? {
        val objectId: ObjectId = try {
            ObjectId(id)
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid ObjectId format provided for updateOrderId: '$id'")
            return null // Invalid ID format, cannot match
        }

        val filter = Filters.eq("_id", objectId)
        val update = Updates.combine(
            Updates.set(TicketBookingDocument::orderId.name, orderId),
            Updates.set(TicketBookingDocument::lastUpdated.name, Date()),
            Updates.inc(TicketBookingDocument::version.name, 1)
        )
        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)

        logger.debug("Attempting to update orderId for document ID: $id")
        return try {
            val result = withContext(Dispatchers.IO) { col.findOneAndUpdate(filter, update, options) }
            if (result != null) {
                logger.info("Successfully updated orderId for document ID: $id.")
            } else {
                logger.warn("Update orderId attempt for document ID: $id found no matching document.")
            }
            result
        } catch (e: Throwable) {
            logger.error("Error updating orderId for document ID: $id", e)
            throw e // Re-throw DB exceptions
        }
    }

    /**
     * Finds all booking documents matching a specific show ID and session ID.
     *
     * @param showId The ID of the show to filter by.
     * @param sessionId The ID of the session to filter by.
     * @return A list of matching TicketBookingDocument objects. Returns an empty list if none are found.
     * @throws Exception Propagates underlying database exceptions during the find operation.
     */
    suspend fun findByShowAndSession(showId: String, sessionId: String): List<TicketBookingDocument> {
        logger.debug("Attempting to find bookings by showId: $showId and sessionId: $sessionId")

        val filter = Filters.and(
            Filters.eq(TicketBookingDocument::showId.name, showId), // Adjust field name if needed
            Filters.eq(TicketBookingDocument::sessionId.name, sessionId) // Adjust field name if needed
        )

        return withContext(Dispatchers.IO) { col.find(filter) }.toList()
    }

    /**
     * Deletes a single booking document by its ID. Useful for compensation logic.
     * @param id The hex string representation of the document's ObjectId.
     * @return Boolean indicating whether a document was found and deleted.
     */
    suspend fun deleteById(id: String): Boolean {
        val objectId: ObjectId = try {
            ObjectId(id)
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid ObjectId format provided for deleteById: '$id'")
            return false
        }
        val filter = Filters.eq("_id", objectId)
        logger.debug("Attempting to delete document ID: $id")
        return try {
            val result = withContext(Dispatchers.IO) {
                col.deleteOne(filter)
            }
            val deleted = result.deletedCount > 0
            if (deleted) {
                logger.info("Successfully deleted document ID: $id")
            } else {
                logger.warn("Delete attempt for document ID: $id found no matching document.")
            }
            deleted
        } catch (e: Exception) {
            logger.error("Error deleting document ID: $id", e)
            throw e
        }
    }
}
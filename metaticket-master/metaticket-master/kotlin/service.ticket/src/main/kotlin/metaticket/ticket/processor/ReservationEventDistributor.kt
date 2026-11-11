package metaticket.ticket.processor

import kotlinx.coroutines.*
import metaticket.common.logger.Logging
import java.util.concurrent.CopyOnWriteArrayList // Use CopyOnWriteArrayList to ensure thread-safety when reading/writing the processor list
import kotlin.coroutines.CoroutineContext

/**
 * Distributes reservation events (reservation IDs) from the Repository to available Processors.
 * Manages the coroutine lifecycle and ensures thread-safety.
 * Guarantees that a pulled reservation ID will wait indefinitely until a processor is available.
 *
 * @author dongcatchay
 */
class ReservationEventDistributor(
    private val repos: ReservationRepository // Source providing the reservation IDs to be processed
) : CoroutineScope, Logging { // Implement CoroutineScope to manage child coroutines structurally

    // SupervisorJob ensures that an error in one child coroutine (e.g., a failed processor) does not cancel other coroutines or the distributor itself.
    private val job = SupervisorJob()

    // Use Dispatchers.IO as repos.pull() might be a waiting operation (I/O-bound).
    // Add CoroutineName for easier identification and debugging in logs or profilers.
    override val coroutineContext: CoroutineContext = Dispatchers.IO + job + CoroutineName("ReservationEventDistributorScope")

    // Use CopyOnWriteArrayList because:
    // 1. Thread-safe: Allows register/unregister from other threads/coroutines while the start loop is reading.
    // 2. Efficient for frequent reads, infrequent writes: Reading (finding a processor) happens constantly, while writing (register/unregister) is much less frequent.
    //    Each write creates a new copy, but reading doesn't require locking (lock-free).
    private val processors: MutableList<ReservationProcessor> = CopyOnWriteArrayList()

    // Stores the Job of the main distribution loop coroutine.
    // Necessary to be able to cancel and join it in the stop() function.
    private var distributorJob: Job? = null

    // Function to calculate the next processor index in a round-robin fashion.
    // Purpose: Distribute work more evenly among processors, avoiding always starting from the first processor.
    private val nextIdx: (Int, Int) -> Int = { currentIdx, listSize ->
        if (listSize == 0) -1 // Handle the case of an empty list to avoid division by zero errors.
        else (currentIdx + 1) % listSize // Basic round-robin formula.
    }

    /**
     * Starts the loop for listening and distributing events.
     * Launches a background coroutine to perform this task.
     */
    fun start() {
        // Prevent calling start multiple times if the distributor is already running.
        // Reason: Avoid creating multiple unnecessary distributor loops running in parallel, which wastes resources and can cause logic errors.
        if (distributorJob?.isActive == true) {
            logger.warn("ReservationEventDistributor is already running.")
            return
        }

        // Launch the main coroutine within the class's scope (managed by 'job').
        // All child coroutines launched from here will be children of 'job'.
        distributorJob = launch {
            logger.info("Starting ReservationEventDistributor loop...")
            var currentProcessorIndex = -1 // Start at -1 so the first nextIdx call returns 0 (the first processor).

            // Main loop, continues running until the coroutine scope is cancelled (by the stop() function).
            // Using 'isActive' is the idiomatic way in coroutines to check the cancellation status.
            while (isActive) {
                try {
                    // Wait here if no processors are registered yet.
                    // Reason: Shouldn't pull() data if there's nothing to process it, avoiding waste and potential errors.
                    // delay() to avoid a continuous checking loop consuming CPU (busy-waiting).
                    while (processors.isEmpty() && isActive) {
                        logger.warn("No reservation processors registered. Waiting...")
                        delay(2000) // Wait for a period before checking again.
                    }
                    // Re-check isActive after waiting, as it might have been stopped during the wait.
                    if (!isActive) break // Exit the loop if already cancelled.

                    // Pull a reservation ID from the repository.
                    // This is a suspend function; the coroutine will pause here if the repository is empty,
                    // and will throw a CancellationException if the scope is cancelled while waiting.
                    val reservationId = repos.pull()
                    logger.debug("Pulled reservation ID: {}", reservationId)

                    // --- Start: Wait indefinitely for an available processor ---
                    // Requirement: Once pulled, it must be processed eventually; do not skip or re-queue.
                    var dispatched = false // Flag indicating whether dispatching was successful.
                    while (isActive && !dispatched) { // Loop until the task is dispatched or the coroutine is cancelled.
                        val currentListSize = processors.size // Get the current list size (safe with CopyOnWriteArrayList).
                        if (currentListSize == 0) {
                            // Rare case: processor list becomes empty after the check above but before entering here.
                            // Need to wait and re-check.
                            logger.warn("Processors list became empty while trying to dispatch {}. Waiting...", reservationId)
                            delay(1000)
                            continue // Go back to the start of the while(!dispatched) loop to re-check the size.
                        }

                        var foundInThisCycle = false // Flag indicating if an available processor was found in this cycle.
                        // Try dispatching within one round-robin cycle (check all processors once).
                        // Reason: Ensure all processors are checked before deciding to delay, avoiding missing an available one.
                        for (i in 0 until currentListSize) {
                            // Get the next processor index using round-robin.
                            currentProcessorIndex = nextIdx(currentProcessorIndex, currentListSize)
                            try {
                                // Get the processor from the list (safe with CopyOnWriteArrayList).
                                val processor = processors[currentProcessorIndex]

                                // Attempt to "claim" the processor atomically.
                                // processor.available is an AtomicBoolean.
                                // compareAndSet(true, false): Only succeeds (returns true) if the current value is true,
                                // and if successful, it immediately sets the value to false in the same uninterruptible operation.
                                // Reason: This is the critical point to avoid race conditions - ensuring only one coroutine/thread
                                // can assign work to this processor at a time.
                                if (processor.available.compareAndSet(true, false)) {
                                    logger.debug("Dispatching {} to processor index {}", reservationId, currentProcessorIndex)
                                    // Launch the processing (processor.process) in a new child coroutine.
                                    // Reason: So the distributor isn't blocked and can immediately continue pulling the next reservation.
                                    // This coroutine is a child of the distributor scope and will be cancelled when the distributor is stopped.
                                    launch(CoroutineName("Processor-${processor.hashCode()}")) { // Set a name for easier debugging.
                                        try {
                                            // Execute the main reservation processing logic.
                                            processor.process(reservationId)
                                        } catch (e: CancellationException) {
                                            // Handle the case where the processing coroutine is cancelled (due to distributor stop).
                                            logger.warn("Processor job for {} cancelled.", reservationId)
                                            // Important: Ensure the processor becomes available again if cancelled mid-way.
                                            // processor.process should handle its own resource cleanup on cancellation if needed.
                                            // Use compareAndSet for safety, only setting it back if it's currently false.
                                            processor.available.compareAndSet(false, true)
                                        } catch (t: Throwable) {
                                            // Handle unexpected errors occurring inside processor.process.
                                            logger.error("Processor at index {} failed processing {}", currentProcessorIndex, reservationId, t)
                                            // IMPORTANT: Ensure the processor becomes available again after an error to accept new work.
                                            // Without this line, a failed processor would get stuck in the busy state forever.
                                            processor.available.compareAndSet(false, true)
                                        }
                                    }
                                    dispatched = true // Mark as successfully dispatched.
                                    foundInThisCycle = true // Mark as found in this cycle.
                                    break // Exit the 'for' loop (found and dispatched).
                                }
                                // If compareAndSet failed, the processor is busy; continue the 'for' loop to check the next processor.

                            } catch (e: IndexOutOfBoundsException) {
                                // Can happen if the processor list changes (unregister) extremely rapidly
                                // between getting the size and accessing the index (less likely with CopyOnWriteArrayList but still good to guard against).
                                logger.warn("IndexOutOfBoundsException while accessing processor at index {}. Retrying cycle.", currentProcessorIndex, e)
                                currentProcessorIndex = -1 // Reset index to safely restart the checking cycle from the beginning.
                                break // Exit the 'for' loop; the outer 'while(!dispatched)' loop will retry.
                            } catch (t: Throwable) {
                                // Other error while trying to access or check the processor (e.g., network error if processor is remote?).
                                logger.error("Error accessing processor at index {} for {}", currentProcessorIndex, reservationId, t)
                                // Continue the 'for' loop to try other processors in this cycle.
                            }
                        } // --- End of 'for' loop (one cycle through processors) ---

                        // If after checking all processors in the cycle, none were available, and the coroutine is still active.
                        if (!foundInThisCycle && isActive) {
                            logger.trace("All {} processors checked for {}, none available. Waiting briefly...", currentListSize, reservationId)
                            // Delay briefly before trying the next check cycle.
                            // Reason: Avoid the 'while(!dispatched)' and 'for' loops running continuously when no processor is free,
                            // causing 100% CPU usage (busy-waiting). The delay yields CPU to other tasks, including running processors.
                            delay(100)
                        }
                        // The 'while (!dispatched)' loop continues until dispatched or cancelled.
                    } // --- End: Wait indefinitely for an available processor ---

                } catch (e: CancellationException) {
                    // Catch the exception when the coroutine is cancelled (due to calling job.cancel() in stop()).
                    logger.info("ReservationEventDistributor loop is stopping due to cancellation.")
                    break // Exit the main while(isActive) loop.
                } catch (t: Throwable) {
                    // Catch critical unexpected errors in the main loop (e.g., error from repos.pull()).
                    logger.error("Critical error in ReservationEventDistributor main loop. Loop continues after delay.", t)
                    // Check isActive before delaying to avoid unnecessary delay if already cancelled.
                    if (isActive) {
                        delay(1000) // Wait a bit before retrying the main loop, hoping the error is resolved.
                    }
                }
            } // --- End of main while(isActive) loop ---

            logger.info("ReservationEventDistributor loop finished.")
        }
        logger.info("ReservationEventDistributor started.")
    }

    /**
     * Stops the distribution loop and cancels all running processor tasks.
     * Waits for the main loop to finish gracefully.
     */
    suspend fun stop() {
        logger.info("Stopping ReservationEventDistributor...")
        // Check if the distributor is running before stopping.
        if (distributorJob?.isActive == true) {
            // Cancel the CoroutineScope's 'job'.
            // This action sends a cancellation signal to 'distributorJob' (the main loop)
            // and ALL running child coroutines (processor handling jobs) launched within this scope.
            job.cancel()

            // Wait for the main loop coroutine (distributorJob) to complete.
            // It will finish after catching CancellationException or completing the final loop iteration.
            // Reason: Ensure the main loop has fully stopped before the stop() function returns.
            try {
                // Add a timeout to prevent `join()` from hanging indefinitely if there's an unexpected issue during cancellation.
                withTimeoutOrNull(5000) {
                    distributorJob?.join()
                } ?: logger.warn("Distributor job did not finish within timeout during stop.") // Log if a timeout occurs.
            } catch (e: Exception) {
                // Catch exceptions that might occur during join (though rare).
                logger.error("Exception while waiting for distributor job to stop", e)
            }
        } else {
            logger.info("ReservationEventDistributor was not running.")
        }
        logger.info("ReservationEventDistributor stopped.")
        // Important Note: Calling job.cancel() only *initiates* the cancellation of processor jobs.
        // These jobs might need more time to actually stop, depending on how
        // they handle CancellationException within their `process()` method (e.g., whether they have suspension points).
        // This stop() function does not wait for processor jobs to fully complete, only for the main loop to stop.
    }

    /**
     * Registers a new processor. Thread-safe due to CopyOnWriteArrayList.
     */
    fun register(p: ReservationProcessor) {
        // Check contains before adding to avoid unnecessary duplicates (although CopyOnWriteArrayList handles it).
        if (!processors.contains(p)) {
            processors.add(p) // The add operation on CopyOnWriteArrayList is thread-safe.
            logger.info("Registered processor: {}", p.hashCode())
        }
    }

    /**
     * Unregisters a processor. Thread-safe due to CopyOnWriteArrayList.
     */
    fun unregister(p: ReservationProcessor) {
        val removed = processors.remove(p) // The remove operation on CopyOnWriteArrayList is thread-safe.
        if (removed) {
            logger.info("Unregistered processor: {}", p.hashCode())
        }
    }
}
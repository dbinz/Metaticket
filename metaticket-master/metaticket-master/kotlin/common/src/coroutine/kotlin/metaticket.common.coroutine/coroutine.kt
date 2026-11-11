package metaticket.common.coroutine


import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 *
 * @author dongcatchay
 */

/**
 * run coroutine async in the suspend function
 */
suspend fun <T> async(b: suspend () -> T?) : Deferred<T?> {
    return CoroutineScope(Dispatchers.Unconfined).async { b() }
}

/**
 * run coroutine async with context in the suspend function
 */
suspend fun <T> async(context: CoroutineContext, b: suspend () -> T?) : Deferred<T?> {
    return CoroutineScope(Dispatchers.Unconfined).async(context) { b() }
}

/**
 * run coroutine launch in the suspend function
 */
suspend fun launch(b: suspend () -> Any?) : Job {
    return CoroutineScope(Dispatchers.Unconfined).launch { b() }
}

/**
 * run coroutine launch with context in the suspend function
 */
suspend fun launch(context: CoroutineContext, b: suspend () -> Any?) : Job {
    return CoroutineScope(Dispatchers.Unconfined).launch(context) { b() }
}

/**
 * Returns a list containing the coroutine deferred results of applying the given [transform] coroutine async function
 * to each element in the original collection.
 */
suspend inline fun <T, R> Iterable<T>.mapAsync(crossinline transform: suspend (T) -> R): List<Deferred<R?>> {
    return this.map { async { transform(it) } }
}

/**
 * Returns a list containing the coroutine job results of applying the given [transform] coroutine launch function
 * to each element in the original collection.
 */
suspend inline fun <T, R> Iterable<T>.mapLaunch(crossinline transform: suspend (T) -> R): List<Job> {
    return this.map { launch { transform(it) } }
}

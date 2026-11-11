package metaticket.order.pocessing

import java.util.concurrent.ConcurrentHashMap


/**
 *
 * @author dongcatchay
 */
private val processingByOrderId: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()

fun isOrderProcessing(orderId: String) : Boolean {
    return processingByOrderId.putIfAbsent(orderId, true) != null
}

fun releaseOrderProcessing(orderId: String) {
    processingByOrderId.remove(orderId)
}

package metaticket.payment.utils

import metaticket.common.checksum.Checksum
import java.text.SimpleDateFormat
import java.util.*


/**
 *
 * @author dongcatchay
 */

fun buildUrlWithChecksum(url: String, data: String, secretKey: String): String {
    val checksum = Checksum.generate(data, secretKey)
    val encodeData = Checksum.encode(data)
    return "$url/$encodeData/$checksum"
}

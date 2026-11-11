package metaticket.admin.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.databind.ObjectMapper
import metaticket.admin.pojo.db.ReportFilterDocument
import metaticket.order.request.LoadInvoiceStatisticsRequest
import java.util.*

fun generateToken(authInfo: LoadInvoiceStatisticsRequest, secret: String): String {

    val builder = JWT.create()
        .withClaim("status", authInfo.status?.map { it.name })
        .withClaim("showId", authInfo.showId)
        .withClaim("sessionId", authInfo.sessionId)
        .withClaim("startDate", authInfo.startDate)
        .withClaim("endDate", authInfo.endDate)

    return builder.sign(Algorithm.HMAC256(secret))
}

fun decodeToken(token: String, secret: String, jsonMapper: ObjectMapper): LoadInvoiceStatisticsRequest {
    val charset = charset("UTF-8")
    val jwt = JWT.require(Algorithm.HMAC256(secret))
    val decodedPayload = jwt.build().verify(token).payload
    val payload = String(Base64.getUrlDecoder().decode(decodedPayload.toByteArray(charset)))
    return jsonMapper.readValue(payload, LoadInvoiceStatisticsRequest::class.java)

}

/** Bộ lọc đầy đủ của report filter: điều kiện trong token + ngày tham quan lưu riêng trên document. */
fun ReportFilterDocument.decodeFilter(secret: String, jsonMapper: ObjectMapper): LoadInvoiceStatisticsRequest =
    decodeToken(filter, secret, jsonMapper).copy(visitDateFrom = visitDateFrom, visitDateTo = visitDateTo)
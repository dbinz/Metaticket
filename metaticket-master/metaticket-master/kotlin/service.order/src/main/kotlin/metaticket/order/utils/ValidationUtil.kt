package metaticket.order.utils

import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.routing.*
import metaticket.common.checksum.Checksum
import metaticket.common.logger.Logging
import metaticket.order.configuration.Environment
import metaticket.order.pojo.request.CreatePromotionRequest
import metaticket.order.pojo.request.LoadOrderByReservationRequest
import metaticket.order.pojo.request.OrderIdOnlyRequest
import metaticket.order.request.CreateOrderRequest
import metaticket.order.request.UpdateOrderInfoRequest
import org.bson.types.ObjectId
import java.util.regex.Pattern

class ValidationUtil constructor() {
    companion object: Logging {
        fun isEmailValid(email: String?): Boolean {
            if (email.isNullOrBlank()) return false
            val regex = Pattern.compile("^[\\w-.]+@([\\w-]+.)+[\\w-]{2,4}\$")
            return regex.matcher(email).matches()
        }

        fun isPhoneValid(phone: String?): Boolean {
            if (phone.isNullOrBlank()) return false
            return phone.matches(Regex("^(?:\\+84|84|0)(3|5|7|8|9)[0-9]{8}\$"))
        }

        fun Route.validateCreateOrderRequest(): PluginInstance = install(RequestValidation) {
            validate<CreateOrderRequest> {
                if (it.reservationId.isBlank()) return@validate ValidationResult.Invalid("Missing or malformed reservation id")
                it.reservationId = Checksum.decode(it.reservationId, Environment.secretKey)
                if (!ObjectId.isValid(it.reservationId)) ValidationResult.Invalid("Missing or malformed reservation id")
                else if (it.name.isBlank()) ValidationResult.Invalid("Tên người thanh toán không được để trống")
                else if (!isEmailValid(it.email)) ValidationResult.Invalid("Email người thanh toán không chính xác")
                else if (!isPhoneValid(it.phone)) ValidationResult.Invalid("Số điện thoại người thanh toán không chính xác")
                else ValidationResult.Valid
            }
        }

        fun Route.validateUpdateOrderInfoRequest(): PluginInstance = install(RequestValidation) {
            validate<UpdateOrderInfoRequest> {
                if (it.orderId.isBlank() || !ObjectId.isValid(it.orderId)) return@validate ValidationResult.Invalid("Missing or malformed order id")
                it.orderId = Checksum.decode(it.orderId, Environment.secretKey)
                if (!ObjectId.isValid(it.orderId)) ValidationResult.Invalid("Missing or malformed order id")
                else if (it.name.isBlank()) ValidationResult.Invalid("Tên người thanh toán không được để trống")
                else if (!isEmailValid(it.email)) ValidationResult.Invalid("Email người thanh toán không chính xác")
                else if (!isPhoneValid(it.phone)) ValidationResult.Invalid("Số điện thoại người thanh toán không chính xác")
                else ValidationResult.Valid
            }
        }

        fun Route.validateOrderId(): PluginInstance = install(RequestValidation) {
            validate<OrderIdOnlyRequest> {
                if (it.orderId.isBlank()) return@validate ValidationResult.Invalid("Missing order id")
                it.orderId = Checksum.decode(it.orderId, Environment.secretKey)
                ValidationResult.Valid
            }
        }

        fun Route.validateLoadOrderByReservationRequest(): PluginInstance = install(RequestValidation) {
            validate<LoadOrderByReservationRequest> { rq ->
                if (rq.showId.isBlank() || !ObjectId.isValid(rq.showId)) return@validate ValidationResult.Invalid("Missing show id")
                if (rq.sessionId.isBlank() || !ObjectId.isValid(rq.sessionId)) return@validate ValidationResult.Invalid("Missing session id")
                if (rq.reservationIds.isEmpty()) return@validate ValidationResult.Invalid("Missing reservation ids")
                rq.reservationIds = rq.reservationIds.map {
                    val decoded = Checksum.decode(it, Environment.secretKey)
                    if (!ObjectId.isValid(decoded)) return@validate ValidationResult.Invalid("invalid reservation ids")
                    decoded
                }
                ValidationResult.Valid
            }
        }

        fun Route.validateCreatePromotionRequest(): PluginInstance = install(RequestValidation) {
            validate<CreatePromotionRequest> {
                if (it.showId.isBlank() || !ObjectId.isValid(it.showId)) ValidationResult.Invalid("Missing or malformed show id")
                else if (it.sessionId.isBlank() || !ObjectId.isValid(it.sessionId)) ValidationResult.Invalid("Missing or malformed session id")
                else if (it.pluginName.isBlank()) ValidationResult.Invalid("Chưa chọn promotion plugin")
                else if (it.configuration.isBlank()) ValidationResult.Invalid("Chưa chọn promotion configuration")
                else ValidationResult.Valid
            }
        }
    }

}

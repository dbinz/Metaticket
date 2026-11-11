package metaticket.payment.datastructure


/**
 *
 * @author dongcatchay
 */

enum class PaymentGateway { NinePay, VNPay, OnePay }

enum class PaymentStatus { Created, Starting, Processing, Succeed, Failed, Refunded, Cancelled }

/**
 * Additive callback-routing discriminator (backward compatible).
 * Absent/empty/legacy payments resolve to [TICKET], preserving the existing ticket flow.
 */
enum class OrderType { TICKET, ACCESSORY }

enum class PaymentType {
    DomesticCard, // ATM_CARD
    InternationalCard, // CREDIT_CARD Thẻ thanh toán thẻ quốc tế
    QrCode, // BANK_TRANSFER Thanh toán Ứng dụng di động ngân hàng và Ví điện tử
    VietQr, // Thanh toán bằng VietQR
    //BNPL, // Mua trước trả sau (Buy Now Pay Later)
}

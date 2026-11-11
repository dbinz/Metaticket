# Tài Liệu Kỹ Thuật - Payment Service

## 1. Tổng Quan Về Chức Năng Chính

Payment Service là một microservice chịu trách nhiệm xử lý toàn bộ quy trình thanh toán trong hệ thống bán vé sự kiện online MetaTicket. Service này đóng vai trò trung gian giữa hệ thống nội bộ và các công ty tài chính bên ngoài (payment gateway), đảm bảo việc xử lý giao dịch thanh toán một cách an toàn và hiệu quả.

### Chức năng chính:
- **Khởi tạo thanh toán**: Tạo và quản lý các giao dịch thanh toán mới
- **Xử lý thanh toán**: Tích hợp với các payment gateway bên ngoài (NinePay, VNPay, OnePay)
- **Theo dõi trạng thái**: Cập nhật và đồng bộ trạng thái thanh toán từ các gateway
- **Thông báo**: Gửi thông báo về trạng thái thanh toán đến service sở hữu đơn hàng, định tuyến theo `orderType` (`TICKET` → Order Service; `ACCESSORY` → Accessory Service)
- **Quản lý cấu hình**: Quản lý thông tin tài khoản payment gateway
- **Bảo mật**: Đảm bảo tính toàn vẹn và bảo mật dữ liệu thanh toán

## 2. Kiến Trúc Và Các Thành Phần Chính

### 2.1 Kiến Trúc Tổng Thể
Payment Service được xây dựng trên framework Ktor với kiến trúc microservice, sử dụng MongoDB làm cơ sở dữ liệu và gRPC để giao tiếp với các service khác.

### 2.2 Các Thành Phần Chính

#### Controllers
- **PaymentController**: Xử lý các API chính về thanh toán
- **NinePayGatewayController**: Xử lý callback từ NinePay gateway
- **VnPayGatewayController**: Xử lý callback từ VNPay gateway
- **OnePayGatewayController**: Xử lý callback từ OnePay gateway
- **PGAccountController**: Quản lý cấu hình tài khoản payment gateway

#### Gateway Workers
- **NinePayPaymentGatewayWorker**: Tích hợp với NinePay API
- **VnPayPaymentGatewayWorker**: Tích hợp với VNPay API
- **OnePayPaymentGatewayWorker**: Tích hợp với OnePay API

#### Database Services
- **PaymentDBService**: Quản lý dữ liệu thanh toán trong MongoDB
- **PGAccountConfigDBService**: Quản lý cấu hình payment gateway

#### Gateway Services
- **OrderNotificationGateway**: Gửi thông báo trạng thái thanh toán qua gRPC, định tuyến theo `orderType` của payment (xem [mục 2.4](#24-định-tuyến-thông-báo-theo-ordertype))
- **JobrunrGateway**: Quản lý background jobs, bao gồm cả job notify trạng thái thanh toán (fallback bất đồng bộ) được định tuyến theo `orderType` (xem [mục 2.4](#24-định-tuyến-thông-báo-theo-ordertype))

### 2.4 Định Tuyến Thông Báo Theo `orderType`

`CreatePaymentRequest` mang thêm một trường phân loại **additive, backward-compatible** là `orderType` (enum, mặc định `TICKET`; giá trị còn lại: `ACCESSORY`) và được lưu trên `PaymentDocument`. Giá trị `null`/rỗng/legacy đều được coi là `TICKET`, nên mọi payment vé hiện có giữ nguyên hành vi.

Khi cập nhật trạng thái thanh toán, Payment Service định tuyến thông báo về đúng service sở hữu đơn hàng dựa trên `orderType`, trên **cả hai** đường:

- **Đồng bộ (gRPC)** — `OrderNotificationGateway` gọi `NotifyPaymentStatusChanged` tới:
  - `TICKET` → `service.order` (luồng vé, không đổi)
  - `ACCESSORY` → handler bên `service.accessory`
- **Bất đồng bộ (JobRunr fallback)** — `JobrunrGateway.notifyPaymentStatusChanged` chọn `JobExecutorServiceName` theo `orderType`:
  - `TICKET` → `OrderService`
  - `ACCESSORY` → `AccessoryService`

#### Cache
- **PGAccountCache**: Cache thông tin tài khoản payment gateway

### 2.3 Cấu Trúc Thư Mục
```
src/main/kotlin/metaticket/payment/
├── controller/          # REST API controllers
├── gateway/            # External gateway integrations
│   └── worker/         # Gateway-specific workers
├── database/           # Database services
├── pojo/              # Data models
│   ├── db/            # Database entities
│   ├── request/       # Request DTOs
│   └── response/      # Response DTOs
├── configuration/     # Configuration classes
└── cache/            # Caching services
```

## 3. Các API Nội Bộ Chính

### 3.1 Payment APIs

#### GET `/payment/status/{paymentId}`
- **Mô tả**: Lấy trạng thái thanh toán
- **Tham số**: 
  - `paymentId` (String): ID của thanh toán (được mã hóa)
- **Response**: `PaymentResponse`
  ```kotlin
  data class PaymentResponse(
      val id: String,
      val paymentNo: String?,
      val payTime: Date?,
      val paymentType: PaymentType?,
      val status: PaymentStatus,
      val amount: Long,
      val pgAccountCode: String,
      val pgAccountName: String,
      val gateway: PaymentGateway
  )
  ```

#### GET `/payment/{paymentId}`
- **Mô tả**: Lấy thông tin chi tiết thanh toán
- **Tham số**: 
  - `paymentId` (String): ID của thanh toán (được mã hóa)
- **Response**: `PaymentDocument`

#### POST `/payment/{paymentId}/start`
- **Mô tả**: Khởi tạo quá trình thanh toán
- **Tham số**: 
  - `paymentId` (String): ID của thanh toán
- **Request Body**: `StartPaymentRequest`
  ```kotlin
  data class StartPaymentRequest(
      var paymentId: String,
      val paymentType: PaymentType
  )
  ```
- **Response**: URL redirect đến payment gateway

#### POST `/payment/{paymentId}/inquiry`
- **Mô tả**: Kiểm tra trạng thái thanh toán từ gateway
- **Request Body**: `InquiryPaymentRequest`
  ```kotlin
  data class InquiryPaymentRequest(
      val paymentId: String
  )
  ```

### 3.2 Payment Gateway Account APIs

#### GET `/pg-account`
- **Mô tả**: Lấy danh sách tài khoản payment gateway
- **Response**: Danh sách `PGAccount`

#### POST `/pg-account`
- **Mô tả**: Thêm tài khoản payment gateway mới
- **Request Body**: Thông tin tài khoản gateway

#### PUT `/pg-account/{code}`
- **Mô tả**: Cập nhật thông tin tài khoản gateway
- **Tham số**: 
  - `code` (String): Mã tài khoản gateway

#### DELETE `/pg-account/{code}`
- **Mô tả**: Xóa tài khoản gateway

#### PUT `/pg-account/{code}/enable`
- **Mô tả**: Kích hoạt/vô hiệu hóa tài khoản gateway

#### PUT `/pg-account/default/{code}`
- **Mô tả**: Đặt tài khoản gateway mặc định

### 3.3 Gateway Callback APIs

#### POST `/ipn/ninepay`
- **Mô tả**: Nhận IPN (Instant Payment Notification) từ NinePay
- **Request Body**: Dữ liệu callback từ NinePay

#### GET `/return/ninepay`
- **Mô tả**: Xử lý return URL từ NinePay
- **Query Parameters**: Các tham số từ NinePay

#### POST `/ipn/vnpay`
- **Mô tả**: Nhận IPN từ VNPay

#### GET `/return/vnpay`
- **Mô tả**: Xử lý return URL từ VNPay

#### POST `/ipn/onepay`
- **Mô tả**: Nhận IPN từ OnePay

#### GET `/return/onepay`
- **Mô tả**: Xử lý return URL từ OnePay

## 4. Tích Hợp Với Payment Gateway Bên Ngoài

### 4.1 NinePay Integration

#### Quy trình tích hợp:
1. **Tạo thanh toán**: Gọi API tạo invoice của NinePay
2. **Redirect**: Chuyển hướng user đến URL thanh toán của NinePay
3. **Callback**: Nhận thông báo từ NinePay qua IPN và return URL
4. **Xác thực**: Kiểm tra checksum và signature
5. **Cập nhật**: Cập nhật trạng thái thanh toán trong database

#### API endpoints sử dụng:
- **Create Invoice**: `POST {baseUrl}/payments/create`
- **Query Status**: `POST {baseUrl}/payments/query`

#### Xử lý response:
```kotlin
// Mapping trạng thái NinePay sang PaymentStatus
val paymentStatus = when (status) {
    NINEPAY_STATUS_PROCESSING -> PaymentStatus.Processing
    NINEPAY_STATUS_SUCCEED -> PaymentStatus.Succeed
    NINEPAY_STATUS_CANCELED -> PaymentStatus.Cancelled
    NINEPAY_STATUS_REFUND -> PaymentStatus.Refunded
    NINEPAY_STATUS_FAILED -> PaymentStatus.Failed
    // ...
}
```

### 4.2 VNPay Integration

#### Quy trình tích hợp:
1. **Tạo URL thanh toán**: Xây dựng URL với các tham số cần thiết
2. **Tạo hash**: Sử dụng HMAC-SHA512 để tạo secure hash
3. **Redirect**: Chuyển hướng user đến VNPay
4. **Callback**: Xử lý return URL và IPN
5. **Xác thực hash**: Kiểm tra tính toàn vẹn dữ liệu

#### Tham số chính:
- `vnp_TmnCode`: Mã merchant
- `vnp_Amount`: Số tiền (VND * 100)
- `vnp_TxnRef`: Payment ID
- `vnp_OrderInfo`: Thông tin đơn hàng
- `vnp_ReturnUrl`: URL trả về
- `vnp_SecureHash`: Hash bảo mật

### 4.3 OnePay Integration

#### Quy trình tích hợp:
1. **Tạo tham số**: Xây dựng các tham số theo định dạng OnePay
2. **Sắp xếp**: Sắp xếp tham số theo thứ tự alphabet
3. **Tạo hash**: Sử dụng HMAC-SHA256 để tạo secure hash
4. **Redirect**: Chuyển hướng đến OnePay gateway
5. **Callback**: Xử lý response từ OnePay

#### Tham số chính:
- `vpc_Merchant`: Merchant ID
- `vpc_AccessCode`: Access code
- `vpc_MerchTxnRef`: Payment ID
- `vpc_Amount`: Số tiền (VND * 100)
- `vpc_OrderInfo`: Thông tin đơn hàng
- `vpc_SecureHash`: Hash bảo mật

## 5. Cơ Chế Xác Thực Và Bảo Mật

### 5.1 Quản Lý Secret Key

#### Environment Configuration
```kotlin
object Environment {
    var secretKey: String = ""
}

// Khởi tạo từ application.conf
private fun Application.configureEnvironment() {
    val secretKey = environment.config.property("secretKey").getString()
    Environment.secretKey = secretKey
}
```

#### Cấu hình Secret Keys:
- **secretKey**: Dùng để mã hóa/giải mã Payment ID (16 ký tự)
- **hashSecret**: Dùng để xác thực return URL và IPN
- **merchantSecretKey**: Secret key riêng cho từng payment gateway

### 5.2 Mã Hóa Payment ID

```kotlin
// Mã hóa Payment ID trước khi trả về client
val encryptedPaymentId = Checksum.encode(paymentId, Environment.secretKey)

// Giải mã Payment ID khi nhận từ client
val decryptedPaymentId = Checksum.decode(encryptedPaymentId, Environment.secretKey)
```

### 5.3 Xác Thực Hash/Signature

#### NinePay Signature Verification
```kotlin
fun signature(queryHttp: String, secretKey: String): String {
    val sha256_HMAC = Mac.getInstance("HmacSHA256")
    val secret_key = SecretKeySpec(secretKey.toByteArray(), "HmacSHA256")
    sha256_HMAC.init(secret_key)
    val hmac = sha256_HMAC.doFinal(queryHttp.toByteArray())
    return Base64.getEncoder().encodeToString(hmac)
}
```

#### VNPay Hash Validation
```kotlin
fun hashAllFields(vnp_HashSecret: String, fields: Map<String, String>): String {
    val fieldNames = fields.keys.sorted()
    val sb = StringBuilder()
    
    fieldNames.forEach { fieldName ->
        val fieldValue = fields[fieldName]
        if (!fieldValue.isNullOrEmpty()) {
            sb.append(fieldName).append("=").append(fieldValue)
            if (fieldName != fieldNames.last()) sb.append("&")
        }
    }
    
    return hmacSHA512(vnp_HashSecret, sb.toString())
}
```

#### OnePay Secure Hash
```kotlin
fun generateSecureHash(params: Map<String, String>, hashKey: String): String {
    val sortedParams = params.toSortedMap()
    val stringToHash = generateStringToHash(sortedParams)
    return OnePayUtil.hashHMAC(stringToHash, hashKey)
}
```

### 5.4 URL Checksum Protection

```kotlin
// Tạo URL với checksum để bảo vệ return URL
fun buildUrlWithChecksum(baseUrl: String, paymentId: String, hashSecret: String): String {
    val checksum = Checksum.generate(paymentId, hashSecret)
    return "$baseUrl?paymentId=${Checksum.encode(paymentId, Environment.secretKey)}&checksum=$checksum"
}

// Xác thực checksum khi nhận return URL
fun validateChecksum(paymentId: String, checksum: String, hashSecret: String): Boolean {
    val expectedChecksum = Checksum.generate(paymentId, hashSecret)
    return checksum == expectedChecksum
}
```

### 5.5 HTTPS và TLS
- Tất cả giao tiếp với payment gateway đều sử dụng HTTPS
- Sử dụng TLS 1.2+ cho bảo mật truyền tải
- Certificate validation cho các kết nối outbound

### 5.6 Input Validation
- Validate tất cả input từ client và payment gateway
- Sanitize dữ liệu trước khi lưu database
- Rate limiting cho các API endpoints

## 6. Các Lưu Ý Đặc Biệt

### 6.1 Đảm Bảo Tính Toàn Vẹn Dữ Liệu

#### Idempotency
- Tất cả các API đều được thiết kế idempotent
- Sử dụng unique constraints trên database
- Kiểm tra trạng thái trước khi thực hiện thay đổi

#### Transaction Management
```kotlin
// Cập nhật trạng thái với điều kiện
suspend fun updatePaymentStatus(
    paymentId: String, 
    status: PaymentStatus, 
    oldStatus: List<PaymentStatus> = emptyList()
): PaymentDocument? {
    val filter = Filters.and(listOfNotNull(
        Filters.eq("_id", ObjectId(paymentId)),
        if (oldStatus.isNotEmpty()) 
            Filters.`in`(PaymentDocument::status.name, oldStatus) 
        else null
    ))
    // ...
}
```

#### Versioning
- Sử dụng version field để detect concurrent modifications
- Increment version mỗi khi update document

### 6.2 Error Handling và Retry Logic

#### Graceful Error Handling
```kotlin
try {
    // Payment gateway call
} catch (t: Throwable) {
    logger.error("Payment {} failed", paymentId, t)
    // Fallback to job queue for retry
    jobrunrGateway.notifyPaymentStatusChanged(payment, PaymentStatus.Failed)
}
```

#### Background Job Retry
- Sử dụng JobRunr cho background processing
- Retry logic với exponential backoff
- Dead letter queue cho failed jobs

### 6.4 Data Privacy và Compliance

#### PCI DSS Compliance
- Không lưu trữ thông tin thẻ tín dụng
- Tokenization cho sensitive data
- Audit trail cho tất cả transactions

### 6.5 Performance Optimization

#### Caching Strategy
```kotlin
// Cache payment gateway configurations
class PGAccountCache {
    private val cache = ConcurrentHashMap<String, PGAccount>()
    
    suspend fun get(code: String): PGAccount? {
        return cache[code] ?: loadFromDB(code)?.also {
            cache[code] = it
        }
    }
}
```

#### Database Indexing
```kotlin
// Tạo indexes cho performance
val idx = paymentCol.createIndexes(listOf(
    IndexModel(Indexes.ascending(PaymentDocument::orderId.name), 
               IndexOptions().unique(true)),
    IndexModel(Indexes.ascending(PaymentDocument::orderCode.name), 
               IndexOptions().unique(true))
))
```

### 6.6 Security Best Practices

#### Secret Management
- Không hardcode secrets trong code
- Sử dụng environment variables
- Rotate secrets định kỳ

#### API Security
- Rate limiting
- Input validation
- CORS configuration
- Authentication và authorization

#### Network Security
- Firewall rules
- VPN cho internal communication
- IP whitelisting cho payment gateways

---

**Lưu ý**: Tài liệu này cần được cập nhật thường xuyên khi có thay đổi trong hệ thống. Mọi thay đổi về security configuration hoặc payment gateway integration cần được review và test kỹ lưỡng trước khi deploy lên production.

**Tài liệu này được cập nhật lần cuối**: 03/06/2026
**Version**: 1.0
**Tác giả**: MetaTicket Development Team
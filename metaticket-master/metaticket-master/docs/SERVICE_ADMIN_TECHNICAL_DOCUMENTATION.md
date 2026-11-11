# Admin Service - Tài liệu Kỹ thuật

## Tổng quan

Admin Service là một microservice quan trọng trong hệ thống MetaTicket, chịu trách nhiệm quản lý các chức năng admin như tạo session draft cho show, quản lý báo cáo, đặt vé và tích hợp với các service khác thông qua gRPC.

### Thông tin cơ bản
- **Framework**: Ktor (Kotlin)
- **Database**: MongoDB
- **Communication**: gRPC, REST API
- **Dependency Injection**: Koin
- **Port**: 4040 (staging), 3040 (production)
- **gRPC Port**: 4140 (staging), 3140 (production)

## Cấu trúc thư mục

```
service.admin/
├── src/main/kotlin/metaticket/admin/
│   ├── controller/           # REST API Controllers
│   │   ├── ShowSessionDraftController.kt
│   │   ├── ReportFilterController.kt
│   │   ├── TicketBookingController.kt
│   │   ├── ReportGovController.kt
│   │   └── ShowSessionDraftPromotionController.kt
│   ├── database/service/     # Database Services
│   │   ├── ShowSessionDraftDBService.kt
│   │   ├── TicketBookingDBService.kt
│   │   ├── ReportFilterDBService.kt
│   │   └── ShowSessionDraftPromotionDBService.kt
│   ├── gateway/             # gRPC Gateway Services
│   │   ├── ShowGateway.kt
│   │   ├── TicketGateway.kt
│   │   ├── ReservationGateway.kt
│   │   ├── OrderGateway.kt
│   │   ├── PaymentGateway.kt
│   │   └── CustomerUserGateway.kt
│   ├── pojo/               # Data Models
│   │   ├── db/             # Database Documents
│   │   ├── request/        # Request DTOs
│   │   └── response/       # Response DTOs
│   ├── configuration/      # Configuration Classes
│   ├── exception/          # Custom Exceptions
│   ├── peer/              # HTTP Client Services
│   ├── utils/             # Utility Functions
│   ├── Application.kt      # Main Application
│   ├── ApplicationDI.kt    # Dependency Injection
│   └── Route.kt           # Route Definitions
└── conf-*/                # Configuration Files
```

## API Documentation

### REST API Endpoints

#### 1. Document Routes (Show Session Draft)

**Base Path**: `/document`

##### POST `/document/session-draft/basic-info`
- **Mô tả**: Tạo thông tin cơ bản cho session draft
- **Request Body**: `CreateShowSessionDraftBasicInfoRequest`
- **Response**: Session draft ID

##### PUT `/document/session-draft/{id}`
- **Mô tả**: Cập nhật session draft
- **Request Body**: `UpdateShowSessionDraftRequest`
- **Response**: Success status

##### PUT `/document/session-draft/{id}/payment`
- **Mô tả**: Cập nhật thông tin thanh toán
- **Request Body**: `UpdateShowSessionDraftPaymentRequest`

##### PUT `/document/session-draft/{id}/seat-plan`
- **Mô tả**: Cập nhật seat plan
- **Request Body**: `UpdateShowSessionDraftSeatPlanRequest`

##### PUT `/document/session-draft/{id}/mail-templates`
- **Mô tả**: Cập nhật mail templates
- **Request Body**: `UpdateShowSessionDraftMailTemplatesRequest`

##### POST `/document/session-draft/{id}/create-session`
- **Mô tả**: Tạo session từ draft
- **Request Body**: `CreateShowSessionRequestDraft`

#### 2. Report Filter Routes

**Base Path**: `/reportFilter`

##### POST `/reportFilter`
- **Mô tả**: Tạo report filter mới
- **Request Body**: `CreateReportFilterRequest`
- **Response**: Report filter ID

##### GET `/reportFilter/list`
- **Mô tả**: Lấy danh sách report filters
- **Query Parameters**: `LoadReportFilterListRequest`
- **Response**: List of `ReportFilterResponse`

##### DELETE `/reportFilter/{id}`
- **Mô tả**: Xóa report filter
- **Response**: Success status

##### PUT `/reportFilter/{id}/enable`
- **Mô tả**: Kích hoạt report filter

##### PUT `/reportFilter/{id}/disable`
- **Mô tả**: Vô hiệu hóa report filter

##### GET `/reportFilter/{reportKey}/load`
- **Mô tả**: Load report data
- **Response**: `LoadReportResponse`

#### 3. Report Government Routes

**Base Path**: `/reportGov`

##### GET `/reportGov`
- **Mô tả**: Lấy báo cáo chính phủ
- **Response**: `ReportGovResponse`

#### 4. Ticket Booking Routes

**Base Path**: `/ticketBooking`

##### GET `/ticketBooking/{showId}/{sessionId}`
- **Mô tả**: Lấy danh sách booking theo show và session
- **Response**: List of `TicketBookingResponse`

##### POST `/ticketBooking/upload-excel`
- **Mô tả**: Upload file Excel để đặt vé hàng loạt
- **Content-Type**: `multipart/form-data`
- **Form Fields**:
  - `file`: Excel file (.xlsx, .xls)
  - `showId`: Show ID
  - `sessionId`: Session ID
  - `customerUserId`: Customer User ID (optional)

##### POST `/ticketBooking/upload-csv`
- **Mô tả**: Upload file CSV để đặt vé hàng loạt
- **Content-Type**: `multipart/form-data`
- **Form Fields**:
  - `file`: CSV file (.csv)
  - `showId`: Show ID
  - `sessionId`: Session ID
  - `customerUserId`: Customer User ID (optional)

### gRPC Services

Admin Service tích hợp với các service khác thông qua gRPC:

#### Show Service
- `getCity(cityId)`: Lấy thông tin thành phố
- `createSession(request)`: Tạo session mới

#### Ticket Service
- `getTicketByShowAndSession()`: Lấy tickets theo show và session
- `getTicketBySids()`: Lấy tickets theo SIDs
- `getTicketByIds()`: Lấy tickets theo IDs

#### Reservation Service
- `createReservation()`: Tạo reservation
- `reserveTicket()`: Reserve ticket
- `getReservation()`: Lấy thông tin reservation
- `releaseReservation()`: Hủy reservation

#### Order Service
- `createOrder()`: Tạo order
- `createOrderPromotion()`: Tạo order promotion

#### Payment Service
- `getPaymentByOrderIds()`: Lấy payments theo order IDs

#### Customer User Service
- `getCustomerUserById()`: Lấy user theo ID
- `getCustomerUserByPhone()`: Lấy user theo số điện thoại

## Database Schema

### Collections

#### 1. show-session-draft
```kotlin
data class ShowSessionDraftDocument(
    val id: String,
    val showId: String,
    val sessionId: String?,
    val seatPlanId: String?,
    val edType: String?,
    val ledgendImage: String?,
    val ledgendImageUrl: String?,
    val eventSquareImage: String?,
    val eventSquareImageUrl: String?,
    val eventNameImage: String?,
    val eventNameImageUrl: String?,
    val code: String?,
    val sessionName: String?,
    val address: String,
    val startTime: Long,
    val endTime: Long,
    val requireDelivery: Boolean,
    val orderNote: String?,
    val paymentOffline: Boolean?,
    val paymentNote: String?,
    val promotionsData: String?,
    val mailPluginTemplate: List<MailPluginTemplate>?,
    val createdAt: Date,
    val status: ShowSessionDraftStatus,
    val city: CityDocument?,
    val isFree: Boolean?,
    val showInHome: Boolean?,
    val pgAccountCode: String?,
    val paymentMethods: List<String>?,
    val slug: String? = null   // Optional canonical session slug carried from the admin form; promoted onto the active session on create-session
)
```

> **Lưu ý về `slug`**: Trường `slug` là tùy chọn (optional). Khi tạo/cập nhật session draft, giá trị slug từ form admin được lưu lại trên draft và được chuyển (promote) sang active session khi gọi `create-session` (chỉ gửi khi slug không rỗng). Bỏ trống đồng nghĩa với không gán slug.

#### 2. show-session-draft-promotion
```kotlin
data class ShowSessionDraftPromotionDocument(
    val id: String,
    val sessionDraftId: String,
    val name: String,
    val data: String,
    val createdAt: Date
)
```

#### 3. show-session-draft-order-custom-field
```kotlin
data class ShowSessionDraftOrderCustomFieldDocument(
    val id: String,
    val sessionDraftId: String,
    val customFields: List<CustomFieldTmpl>,
    val createdAt: Date
)
```

#### 4. report-filter
```kotlin
data class ReportFilterDocument(
    val id: String,
    val showId: String?,
    val sessionId: String?,
    val reportKey: String,
    val reportName: String,
    val username: String,
    val status: ReportFilterStatus,
    val filter: String,
    val version: Int,
    val lastUpdated: Date,
    val createdAt: Date
)
```

#### 5. ticket_booking
```kotlin
data class TicketBookingDocument(
    val id: String,
    val showId: String,
    val sessionId: String,
    val ticketId: String,
    val capacityId: String?,
    val priceCat: String?,
    val sid: String,
    val quantity: Int,
    val customerName: String,
    val customerPhone: String,
    val customerEmail: String,
    val deliveryName: String?,
    val deliveryPhone: String?,
    val provinceName: String?,
    val districtName: String?,
    val wardName: String?,
    val address: String?,
    val customFields: List<CustomFieldData>?,
    val promotionCode: String?,
    val reservationId: String?,
    val orderId: String?,
    val paymentId: String?,
    val version: Int,
    val lastUpdated: Date,
    val createdAt: Date
)
```

## Business Logic

### 1. Show Session Draft Management

#### Workflow tạo Session Draft:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CREATE SESSION DRAFT FLOW                                │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │       ADMIN         │
                              │  POST /document/    │
                              │  session-draft/     │
                              │  basic-info         │
                              └──────────┬──────────┘
                                         │
                                         ▼
                         ┌──────────────────────────────┐
                         │  ShowSessionDraftController  │
                         │  .createBasicInfo()          │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Validation                  │
                         │  - showId must exist         │
                         │  - sessionName required      │
                         │  - code must be unique       │
                         │  - startTime < endTime       │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  ShowSessionDraftDBService   │
                         │  .createDraft()              │
                         │                              │
                         │  - Save to MongoDB           │
                         │  - Status: CREATED           │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │   RESPONSE                   │
                         │   Session draft ID           │
                         └──────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                    UPDATE SESSION DRAFT FLOW                                │
└─────────────────────────────────────────────────────────────────────────────┘

      ┌─────────────────────┐
      │       ADMIN         │
      │  PUT /document/     │
      │  session-draft/{id} │
      │  /payment           │
      └──────────┬──────────┘
                 │
                 ▼
      ┌──────────────────────────────┐
      │  Update Payment Info         │
      │  - paymentOffline            │
      │  - paymentNote               │
      │  - pgAccountCode             │
      │  - paymentMethods            │
      └──────────────┬───────────────┘
                     │
                     ▼
      ┌──────────────────────────────┐
      │  Update Seat Plan            │
      │  - edType                    │
      │  - seatPlanId                │
      └──────────────┬───────────────┘
                     │
                     ▼
      ┌──────────────────────────────┐
      │  Update Mail Templates       │
      │  - mailPluginTemplate        │
      └──────────────┬───────────────┘
                     │
                     ▼
      ┌──────────────────────────────┐
      │  Create Session              │
      │  POST /document/session-     │
      │  draft/{id}/create-session   │
      │                              │
      │  - ShowGateway.createSession │
      │  - Create tickets            │
      │  - Open for sales            │
      └──────────────┬───────────────┘
                     │
                     ▼
      ┌──────────────────────────────┐
      │   RESPONSE                   │
      │   Session created            │
      └──────────────────────────────┘
```

#### Validation Rules:
- `showId` phải hợp lệ và tồn tại
- `sessionName` không được trống
- `code` phải unique
- `startTime` phải nhỏ hơn `endTime`
- `cityId` phải tồn tại trong Show Service

### 2. Report Filter Management

#### Workflow:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       REPORT FILTER FLOW                                    │
└─────────────────────────────────────────────────────────────────────────────┘

      ┌─────────────────────┐
      │       ADMIN         │
      │  POST /reportFilter │
      └──────────┬──────────┘
                 │
                 ▼
      ┌──────────────────────────────┐
      │  ReportFilterController      │
      │  .createReportFilter()       │
      └──────────────┬───────────────┘
                     │
                     ▼
      ┌──────────────────────────────┐
      │  Encode Filter Data          │
      │  - Use secretKey             │
      │  - Encrypt filter conditions │
      └──────────────┬───────────────┘
                     │
                     ▼
      ┌──────────────────────────────┐
      │  ReportFilterDBService       │
      │  .createFilter()             │
      │                              │
      │  - Save to MongoDB           │
      │  - report-filter collection │
      └──────────────┬───────────────┘
                     │
                     ▼
      ┌──────────────────────────────┐
      │   Load Report                │
      │   GET /reportFilter/         │
      │   {reportKey}/load           │
      └──────────────┬───────────────┘
                     │
                     ▼
      ┌──────────────────────────────┐
      │  Decode Filter               │
      │  - Decrypt filter data       │
      │  - Get filter conditions     │
      └──────────────┬───────────────┘
                     │
        ┌────────────┴────────────┐
        │                         │
        ▼                         ▼
┌─────────────────────┐  ┌─────────────────────┐
│  Order Gateway      │  │  Payment Gateway    │
│  - getOrders()      │  │  - getPayments()    │
└─────────┬───────────┘  └─────────┬───────────┘
          │                         │
          └───────────┬─────────────┘
                      │
                      ▼
      ┌──────────────────────────────┐
      │   RESPONSE                   │
      │   Report data                │
      └──────────────────────────────┘
```

#### Security:
- Filter data được mã hóa bằng secret key
- Chỉ user có quyền mới có thể tạo/xóa filter

### 3. Ticket Booking Management

#### Workflow đặt vé:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    TICKET BOOKING FLOW                                      │
└─────────────────────────────────────────────────────────────────────────────┘

      ┌─────────────────────┐
      │       ADMIN         │
      │  POST /ticketBooking│
      │  /upload-excel      │
      │  (multipart/form)   │
      └──────────┬──────────┘
                 │
                 ▼
      ┌──────────────────────────────┐
      │  TicketBookingController     │
      │  .uploadExcel()              │
      └──────────────┬───────────────┘
                     │
                     ▼
      ┌──────────────────────────────┐
      │  Parse Excel File            │
      │  - Read .xlsx/.xls           │
      │  - Validate structure        │
      └──────────────┬───────────────┘
                     │
                     ▼
      ┌──────────────────────────────┐
      │  Validate Data               │
      │  - Check show/session exist │
      │  - Validate ticket data      │
      │  - Check capacity available │
      └──────────────┬───────────────┘
                     │
        ┌────────────┴────────────┐
        │                         │
        ▼                         ▼
┌─────────────────────┐  ┌─────────────────────┐
│  Validation Failed  │  │  Validation OK      │
│                     │  │                     │
│  Return 400 Error   │  │  Continue           │
└─────────────────────┘  └──────────┬──────────┘
                                    │
                                    ▼
      ┌──────────────────────────────┐
      │  TicketBookingDBService      │
      │  .createBookings()           │
      │                              │
      │  - Save booking records     │
      │  - Status: PENDING          │
      └──────────────┬───────────────┘
                     │
                     ▼
      ┌──────────────────────────────┐
      │  ReservationGateway          │
      │  .createReservation()        │
      │  .reserveTicket()            │
      │                              │
      │  - Create reservation        │
      │  - Reserve tickets           │
      └──────────────┬───────────────┘
                     │
                     ▼
      ┌──────────────────────────────┐
      │  OrderGateway                │
      │  .createOrder()              │
      │                              │
      │  - Create order from         │
      │    reservation              │
      └──────────────┬───────────────┘
                     │
                     ▼
      ┌──────────────────────────────┐
      │  Update Booking Status       │
      │  - Status: COMPLETED         │
      │  - Link orderId, resId       │
      └──────────────┬───────────────┘
                     │
                     ▼
      ┌──────────────────────────────┐
      │   RESPONSE                   │
      │   List of booking results    │
      └──────────────────────────────┘
```

#### File Format Support:
- **Excel**: .xlsx, .xls
- **CSV**: .csv

#### Validation:
- File size không quá giới hạn
- Format file phải đúng
- Dữ liệu trong file phải hợp lệ
- Show và Session phải tồn tại

## Security và Authorization

### 1. Secret Key Management

```kotlin
object Environment {
    var secretKey: String = ""
}
```

- Secret key được load từ configuration file
- Sử dụng để mã hóa/giải mã sensitive data

### 2. CORS Configuration

```kotlin
install(CORS) {
    allowHost("*", listOf("http", "https"))
    allowOrigins { it in listOf(
        "http://localhost", 
        "https://devlocal.metaticket.vn", 
        "https://admin.devlocal.metaticket.vn", 
        "*"
    ) }
    allowMethod(HttpMethod.Get)
    allowMethod(HttpMethod.Post)
    allowMethod(HttpMethod.Options)
    allowCredentials = true
    allowHeader(HttpHeaders.ContentType)
}
```

### 3. gRPC Security

### 4. Data Encryption

- Report filter data được mã hóa
- Sensitive IDs (reservationId, orderId, paymentId) được encode bằng checksum

## Gateway Services

### 1. ShowGateway
```kotlin
class ShowGateway {
    suspend fun getCity(cityId: String): ShowMessage.CityProto?
    suspend fun createSession(request: ShowMessage.CreateSessionRequest): String?
}
```

### 2. TicketGateway
```kotlin
class TicketGateway {
    suspend fun loadTicketByShowAndSession(showId: String, sessionId: String): List<TicketMessage.TicketProto>
    suspend fun loadTicketBySids(showId: String, sessionId: String, sids: List<String>): List<TicketMessage.TicketProto>
    suspend fun loadTicketByIds(ids: List<String>): List<TicketMessage.TicketProto>
}
```

### 3. ReservationGateway
```kotlin
class ReservationGateway {
    suspend fun createReservation(opUserId: String, customerUserId: String, showId: String, sessionId: String, details: TicketMessage.ReservationDetailProto): String
    suspend fun reserveTicket(opUserId: String, customerUserId: String, reservationId: String): String
    suspend fun getReservation(reservationId: String): TicketMessage.ReservationProto
    suspend fun releaseTicket(opUserId: String, customerUserId: String, reservationId: String)
}
```

### 4. OrderGateway
```kotlin
class OrderGateway {
    suspend fun createOrder(req: OrderMessage.CreateOrderRequest): String
    suspend fun createOrderPromotion(showId: String, sessionId: String, data: String, pluginName: String)
}
```

### 5. PaymentGateway
```kotlin
class PaymentGateway {
    suspend fun getPaymentByOrderIds(orderIds: List<String>): List<PaymentMessage.PaymentProto>
}
```

### 6. CustomerUserGateway
```kotlin
class CustomerUserGateway {
    suspend fun findUserByIdOrPhone(idOrPhone: String): CustomerUserMessage.CustomerUserProto
}
```

## Dependency Injection (Koin)

### Module Structure

```kotlin
// Configuration Module
val configurationModule = module {
    single<ApplicationEnvironment> { ktorEnv }
    single<Events> { monitor }
    single<JsonNodeFactory> { JsonNodeFactory.instance }
    single<ObjectMapper> { /* Jackson configuration */ }
}

// HTTP Client Module
val httpClientModule = module {
    single<HttpClient> { /* Ktor HTTP client with Jackson */ }
    single<OrderClient> { OrderClient(get(), get()) }
}

// Controller Module
val controllerModule = module {
    single<ShowSessionDraftController> { /* Dependencies injection */ }
    single<ReportFilterController> { /* Dependencies injection */ }
    single<TicketBookingController> { /* Dependencies injection */ }
    // ... other controllers
}

// Database Module
val databaseModule = module {
    single<CodecRegistry> { /* MongoDB codec configuration */ }
    single<MongoDatabase> { /* MongoDB connection */ }
    // Collections
    single<MongoCollection<ShowSessionDraftDocument>> { /* Collection setup */ }
    // Services
    single<ShowSessionDraftDBService> { /* Service injection */ }
}

// Gateway Module
val gatewayModule = module {
    single<ShowGateway> { ShowGateway(get()) }
    single<TicketGateway> { TicketGateway(get()) }
    // ... other gateways
}

// gRPC Channel Module
val grpcChannelModule = module {
    single<ShowServiceGrpcKt.ShowServiceCoroutineStub> { /* gRPC stub setup */ }
    // ... other gRPC stubs
}
```

### DI Context

```kotlin
class ApplicationDI(ktorEnv: ApplicationEnvironment, monitor: Events) : DIContext(ktorEnv, monitor) {
    val startUp: StartUp by inject()
    val sessionDraftCtrl: ShowSessionDraftController by inject()
    val reportFilterCtrl: ReportFilterController by inject()
    val ticketBookingCtrl: TicketBookingController by inject()
    // ... other controllers
}
```

## Configuration Management

### Environment-specific Configs

### Configuration Properties

- **secretKey**: Encryption/decryption key
- **db.connection**: MongoDB connection string
- **db.dbName**: Database name
- **db.*ColName**: Collection names
- **ktor.deployment.port**: HTTP server port
- **grpc.server.port**: gRPC server port
- **grpc.channel.***: gRPC client configurations
- **ticketBooking.defaultCustomerUserId**: Default customer for booking
- **reportGov.***: Government report credentials

### Error Handling

#### gRPC Error Handling
```kotlin
private suspend inline fun <T> executeGrpcCall(
    operationName: String,
    crossinline grpcCall: suspend () -> T
): T {
    return try {
        grpcCall()
    } catch (e: StatusException) {
        val description = e.status.description ?: "Unknown error"
        val errorMessage = when (e.status.code) {
            Status.Code.NOT_FOUND -> "Không tìm thấy '$operationName': $description"
            Status.Code.FAILED_PRECONDITION -> "Không hợp lệ '$operationName': $description"
            Status.Code.UNAVAILABLE -> "Hệ thống chưa sẵn sàng '$operationName'. Please try again later."
            Status.Code.INTERNAL -> "Lỗi hệ thống '$operationName': $description"
            Status.Code.PERMISSION_DENIED -> "Không có quyền truy cập '$operationName': $description"
            else -> "Lỗi chưa xác định '$operationName': ${e.status.code} - $description"
        }
        throw PeerServiceException(errorMessage, e)
    }
}
```

## Integration với Other Services

### 1. Show Service
- Lấy thông tin city và show
- Tạo session từ draft

### 2. Ticket Service
- Quản lý tickets và reservations
- Seat management

### 3. Order Service
- Tạo orders từ bookings
- Order promotion management

### 4. Payment Service
- Payment tracking và reporting
- Payment method configuration

### 5. Customer User Service
- Customer information lookup
- User validation

### 6. Notification Service
- Email notifications
- SMS notifications (nếu có)

## Troubleshooting

### Common Issues

1. **gRPC Connection Issues**
   - Check service availability
   - Verify port configurations
   - Check network connectivity

2. **Database Connection Issues**
   - Verify MongoDB connection string
   - Check database credentials
   - Monitor connection pool

3. **File Upload Issues**
   - Check file format và size
   - Verify file content structure
   - Monitor memory usage

4. **Configuration Issues**
   - Verify environment-specific configs
   - Check secret key configuration
   - Validate port assignments

---

**Tài liệu này được cập nhật lần cuối**: 03/06/2026
**Version**: 1.0
**Tác giả**: MetaTicket Development Team
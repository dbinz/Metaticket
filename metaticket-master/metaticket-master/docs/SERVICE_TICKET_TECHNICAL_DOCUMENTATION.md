# Tài Liệu Kỹ Thuật - Ticket Service

## 1. Tổng Quan

### Chức Năng Chính
Ticket Service là một microservice quan trọng trong hệ thống MetaTicket, chịu trách nhiệm quản lý toàn bộ vòng đời của vé sự kiện bao gồm:

- **Tạo và quản lý vé**: Tạo vé cho các show và session, cập nhật thông tin vé
- **Đặt chỗ (Reservation)**: Quản lý việc đặt chỗ tạm thời với thời gian hết hạn
- **Phân phối vé**: Kiểm soát số lượng vé có sẵn và đã bán
- **Kiểm tra và xác thực vé**: Xác minh tính hợp lệ của vé
- **Hủy đặt vé và giải phóng vé**: Xử lý việc hủy đặt chỗ và giải phóng vé
- **Quản lý danh mục**: Phân loại vé theo khu vực, loại ghế, mức giá

### Vai Trò Trong Hệ Thống
- Tích hợp với Show Service để lấy thông tin sự kiện
- Kết nối với JobRunr Service để xử lý các tác vụ nền
- Sử dụng Redis để cache và quản lý trạng thái real-time
- Lưu trữ dữ liệu trong MongoDB

## 2. Kiến Trúc và Thành Phần Chính

### Cấu Trúc Thư Mục
```
service.ticket/
├── src/main/kotlin/metaticket/ticket/
│   ├── controller/          # REST API Controllers
│   ├── database/service/    # Database Access Layer
│   ├── pojo/               # Data Transfer Objects
│   │   ├── db/             # Database Documents
│   │   ├── request/        # Request Models
│   │   └── response/       # Response Models
│   ├── service/            # Business Logic Services
│   ├── gateway/            # External Service Gateways
│   ├── cache/              # Caching Layer
│   ├── redis/              # Redis Operations
│   ├── processor/          # Background Processors
│   └── datastructure/      # Enums and Constants
└── src/api/kotlin/         # API Definitions
```

### Thành Phần Chính

#### Controllers
- **TicketDocumentController**: Quản lý CRUD operations cho vé
- **TicketReservationController**: Xử lý đặt chỗ và giải phóng vé
- **CategoryController**: Quản lý phân loại vé

#### Database Services
- **TicketDocDBService**: Thao tác với collection ticket-doc
  - `addReservedCap(pairs)`: Atomic increment resCap và decrement remCap trong MongoDB
- **TicketReservationDBService**: Quản lý reservation data
- **TicketCapacityDBService**: Quản lý sức chứa vé
  - `addReservedCap(tcId, quantity)`: Atomic increment resCap và decrement remCap cho ticket capacity
  - `addReservedCap(pairs)`: Batch update reserved capacity
- **CategoryMappingDBService**: Quản lý mapping danh mục

#### Core Services
- **ReservationCoreService**: Logic nghiệp vụ chính cho reservation
- **ReservationExecutionRetryable**: Xử lý retry và rollback
- **ReservationProcessorManager**: Quản lý background processors

#### Redis Layer
- **RedisAsyncClient**: Async client cho Redis operations
  - `addTicketRemCap(ticketId, quantity)`: Atomic increment/decrement remaining capacity (dùng Lua script)
  - `updateReservationStatus(resId, oldStatus, newStatus)`: Atomic CAS update reservation status (dùng Lua script)
  - `setReservation(reservation)`: Store reservation document
  - `getReservation(resId)`: Fetch reservation document
  - `removeReservation(resId)`: Remove reservation
- **Lua Scripts**: 
  - `update.ticket.remaining.script.lua`: Atomic capacity updates
  - `update.reservation.status.script.lua`: CAS status updates

#### Cache Layer
- **ShowSessionCache**: Cache trạng thái mở bán của show/session
- **ConfigPerReservationCache**: Cache cấu hình đặt chỗ

### Trạng Thái Vé (TicketStatus)
- **Created**: Vé mới được tạo
- **Opened**: Vé đang mở bán
- **Blocked**: Vé bị khóa tạm thời
- **Closed**: Vé đã đóng bán

### Trạng Thái Đặt Chỗ (TicketReservationStatus)
- **Created**: Đặt chỗ mới tạo
- **Reserving**: Đang trong quá trình đặt chỗ
- **Reserved**: Đã đặt chỗ thành công
- **Locked**: Đã khóa để thanh toán
- **Unlocked**: Đã mở khóa
- **Removing**: Đang hủy đặt chỗ
- **Removed**: Đã hủy đặt chỗ
- **OrderPaying**: Đang thanh toán
- **OrderSuccessful**: Thanh toán thành công
- **OrderCancelled**: Đã hủy đơn hàng
- **OrderFailed**: Thanh toán thất bại

## 2.1 Redis Caching Layer

### Mục Đích Sử Dụng Redis
Redis được sử dụng để đảm bảo tính atomic khi cập nhật các thông tin sau:
- **Remaining capacity**: Số lượng vé còn lại (remCap)
- **Reserved capacity**: Số lượng vé đã đặt (resCap)
- **Reservation status**: Trạng thái của đặt chỗ

### Cấu Hình Redis
```kotlin
// Redis keys prefix
ticket.{ticketId}      // Remaining capacity for a ticket
reservation.{resId}    // Reservation document in Redis
```

### Lua Scripts Để Đảm Bảo Tính Atomic
Hai Lua scripts được sử dụng để đảm bảo atomic operations khi cập nhật capacities trong Redis:

#### update.ticket.remaining.script.lua
**Vị trí**: `src/main/resources/lua/update.ticket.remaining.script.lua`

**Chức năng**: Cập nhật remaining capacity một cách atomic, chỉ cho phép update khi remaining sau khi cập nhật >= 0.

**Parameters**:
- `KEYS[1]`: Redis key cho remaining capacity (ticket.{ticketId})
- `ARGV[1]`: Quantity thay đổi (âm để giảm, dương để tăng)

**Return Values**:
- `>= 0`: Remaining capacity mới sau khi cập nhật thành công
- `-1`: Thao tác thất bại (dẫn đến negative capacity hoặc Redis set lỗi)
- `-999`: Ticket capacity key không tồn tại

**Mã nguồn**:
```lua
local remCap = redis.call('get', KEYS[1])
if not remCap then return -999 end

remCap = tonumber(remCap)
local qty = tonumber(ARGV[1])
local tmp = remCap + qty

if tmp < 0 then return -1 end

if redis.call('set', KEYS[1], tmp) then return tmp end
return -1
```

#### update.reservation.status.script.lua
**Vị trí**: `src/main/resources/lua/update.reservation.status.script.lua`

**Chức năng**: Cập nhật status của reservation một cách atomic, chỉ cho phép update khi current status khớp với expected old status (CAS - Compare-And-Swap).

**Parameters**:
- `KEYS[1]`: Redis key cho reservation (reservation.{resId})
- `ARGV[1]`: Expected current status (JSON encoded)
- `ARGV[2]`: New status để set (JSON encoded)

**Return Values**:
- `0`: Update thành công
- `-1`: Update thất bại (old status mismatch hoặc input không hợp lệ)
- `-999`: Reservation key không tồn tại

**Mã nguồn**:
```lua
local id = KEYS[1]
local inputOldStatus = cjson.decode(ARGV[1])
local newStatus = cjson.decode(ARGV[2])
local reservation = redis.call('get', id)

if reservation == false then return -999 end
if inputOldStatus == false then return -1 end
if newStatus == false then return -1 end

local obj = cjson.decode(reservation)
local oldStatus = obj.status

if oldStatus ~= inputOldStatus then return -1 end

obj.status = newStatus
obj.version = obj.version + 1

if redis.call('set', KEYS[1], cjson.encode(obj)) then return 0 end
return -1
```

### RedisAsyncClient
**Vị trí**: `src/main/kotlin/metaticket/ticket/redis/RedisAsyncClient.kt`

Cung cấp các async methods để tương tác với Redis:
- `start()`: Load và register Lua scripts vào Redis server
- `shutdown()`: Đóng connections và cleanup
- `getTicketRemCap(ticketId)`: Lấy remaining capacity
- `setTicketRemCap(ticketId, quantity)`: Set remaining capacity
- `addTicketRemCap(ticketId, quantity)`: Atomic increment/decrement remaining capacity (dùng Lua script)
- `updateReservationStatus(resId, oldStatus, newStatus)`: Atomic CAS update reservation status (dùng Lua script)
- `setReservation(reservation)`: Set reservation document
- `getReservation(resId)`: Lấy reservation document
- `removeReservation(resId)`: Xóa reservation khỏi Redis

### JobRunr Integration
Các background jobs để đảm bảo eventual consistency khi Redis operations thất bại:
- **RollbackRemainingCapRedis**: Rollback remaining capacity trên Redis
- **RollbackReservedCapDb**: Rollback reserved capacity trên Database
- **UpdateReservationStatusDb**: Update reservation status trên DB nếu Redis update thất bại

## 3. API Chính

### 3.1 Ticket Document APIs

#### Public APIs

**GET /ticket/doc/fetch**
- Mô tả: Lấy thông tin vé theo ID (có kiểm tra session)
- Tham số: `id` (query parameter)
- Response: `TicketResponse`

**POST /ticket/doc/fetch**
- Mô tả: Lấy nhiều vé theo danh sách ID (có kiểm tra session)
- Body: `List<String>` (ticket IDs)
- Response: `List<TicketResponse>`

**GET /ticket/doc/fetch-all**
- Mô tả: Lấy tất cả vé của show và session (có versioning)
- Tham số: `showId`, `sessionId`
- Response: `List<TicketResponse>`

**POST /ticket/doc/fetch-all**
- Mô tả: Lấy tất cả vé của show và session (backward compatible)
- Body: `LoadAllTicketDocRequest`
- Response: `List<TicketResponse>`

**GET /ticket/doc/fetch-time**
- Mô tả: Lấy vé theo khoảng thời gian (có versioning)
- Tham số: `showId`, `sessionId`, `startTime`, `endTime`
- Response: `List<TicketResponse>`

**POST /ticket/doc/fetch-time**
- Mô tả: Lấy vé theo khoảng thời gian (backward compatible)
- Body: `LoadTicketDocByTimeRequest`
- Response: `List<TicketResponse>`

#### Private APIs

**GET /private/ticket/doc/fetch**
- Mô tả: Lấy thông tin vé theo ID (không kiểm tra session)
- Tham số: `id` (query parameter)
- Response: `TicketResponse`

**POST /private/ticket/doc/fetch**
- Mô tả: Lấy nhiều vé theo danh sách ID (không kiểm tra session)
- Body: `List<String>` (ticket IDs)
- Response: `List<TicketResponse>`

**POST /private/ticket/doc/fetch-all**
- Mô tả: Lấy tất cả vé của show và session (không kiểm tra session)
- Body: `LoadAllTicketDocRequest`
- Response: `List<TicketResponse>`

**POST /private/ticket/doc/create**
- Mô tả: Tạo vé mới
- Body: `CreateTicketDocRequest`
- Response: `List<TicketResponse>`

**POST /private/ticket/doc/update**
- Mô tả: Cập nhật thông tin vé
- Body: `UpdateTicketDocRequest`
- Response: `TicketResponse`

**POST /private/ticket/doc/update-slot**
- Mô tả: Cập nhật số slot vé
- Body: `UpdateTicketSlotRequest`
- Response: Success message

**GET /private/ticket/doc/count/by-show**
- Mô tả: Đếm số lượng vé theo show
- Tham số: `showId`
- Response: `CountTicketOfShowResponse`

**POST /private/ticket/doc/delete-by-cat**
- Mô tả: Xóa vé theo danh mục
- Body: Category filter
- Response: Success message

### 3.2 Ticket Reservation APIs

**POST /ticket/reservation/create**
- Mô tả: Tạo đặt chỗ mới
- Headers: `X-Customer-User-Id`
- Body: `CreateTicketReservationRequest`
- Response: `ReservationResponse` (với encoded ID)
- Flow:
  1. Validate show/session mở
  2. Validate số lượng vé
  3. Generate reservation ID
  4. Store reservation document vào Redis (status = Created)
  5. Schedule auto-expiration job nếu cần

**GET /ticket/reservation/reserve**
- Mô tả: Xác nhận đặt chỗ (chuyển trạng thái từ Created sang Reserving)
- Headers: `X-Customer-User-Id`
- Tham số: `id` (encoded reservation ID)
- Response: `ReservationResponse`
- Flow:
  1. Atomic CAS update reservation status trên Redis (Created -> Reserving)
  2. Schedule background processor để reserve tickets
  3. Background processor:
     - Atomic update remaining capacity trên Redis (`addTicketRemCap`)
     - Update reserved capacity trên Database
     - Atomic update reservation status trên Redis (Reserving -> Reserved)
     - Store reservation vào Database

**GET /ticket/reservation/release**
- Mô tả: Hủy đặt chỗ
- Headers: `X-Customer-User-Id`
- Tham số: `id` (encoded reservation ID)
- Response: Success message
- Flow:
  1. Atomic CAS update reservation status trên Redis (Reserved -> Removing)
  2. Atomic update remaining capacity trên Redis (increase remCap)
  3. Update reserved capacity trên Database (decrease resCap)
  4. Xóa reservation khỏi Redis
  5. Update reservation status trên Database (Removing -> Removed)

**GET /ticket/reservation/fetch**
- Mô tả: Lấy thông tin đặt chỗ
- Tham số: `id` (encoded reservation ID)
- Response: `TicketReservationResponse`
- Priority: Redis -> Database (fallback)

**POST /private/ticket/reservation/fetch**
- Mô tả: Lấy danh sách đặt chỗ (private)
- Body: `List<String>` (reservation IDs)
- Response: `List<TicketReservationResponse>`

**POST /private/ticket/reservation/fetch-by-ticket**
- Mô tả: Lấy đặt chỗ theo ticket ID
- Body: `LoadReservationByTicketId`
- Response: `List<TicketReservationResponse>`

**GET /private/ticket/reservation/unlock**
- Mô tả: Mở khóa đặt chỗ
- Tham số: `id` (reservation ID)
- Response: Success message

**POST /private/ticket/reservation/lock-ticket**
- Mô tả: Khóa vé để thanh toán
- Body: Lock request
- Response: Success message

**POST /private/ticket/reservation/release-expired**
- Mô tả: Giải phóng đặt chỗ hết hạn
- Body: `ReleaseExpiredReservationRequest`
- Response: Success message

### 3.3 Capacity Update APIs (Internal/Background)

**POST /private/ticket/doc/capacity/update**
- Mô tả: Cập nhật capacity trong MongoDB (reserved và remaining)
- Body: `UpdateCapacityRequest`
- Response: Success message
- Flow:
  1. Atomic increment resCap: `resCap = resCap + quantity`
  2. Atomic decrement remCap: `remCap = remCap - quantity`
  3. Increment version
  4. Update lastModified

**POST /private/ticket/capacity/update**
- Mô tả: Cập nhật capacity trong ticket-capacity collection
- Body: `UpdateCapacityRequest`
- Response: `TicketCapacityResponse`

### 3.4 Category Management APIs

**POST /private/ticket/category/mapping/create**
- Mô tả: Tạo mapping danh mục
- Body: `CreateCategoryMappingRequest`
- Response: Success message

**POST /private/ticket/category/mapping/add-update**
- Mô tả: Thêm hoặc cập nhật mapping danh mục
- Body: `AddOrUpdateCategoryMappingRequest`
- Response: Success message

**POST /private/ticket/category/mapping/delete**
- Mô tả: Xóa mapping danh mục
- Body: `DeleteCategoryMappingRequest`
- Response: Success message

**GET /private/ticket/category/mapping/load-by-show-session**
- Mô tả: Lấy mapping theo show và session
- Tham số: `showId`, `sessionId`
- Response: Category mapping data

## 4. Ví Dụ Request/Response

### 4.1 Tạo Vé

**Request:**
```json
POST /private/ticket/doc/create
{
  "showId": "507f1f77bcf86cd799439011",
  "sessionId": "507f1f77bcf86cd799439012",
  "tickets": [
    {
      "sid": "A1",
      "dname": "Ghế A1",
      "seatType": "Seat",
      "totCap": 1,
      "priceCat": "VIP",
      "basePrice": 500000,
      "policy": "Không hoàn tiền"
    }
  ]
}
```

**Response:**
```json
[
  {
    "id": "507f1f77bcf86cd799439013",
    "sid": "A1",
    "showId": "507f1f77bcf86cd799439011",
    "sessionId": "507f1f77bcf86cd799439012",
    "seatType": "Seat",
    "status": "Created",
    "totCap": 1,
    "remCap": 1,
    "resCap": 0,
    "priceCat": "VIP",
    "ticketSlots": 1,
    "basePrice": 500000,
    "dname": "Ghế A1",
    "policy": "Không hoàn tiền",
    "version": 1,
    "createdAt": 1640995200000,
    "lastModified": 1640995200000
  }
]
```

### 4.2 Tạo Đặt Chỗ

**Request:**
```json
POST /ticket/reservation/create
Headers: X-Customer-User-Id: user123
{
  "showId": "507f1f77bcf86cd799439011",
  "sessionId": "507f1f77bcf86cd799439012",
  "details": [
    {
      "ticketId": "507f1f77bcf86cd799439013",
      "quantity": 1,
      "priceCat": "VIP",
      "sid": "A1"
    }
  ]
}
```

**Response:**
```json
{
  "id": "encoded_reservation_id_abc123"
}
```

### 4.3 Lấy Thông Tin Đặt Chỗ

**Request:**
```
GET /ticket/reservation/fetch?id=encoded_reservation_id_abc123
```

**Response:**
```json
{
  "id": "507f1f77bcf86cd799439014",
  "showId": "507f1f77bcf86cd799439011",
  "sessionId": "507f1f77bcf86cd799439012",
  "details": [
    {
      "ticketId": "507f1f77bcf86cd799439013",
      "quantity": 1,
      "priceCat": "VIP",
      "sid": "A1"
    }
  ],
  "status": "Reserved",
  "customerUserId": "user123",
  "expireInS": 300,
  "createdAt": 1640995200000,
  "updatedAt": 1640995200000
}
```

### 4.4 Cập nhật Trạng Thái Vé

**Request:**
```json
POST /private/ticket/doc/update
{
  "showId": "507f1f77bcf86cd799439011",
  "sessionId": "507f1f77bcf86cd799439012",
  "sid": "A1",
  "dname": "Ghế A1 - Cập nhật",
  "totCap": 1,
  "priceCat": "VIP",
  "policy": "Vé VIP"
}
```

**Response:**
```json
{
  "id": "507f1f77bcf86cd799439013",
  "sid": "A1",
  "showId": "507f1f77bcf86cd799439011",
  "sessionId": "507f1f77bcf86cd799439012",
  "seatType": "Seat",
  "status": "Created",
  "totCap": 1,
  "remCap": 1,
  "resCap": 0,
  "priceCat": "VIP",
  "ticketSlots": 1,
  "basePrice": 500000,
  "dname": "Ghế A1 - Cập nhật",
  "policy": "Vé ghế A1",
  "version": 2,
  "createdAt": 1640995200000,
  "lastModified": 1640995500000
}
```

### 4.5 Cập Nhật Remaining Capacity trên Redis (Atomic)

**Lua Script**: `update.ticket.remaining.script.lua`

**Request (via RedisAsyncClient.addTicketRemCap)**:
- ticketId: "507f1f77bcf86cd799439013"
- quantity: -1 (giảm 1 vé đã đặt)
- hoặc quantity: +1 (tăng lại khi hủy đặt chỗ)

**Response**:
- `>= 0`: Remaining capacity mới sau khi cập nhật thành công
- `-1`: Không đủ vé (remaining < 0 sau khi cập nhật)
- `-999`: Ticket capacity key không tồn tại

**Atomic Flow**:
1. `GET ticket.{ticketId}` -> nhận remCap hiện tại
2. Tính toán: `tmp = remCap + quantity`
3. Kiểm tra: `if tmp < 0 then return -1`
4. `SET ticket.{ticketId} tmp`
5. `return tmp`

**Example - Đặt 1 vé VIP (giảm remaining)**:
```lua
-- Redis: GET ticket.507f1f77bcf86cd799439013
-- Return: "5"

-- Redis: SET ticket.507f1f77bcf86cd799439013 4
-- Return: 4 (success)
```

**Example - Hủy đặt chỗ (tăng remaining)**:
```lua
-- Redis: GET ticket.507f1f77bcf86cd799439013
-- Return: "4"

-- Redis: SET ticket.507f1f77bcf86cd799439013 5
-- Return: 5 (success)
```

## 5. Cơ Chế Xác Thực và Bảo Mật

### 5.1 Phân Loại API

#### Public APIs
- Yêu cầu kiểm tra session hợp lệ
- Chỉ cho phép truy cập dữ liệu công khai
- Có cơ chế rate limiting

#### Private APIs
- Dành cho internal services
- Không yêu cầu session validation
- Có full access đến dữ liệu

### 5.2 Xác Thực Người Dùng

#### Header Authentication
- **X-Customer-User-Id**: Xác định người dùng cho reservation APIs
- Được validate trong `getCustomerUserIdIdOrThrow()` method
- Throw `HttpUnauthorizedException` nếu thiếu

#### ID Encoding/Decoding
- Sử dụng `Checksum.encode()` và `Checksum.decode()`
- Secret key được cấu hình trong `Environment.secretKey`
- Mã hóa reservation ID trước khi trả về client
- Giải mã khi nhận request từ client

### 5.3 Bảo Vệ Thông Tin Vé

#### Data Validation
- Validate input parameters trong controllers
- Sử dụng `RequestValidationException` cho bad requests
- Type-safe data models với Kotlin data classes

#### Access Control
- Kiểm tra quyền sở hữu reservation trong `validateUserPermission()`
- Chỉ cho phép user truy cập reservation của chính họ
- Admin APIs được bảo vệ bằng `/private/` prefix

#### CORS Configuration
```kotlin
install(CORS) {
    allowHost("*", listOf("http", "https"))
    allowOrigins { it in listOf("http://localhost", "https://devlocal.metaticket.vn", "*") }
    allowMethod(HttpMethod.Get)
    allowMethod(HttpMethod.Post)
    allowMethod(HttpMethod.Options)
    allowCredentials = true
    allowHeader(HttpHeaders.ContentType)
}
```

### 5.4 Bảo Mật Dữ Liệu

#### Database Security
- MongoDB connection với authentication
- Sử dụng ObjectId cho primary keys
- Indexed queries để tránh full table scan

#### Redis Security
- Redis cluster configuration
- Custom value codec cho serialization
- TTL cho cached data

#### gRPC Security
- Plaintext trong development
- TLS support cho production (commented)
- Service-to-service authentication

### 5.5 Error Handling và Logging

#### Exception Handling
```kotlin
exception<RequestValidationException> { call, cause ->
    call.respond(HttpStatusCode.BadRequest, cause.reasons.joinToString())
}
exception<Throwable> { call, cause ->
    logger.error("unknown exception... ${call.request.toLogString()}", cause)
    call.respondText(text = "500: InternalServerError", status = HttpStatusCode.InternalServerError)
}
```

#### Security Logging
- Log tất cả unauthorized attempts
- Log reservation operations với user ID
- Không log sensitive data (passwords, tokens)
- Request/response logging với sanitization

### 5.6 Rate Limiting và DDoS Protection

#### Background Job Protection
- JobRunr integration cho async processing
- Retry mechanisms với exponential backoff
- Circuit breaker pattern cho external services

#### Resource Protection
- Connection pooling cho database
- Redis cluster cho high availability
- Graceful shutdown mechanisms

## 6. Mã Lỗi Thường Gặp

### HTTP Status Codes

| Code | Mô Tả | Nguyên Nhân |
|------|-------|-------------|
| 200 | OK | Request thành công |
| 400 | Bad Request | Dữ liệu đầu vào không hợp lệ |
| 401 | Unauthorized | Thiếu hoặc sai thông tin xác thực |
| 404 | Not Found | Không tìm thấy resource |
| 500 | Internal Server Error | Lỗi hệ thống |

### Error Response Format
```json
{
  "error": "Mô tả lỗi bằng tiếng Việt"
}
```

### Xử Lý Lỗi

#### Retry Mechanisms
- Automatic retry cho transient errors
- Exponential backoff cho external service calls
- Circuit breaker cho cascading failures

#### Fallback Strategies
- Cache fallback khi database unavailable
- Graceful degradation cho non-critical features
- Error recovery procedures

## 7. Hướng Dẫn Triển Khai

### 7.1 Yêu Cầu Hệ Thống

#### Runtime Requirements
- **JVM**: OpenJDK 11 hoặc cao hơn
- **Memory**: Tối thiểu 2GB RAM
- **CPU**: 2 cores trở lên
- **Storage**: 10GB free space

#### Dependencies
- **MongoDB**: 4.4 trở lên
- **Redis Cluster**: 6.0 trở lên
- **Lettuce**: Redis async client (Redis Cluster support)
- **Show Service**: gRPC endpoint
- **JobRunr Service**: gRPC endpoint

### 7.2 Cấu Hình Môi Trường

#### Application Configuration
```hocon
# secretKey to encrypt/decrypt 16 bit
secretKey = "METATICKET____VN"

# database configuration
db {
    connection = "mongodb://localhost:27017"
    dbName = "metaticket"
    colTicketDoc = "ticket-doc"
    colTicketCapacity = "ticket-capacity"
    colTicketReservation = "ticket-reservation"
    colConfigPerReservation = "config-per-reservation"
    categoryMappingColName = "category-mapping"
}

# redis configuration
redis {
    cluster {
        nodes = [
            { host = "redis1", port = 6379 },
            { host = "redis2", port = 6379 },
            { host = "redis3", port = 6379 }
        ]
    }
    keyPrefix = {
        ticket = "ticket."
        reservation = "reservation."
    }
}

# ktor configuration
ktor {
    deployment {
        port = ...
    }
    application {
        modules = [ metaticket.ticket.ApplicationKt.module ]
    }
}

# grpc configuration
grpc {
    server {
        port = ...
    }
    channel {
        showS {
            host = "127.0.0.1"
            port = ...
        }
        jobrunrS {
            host = "127.0.0.1"
            port = ...
        }
    }
}
```

#### Environment Variables
- `TICKET_SERVICE_PORT`: HTTP server port
- `TICKET_GRPC_PORT`: gRPC server port
- `MONGODB_URI`: MongoDB connection string
- `REDIS_CLUSTER_NODES`: Redis cluster endpoints

### 7.3 Build và Deploy

### 7.4 Health Check

### 7.6 Backup và Recovery

## 8. Ghi Chú Đặc Biệt

### 8.1 Quản Lý Quan Hệ Dữ Liệu

#### Ticket-Show Relationship
- Mỗi ticket thuộc về một show và session cụ thể
- Foreign key references: `showId`, `sessionId`
- Cascade operations khi show/session bị xóa

#### Reservation-Ticket Relationship
- One-to-many: một reservation có thể chứa nhiều ticket
- Atomic operations để đảm bảo consistency
- Rollback mechanisms khi có lỗi

### 8.2 Quy Tắc Chuyển Đổi Trạng Thái

#### Ticket Status Transitions
```
Created → Opened → Blocked → Closed
         ↓
       Blocked ← → Opened
```

#### Reservation Status Flow
```
Created → Reserving → Reserved → Locked → OrderPaying → OrderSuccessful
    ↓         ↓          ↓         ↓           ↓
  Removed   Removed   Unlocked  Unlocked   OrderFailed
                                            OrderCancelled
```

### 8.3 Đảm Bảo Tính Nhất Quán Dữ Liệu

#### ACID Transactions
- MongoDB transactions cho multi-document operations
- Redis transactions cho atomic updates
- Compensating transactions cho distributed operations

### 8.4 Tối Ưu Hóa Hiệu Suất

#### Caching Strategy
- Redis caching cho hot data (remaining capacity, reservations)
- Application-level caching (show sessions, configs)
- Cache invalidation policies

#### Async Processing
- Background job processing với JobRunr
- Event-driven architecture
- Non-blocking I/O operations

### 8.5 Quy Trình Cập Nhật Capacity

#### Flow Đặt Chỗ (Reservation)
1. **Redis (Atomic)**:
   - `addTicketRemCap(ticketId, -quantity)`: Giảm remaining capacity
   - Lua script đảm bảo remaining >= 0 sau khi giảm
   - Return: new remaining capacity hoặc error code

2. **MongoDB (Atomic)**:
   - `addReservedCap(ticketId, quantity)`: Tăng reserved capacity
   - `remCap = remCap - quantity`
   - `resCap = resCap + quantity`
   - Version incremented

3. **Redis (CAS)**:
   - `updateReservationStatus(resId, oldStatus, newStatus)`
   - Compare-and-swap để tránh race conditions

#### Flow Hủy Đặt Chỗ (Release)
1. **Redis (CAS)**:
   - `updateReservationStatus(resId, Reserved, Removing)`

2. **Redis (Atomic)**:
   - `addTicketRemCap(ticketId, +quantity)`: Tăng lại remaining capacity

3. **MongoDB (Atomic)**:
   - `addReservedCap(ticketId, -quantity)`: Giảm reserved capacity
   - `remCap = remCap + quantity`
   - `resCap = resCap - quantity`

4. **Redis (Remove)**:
   - `removeReservation(resId)`: Xóa reservation khỏi Redis

5. **MongoDB (Update)**:
   - `updateStatus(resId, Removing, Removed)`: Cập nhật status

#### Error Handling và Retry
- Redis operation failures → JobRunr background jobs để retry
- Database operation failures → JobRunr background jobs để retry
- Compensating transactions để đảm bảo eventual consistency

### 8.6 Database Schema

#### ticket-doc Collection
```kotlin
data class TicketDocument(
    _id: ObjectId,              // Primary key
    showId: ObjectId,           // Reference to show
    sessionId: ObjectId,        // Reference to session
    capacityId: ObjectId?,      // Reference to ticket-capacity (optional)
    sid: String,                // Seat ID (e.g., "A1")
    seatType: SeatType,         // Seat/Block/Zone
    status: TicketStatus,       // Created/Open/Blocked/Closed
    dname: String?,             // Display name
    resCap: Int?,               // Reserved capacity
    totCap: Int?,               // Total capacity
    remCap: Int?,               // Remaining capacity
    priceCat: String,           // Price category (e.g., "VIP")
    basePrice: Long,            // Base price
    policy: String?,            // Ticket policy
    ticketSlots: Int?,          // Number of slots
    version: Int,               // Optimistic locking
    createdAt: Date,
    lastModified: Date
)
```

#### ticket-capacity Collection
```kotlin
data class TicketCapacityDocument(
    _id: ObjectId,              // Primary key
    showId: ObjectId,           // Reference to show
    sessionId: ObjectId?,       // Reference to session (nullable for global)
    priceCat: String?,          // Price category (nullable for global)
    description: String?,       // Capacity description
    resCap: Int,                // Reserved capacity
    totCap: Int,                // Total capacity
    remCap: Int,                // Remaining capacity
    version: Int,               // Optimistic locking
    createdAt: Date,
    lastModified: Date
)
```

#### ticket-reservation Collection
```kotlin
data class TicketReservationDocument(
    _id: ObjectId,              // Primary key
    showId: ObjectId,           // Reference to show
    sessionId: ObjectId,        // Reference to session
    details: List<TicketReservationDetailsDocument>,
    status: TicketReservationStatus,  // Created/Reserving/Reserved/Locked/etc.
    customerUserId: String,     // Customer user ID
    expireInS: Long,            // Expiration time in seconds
    version: Int,               // Optimistic locking
    createdAt: Date,
    lastModified: Date
)

data class TicketReservationDetailsDocument(
    ticketId: ObjectId,         // Reference to ticket-doc
    capacityId: ObjectId?,      // Reference to ticket-capacity (optional)
    quantity: Int,              // Number of tickets
    priceCat: String,           // Price category
    sid: String                 // Seat ID
)
```
---

**Tài liệu này được cập nhật lần cuối**: 03/06/2026
**Version**: 1.0
**Tác giả**: MetaTicket Development Team
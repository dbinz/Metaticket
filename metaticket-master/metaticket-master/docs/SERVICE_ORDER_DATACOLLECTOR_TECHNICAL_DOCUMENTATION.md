# Tài liệu Kỹ thuật - Order DataCollector Service

## 1. Tổng quan về chức năng chính

Order DataCollector Service là một microservice trong hệ thống bán vé sự kiện online MetaTicket, chịu trách nhiệm thu thập và quản lý dữ liệu khách hàng (custom fields) cho các đơn đặt vé. Service này cho phép khách hàng nhập thông tin cá nhân (họ tên, email, số điện thoại, v.v.) theo template được định nghĩa trước cho từng show/session.

### Chức năng chính:
- **Tạo link thu thập dữ liệu**: Sinh link để khách hàng nhập thông tin
- **Quản lý template**: Lưu trữ và cung cấp template fields cho từng show/session
- **Thu thập dữ liệu**: Lưu trữ thông tin khách hàng đã nhập
- **Validate dữ liệu**: Kiểm tra tính hợp lệ của dữ liệu thu thập
- **Kiểm tra trạng thái**: Cung cấp gRPC API để kiểm tra dữ liệu đã thu thập chưa

## 2. Kiến trúc và các thành phần chính

### 2.1. Cấu trúc thư mục

```
service.order.datacollector/
├── src/
│   ├── api/kotlin/metaticket/order/datacollector/
│   │   ├── datastructure/
│   │   │   └── request.kt                    # Request DTOs
│   │   └── proto/                            # gRPC proto files
│   └── main/kotlin/metaticket/order/datacollector/
│       ├── Application.kt                    # Entry point và cấu hình Ktor
│       ├── ApplicationDI.kt                  # Dependency injection setup
│       ├── StartUp.kt                        # Startup logic
│       ├── controller/
│       │   └── OrderDataCollectorController.kt  # REST API controller
│       ├── service/
│       │   └── OrderDataCollectorServiceImpl.kt # gRPC service implementation
│       ├── database/
│       │   └── service/
│       │       ├── OrderDataCollectorDBService.kt
│       │       ├── OrderDataCollectedDBService.kt
│       │       └── OrderDataCollectorMappingDBService.kt
│       ├── gateway/
│       │   └── ReservationGateway.kt         # gRPC to Ticket Service
│       ├── pojo/
│       │   └── db/                           # Database models
│       │       ├── OrderDataCollectorMapping.kt
│       │       ├── OrderDataCollector.kt
│       │       └── OrderDataCollected.kt
│       └── constants/
│           └── Environment.kt                # Configuration utilities
├── conf/
│   └── application.conf                      # Application configuration
└── build.gradle.kts                          # Build configuration
```

### 2.2. Các thành phần chính

#### Controllers
| Controller | Mô tả |
|------------|-------|
| **OrderDataCollectorController** | Xử lý các REST API endpoints |

#### Services
| Service | Mô tả |
|---------|-------|
| **OrderDataCollectorServiceImpl** | gRPC service implementation |

#### Database Services
| Service | Mô tả |
|---------|-------|
| **OrderDataCollectorDBService** | Quản lý OrderDataCollector (đơn hàng chờ thu thập) |
| **OrderDataCollectedDBService** | Quản lý OrderDataCollected (dữ liệu đã thu thập) |
| **OrderDataCollectorMappingDBService** | Quản lý mapping template cho show/session |

#### Gateways
| Gateway | Mô tả |
|---------|-------|
| **ReservationGateway** | Giao tiếp với Ticket Service qua gRPC |

### 2.3. Công nghệ sử dụng

- **Framework**: Ktor (Netty engine)
- **Database**: MongoDB với Kotlin Coroutine driver
- **Communication**: REST API + gRPC
- **Dependency Injection**: Koin

## 3. Data Models

### 3.1. OrderDataCollectorMapping

```kotlin
data class OrderDataCollectorMapping(
    val showId: String,                               // Show ID
    val sessionId: String,                            // Session ID
    val eventName: String?,                           // Tên sự kiện
    val faviconUrl: String?,                          // URL favicon
    // Fields cho từng ticket (key: ticketId)
    val individualTickets: Map<String, List<CustomFieldTmpl>> = emptyMap(),
    // Fields chung cho từng price category (key: priceCat)
    val priceCategories: Map<String, List<CustomFieldTmpl>> = emptyMap()
)
```

### 3.2. CustomFieldTmpl

```kotlin
data class CustomFieldTmpl(
    val id: String,                    // Field ID
    val name: String,                  // Tên hiển thị
    val type: String,                  // Loại: text, email, phone, select
    val required: Boolean,             // Bắt buộc hay không
    val options: List<String>?,        // Options cho select type
    val regex: String?,                // Regex validation
    val placeholder: String?           // Placeholder text
)
```

### 3.3. OrderDataCollector

```kotlin
data class OrderDataCollector(
    val id: String,                    // ObjectId
    val showId: String,                // Show ID
    val sessionId: String,             // Session ID
    val resId: String,                 // Reservation ID
    val tickets: List<TicketReservationDetail>, // Thông tin tickets
    val dataCollectedId: String?,      // ID của dữ liệu đã thu thập
    val dataCollectedStatus: OrderDataCollectedStatus  // Trạng thái
)
```

### 3.4. OrderDataCollected

```kotlin
data class OrderDataCollected(
    val id: String,                    // ObjectId
    val showId: String,                // Show ID
    val sessionId: String,             // Session ID
    val resId: String,                 // Reservation ID
    val status: OrderDataCollectedStatus,  // Trạng thái: EMPTY, INVALID, VALID
    // Dữ liệu cho từng ticket (key: ticketId)
    val individualTickets: Map<String, List<ListCustomFieldData>> = emptyMap(),
    // Dữ liệu cho từng price category (key: priceCat)
    val priceCategories: Map<String, List<CustomFieldData>> = emptyMap()
)
```

### 3.5. OrderDataCollectedStatus

```kotlin
enum class OrderDataCollectedStatus {
    EMPTY,      // Chưa thu thập dữ liệu
    INVALID,    // Dữ liệu không hợp lệ
    VALID       // Dữ liệu hợp lệ
}
```

## 4. Các API chính

### 4.1. Link Generation APIs

#### POST `/odc/link/generate`
- **Mô tả**: Tạo link thu thập dữ liệu từ reservation ID
- **Headers**: `X-Customer-User-Id` (required)
- **Request Body**: `GenerateLinkRequest`
```kotlin
data class GenerateLinkRequest(
    val reservationId: String  // Encoded reservation ID
)
```
- **Response**: `GenerateLinkResponse`
```kotlin
data class GenerateLinkResponse(
    val collectorId: String,   // Encoded collector ID
    val link: String           // Full URL to collect data
)
```

### 4.2. Template APIs

#### GET `/odc/template/load/{id}`
- **Mô tả**: Load template thu thập dữ liệu
- **Path Parameters**: `id` - Encoded collector ID
- **Response**: `OrderDataCollectorTemplateResponse`

### 4.3. Data Collection APIs

#### POST `/odc/data/save`
- **Mô tả**: Lưu dữ liệu khách hàng đã nhập
- **Request Body**: `SaveOrderDataCollectedRequest`
```kotlin
data class SaveOrderDataCollectedRequest(
    val collectorId: String,   // Encoded collector ID
    val individualTickets: Map<String, List<CustomFieldData>>,
    val priceCategories: Map<String, List<CustomFieldData>>
)
```
- **Response**: Success/Error message

#### GET `/odc/data/load/{id}`
- **Mô tả**: Load dữ liệu đã thu thập
- **Path Parameters**: `id` - Encoded collector ID
- **Response**: `OrderDataCollectedResponse`

#### POST `/odc/data/is-valid/link`
- **Mô tả**: Kiểm tra dữ liệu có hợp lệ không
- **Request Body**: `CheckValidLinkRequest`
```kotlin
data class CheckValidLinkRequest(
    val collectorId: String
)
```
- **Response**: `CheckValidLinkResponse`
```kotlin
data class CheckValidLinkResponse(
    val isValid: Boolean,
    val message: String?
)
```

### 4.4. gRPC Service

#### OrderDataCollectorService
- **Port**: 4132
- **Method**: `checkOrderDataCollectorIsValid`
```protobuf
rpc CheckOrderDataCollectorIsValid(CheckOrderDataCollectorIsValidRequest) 
    returns (CheckOrderDataCollectorIsValidResponse);
```

## 5. Flow xử lý chính

### 5.1. Flow tạo link thu thập dữ liệu

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    GENERATE COLLECTION LINK FLOW                            │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │      CLIENT         │
                              │  POST /odc/link     │
                              │      /generate      │
                              │  X-Customer-User-Id │
                              └──────────┬──────────┘
                                         │
                                         ▼
                         ┌──────────────────────────────┐
                         │  OrderDataCollectorController│
                         │  .generateLink()             │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Decode reservation ID       │
                         │  Checksum.decode()           │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  ReservationGateway          │
                         │  .getReservation()           │
                         │  (gRPC to Ticket Service)    │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Get showId, sessionId       │
                         │  from reservation           │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  OrderDataCollectorDBService │
                         │  .createCollector()          │
                         │                              │
                         │  - Save to MongoDB           │
                         │  - Initialize status = EMPTY │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Encode collector ID         │
                         │  Checksum.encode()           │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │   RESPONSE                   │
                         │   {                           │
                         │     collectorId: "...",      │
                         │     link: "http://..."       │
                         │   }                           │
                         └──────────────────────────────┘
```

### 5.2. Flow tải template

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       LOAD TEMPLATE FLOW                                    │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │      CLIENT         │
                              │  GET /odc/template  │
                              │  /load/{id}         │
                              └──────────┬──────────┘
                                         │
                                         ▼
                         ┌──────────────────────────────┐
                         │  OrderDataCollectorController│
                         │  .loadTemplate()             │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Decode collector ID         │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  OrderDataCollectorDBService │
                         │  .loadCollector()            │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Get showId, sessionId       │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  OrderDataCollectorMapping   │
                         │  DBService                   │
                         │  .loadMapping()              │
                         │                              │
                         │  - Load template by          │
                         │    showId/sessionId          │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Merge with ticket info      │
                         │  - Add ticket IDs            │
                         │  - Add price categories      │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │   RESPONSE                   │
                         │   OrderDataCollector         │
                         │   TemplateResponse           │
                         │                              │
                         │   {                           │
                         │     eventName: "...",        │
                         │     individualTickets: {...},│
                         │     priceCategories: {...}   │
                         │   }                           │
                         └──────────────────────────────┘
```

### 5.3. Flow lưu dữ liệu

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        SAVE COLLECTED DATA FLOW                             │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │      CLIENT         │
                              │  POST /odc/data     │
                              │  /save              │
                              └──────────┬──────────┘
                                         │
                                         ▼
                         ┌──────────────────────────────┐
                         │  OrderDataCollectorController│
                         │  .saveData()                 │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Validate Input              │
                         │  - Check required fields     │
                         │  - Validate format           │
                         └──────────────┬───────────────┘
                                        │
                          ┌────────────┴────────────┐
                          │                         │
                          ▼                         ▼
               ┌─────────────────────┐   ┌─────────────────────┐
               │  Validation Failed  │   │  Validation OK      │
               │                     │   │                     │
               │  Return 400 Error   │   │  Continue           │
               └─────────────────────┘   └──────────┬──────────┘
                                                    │
                                                    ▼
                         ┌──────────────────────────────┐
                         │  OrderDataCollectedDBService │
                         │  .saveCollectedData()        │
                         │                              │
                         │  - Save to MongoDB           │
                         │  - Set status: VALID         │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  OrderDataCollectorDBService │
                         │  .updateCollectorStatus()    │
                         │                              │
                         │  - Link dataCollectedId      │
                         │  - Update status             │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │   RESPONSE                   │
                         │   {                           │
                         │     message: "Saved          │
                         │            successfully"     │
                         │   }                           │
                         └──────────────────────────────┘
```

### 5.4. Flow kiểm tra dữ liệu hợp lệ (gRPC)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CHECK DATA VALIDITY FLOW (gRPC)                          │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │   ORDER SERVICE     │
                              │                     │
                              │  Check data before  │
                              │  creating order     │
                              └──────────┬──────────┘
                                         │
                                         ▼
                         ┌──────────────────────────────┐
                         │  gRPC Call                  │
                         │  CheckOrderDataCollector    │
                         │  IsValid()                  │
                         │                             │
                         │  Request:                   │
                         │  - resId                    │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────��───────┐
                         │  OrderDataCollectorService   │
                         │  Impl                        │
                         │  .checkOrderDataCollector    │
                         │  IsValid()                   │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  OrderDataCollectorDBService │
                         │  .loadCollectorByResId()     │
                         └──────────────┬───────────────┘
                                        │
                          ┌────────────┴────────────┐
                          │                         │
                          ▼                         ▼
               ┌─────────────────────┐   ┌─────────────────────┐
               │  Collector Found    │   │  Collector Not      │
               │                     │   │  Found              │
               └─────────┬───────────┘   └─────────┬───────────┘
                         │                         │
                         ▼                         ▼
               ┌─────────────────────┐   ┌─────────────────────┐
               │  Check status:      │   │  Return:            │
               │  - EMPTY → INVALID  │   │  isValid = false    │
               │  - VALID → OK       │   │  message = "Not     │
               │  - INVALID → OK     │   │  found"             │
               └─────────┬───────────┘   └─────────────────────┘
                         │
                         ▼
               ┌─────────────────────────────────┐
               │  gRPC Response:                 │
               │  {                              │
               │    isValid: true/false,        │
               │    message: "..."              │
               │  }                              │
               └─────────────────────────────────┘
```

## 6. Database Schema

### 6.1. Collections

| Collection | Mô tả |
|------------|-------|
| **order-data-collector-mapping** | Template fields cho từng show/session |
| **order-data-collector** | Đơn hàng chờ thu thập dữ liệu |
| **order-data-collected** | Dữ liệu đã thu thập |

### 6.2. Document Structures

**order-data-collector-mapping:**
```json
{
  "_id": "ObjectId",
  "showId": "show123",
  "sessionId": "session123",
  "eventName": "Concert 2024",
  "faviconUrl": "https://example.com/favicon.ico",
  "individualTickets": {
    "ticket1": [
      { "id": "name", "name": "Họ tên", "type": "text", "required": true },
      { "id": "email", "name": "Email", "type": "email", "required": true }
    ]
  },
  "priceCategories": {
    "VIP": [
      { "id": "company", "name": "Tên công ty", "type": "text", "required": false }
    ]
  }
}
```

**order-data-collector:**
```json
{
  "_id": "ObjectId",
  "showId": "show123",
  "sessionId": "session123",
  "resId": "res123",
  "tickets": [
    { "ticketId": "ticket1", "quantity": 2, "priceCat": "VIP" }
  ],
  "dataCollectedId": "collectorData123",
  "dataCollectedStatus": "VALID"
}
```

**order-data-collected:**
```json
{
  "_id": "ObjectId",
  "showId": "show123",
  "sessionId": "session123",
  "resId": "res123",
  "status": "VALID",
  "individualTickets": {
    "ticket1": [
      { "id": "name", "value": "Nguyễn Văn A" },
      { "id": "email", "value": "email@example.com" }
    ]
  },
  "priceCategories": {
    "VIP": [
      { "id": "company", "value": "Công ty ABC" }
    ]
  }
}
```

## 7. Cấu hình

### 7.1. Application Configuration

**File**: `conf/application.conf`

```hocon
db {
    connection = "mongodb://localhost:47017"
    dbName = "metaticket"
    orderDataCollectorMappingColName = "order-data-collector-mapping"
    orderDataCollectorColName = "order-data-collector"
    orderDataCollectedColName = "order-data-collected"
}

odc {
    linkBaseUrl = "http://localhost:4200/odcapp"
}

ktor {
    deployment {
        port = 4032
    }
}

grpc {
    server {
        port = 4132
    }
    channel {
        ticketS {
            host = "127.0.0.1"
            port = 4104
        }
    }
}

secretKey = "METATICKET____VN"
```

### 7.2. Ports

| Environment | HTTP Port | gRPC Port |
|-------------|-----------|-----------|
| Development | 4032 | 4132 |
| Production | 3032 | 3132 |

## 8. Tích hợp với các Service khác

### 8.1. Các Service tích hợp

| Service | Port | Mục đích |
|---------|------|----------|
| **Ticket Service** | 4104 | Lấy thông tin reservation |
| **Order Service** | 4105 | Kiểm tra dữ liệu trước khi tạo order |

### 8.2. Integration Flow

```
┌─────────────────┐         ┌─────────────────────┐
│  Order Service  │         │  Ticket Service     │
│                 │         │                     │
│  Before create  │         │  getReservation()   │
│  order          │◄────────┤                     │
└────────┬────────┘         └─────────────────────┘
         │
         │ gRPC
         ▼
┌─────────────────────────────────────────────────┐
│        Order DataCollector Service              │
│                                                 │
│  - checkOrderDataCollectorIsValid()            │
│  - Return isValid + message                     │
└────────┬────────────────────────────────────────┘
         │
         │ Validation result
         ▼
┌─────────────────┐
│  Order Service  │
│                 │
│  Proceed or     │
│  reject order   │
└─────────────────┘
```

## 9. Bảo mật

### 9.1. Cơ chế bảo mật

- **ID Encoding**: Sử dụng Checksum để encode/decode IDs
- **Secret Key**: Dùng để mã hóa ID (16 ký tự)
- **Customer User ID**: Yêu cầu header X-Customer-User-Id cho một số operations

### 9.2. CORS Configuration

```kotlin
install(CORS) {
    allowHost("*", listOf("http", "https"))
    allowOrigins { it in listOf(
        "http://localhost",
        "https://devlocal.metaticket.vn",
        "http://localhost:4200"
    ) }
    allowMethod(HttpMethod.Get)
    allowMethod(HttpMethod.Post)
    allowMethod(HttpMethod.Options)
    allowCredentials = true
    allowHeader(HttpHeaders.ContentType)
}
```

## 10. Dependencies

| Library | Mục đích |
|---------|----------|
| **ktor-server-netty** | HTTP Server |
| **mongodb-driver-sync** | MongoDB Driver |
| **koin-core** | Dependency Injection |
| **jackson-module-kotlin** | JSON Serialization |
| **grpc-kotlin** | gRPC communication |

## 11. Error Handling

### 11.1. HTTP Status Codes

| Status Code | Mô tả |
|-------------|-------|
| 200 | Thành công |
| 400 | Bad Request - Dữ liệu không hợp lệ |
| 404 | Không tìm thấy document |
| 500 | Internal Server Error |

### 11.2. Error Response Format

```json
{
  "error": "Mô tả lỗi bằng tiếng Việt"
}
```

---

**Tài liệu này được cập nhật lần cuối**: 04/06/2026
**Version**: 1.0
**Tác giả**: MetaTicket Development Team
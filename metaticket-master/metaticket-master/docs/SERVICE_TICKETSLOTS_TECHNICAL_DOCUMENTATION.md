# Tài liệu Kỹ thuật - Ticket Slots Service

## 1. Tổng quan về chức năng chính

Ticket Slots Service (còn gọi là Smart Ticket) là một microservice trong hệ thống bán vé sự kiện online MetaTicket, chịu trách nhiệm quản lý và theo dõi các ticket slots - đại diện cho từng vị trí ngồi cụ thể trong một sự kiện có chỗ ngồi cố định (theater, concert, stadium...).

### Chức năng chính:
- **Tạo ticket slots từ order**: Chuyển đổi đơn hàng thành các ticket slots cụ thể
- **Quản lý QR code**: Tạo và quản lý mã QR cho từng ticket slot
- **Quét QR code tại cổng**: Xác thực và check-in vé tại điểm kiểm soát
- **Chuyển nhượng vé**: Cho phép chuyển vé giữa các người dùng với xác thực OTP
- **Theo dõi trạng thái**: Quản lý vòng đời của ticket slot từ tạo đến check-in

## 2. Kiến trúc và các thành phần chính

### 2.1. Cấu trúc thư mục

```
service.ticketslots/
├── src/
│   ├── grpc/proto/                             # gRPC protocol definitions
│   │   ├── ticket_slots_service.proto          # Service definition
│   │   └── ticket_slots_message.proto          # Message definitions
│   └── main/kotlin/metaticket/ticketslots/
│       ├── Application.kt                      # Entry point và cấu hình Ktor
│       ├── ApplicationDI.kt                    # Dependency injection setup
│       ├── StartUp.kt                          # Startup logic
│       ├── controller/                         # REST API Controllers
│       │   ├── TicketSlotsCtrl.kt
│       │   ├── AdminQRCodeProducerEventController.kt
│       │   ├── AdminQRCodeConsumerEventController.kt
│       │   └── AdminOrderSucceedEventController.kt
│       ├── service/                            # gRPC Service Implementation
│       │   └── TicketSlotsServiceImpl.kt
│       ├── dbservice/                          # Database Operations
│       │   └── TicketSlotDatabaseService.kt
│       ├── processor/                          # Event Processors
│       │   ├── order.succeed.processor.kt      # Tạo slots từ order
│       │   └── generate.ticket.slot.processor.kt # Tạo QR code
│       ├── kafka/                              # Kafka Integration
│       │   ├── OrderSucceedConsumer.kt
│       │   ├── GenerateTicketSlotQRCodeProducer.kt
│       │   ├── GenerateTicketSlotQRCodeConsumer.kt
│       │   └── ticket.slots.qrcode.kafka.model.kt
│       ├── gateway/                            # External Service Gateways
│       │   ├── ShowGateway.kt
│       │   ├── CustomerUserGateway.kt
│       │   ├── NotificationGateway.kt
│       │   └── CaptchaGateway.kt
│       ├── dlq/                                # Dead Letter Queue
│       │   ├── service/
│       │   └── storage/
│       ├── cache/                              # Caching Layer
│       │   └── ShowCache.kt
│       ├── pojo/db/                            # Database Models
│       │   └── ticketslots.document.kt
│       ├── constants/                          # Constants
│       │   └── constants.kt
│       └── configuration/                      # Configuration
│           └── Environment.kt
├── conf/                                       # Configuration Files
└── build.gradle.kts                            # Build Configuration
```

### 2.2. Các thành phần chính

#### Controllers
| Controller | Mô tả |
|------------|-------|
| **TicketSlotsCtrl** | REST APIs chính cho quản lý ticket slots |
| **AdminQRCodeProducerEventController** | Quản lý failed events của QR code producer |
| **AdminQRCodeConsumerEventController** | Quản lý failed events của QR code consumer |
| **AdminOrderSucceedEventController** | Quản lý failed events của order succeed |

#### Services
| Service | Mô tả |
|---------|-------|
| **TicketSlotsServiceImpl** | gRPC service implementation cho scan QR và lấy QR code |

#### Database Services
| Service | Mô tả |
|---------|-------|
| **TicketSlotDatabaseService** | CRUD operations cho ticket slots |

#### Processors
| Processor | Mô tả |
|-----------|-------|
| **OrderSucceedProcessor** | Xử lý OrderSucceedEvent để tạo ticket slots |
| **GenerateTicketSlotQRCodeProcessor** | Tạo QR code cho ticket slots |

### 2.3. Công nghệ sử dụng

- **Framework**: Ktor (Netty engine)
- **Database**: MongoDB với Kotlin Coroutine driver
- **Message Queue**: Apache Kafka cho event-driven architecture
- **Communication**: gRPC cho inter-service communication
- **Dependency Injection**: Koin
- **QR Code Generation**: QRCode library với Base64 encoding

## 3. Mối quan hệ Order và Ticket Slots

### 3.1. Tổng quan mối quan hệ

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ORDER → TICKET SLOTS RELATIONSHIP                        │
└─────────────────────────────────────────────────────────────────────────────┘

                                    ┌─────────────────────┐
                                    │     ORDER SERVICE   │
                                    │                     │
                                    │  Payment Succeed    │
                                    │  OrderSucceedEvent  │
                                    └──────────┬──────────┘
                                               │
                                               │ Kafka: order.succeed.topic
                                               ▼
                                    ┌─────────────────────┐
                                    │   TICKET SLOTS      │
                                    │     SERVICE         │
                                    │                     │
                                    │  OrderSucceed       │
                                    │    Processor        │
                                    └──────────┬──────────┘
                                               │
                                               ▼
                                    ┌─────────────────────┐
                                    │   MongoDB           │
                                    │   ticket_slots      │
                                    │   collection        │
                                    └─────────────────────┘
```

### 3.2. Cấu trúc Ticket Slot

Một **ticket slot** đại diện cho một vị trí cụ thể trong một đơn hàng. Cấu trúc phân cấp:

```
ORDER (orderId, orderCode)
│
├── TICKET 1 (ticketId = "ticket_A1", sid = "A1", quantity = 3, ticketSlots = 2)
│   ├── TicketSlot: ticketIdx=0, slotIdx=0
│   ├── TicketSlot: ticketIdx=0, slotIdx=1
│   ├── TicketSlot: ticketIdx=1, slotIdx=0
│   ├── TicketSlot: ticketIdx=1, slotIdx=1
│   ├── TicketSlot: ticketIdx=2, slotIdx=0
│   └── TicketSlot: ticketIdx=2, slotIdx=1
│
├── TICKET 2 (ticketId = "ticket_A2", sid = "A2", quantity = 2, ticketSlots = 3)
│   ├── TicketSlot: ticketIdx=0, slotIdx=0
│   ├── TicketSlot: ticketIdx=0, slotIdx=1
│   ├── TicketSlot: ticketIdx=0, slotIdx=2
│   ├── TicketSlot: ticketIdx=1, slotIdx=0
│   ├── TicketSlot: ticketIdx=1, slotIdx=1
│   └── TicketSlot: ticketIdx=1, slotIdx=2
```

**Giải thích:**
- **ticketIdx**: Index của ticket trong danh sách tickets của order (bắt đầu từ 0)
- **slotIdx**: Index của slot trong một ticket (nếu ticket có nhiều slots)
- **sid**: Seat ID - định danh ghế/vị trí
- **ticketId**: ID của ticket trong Show Service

### 3.3. Flow tạo Ticket Slots từ Order

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CREATE TICKET SLOTS FROM ORDER FLOW                      │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │   ORDER SERVICE     │
                              │                     │
                              │  Payment Succeeded  │
                              │  OrderSucceedEvent  │
                              │  - showId           │
                              │  - sessionId        │
                              │  - orderId          │
                              │  - orderCode        │
                              │  - ownerId          │
                              │  - ticketInfo[]     │
                              └──────────┬──────────┘
                                         │
                                         │ Kafka: order.succeed.topic
                                         ▼
                         ┌──────────────────────────────┐
                         │  OrderSucceedConsumer        │
                         │  (Kafka Consumer)            │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  OrderSucceedProcessor       │
                         │  .process(event)             │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  FlatMap Tickets             │
                         │                              │
                         │  For each ticket:            │
                         │  - quantity = N              │
                         │  - ticketSlots = M           │
                         │                              │
                         │  Create N × M TicketSlotDocs │
                         │                              │
                         │  ticketIdx: 0 → N-1          │
                         │  slotIdx: 0 → M-1            │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Create Initial State        │
                         │                              │
                         │  TicketSlotState(            │
                         │    stateIdx = 0,             │
                         │    ownerId = ownerId,        │
                         │    status = CREATED,         │
                         │    reason = INITIAL_PURCHASE │
                         │  )                           │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  TicketSlotDatabaseService   │
                         │  .createTicketSlots(docs)    │
                         │                              │
                         │  - Insert to MongoDB         │
                         │  - ticket_slots collection   │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Generate QR Code Events     │
                         │                              │
                         │  For each created slot:      │
                         │  - Publish to Kafka          │
                         │  generate.ticket.slot.       │
                         │  qrcode.topic                │
                         │                              │
                         │  Event: {                    │
                         │    slotId,                   │
                         │    stateIdx = 0              │
                         │  }                           │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │   RESPONSE                   │
                         │   Ticket slots created       │
                         │   with status = CREATED      │
                         └──────────────────────────────┘
```

### 3.4. Data Models

#### TicketSlotDoc

```kotlin
data class TicketSlotDoc(
    // Immutable Information (Set once at creation)
    val id: String,                     // ObjectId - Unique ID
    val showId: String,                 // Show ID
    val sessionId: String,              // Session ID
    val orderId: String,                // Original Order ID
    val orderCode: String,              // Original Order Code
    val ticketId: String,               // Ticket ID from Show Service
    val priceCat: String,               // Price Category (VIP, Normal, etc.)
    val sid: String,                    // Seat ID
    val ticketIdx: Int,                 // Index of ticket in order (0, 1, 2...)
    val slotIdx: Int,                   // Index of slot in ticket (0, 1, 2...)
    val originalOwnerId: String,        // Original buyer User ID

    // Denormalized Current State (For query optimization)
    val currentOwnerId: String? = null,
    val currentOwnerPhone: String? = null,
    val currentStatus: TicketSlotStatus? = null,
    val currentQRString: String? = null,
    val currentStateIdx: Int = 0,

    // Operator Status
    val optStatus: TicketSlotOptStatus = TicketSlotOptStatus.NONE,

    // Metadata
    val lastModified: Date = Date(),
    val createdAt: Date = Date(),
    val version: Int = 1,

    // State History
    val states: List<TicketSlotState> = listOf()
)
```

#### TicketSlotState

```kotlin
data class TicketSlotState(
    val stateIdx: Int,                  // Index trong danh sách states
    val ownerId: String? = null,        // User ID của chủ sở hữu
    val ownerPhone: String? = null,     // SĐT của chủ sở hữu
    val status: TicketSlotStatus,       // Trạng thái slot
    val reason: StateReason,            // Lý do tạo state
    val senderId: String? = null,       // User ID người gửi (transfer)
    val recipientId: String? = null,    // User ID người nhận (transfer)
    val qrcodeString: String? = null,   // QR code (Base64)
    val timestamp: Date = Date()        // Thời gian tạo state
)
```

#### Enums

**TicketSlotStatus:**
```kotlin
enum class TicketSlotStatus {
    CREATED,        // Mới tạo hoặc nhận qua transfer
    QR_GENERATED,   // QR code đã được tạo
    CHECKED_IN      // QR code đã được quét thành công
}
```

**TicketSlotOptStatus:**
```kotlin
enum class TicketSlotOptStatus {
    NONE,       // Không có action từ operator
    OPENED,     // Slot được mở - hiển thị QR và check-in
    CLOSED      // Slot bị đóng - không thể hiển thị QR hay check-in
}
```

**StateReason:**
```kotlin
enum class StateReason {
    INITIAL_PURCHASE,   // Tạo từ đơn hàng ban đầu
    TRANSFER_RECEIVED,  // Nhận từ người khác
    TRANSFERRED_AWAY    // Chuyển cho người khác
}
```

## 4. QR Code System

### 4.1. QR Code Format

```
MTTKSLOTQR:{checksum}

Trong đó:
- Prefix: "MTTKSLOTQR:" (định danh đây là QR code của ticket slot)
- Checksum: Checksum.encode("${slotId}:${stateIdx}", qrcodeSecretKey)
```

**Ví dụ:**
```
MTTKSLOTQR:a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6:0
```

### 4.2. QR Code Generation Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       QR CODE GENERATION FLOW                                │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │  ORDER SUCCEED      │
                              │  PROCESSOR          │
                              │                     │
                              │  Created ticket     │
                              │  slots              │
                              └──────────┬──────────┘
                                         │
                                         │ Publish to Kafka
                                         │ generate.ticket.slot.qrcode.topic
                                         ▼
                         ┌──────────────────────────────┐
                         │  GenerateTicketSlot          │
                         │  QRCodeConsumer              │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  GenerateTicketSlot          │
                         │  QRCodeProcessor             │
                         │  .process(event)             │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Create Checksum             │
                         │                              │
                         │  verifyCode =                │
                         │  Checksum.encode(            │
                         │    "${slotId}:${stateIdx}",  │
                         │    qrcodeSecretKey           │
                         │  )                           │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Generate QR Code Image      │
                         │                              │
                         │  QRCode.ofSquares()          │
                         │    .withSize(5)              │
                         │    .withInnerSpacing(0)      │
                         │    .build(                   │
                         │      "MTTKSLOTQR:" +         │
                         │      verifyCode              │
                         │    )                         │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Convert to Base64           │
                         │                              │
                         │  pngData.encodeBase64()      │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Update Database             │
                         │                              │
                         │  db.setQRCode(               │
                         │    slotId,                   │
                         │    stateIdx,                 │
                         │    qrcodeString              │
                         │  )                           │
                         │                              │
                         │  - Update currentStatus =    │
                         │    QR_GENERATED              │
                         │  - Update currentQRString    │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │   RESPONSE                   │
                         │   QR code generated          │
                         │   Status: QR_GENERATED       │
                         └──────────────────────────────┘
```

## 5. QR Code Scanner (Check-in)

### 5.1. Scanner Flow tại cổng

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       QR CODE SCANNING FLOW                                  │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │    SCANNER DEVICE   │
                              │    (Phone/Tablet)   │
                              │                     │
                              │  Scan QR Code       │
                              │  MTTKSLOTQR:xxx     │
                              └──────────┬──────────┘
                                         │
                                         │ gRPC
                                         ▼
                         ┌──────────────────────────────┐
                         │  TICKET SLOTS SERVICE        │
                         │                              │
                         │  ScanTicketSlot()            │
                         │  - verifyCode                │
                         │  - sessionId                 │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  1. Validate QR Prefix       │
                         │                              │
                         │  if (!verifyCode.startsWith  │
                         │      "MTTKSLOTQR:"))         │
                         │    → INVALID_QR              │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  2. Decode Checksum          │
                         │                              │
                         │  decoded =                   │
                         │  Checksum.decode(            │
                         │    verifyCode.substring(13), │
                         │    qrcodeSecretKey           │
                         │  )                           │
                         │                              │
                         │  Extract: slotId, stateIdx   │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  3. Find Ticket Slot         │
                         │                              │
                         │  db.findByIdWithCurrentState │
                         │  Only(slotId)                │
                         │                              │
                         │  if not found                │
                         │    → SLOT_NOT_FOUND          │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  4. Validate Session         │
                         │                              │
                         │  if (slot.sessionId !=       │
                         │      request.sessionId)      │
                         │    → PERMISSION_DENIED       │
                         │    (QR not for this event)   │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  5. Validate State Index     │
                         │                              │
                         │  if (slot.currentStateIdx != │
                         │      stateIdx.toInt())       │
                         │    → QR_TRANSFERRED          │
                         │    (slot đã được chuyển)     │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  6. Validate Operator Status │
                         │                              │
                         │  if (slot.optStatus !=       │
                         │      OPENED)                 │
                         │    → QR_NOT_OPENED           │
                         │    (admin chưa mở slot)      │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  7. Validate Slot Status     │
                         │                              │
                         │  if (slot.currentStatus ==   │
                         │      CHECKED_IN)             │
                         │    → ALREADY_CHECKED_IN      │
                         │                              │
                         │  if (slot.currentStatus ==   │
                         │      CREATED)                │
                         │    → QR_NOT_READY            │
                         │                              │
                         │  if (slot.currentStatus ==   │
                         │      QR_GENERATED)           │
                         │    → PROCEED                 │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  8. Update Check-in Status   │
                         │                              │
                         │  db.updateCheckInStatus(     │
                         │    slotId,                   │
                         │    CHECKED_IN                │
                         │  )                           │
                         │                              │
                         │  - Create new state          │
                         │  - Update currentStatus      │
                         │  - Increment stateIdx        │
                         └──────────────┬──────────��────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │   RESPONSE                   │
                         │   ScanTicketSlotResponse     │
                         │   {                           │
                         │     orderCode: "ORD123",     │
                         │     cat: "VIP",              │
                         │     sid: "A1",               │
                         │     ticketIdx: 1,            │
                         │     slotIdx: 0               │
                         │   }                           │
                         └──────────────────────────────┘
```

### 5.2. gRPC Service Definition

```protobuf
service TicketSlotsService {
  rpc ScanTicketSlot(ScanTicketSlotRequest) returns(ScanTicketSlotResponse);
  rpc GetQRCode(GetQRCodeRequest) returns(GetQRCodeResponse);
}

message ScanTicketSlotRequest {
    string session_id = 1;
    string verify_code = 2;
}

message ScanTicketSlotResponse {
    string order_code = 1;
    string cat = 2;
    string sid = 3;
    int32 ticket_idx = 4;
    int32 slot_idx = 5;
}

message GetQRCodeRequest {
    string order_id = 1;
    string order_code = 2;
    int32 required_qrcode = 3;
    optional int64 timeout_full = 4;
}

message TicketSlotQRCodeProto {
    string ticket_id = 1;
    string price_cat = 2;
    string sid = 3;
    string ticket_idx = 4;
    string slot_id = 5;
    string slot_idx = 6;
    string slot_state_idx = 7;
    string slot_status = 8;
    optional bytes qr_code = 9;
}

message GetQRCodeResponse {
    string order_id = 1;
    string order_code = 2;
    repeated TicketSlotQRCodeProto ticket_slots = 3;
}
```

### 5.3. Error Responses

| Error | Mô tả | Nguyên nhân |
|-------|-------|-------------|
| `INVALID_QR` | Mã QR không hợp lệ | QR code không bắt đầu bằng `MTTKSLOTQR:` |
| `SLOT_NOT_FOUND` | Mã QR không tồn tại | Slot ID không tìm thấy trong DB |
| `PERMISSION_DENIED` | Mã QR không thuộc sự kiện này | Session ID không khớp |
| `QR_TRANSFERRED` | Mã QR đã được chuyển nhượng | StateIdx không khớp (vé đã được chuyển) |
| `QR_NOT_OPENED` | Mã QR chưa được mở | optStatus != OPENED |
| `ALREADY_CHECKED_IN` | Mã QR đã được quét | currentStatus == CHECKED_IN |
| `QR_NOT_READY` | Mã QR chưa sẵn sàng | currentStatus == CREATED |

## 6. Kafka Integration

### 6.1. Topics

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `order.succeed.topic` | Order Service | OrderSucceedConsumer | Tạo ticket slots từ order thành công |
| `generate.ticket.slot.qrcode.topic` | OrderSucceedProcessor, TicketSlotsCtrl | GenerateTicketSlotQRCodeConsumer | Tạo QR code cho ticket slots |

### 6.2. Event Models

**GenerateTicketSlotQRCodeEvent:**
```kotlin
data class GenerateTicketSlotQRCodeEvent(
    val slotId: String,     // ID của ticket slot
    val stateIdx: Int       // Index của state cần tạo QR
)
```

### 6.3. Dead Letter Queue (DLQ)

Service hỗ trợ quản lý các failed events:

| DLQ Collection | Purpose |
|----------------|---------|
| `qrcode-producer-events` | Failed events khi publish QR generation |
| `qrcode-consumer-events` | Failed events khi xử lý QR generation |
| `order-succeed-events` | Failed events khi xử lý order succeed |

**Admin Endpoints:**
```http
GET /admin/qrcode-producer-events/failed
POST /admin/qrcode-producer-events/{id}/retry

GET /admin/qrcode-consumer-events/failed
POST /admin/qrcode-consumer-events/{id}/retry

GET /admin/order-succeed-events/failed
POST /admin/order-succeed-events/{id}/retry
```

## 7. Database Schema

### 7.1. Collections

| Collection | Mô tả |
|------------|-------|
| **ticket_slots** | Lưu trữ tất cả ticket slots |

### 7.2. Indexes

```kotlin
// Tự động tạo indexes
- _id (primary key)
- orderId (index)
- currentOwnerId (index)
- currentStatus (index)
- (showId, sessionId, currentOwnerId) - compound index
- (orderId, priceCat, ticketId, ticketIdx, slotIdx) - unique compound
```

### 7.3. Document Structure

```json
{
  "_id": "ObjectId",
  "showId": "show123",
  "sessionId": "session123",
  "orderId": "order456",
  "orderCode": "ORD001234",
  "ticketId": "ticket789",
  "priceCat": "VIP",
  "sid": "A1",
  "ticketIdx": 0,
  "slotIdx": 0,
  "originalOwnerId": "user123",
  "currentOwnerId": "user123",
  "currentOwnerPhone": "0901234567",
  "currentStatus": "QR_GENERATED",
  "currentQRString": "iVBORw0KGgoAAAANSUhEUgAA...",
  "currentStateIdx": 0,
  "optStatus": "OPENED",
  "version": 1,
  "createdAt": "2026-06-04T10:00:00.000Z",
  "lastModified": "2026-06-04T10:00:00.000Z",
  "states": [
    {
      "stateIdx": 0,
      "ownerId": "user123",
      "ownerPhone": "0901234567",
      "status": "QR_GENERATED",
      "reason": "INITIAL_PURCHASE",
      "qrcodeString": "iVBORw0KGgoAAAANSUhEUgAA...",
      "timestamp": "2026-06-04T10:00:00.000Z"
    }
  ]
}
```

## 8. REST API Endpoints

### 8.1. Public Endpoints

| Endpoint | Method | Mô tả |
|----------|--------|-------|
| `/ticket-slots/captcha-setting` | GET | Lấy cấu hình CAPTCHA |
| `/ticket-slots/init-session-id` | POST | Khởi tạo session cho transfer |
| `/ticket-slots/fetch-by-show` | GET | Lấy ticket slots theo show/session |
| `/ticket-slots/fetch-by-owner` | GET | Lấy ticket slots theo owner |
| `/ticket-slots/get-qrcode` | GET | Lấy QR code của ticket slot |
| `/ticket-slots/send-transfer-otp` | POST | Gửi OTP chuyển vé |
| `/ticket-slots/verify-transfer-otp` | POST | Xác thực OTP chuyển vé |
| `/ticket-slots/transfer` | POST | Thực hiện chuyển vé |

### 8.2. Admin Endpoints

| Endpoint | Method | Mô tả |
|----------|--------|-------|
| `/ticket-slots/open` | PUT | Mở ticket slot |
| `/ticket-slots/close` | PUT | Đóng ticket slot |
| `/ticket-slots/open-selected` | PUT | Mở nhiều ticket slots |
| `/ticket-slots/close-selected` | PUT | Đóng nhiều ticket slots |

### 8.3. Admin Failed Events

| Endpoint | Method | Mô tả |
|----------|--------|-------|
| `/admin/qrcode-producer-events/failed` | GET | Lấy failed producer events |
| `/admin/qrcode-producer-events/{id}/retry` | POST | Retry failed event |
| `/admin/qrcode-consumer-events/failed` | GET | Lấy failed consumer events |
| `/admin/qrcode-consumer-events/{id}/retry` | POST | Retry failed event |
| `/admin/order-succeed-events/failed` | GET | Lấy failed order events |
| `/admin/order-succeed-events/{id}/retry` | POST | Retry failed event |

## 9. Tích hợp với các Service khác

### 9.1. Integration Overview

```
┌─────────────────┐      order.succeed.topic       ┌─────────────────────┐
│  Order Service  │ ─────────────────────────────▶ │  Ticket Slots       │
│                 │                                  │     Service         │
└─────────────────┘                                  └──────────┬──────────┘
                                                                   │
                                                                   │ gRPC
                                                                   ▼
                                                          ┌─────────────────────┐
                                                          │  Scanner Devices    │
                                                          │  (Gate Check-in)    │
                                                          └─────────────────────┘

┌─────────────────┐                                  ┌─────────────────────┐
│  Show Service   │ ◀───────────────────────────────── │  Notification       │
│                 │   gRPC                              │    Service          │
└─────────────────┘                                    └─────────────────────┘
```

### 9.2. Service Dependencies

| Service | Port | Purpose |
|---------|------|---------|
| **Order Service** | 3105/4105 | Gửi OrderSucceedEvent khi thanh toán thành công |
| **Show Service** | 3103/4103 | Validate show/session information |
| **Customer User Service** | 3109/4109 | Validate user information |
| **Notification Service** | 3108/4108 | Gửi OTP, thông báo chuyển vé |

## 10. Constants

```kotlin
package metaticket.ticketslots.constants

const val prefixSlotQRCode = "MTTKSLOTQR:"
```

## 11. Cấu hình

```hocon
# Application Configuration
ktor {
    deployment.port = 4033  # HTTP port
}

grpc {
    server.port = 4133  # gRPC port
}

db {
    connection = "mongodb://localhost:27017"
    dbName = "metaticket"
    ticketSlotsColName = "ticket_slots"
}

secretKey = "METATICKET____VN"
qrcodeSecretKey = "QRCODE_SECRET____"
```

---

**Tài liệu này được cập nhật lần cuối**: 04/06/2026
**Version**: 2.0
**Tác giả**: MetaTicket Development Team
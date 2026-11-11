# Tài liệu Kỹ thuật - Editor SeatPlanner Service

## 1. Tổng quan về chức năng chính

Editor SeatPlanner Service là một microservice trong hệ thống bán vé sự kiện online MetaTicket, chịu trách nhiệm quản lý và chỉnh sửa sơ đồ chỗ ngồi (seat plan) cho các sự kiện. Service này cung cấp API cho phép người dùng admin tạo, chỉnh sửa, và lưu trữ sơ đồ chỗ ngồi dưới dạng các documents có thể tái sử dụng.

### Chức năng chính:
- **Quản lý Documents**: Tạo, xóa, tải các seat plan documents
- **Command Processing**: Xử lý các command để chỉnh sửa seat plan (INSERT, UPDATE, DELETE items)
- **Image Management**: Upload và quản lý ảnh nền cho seat plan
- **Style & Definition**: Lưu trữ CSS styles và SVG definitions
- **Category Management**: Quản lý danh mục cho seat plans

## 2. Kiến trúc và các thành phần chính

### 2.1. Cấu trúc thư mục

```
service.editor.seatplanner/
├── src/
│   ├── api/kotlin/metaticket/editor/seatplanner/
│   │   └── pojo/                      # Data models
│   │       ├── document.kt            # SeatPlanDocument
│   │       ├── primitives.kt          # Position, Boundary, BezierCurve
│   │       ├── plan_item.kt           # PlanItem (Seat, Label, SeatLine, SeatZone)
│   │       └── command_state.kt       # Command state
│   └── main/kotlin/metaticket/editor/seatplanner/
│       ├── Application.kt             # Entry point và cấu hình Ktor
│       ├── ApplicationDI.kt           # Dependency injection setup
│       ├── StartUp.kt                 # Startup logic
│       ├── controller/
│       │   ├── DocumentController.kt  # Document management APIs
│       │   └── ShapeController.kt     # Shape operations (reserved)
│       ├── database/
│       │   └── DatabaseService.kt     # MongoDB operations
│       └── utils/
│           └── Environment.kt         # Configuration utilities
├── conf/
│   └── application.conf               # Application configuration
└── build.gradle.kts                   # Build configuration
```

### 2.2. Các thành phần chính

#### Controllers
| Controller | Mô tả |
|------------|-------|
| **DocumentController** | Xử lý tất cả các API endpoints liên quan đến seat plan documents |
| **ShapeController** | Class dự phòng cho các thao tác với hình dạng (shape) |

#### Database Services
| Service | Mô tả |
|---------|-------|
| **DatabaseService** | Service database layer tương tác với MongoDB |

#### Core Components
| Component | Mô tả |
|-----------|-------|
| **SeatPlanDocument** | Document chính lưu trữ seat plan |
| **PlanItem** | Sealed class đại diện cho các item trên bản đồ |
| **Command Processor** | Xử lý các command (Protobuf) |

### 2.3. Công nghệ sử dụng

- **Framework**: Ktor (Netty engine)
- **Database**: MongoDB với Kotlin Coroutine driver
- **Dependency Injection**: Koin
- **Serialization**: Jackson (JSON) + Protobuf

## 3. Data Models

### 3.1. SeatPlanDocument

```kotlin
data class SeatPlanDocument(
    val id: String,                    // Unique identifier (ObjectId)
    val name: String,                  // Document name
    val category: String,              // Category (e.g., "theater", "stadium")
    val items: List<PlanItem>?,        // List of plan items
    val styles: String?,               // CSS styles (JSON string)
    val definitions: String?,          // SVG definitions (JSON string)
    val backgroundImageUrl: String?,   // Background image URL
    val version: Int,                  // Version number
    val createdAt: Long,               // Creation timestamp
    val lastModified: Long             // Last modification timestamp
)
```

### 3.2. PlanItem (Sealed Class)

```kotlin
sealed class PlanItem {
    // Ghế ngồi
    data class Seat(
        val id: String,
        val position: Position,
        val label: String,
        val priceCategory: String,
        val status: String
    ): PlanItem()
    
    // Nhãn văn bản
    data class Label(
        val id: String,
        val position: Position,
        val text: String,
        val style: String
    ): PlanItem()
    
    // Đường kẻ ghế
    data class SeatLine(
        val id: String,
        val startPoint: Position,
        val endPoint: Position,
        val lineType: String
    ): PlanItem()
    
    // Vùng ghế (zone)
    data class SeatZone(
        val id: String,
        val boundary: Boundary,
        val label: String,
        val priceCategory: String
    ): PlanItem()
}
```

### 3.3. Primitives

```kotlin
// Vị trí trên bản đồ
data class Position(
    val x: Double,
    val y: Double
)

// Đường cong Bezier
data class BezierCurve(
    val startPoint: Position,
    val controlPoint1: Position,
    val controlPoint2: Position,
    val endPoint: Position
)

// Biên giới vùng
data class Boundary(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double
)
```

### 3.4. Command Types

```kotlin
enum class CommandType {
    INSERT_DOC,
    INSERT_LAYER,
    SAVE_ITEMS,
    // ... các command khác
}

data class SaveItemsCmdState(
    val cmdType: CommandType,
    val items: List<PlanItem>
)
```

## 4. Các API chính

### 4.1. Document Management APIs

#### GET `/private/editor/seatplanner/createDoc`
- **Mô tả**: Tạo document seat plan mới
- **Query Parameters**: `name`, `category`
- **Response**: Created document ID

#### GET `/private/editor/seatplanner/deleteDoc`
- **Mô tả**: Xóa document seat plan
- **Query Parameters**: `id`
- **Response**: Success message

#### GET `/private/editor/seatplanner/document`
- **Mô tả**: Tải document seat plan (private)
- **Query Parameters**: `id`
- **Response**: `SeatPlanDocument`

#### GET `/editor/seatplanner/document`
- **Mô tả**: Tải document seat plan (public)
- **Query Parameters**: `id`
- **Response**: `SeatPlanDocument`

#### POST `/private/editor/seatplanner/saveDocName`
- **Mô tả**: Lưu tên document
- **Request Body**:
```json
{
  "id": "document_id",
  "name": "New Name"
}
```
- **Response**: Success message

#### POST `/private/editor/seatplanner/saveDocStyle`
- **Mô tả**: Lưu CSS styles cho document
- **Request Body**:
```json
{
  "id": "document_id",
  "styles": "{ \"seat\": { \"fill\": \"red\" } }"
}
```
- **Response**: Success message

#### POST `/private/editor/seatplanner/saveDocDef`
- **Mô tả**: Lưu SVG definitions cho document
- **Request Body**:
```json
{
  "id": "document_id",
  "definitions": "<defs>...</defs>"
}
```
- **Response**: Success message

#### POST `/private/editor/seatplanner/saveCategories`
- **Mô tả**: Lưu categories cho document
- **Request Body**:
```json
{
  "id": "document_id",
  "category": "theater"
}
```
- **Response**: Success message

### 4.2. Category Management APIs

#### GET `/private/editor/seatplanner/document/category`
- **Mô tả**: Tải category của document
- **Query Parameters**: `id`
- **Response**: Category string

#### GET `/private/editor/seatplanner/documents/category`
- **Mô tả**: Tải tất cả documents theo category
- **Query Parameters**: `category`
- **Response**: List of documents

### 4.3. Command Processing API

#### POST `/private/editor/seatplanner/cmd`
- **Mô tả**: Xử lý command (Protobuf)
- **Content-Type**: application/protobuf
- **Request Body**: Protobuf message với các command
- **Response**: Command result

### 4.4. Image Management APIs

#### POST `/private/editor/seatplanner/uploadImage`
- **Mô tả**: Upload ảnh nền cho seat plan
- **Content-Type**: multipart/form-data
- **Form Fields**:
  - `file`: Image file
  - `docId`: Document ID
- **Response**: Uploaded file path

#### POST `/private/editor/seatplanner/updateBaseImgUrl`
- **Mô tả**: Cập nhật URL ảnh nền
- **Request Body**:
```json
{
  "docId": "document_id",
  "baseImgUrl": "https://cdn.example.com/image.png"
}
```
- **Response**: Success message

## 5. Flow xử lý chính

### 5.1. Flow tạo Seat Plan Document

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CREATE SEAT PLAN DOCUMENT FLOW                           │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │       ADMIN         │
                              │   GET /createDoc    │
                              │   name=SeatPlan1    │
                              │   category=theater  │
                              └──────────┬──────────┘
                                         │
                                         ▼
                         ┌──────────────────────────────┐
                         │  DocumentController          │
                         │  .createDocument()           │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Validation                  │
                         │  - Check name not empty      │
                         │  - Check category valid      │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  DatabaseService             │
                         │  .createDocument()           │
                         │                              │
                         │  - Insert to MongoDB         │
                         │  - Initialize version = 1    │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  MongoDB                     │
                         │  Collection: seatplan        │
                         │                              │
                         │  {                           │
                         │    name: "SeatPlan1",        │
                         │    category: "theater",      │
                         │    version: 1                │
                         │  }                           │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │   RESPONSE                   │
                         │   Document ID                │
                         └──────────────────────────────┘
```

### 5.2. Flow lưu trữ Items (Command Processing)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       SAVE ITEMS FLOW                                       │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │       ADMIN         │
                              │   POST /cmd         │
                              │   Protobuf data     │
                              └──────────┬──────────┘
                                         │
                                         ▼
                         ┌──────────────────────────────┐
                         │  DocumentController          │
                         │  .processCommand()           │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Parse Protobuf              │
                         │  - Extract command type      │
                         │  - Extract items data        │
                         └──────────────┬───────────────┘
                                        │
                          ┌─────────────┴─────────────┐
                          │                           │
                          ▼                           ▼
              ┌─────────────────────┐    ┌─────────────────────┐
              │  CommandType        │    │  CommandType        │
              │  = INSERT_DOC       │    │  = SAVE_ITEMS       │
              └─────────┬───────────┘    └─────────┬───────────┘
                        │                           │
                        ▼                           ▼
              ┌─────────────────────┐    ┌──────────────────────────────┐
              │  Create new         │    │  Update document items       │
              │  document           │    │                              │
              │                     │    │  DatabaseService             │
              │                     │    │  .updateDocument()           │
              └─────────────────────┘    └──────────────┬───────────────┘
                                                       │
                                                       ▼
                                            ┌──────────────────────────────┐
                                            │  MongoDB                     │
                                            │  Collection: seatplan        │
                                            │                              │
                                            │  - Update items array        │
                                            │  - Increment version         │
                                            │  - Update lastModified       │
                                            └──────────────┬───────────────┘
                                                           │
                                                           ▼
                                            ┌──────────────────────────────┐
                                            │   RESPONSE                   │
                                            │   Success message            │
                                            └──────────────────────────────┘
```

### 5.3. Flow upload ảnh nền

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     UPLOAD BACKGROUND IMAGE FLOW                            │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │       ADMIN         │
                              │  POST /uploadImage  │
                              │  file + docId       │
                              └──────────┬──────────┘
                                         │
                                         ▼
                         ┌──────────────────────────────┐
                         │  DocumentController          │
                         │  .uploadImage()              │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Validate File               │
                         │  - Check file type           │
                         │  - Check file size           │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Save to Disk                │
                         │  Path: uploads/{docId}/      │
                         │  {filename}                  │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Update Document             │
                         │  DatabaseService             │
                         │  .updateBackgroundImage()    │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  MongoDB                     │
                         │  Collection: seatplan        │
                         │                              │
                         │  {                           │
                         │    backgroundImageUrl:      │
                         │      "/uploads/..."         │
                         │  }                           │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │   RESPONSE                   │
                         │   File path                  │
                         └──────────────────────────────┘
```

### 5.4. Flow tải Seat Plan Document

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       LOAD DOCUMENT FLOW                                     │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │    CLIENT/FRONTEND  │
                              │                     │
                              │  GET /document      │
                              │  ?id={docId}        │
                              └──────────┬──────────┘
                                         │
                                         ▼
                         ┌──────────────────────────────┐
                         │  DocumentController          │
                         │  .loadDocument()             │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Validate ID                 │
                         │  - Check ObjectId format     │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  DatabaseService             │
                         │  .getDocument()              │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  MongoDB                     │
                         │  Collection: seatplan        │
                         │  Query: { _id: docId }       │
                         └─���────────────┬───────────────┘
                                        │
                          ┌────────────┴────────────┐
                          │                         │
                          ▼                         ▼
               ┌─────────────────────┐   ┌─────────────────────┐
               │  Document Found     │   │  Document Not       │
               │                     │   │  Found              │
               └─────────┬───────────┘   └─────────┬───────────┘
                         │                         │
                         ▼                         ▼
               ┌─────────────────────┐   ┌─────────────────────┐
               │  Return document    │   │  Return 404         │
               │  with items,        │   │  error              │
               │  styles, defs       │   │                     │
               └─────────────────────┘   └─────────────────────┘
```

## 6. Database Schema

### 6.1. Collections

| Collection | Mô tả |
|------------|-------|
| **seatplan** | Lưu trữ tất cả seat plan documents |

### 6.2. Document Structure

```json
{
  "_id": "ObjectId",
  "name": "Seat Plan Name",
  "category": "theater",
  "items": [
    {
      "type": "seat",
      "id": "seat_1",
      "position": { "x": 100, "y": 200 },
      "label": "A1",
      "priceCategory": "VIP"
    }
  ],
  "styles": "{ \"seat\": { \"fill\": \"#FF0000\" } }",
  "definitions": "<defs><marker...</defs>",
  "backgroundImageUrl": "/uploads/doc123/bg.png",
  "version": 1,
  "createdAt": 1704067200000,
  "lastModified": 1704153600000
}
```

### 6.3. Indexes

```kotlin
// Tự động tạo index
- _id (primary key)
- name (unique - tùy chọn)
- category (index)
```

## 7. Cấu hình

### 7.1. Application Configuration

**File**: `conf/application.conf`

```hocon
db {
    connection = "mongodb://devlocal.metaticket.vn:47018"
    dbName = "metaticket"
    seatplanColName = "seatplan"
}

ktor {
    deployment {
        port = 4012  # Development port
    }
}

storage {
    upload_path = "./uploads"
    baseUrl = "https://cdn.metaticket.vn"
}
```

### 7.2. Environment Variables

| Variable | Mô tả |
|----------|-------|
| `SEATPLANNER_PORT` | HTTP server port |
| `MONGODB_URI` | MongoDB connection string |
| `UPLOAD_PATH` | Thư mục lưu file upload |

## 8. Ví dụ Request và Response

### 8.1. Tạo Document

**Request:**
```
GET /private/editor/seatplanner/createDoc?name=ConcertHall&category=stadium
```

**Response:**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "name": "ConcertHall",
  "category": "stadium",
  "version": 1,
  "createdAt": 1704067200000
}
```

### 8.2. Tải Document

**Request:**
```
GET /private/editor/seatplanner/document?id=507f1f77bcf86cd799439011
```

**Response:**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "name": "ConcertHall",
  "category": "stadium",
  "items": [
    {
      "type": "seat",
      "id": "seat_1",
      "position": { "x": 100, "y": 200 },
      "label": "A1",
      "priceCategory": "VIP"
    },
    {
      "type": "label",
      "id": "label_1",
      "position": { "x": 50, "y": 50 },
      "text": "Stage"
    }
  ],
  "styles": "{ \"seat\": { \"fill\": \"red\" } }",
  "backgroundImageUrl": "/uploads/123/bg.png",
  "version": 3,
  "createdAt": 1704067200000,
  "lastModified": 1704153600000
}
```

### 8.3. Upload ảnh nền

**Request:**
```
POST /private/editor/seatplanner/uploadImage
Content-Type: multipart/form-data

file: [binary]
docId: 507f1f77bcf86cd799439011
```

**Response:**
```json
{
  "path": "/uploads/507f1f77bcf86cd799439011/background.png"
}
```

### 8.4. Lưu Style

**Request:**
```json
POST /private/editor/seatplanner/saveDocStyle
{
  "id": "507f1f77bcf86cd799439011",
  "styles": "{\"seat\": {\"fill\": \"#FF0000\", \"stroke\": \"#000000\"}, \"zone\": {\"fill\": \"#00FF00\"}}"
}
```

**Response:**
```json
{
  "message": "Document styles saved successfully"
}
```

## 9. Tích hợp với các Service khác

### 9.1. Các Service tích hợp

| Service | Mô tả |
|---------|-------|
| **Show Service** | Sử dụng seat plan đã tạo cho các session |
| **Ticket Service** | Tạo tickets dựa trên seat plan |
| **Admin Service** | Quản lý seat plan qua admin interface |

### 9.2. Integration Flow

```
┌─────────────────────┐         ┌─────────────────────┐
│   Admin Service     │         │  Show Service       │
│                     │         │                     │
│  Create/Edit        │         │  Link seatPlanId    │
│  SeatPlan           │         │  to session         │
└─────────┬───────────┘         └──────────┬──────────┘
          │                                │
          │  SeatPlan ID                   │  Session
          ▼                                ▼
┌─────────────────────────────────────────────────────┐
│            Editor SeatPlanner Service               │
│                                                     │
│  - Create/Edit Documents                           │
│  - Store seat layouts                              │
│  - Manage categories                               │
└─────────────────────────────────────────────────────┘
          │
          │  SeatPlan ID + Items
          ▼
┌─────────────────────┐
│  Ticket Service     │
│                     │
│  Generate tickets   │
│  from seat layout   │
└─────────────────────┘
```

## 10. Bảo mật

### 10.1. Cơ chế bảo mật

- **Private APIs**: Tất cả APIs yêu cầu xác thực admin
- **Input Validation**: Validate ObjectId format và input data
- **File Upload Security**: Kiểm tra file type và size

### 10.2. CORS Configuration

```kotlin
install(CORS) {
    allowHost("*", listOf("http", "https"))
    allowOrigins { it in listOf(
        "http://localhost",
        "https://devlocal.metaticket.vn",
        "https://admin.devlocal.metaticket.vn"
    ) }
    allowMethod(HttpMethod.Get)
    allowMethod(HttpMethod.Post)
    allowMethod(HttpMethod.Options)
    allowCredentials = true
    allowHeader(HttpHeaders.ContentType)
}
```

## 11. Dependencies

| Library | Mục đích |
|---------|----------|
| **ktor-server-netty** | HTTP Server |
| **mongodb-driver-sync** | MongoDB Driver |
| **koin-core** | Dependency Injection |
| **jackson-module-kotlin** | JSON Serialization |

## 12. Error Handling

### 12.1. HTTP Status Codes

| Status Code | Mô tả |
|-------------|-------|
| 200 | Thành công |
| 400 | Bad Request |
| 404 | Document not found |
| 413 | File too large |
| 500 | Internal Server Error |

### 12.2. Error Response Format

```json
{
  "error": "Mô tả lỗi"
}
```

---

**Tài liệu này được cập nhật lần cuối**: 04/06/2026
**Version**: 1.0
**Tác giả**: MetaTicket Development Team
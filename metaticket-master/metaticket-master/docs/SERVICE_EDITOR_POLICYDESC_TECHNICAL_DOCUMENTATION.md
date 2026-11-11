# Tài liệu Kỹ thuật - Editor PolicyDesc Service

## 1. Tổng quan về chức năng chính

Editor PolicyDesc Service là một microservice trong hệ thống bán vé sự kiện online MetaTicket, chịu trách nhiệm quản lý và cung cấp các tài liệu mô tả chính sách (policy description) cho hệ thống đặt vé. Service này cho phép admin tạo, lưu trữ và truy xuất các tài liệu policy được sử dụng trong seat planner.

### Chức năng chính:
- **Quản lý Policy Documents**: Lưu trữ và truy xuất các tài liệu policy
- **Category Management**: Quản lý danh mục categories cho policy documents
- **Style Management**: Lưu trữ CSS styles cho seat zones
- **Content Management**: Lưu trữ nội dung policy (HTML/markdown)

## 2. Kiến trúc và các thành phần chính

### 2.1. Cấu trúc thư mục

```
service.editor.policydesc/
├── conf/                           # Configuration files
│   ├── application.conf            # Dev configuration
│   ├── conf-sample/                # Sample configuration
│   └── conf-prod/                  # Production configuration
├── src/main/kotlin/metaticket/editor/policydesc/
│   ├── Application.kt              # Entry point và cấu hình Ktor
│   ├── ApplicationDI.kt            # Dependency injection setup
│   ├── DatabaseService.kt          # Database operations
│   ├── Launcher.kt                 # Main entry point
│   ├── Route.kt                    # API routing
│   ├── controller/
│   │   └── DocumentController.kt   # REST API controller
│   └── pojo/db/policydesc/
│       └── policydesc.document.kt  # Data models
└── build.gradle.kts                # Build configuration
```

### 2.2. Các thành phần chính

#### Controllers
| Controller | Mô tả |
|------------|-------|
| **DocumentController** | Xử lý các API endpoints liên quan đến policy documents |

#### Database Services
| Service | Mô tả |
|---------|-------|
| **DatabaseService** | Service database layer tương tác với MongoDB |

#### Data Models
| Model | Mô tả |
|-------|-------|
| **PolicyDescDocument** | Document chính lưu trữ policy |
| **PolicyDescItem** | Item trong policy (seat, zone, category) |
| **FetchSeatPlanCategoryResult** | Result object cho category queries |

### 2.3. Công nghệ sử dụng

- **Framework**: Ktor (Netty engine)
- **Database**: MongoDB với Kotlin Coroutine driver
- **Dependency Injection**: Koin
- **Serialization**: Jackson (JSON)

## 3. Data Models

### 3.1. PolicyDescDocument

```kotlin
data class PolicyDescDocument(
    @BsonId
    @BsonRepresentation(BsonType.OBJECT_ID)
    val id: String = ObjectId().toHexString(),
    val styleSheet: String = "",           // CSS styles cho seat zones
    val content: String = "",              // Nội dung policy (HTML/markdown)
    val displayName: String,               // Tên hiển thị
    var catNames: List<String> = emptyList(), // Danh sách categories
    val items: List<PolicyDescItem> = listOf(), // Các items
    val version: Int = 1,                  // Version number
    val lastModified: Date = Date()        // Thời gian sửa đổi cuối
)
```

### 3.2. PolicyDescItem

```kotlin
data class PolicyDescItem(
    val id: Int,                    // Item ID
    val sid: String,                // Seat/Zone ID
    val cat: String = "",           // Category
    var displayName: String = "",   // Tên hiển thị (nếu khác sid)
    val type: String = "SeatZone"   // Loại item: SeatZone, Seat, Label
)
```

### 3.3. FetchSeatPlanCategoryResult

```kotlin
data class FetchSeatPlanCategoryResult(
    @BsonId
    @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String,
    var displayName: String = "",   // Tên hiển thị
    var catNames: List<String> = emptyList() // Categories
)
```

## 4. Các API chính

### 4.1. Document Management APIs

#### GET `/private/editor/policydesc/document`
- **Mô tả**: Load document theo ID (private - requires auth)
- **Query Parameters**: `globalId` (required) - Document ID
- **Response**: `PolicyDescDocument`
- **Status Codes**:
  - 200: Success
  - 400: Bad Request (missing id)
  - 404: Document not found

#### GET `/editor/policydesc/document`
- **Mô tả**: Load document theo ID (public)
- **Query Parameters**: `globalId` (required) - Document ID
- **Response**: `PolicyDescDocument`

### 4.2. Category Management APIs

#### GET `/private/editor/policydesc/document/category`
- **Mô tả**: Load category info của document
- **Query Parameters**: `globalId` (required) - Document ID
- **Response**: `FetchSeatPlanCategoryResult`
- **Status Codes**:
  - 200: Success
  - 400: Bad Request (missing id)
  - 404: Document not found

#### GET `/private/editor/policydesc/documents/category`
- **Mô tả**: Load tất cả document categories
- **Response**: List of `FetchSeatPlanCategoryResult`

## 5. Flow xử lý chính

### 5.1. Flow tải Policy Document

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       LOAD POLICY DOCUMENT FLOW                             │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │    CLIENT/FRONTEND  │
                              │                     │
                              │  GET /private/      │
                              │  editor/policydesc  │
                              │  /document          │
                              │  ?globalId={id}     │
                              └──────────┬──────────┘
                                         │
                                         ▼
                         ┌──────────────────────────────┐
                         │  DocumentController          │
                         │  .loadDoc()                  │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Validate Parameter          │
                         │  - Check globalId exists     │
                         │  - Validate ObjectId format  │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  DatabaseService             │
                         │  .loadDocument()             │
                         │                              │
                         ��  Filter: { _id: ObjectId }   │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  MongoDB                     │
                         │  Collection: policydesc      │
                         │  Query: { _id: docId }       │
                         └──────────────┬───────────────┘
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
               │  with all fields    │   │  "not found         │
               │  - styleSheet       │   │  document"          │
               │  - content          │   │                     │
               │  - items            │   │                     │
               │  - catNames         │   │                     │
               └─────────────────────┘   └─────────────────────┘
```

### 5.2. Flow tải Category Information

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      LOAD CATEGORY INFO FLOW                                │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │    CLIENT/FRONTEND  │
                              │                     │
                              │  GET /private/      │
                              │  editor/policydesc  │
                              │  /document/category │
                              │  ?globalId={id}     │
                              └──────────┬──────────┘
                                         │
                                         ▼
                         ┌──────────────────────────────┐
                         │  DocumentController          │
                         │  .loadDocCategory()          │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Validate Parameter          │
                         │  - Check globalId exists     │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  DatabaseService             │
                         │  .loadDocCategoryById()      │
                         │                              │
                         │  - Use projection to fetch   │
                         │    only: id, displayName,    │
                         │    catNames                  │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  MongoDB                     │
                         │  Collection: policydesc      │
                         │  Query: { _id: docId }       │
                         │  Projection: id, displayName,│
                         │          catNames             │
                         └──────────────┬───────────────┘
                                        │
                          ┌────────────┴────────────┐
                          │                         │
                          ▼                         ▼
               ┌─────────────────────┐   ┌─────────────────────┐
               │  Category Found     │   │  Category Not       │
               │                     │   │  Found              │
               └─────────┬───────────┘   └─────────┬───────────┘
                         │                         │
                         ▼                         ▼
               ┌─────────────────────┐   ┌─────────────────────┐
               │  Return category    │   │  Return 404         │
               │  info               │   │  "not found         │
               │  - id               │   │  document"          │
               │  - displayName      │   │                     │
               │  - catNames         │   │                     │
               └─────────────────────┘   └─────────────────────┘
```

### 5.3. Flow tải tất cả Categories

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    LOAD ALL CATEGORIES FLOW                                 │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │    CLIENT/FRONTEND  │
                              │                     │
                              │  GET /private/      │
                              │  editor/policydesc  │
                              │  /documents/category│
                              └──────────┬──────────┘
                                         │
                                         ▼
                         ┌──────────────────────────────┐
                         │  DocumentController          │
                         │  .loadAllDocCategory()       │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  DatabaseService             │
                         │  .loadAllDocCategory()       │
                         │                              │
                         │  - Fetch all documents       │
                         │  - Use projection:           │
                         │    id, displayName, catNames │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  MongoDB                     │
                         │  Collection: policydesc      │
                         │  Query: { } (all documents)  │
                         │  Projection: id, displayName,│
                         │          catNames             │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │   RESPONSE                   │
                         │   List<FetchSeatPlanCategory │
                         │   _Result>                   │
                         │                              │
                         │   [                           │
                         │     {                         │
                         │       id: "doc1",            │
                         │       displayName: "Theater",│
                         │       catNames: ["VIP",      │
                         │              "Normal"]       │
                         │     }                        │
                         │   ]                          │
                         └──────────────────────────────┘
```

## 6. Database Schema

### 6.1. Collections

| Collection | Mô tả |
|------------|-------|
| **policydesc** | Lưu trữ tất cả policy documents |

### 6.2. Document Structure

```json
{
  "_id": "ObjectId",
  "displayName": "Theater Policy",
  "catNames": ["VIP", "Normal", "Economy"],
  "styleSheet": ".seat-vip { fill: #FFD700; } .seat-normal { fill: #4169E1; }",
  "content": "<h1>Policy Description</h1><p>Terms and conditions...</p>",
  "items": [
    {
      "id": 1,
      "sid": "zone_vip",
      "cat": "VIP",
      "displayName": "VIP Zone",
      "type": "SeatZone"
    },
    {
      "id": 2,
      "sid": "zone_normal",
      "cat": "Normal",
      "displayName": "Normal Zone",
      "type": "SeatZone"
    }
  ],
  "version": 1,
  "lastModified": "2026-01-15T10:30:00.000Z"
}
```

### 6.3. Indexes

```kotlin
// Tự động tạo index
- _id (primary key)
- displayName (index)
- catNames (index - array)
```

## 7. Cấu hình

### 7.1. Application Configuration

**File**: `conf/application.conf`

```hocon
db {
    connection = "mongodb://localhost:47017"
    dbName = "metaticket"
}

ktor {
    deployment {
        port = 4011  # Development port
    }
    application {
        modules = [ metaticket.editor.policydesc.ApplicationKt.module ]
    }
}
```

### 7.2. Environment-specific Ports

| Environment | Port |
|-------------|------|
| Development | 4011 |
| Production | 3011 |

### 7.3. CORS Configuration

```kotlin
install(CORS) {
    allowHost("*", listOf("http", "https"))
    allowOrigins { it in listOf(
        "http://localhost",
        "https://devlocal.metaticket.vn",
        "*"
    ) }
    allowMethod(HttpMethod.Get)
    allowMethod(HttpMethod.Post)
    allowMethod(HttpMethod.Options)
    allowCredentials = true
    allowHeader(HttpHeaders.ContentType)
}
```

## 8. Ví dụ Request và Response

### 8.1. Tải Policy Document

**Request:**
```
GET /private/editor/policydesc/document?globalId=507f1f77bcf86cd799439011
```

**Response:**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "displayName": "Concert Hall Policy",
  "catNames": ["VIP", "Premium", "Standard"],
  "styleSheet": ".seat-vip { fill: #FFD700; } .seat-premium { fill: #C0C0C0; }",
  "content": "<h1>Concert Hall Seating Policy</h1><p>VIP seats are closest to the stage...</p>",
  "items": [
    {
      "id": 1,
      "sid": "zone_vip",
      "cat": "VIP",
      "displayName": "VIP Section",
      "type": "SeatZone"
    },
    {
      "id": 2,
      "sid": "zone_premium",
      "cat": "Premium",
      "displayName": "Premium Section",
      "type": "SeatZone"
    }
  ],
  "version": 3,
  "lastModified": "2026-06-01T15:30:00.000Z"
}
```

### 8.2. Tải Category Information

**Request:**
```
GET /private/editor/policydesc/document/category?globalId=507f1f77bcf86cd799439011
```

**Response:**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "displayName": "Concert Hall Policy",
  "catNames": ["VIP", "Premium", "Standard"]
}
```

### 8.3. Tải tất cả Categories

**Request:**
```
GET /private/editor/policydesc/documents/category
```

**Response:**
```json
[
  {
    "id": "507f1f77bcf86cd799439011",
    "displayName": "Concert Hall Policy",
    "catNames": ["VIP", "Premium", "Standard"]
  },
  {
    "id": "507f1f77bcf86cd799439012",
    "displayName": "Stadium Policy",
    "catNames": ["Bronze", "Silver", "Gold", "Platinum"]
  },
  {
    "id": "507f1f77bcf86cd799439013",
    "displayName": "Theater Policy",
    "catNames": ["Orchestra", "Mezzanine", "Balcony"]
  }
]
```

## 10. Bảo mật

### 10.1. Cơ chế bảo mật

- **Private APIs**: Các endpoints `/private/*` yêu cầu xác thực
- **Input Validation**: Validate ObjectId format
- **Error Handling**: Không expose internal errors ra ngoài

### 10.2. CORS Configuration

Service cho phép CORS từ:
- `http://localhost`
- `https://devlocal.metaticket.vn`
- `*` (all origins for development)

## 11. Dependencies

| Library | Mục đích |
|---------|----------|
| **ktor-server-netty** | HTTP Server |
| **mongodb-driver-sync** | MongoDB Driver |
| **koin-core** | Dependency Injection |
| **jackson-module-kotlin** | JSON Serialization |
| **jackson-datatype-jsr310** | Java 8 Date/Time support |

## 12. Error Handling

### 12.1. HTTP Status Codes

| Status Code | Mô tả |
|-------------|-------|
| 200 | Thành công |
| 400 | Bad Request - Thiếu hoặc sai parameter |
| 404 | Document not found |
| 500 | Internal Server Error |

### 12.2. Error Response Format

```json
{
  "error": "Mô tả lỗi bằng tiếng Việt"
}
```

---

**Tài liệu này được cập nhật lần cuối**: 04/06/2026
**Version**: 1.0
**Tác giả**: MetaTicket Development Team
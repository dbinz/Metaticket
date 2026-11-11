# Tài liệu Kỹ thuật Show Service

## 1. Tổng quan Service

### Mô tả chức năng chính
Show Service là một microservice trong hệ thống MetaTicket, chịu trách nhiệm quản lý các sự kiện và buổi biểu diễn. Service này cung cấp các chức năng:

- **Quản lý Show**: Tạo, cập nhật, xóa thông tin các buổi biểu diễn/sự kiện
- **Quản lý Show Session**: Quản lý chi tiết các buổi biểu diễn cụ thể (thời gian, địa điểm, giá vé)
- **Quản lý trạng thái**: Kiểm soát vòng đời của show và session (tạo mới → chờ duyệt → mở bán → tạm dừng → dừng → chấm dứt)
- **Tích hợp thanh toán**: Cấu hình phương thức thanh toán cho từng session
- **Quản lý hình ảnh**: Upload và quản lý hình ảnh banner, thumbnail, logo
- **Tích hợp với các service khác**: Ticket Service, Payment Service, JobRunr, Mail Service

### Công nghệ sử dụng
- **Framework**: Ktor (Kotlin)
- **Database**: MongoDB với Kotlin MongoDB Driver
- **Communication**: gRPC, REST API
- **Dependency Injection**: Koin
- **Background Jobs**: JobRunr
- **Serialization**: Jackson, Protobuf

## 2. Kiến trúc và Thành phần Chính

### 2.1 Cấu trúc tổng quan
```
service.show/
├── src/main/kotlin/metaticket/show/
│   ├── controller/          # Controllers xử lý HTTP requests
│   ├── database/           # Database services và repositories
│   ├── pojo/              # Data models (request/response/document)
│   ├── grpc/              # gRPC service implementations
│   ├── gateway/           # External service gateways
│   ├── metasearch/        # Search integration models
│   ├── cloudflare/        # Cloudflare cache management
│   ├── exception/         # Custom exceptions
│   └── Application.kt     # Main application setup
└── src/api/kotlin/        # Shared data structures
```

### 2.2 Thành phần chính

#### Show (Buổi biểu diễn)
- **Mục đích**: Đại diện cho thông tin chung của một sự kiện/buổi biểu diễn
- **Thuộc tính chính**:
  - `code`: Mã định danh duy nhất
  - `showName`: Tên buổi biểu diễn
  - `slug`: Slug thân thiện URL (tùy chọn). Lưu ở dạng canonical `[a-z0-9-]`, `null` khi vắng mặt; có partial unique index đảm bảo duy nhất
  - `eventCategoryCode`, `eventCategory`: Mã và tên danh mục sự kiện
  - `description`, `policy`, `faq`: Thông tin mô tả
  - `website`: Website của sự kiện
  - `ticketDescription`: Mô tả vé
  - `bannerImageUrl`: Ảnh hiển thị trong trang admin
  - `detailImageUrl`: Ảnh hiển thị trên trang chủ
  - `thumbnailUrl`: Ảnh thumbnail
  - `logoShowUrl`: Logo show
  - `logoHostUrl`: Logo host/nhà tổ chức
  - `status`: Trạng thái chung (mặc định: Open)
  - `pinned`: Show được ghim lên đầu
  - `shipPrice`: Phí vận chuyển
  - `soldTicket`: Số vé đã bán
  - `sessions`: Danh sách các session
  - `version`: Số phiên bản cho optimistic locking
  - `createdAt`, `lastModified`: Thời gian tạo và cập nhật

#### Show Session (Buổi biểu diễn cụ thể)
- **Mục đích**: Thể hiện chi tiết các buổi biểu diễn cụ thể
- **Thuộc tính chính**:
  - `code`: Mã session
  - `sessionName`: Tên session
  - `slug`: Slug thân thiện URL (tùy chọn). Lưu ở dạng canonical, `null` khi vắng mặt; duy nhất trong phạm vi một show
  - `startTime`, `endTime`: Thời gian bắt đầu và kết thúc
  - `venue`: Địa điểm
  - `city`: Thành phố
  - `status`: Trạng thái session (mặc định: Created)
  - `seatPlanId`: ID sơ đồ chỗ ngồi
  - `edType`: Loại seat plan (VD: "seat", "slot")
  - `paymentMethods`: Phương thức thanh toán
  - `isFree`: Miễn phí hay không
  - `requireDelivery`: Yêu cầu giao hàng
  - `orderNote`: Ghi chú đơn hàng
  - `showInHome`: Hiển thị trên trang chủ
  - `ledgendImageUrl`: Hình ảnh legend
  - `eventSquareImageUrl`: Hình ảnh vuông sự kiện
  - `eventNameImageUrl`: Hình ảnh tên sự kiện
  - `manageTicket`: Quản lý vé (ticket có mã QR)
  - `qrcodeEmail`: Gửi QR code qua email
  - `paymentOffline`: Thanh toán offline
  - `paymentNote`: Ghi chú thanh toán
  - `pgAccountCode`: Mã tài khoản payment gateway
  - `mailTemplates`: Template email
  - `saleMode`: Chế độ bán (Auto/Manually)
  - `uniqueIncrement`: Số tăng dùng để sinh order ID duy nhất

### 2.3 Trạng thái Show Session

```kotlin
enum class ShowSessionStatus {
    Created,        // Vừa được tạo
    ApprovalWait,   // Chờ duyệt
    Rejected,       // Không duyệt, từ chối
    OpeningWait,    // Chờ mở bán
    Open,           // Mở bán
    Paused,         // Tạm dừng mở bán
    Stopped,        // Dừng mở bán
    Terminated      // Chấm dứt hoàn toàn
}
```

**Luồng trạng thái**:
```
Created → ApprovalWait → OpeningWait → Open → Paused/Stopped → Terminated
                ↓
            Rejected → Terminated
```

### 2.4 Sale Mode
```kotlin
enum class SaleMode { Auto, Manually }
```
- **Auto**: Hệ thống tự động xử lý đơn hàng
- **Manually**: Admin cần xử lý thủ công từng đơn

### 2.5 Payment Types
```kotlin
enum class PaymentType {
    DomesticCard,       // ATM_CARD - Thẻ ATM nội địa
    InternationalCard,  // CREDIT_CARD - Thẻ thanh toán quốc tế
    QrCode             // BANK_TRANSFER - QR Code thanh toán
}
```

## 3. Các API Chính

### 3.1 Public APIs (Không yêu cầu xác thực)

#### GET `/public/show/fetch`
**Mô tả**: Lấy thông tin chi tiết một show theo ID

**Tham số**:
- `id` (query): ID của show (ObjectId)

**Response**: `ShowResponse`
```json
{
  "id": "string",
  "code": "string",
  "showName": "string",
  "slug": "string (canonical slug, hoặc \"\" nếu show chưa có slug)",
  "sessions": [ShowSessionResponse],
  "eventCategory": "string",
  "description": "string",
  "bannerImageUrl": "string",
  "status": "Open",
  "createdAt": 1234567890
}
```

**Lưu ý về `slug`**: Trường `slug` luôn xuất hiện trong response. Nếu show chưa có slug, giá trị là chuỗi rỗng `""` (không bao giờ bị bỏ qua). Giá trị được lưu luôn ở dạng canonical (`[a-z0-9-]`).

#### GET `/public/show/fetch/all`
**Mô tả**: Lấy danh sách tất cả show

**Response**: `List<ShowResponse>`

#### GET `/public/show/fetch/slug`
**Mô tả**: Phân giải một Show_Slug (và tùy chọn một Session_Slug) thành dữ liệu Show/Session. Endpoint chỉ đọc, không thay đổi dữ liệu đã lưu.

**Tham số**:
- `showSlug` (query, bắt buộc): Slug của show. Được chuẩn hóa (normalize) trước khi tra cứu nên chấp nhận cả input chưa canonical từ link.
- `sessionSlug` (query, optional): Slug của session trong phạm vi show. Cũng được chuẩn hóa trước khi so khớp.

**Response** (`200`): `ShowResponse` kèm `matchedSessionId`
```json
{
  "id": "string",
  "code": "string",
  "showName": "string",
  "slug": "string",
  "sessions": [ShowSessionResponse],
  "matchedSessionId": "string (chỉ có khi sessionSlug được cung cấp và khớp)"
}
```

**Lỗi**:
- `404 { "message": "Not found show" }`: không tìm thấy show theo `showSlug`
- `404 { "message": "Not found session" }`: tìm thấy show nhưng không có session khớp `sessionSlug`

**Lưu ý**: Tra cứu show theo slug dùng partial unique index trên `ShowDocument.slug`. Tra cứu theo ID (`/public/show/fetch`) không bị ảnh hưởng bởi việc có hay không có slug.

#### GET `/public/show/session/fetch`
**Mô tả**: Lấy thông tin chi tiết một session

**Tham số**:
- `showId` (query): ID của show
- `sessionId` (query): ID của session

**Response**: `ShowSessionResponse`
```json
{
  "id": "string",
  "code": "string",
  "sessionName": "string",
  "startTime": 1234567890,
  "endTime": 1234567890,
  "venue": "string",
  "city": {
    "id": "string",
    "name": "string",
    "code": "string"
  },
  "status": "Open",
  "isFree": false,
  "slug": "string (canonical slug, hoặc \"\" nếu session chưa có slug)",
  "paymentMethods": ["DomesticCard", "InternationalCard", "QrCode"]
}
```

#### GET `/public/show/sessions/fetch`
**Mô tả**: Lấy danh sách sessions của một show

**Tham số**:
- `showId` (query): ID của show

**Response**: `List<ShowSessionResponse>`

#### GET `/public/show/event-category/fetch`
**Mô tả**: Lấy danh sách danh mục sự kiện

**Response**: `List<EventCategory>`

#### GET `/public/show/city/fetch/all`
**Mô tả**: Lấy danh sách tất cả thành phố

**Response**: `List<CityDocument>`

#### GET `/public/show/city/fetch`
**Mô tả**: Lấy thông tin một thành phố

**Tham số**:
- `id` (query): ID của thành phố

**Response**: `CityDocument`

### 3.2 Private APIs (Yêu cầu xác thực)

#### POST `/private/show/create`
**Mô tả**: Tạo show mới

**Request Body**: `CreateShowRequest`
```json
{
  "code": "string",
  "showName": "string",
  "bannerImage": "string (đường dẫn file tạm)",
  "detailImageUrl": "string (đường dẫn file tạm)",
  "thumbnailUrl": "string (đường dẫn file tạm)",
  "logoShowImage": "string (đường dẫn file tạm, optional)",
  "logoHostImage": "string (đường dẫn file tạm, optional)",
  "website": "string (optional)",
  "ticketDescription": "string (optional)",
  "description": "string (optional)",
  "policy": "string (optional)",
  "eventCategoryCode": "string (optional)",
  "eventCategory": "string (optional)",
  "faq": "string (optional)",
  "pinned": boolean (optional),
  "slug": "string (optional) - slug thân thiện URL cho show"
}
```

**Lưu ý**: 
- Các trường hình ảnh (`bannerImage`, `detailImageUrl`, `thumbnailUrl`, `logoShowImage`, `logoHostImage`) cần được upload trước qua API `/private/show/upload/image` để lấy đường dẫn file tạm
- Show được tạo sẽ có danh sách sessions rỗng ban đầu
- `slug` là tùy chọn. Nếu vắng mặt (null/rỗng/khoảng trắng) → show được lưu không có slug. Nếu có, slug được **kiểm tra đúng theo giá trị nhập vào** (validate nghiêm, KHÔNG tự chuẩn hóa lại): phải đã ở dạng canonical — chỉ gồm `[a-z0-9]` ngăn cách bởi dấu `-` đơn, không chữ hoa, không khoảng trắng/dấu/ký tự lạ, không bắt đầu/kết thúc bằng `-` và không có `--`, độ dài 1..200. Mọi kiểm tra xảy ra **trước** khi thay đổi dữ liệu:
  - Không đúng định dạng/độ dài → `400 { "message": "Invalid slug", "slug": "<giá trị gốc>" }`
  - Trùng với slug của show khác → `409 { "message": "Duplicate slug", "slug": "<canonical>" }`

**Response**: `ShowResponse`

#### POST `/private/show/update`
**Mô tả**: Cập nhật thông tin show

**Request Body**: `UpdateShowRequest`
```json
{
  "id": "string",
  "code": "string",
  "showName": "string (optional)",
  "bannerImage": "string (đường dẫn file tạm, optional)",
  "detailImageUrl": "string (đường dẫn file tạm, optional)",
  "thumbnailUrl": "string (đường dẫn file tạm, optional)",
  "logoShowImage": "string (đường dẫn file tạm, optional)",
  "logoHostImage": "string (đường dẫn file tạm, optional)",
  "eventCategoryCode": "string (optional)",
  "eventCategory": "string (optional)",
  "website": "string (optional)",
  "ticketDescription": "string (optional)",
  "description": "string (optional)",
  "policy": "string (optional)",
  "faq": "string (optional)",
  "pinned": boolean (optional)
}
```

**Response**: `ShowResponse`

**Lưu ý về `slug`**: `UpdateShowRequest` cũng chấp nhận trường tùy chọn `slug` với **cùng quy tắc validate nghiêm** (kiểm tra đúng giá trị nhập, không tự chuẩn hóa) như khi tạo. Việc gán lại đúng slug hiện tại của chính show đó được chấp nhận (không bị coi là trùng). Slug không hợp lệ → `400 { "message": "Invalid slug", "slug": "<giá trị gốc>" }`; trùng với show khác → `409 { "message": "Duplicate slug", "slug": "<canonical>" }`. Mọi từ chối xảy ra trước khi dữ liệu được thay đổi.

> **Chi tiết kỹ thuật (duplicate trên update)**: `createShow` dùng `insertOne` nên đụng độ unique index ném `MongoWriteException`; còn `updateShow` dùng `findOneAndUpdate` nên đụng độ ném `MongoCommandException` (code `11000`). Controller bắt cả hai (qua `isDuplicateSlugError`) để luôn trả về `409` thay vì lỗi 500 chung chung.

#### POST `/private/show/upload/image`
**Mô tả**: Upload hình ảnh cho show

**Request**: Multipart form data
- `file`: File hình ảnh
- `type`: Loại hình ảnh (banner, thumbnail, logo)

**Response**: 
```json
{
  "imageUrl": "string"
}
```

#### POST `/private/show/session/update`
**Mô tả**: Cập nhật thông tin session

**Request Body**: `UpdateShowSessionRequest`
```json
{
  "showId": "string",
  "sessionId": "string",
  "cityId": "string",
  "sessionName": "string (optional)",
  "code": "string",
  "address": "string",
  "startTime": 1234567890,
  "endTime": 1234567890,
  "requireDelivery": false,
  "orderNote": "string (optional)",
  "isFree": false,
  "showInHome": true,
  "ledgendImage": "string (đường dẫn file tạm, optional)",
  "eventSquareImage": "string (đường dẫn file tạm, optional)",
  "eventNameImage": "string (đường dẫn file tạm, optional)",
  "manageTicket": boolean (optional) - có quản lý vé với mã QR hay không,
  "qrcodeEmail": boolean (optional) - gửi QR code qua email (yêu cầu manageTicket=true),
  "slug": "string (optional) - slug thân thiện URL cho session"
}
```

**Lưu ý**: 
- `qrcodeEmail` chỉ có hiệu lực khi `manageTicket = true`
- Các trường hình ảnh cần được upload trước qua API `/private/show/upload/image`
- `slug` là tùy chọn và duy nhất trong phạm vi một show. Nếu vắng mặt → slug đã lưu giữ nguyên. Nếu có, slug được chuẩn hóa + kiểm tra hợp lệ + kiểm tra trùng trong show **trước** khi cập nhật:
  - Định dạng/độ dài không hợp lệ → `400 { "message": "Invalid slug", "slug": "<giá trị gốc>" }`
  - Trùng với session khác trong cùng show → `409 { "message": "Duplicate slug", "slug": "<canonical>" }`
  - Xung đột version khi cập nhật đồng thời → `409 { "message": "Slug update conflict, please retry" }`
  - Gán lại đúng slug hiện tại của chính session đó được chấp nhận

**Response**: `ShowSessionResponse`

#### POST `/private/show/session/update/payment`
**Mô tả**: Cập nhật cấu hình thanh toán cho session

**Request Body**: `UpdateShowSessionPaymentRequest`
```json
{
  "showId": "string",
  "sessionId": "string",
  "paymentOffline": false,
  "paymentNote": "string",
  "pgAccountCode": "string",
  "paymentMethods": ["DomesticCard", "InternationalCard", "QrCode"]
}
```

**Lưu ý**: 
- `paymentMethods` hỗ trợ các giá trị: `DomesticCard`, `InternationalCard`, `QrCode` (từ PaymentType enum trong payment service)

**Response**: `ShowSessionResponse`

#### POST `/private/show/session/update/seatplan`
**Mô tả**: Cập nhật sơ đồ chỗ ngồi cho session

**Request Body**: `UpdateShowSessionSeatPlanRequest`
```json
{
  "showId": "string",
  "sessionId": "string",
  "edType": "string",
  "seatPlanId": "string"
}
```

**Response**: `ShowSessionResponse`

#### POST `/private/show/session/update/mail/templates`
**Mô tả**: Cập nhật template email cho session

**Request Body**: `UpdateShowSessionMailTemplateRequest`
```json
{
  "showId": "string",
  "sessionId": "string",
  "mailTemplates": [
    {
      "orderStatus": "string",
      "plugin": "string",
      "templateId": "string"
    }
  ]
}
```

**Response**: `ShowSessionResponse`

### 3.3 Session Management APIs

#### POST `/private/show/session/approval/request`
**Mô tả**: Yêu cầu duyệt session

**Request Body**: `ShowSessionRequest`
```json
{
  "showId": "string",
  "sessionId": "string"
}
```

**Response**: `ShowSessionResponse`

#### POST `/private/show/session/approval/approve`
**Mô tả**: Duyệt session

**Request Body**: `ShowSessionRequest`

**Response**: `ShowSessionResponse`

#### POST `/private/show/session/approval/reject`
**Mô tả**: Từ chối duyệt session

**Request Body**: `ShowSessionRequest`

**Response**: `ShowSessionResponse`

#### POST `/private/show/session/sale/start`
**Mô tả**: Bắt đầu bán vé cho session

**Request Body**: `ShowSessionRequest`

**Response**: `ShowSessionResponse`

#### POST `/private/show/session/sale/pause`
**Mô tả**: Tạm dừng bán vé

**Request Body**: `ShowSessionRequest`

**Response**: `ShowSessionResponse`

#### POST `/private/show/session/sale/stop`
**Mô tả**: Dừng bán vé

**Request Body**: `ShowSessionRequest`

**Response**: `ShowSessionResponse`

#### POST `/private/show/session/terminate`
**Mô tả**: Chấm dứt session

**Request Body**: `ShowSessionRequest`

**Response**: `ShowSessionResponse`

#### POST `/private/show/session/sale/mode/change`
**Mô tả**: Thay đổi chế độ bán vé (Auto/Manual)

**Request Body**: `ChangeSaleModeShowSessionRequest`
```json
{
  "showId": "string",
  "sessionId": "string",
  "saleMode": "Auto" // Hoặc "Manually"
}
```

**Response**: Message string

#### POST `/private/show/session/link`
**Mô tả**: Tạo link cho session

**Tham số**:
- `showId` (query): ID của show
- `sessionId` (query): ID của session

**Response**: Link string (VD: `https://domain?showId=xxx&sessionId=xxx`)

### 3.4 MetaSearch APIs

#### GET `/private/show/sync-metasearch`
**Mô tả**: Đồng bộ tất cả show data lên MetaSearch service và clear Cloudflare cache

**Response**: Success message

## 4. Ví dụ Request và Response

### 4.1 Tạo Show mới

**Request**:
```bash
POST /private/show/create
Content-Type: application/json

{
  "code": "CONCERT2024",
  "showName": "Concert Mùa Hè 2024",
  "eventCategoryCode": "MUSIC",
  "description": "Concert âm nhạc đặc sắc mùa hè 2024",
  "policy": "Không hoàn tiền sau khi mua vé",
  "faq": "Q: Có được mang đồ ăn vào không? A: Không được phép"
}
```

**Response**:
```json
{
  "id": "64a1b2c3d4e5f6789012345",
  "code": "CONCERT2024",
  "showName": "Concert Mùa Hè 2024",
  "sessions": [],
  "eventCategoryCode": "MUSIC",
  "eventCategory": "Âm nhạc",
  "description": "Concert âm nhạc đặc sắc mùa hè 2024",
  "policy": "Không hoàn tiền sau khi mua vé",
  "faq": "Q: Có được mang đồ ăn vào không? A: Không được phép",
  "status": "Open",
  "shipPrice": 0,
  "pinned": false,
  "createdAt": 1688123456789
}
```

### 4.2 Cập nhật Session

**Request**:
```bash
POST /private/show/session/update
Content-Type: application/json

{
  "showId": "64a1b2c3d4e5f6789012345",
  "sessionId": "64a1b2c3d4e5f6789012346",
  "cityId": "64a1b2c3d4e5f6789012347",
  "sessionName": "Đêm nhạc 1",
  "code": "NIGHT1",
  "address": "Nhà hát Lớn Hà Nội",
  "startTime": 1688140800000,
  "endTime": 1688151600000,
  "requireDelivery": false,
  "isFree": false,
  "showInHome": true
}
```

**Response**:
```json
{
  "id": "64a1b2c3d4e5f6789012346",
  "code": "NIGHT1",
  "sessionName": "Đêm nhạc 1",
  "startTime": 1688140800000,
  "endTime": 1688151600000,
  "venue": "Nhà hát Lớn Hà Nội",
  "city": {
    "id": "64a1b2c3d4e5f6789012347",
    "name": "Hà Nội",
    "code": "HN"
  },
  "status": "Created",
  "isFree": false,
  "showInHome": true,
  "requireDelivery": false
}
```

### 4.3 Lấy thông tin Show

**Request**:
```bash
GET /public/show/fetch?id=64a1b2c3d4e5f6789012345
```

**Response**:
```json
{
  "id": "64a1b2c3d4e5f6789012345",
  "code": "CONCERT2024",
  "showName": "Concert Mùa Hè 2024",
  "sessions": [
    {
      "id": "64a1b2c3d4e5f6789012346",
      "code": "NIGHT1",
      "sessionName": "Đêm nhạc 1",
      "startTime": 1688140800000,
      "endTime": 1688151600000,
      "venue": "Nhà hát Lớn Hà Nội",
      "status": "Open",
      "isFree": false
    }
  ],
  "eventCategoryCode": "MUSIC",
  "eventCategory": "Âm nhạc",
  "description": "Concert âm nhạc đặc sắc mùa hè 2024",
  "status": "Open",
  "createdAt": 1688123456789
}
```

### 4.4 Lấy thông tin City

**Request**:
```bash
GET /public/show/city/fetch?id=64a1b2c3d4e5f6789012347
```

**Response**:
```json
{
  "id": "64a1b2c3d4e5f6789012347",
  "name": "Hà Nội",
  "code": "HN"
}
```

### 4.5 Lấy danh sách Cities

**Request**:
```bash
GET /public/show/city/fetch/all
```

**Response**:
```json
[
  {
    "id": "64a1b2c3d4e5f6789012347",
    "name": "Hà Nội",
    "code": "HN"
  },
  {
    "id": "64a1b2c3d4e5f6789012348",
    "name": "TP. Hồ Chí Minh",
    "code": "HCM"
  }
]
```

## 5. Cơ chế Xác thực và Bảo mật

### 5.1 Phân loại API
- **Public APIs** (`/public/*`): Không yêu cầu xác thực, dành cho frontend hiển thị thông tin
- **Private APIs** (`/private/*`): Yêu cầu xác thực, dành cho admin/management

### 5.2 CORS Configuration
```kotlin
install(CORS) {
    allowHost("*", listOf("http", "https"))
    allowOrigins { 
        it in listOf(
            "http://localhost", 
            "https://devlocal.metaticket.vn", 
            "https://admin.devlocal.metaticket.vn"
        ) 
    }
    allowMethod(HttpMethod.Get)
    allowMethod(HttpMethod.Post)
    allowMethod(HttpMethod.Options)
    allowCredentials = true
    allowHeader(HttpHeaders.ContentType)
}
```

### 5.3 Bảo mật dữ liệu
- **Input Validation**: Kiểm tra tính hợp lệ của ObjectId
- **Error Handling**: Không expose thông tin nhạy cảm trong error messages
- **File Upload**: Kiểm tra loại file và kích thước khi upload hình ảnh

## 6. Các Mã Lỗi Phổ biến

| Mã lỗi (HTTP Status) | Message | Mô tả |
|---------------------|---------|-------|
| 400 | Missing show name | Thiếu tên show |
| 400 | Missing show code | Thiếu mã show |
| 400 | Missing show banner image | Thiếu ảnh banner |
| 400 | Missing show detail image | Thiếu ảnh chi tiết |
| 400 | Missing show thumbnail image | Thiếu ảnh thumbnail |
| 400 | Missing or malformed id | Thiếu hoặc sai định dạng ID |
| 400 | Invalid show id | ID show không hợp lệ |
| 400 | Invalid session id | ID session không hợp lệ |
| 400 | Invalid city id | ID city không hợp lệ |
| 400 | Invalid show | Show không tồn tại |
| 400 | Invalid city | City không tồn tại |
| 400 | Missing or malformed show id | Thiếu hoặc sai định dạng showId |
| 400 | Missing or malformed session id | Thiếu hoặc sai định dạng sessionId |
| 400 | qrcodeEmail requires manageTicket | qrcodeEmail yêu cầu manageTicket=true |
| 403 | fail to waiting to approve session | Lỗi khi gửi yêu cầu duyệt session |
| 403 | fail to approve session | Lỗi khi duyệt session |
| 403 | fail to reject session | Lỗi khi từ chối session |
| 403 | fail to pause session | Lỗi khi tạm dừng session |
| 403 | fail to stop session | Lỗi khi dừng session |
| 403 | fail to terminate session | Lỗi khi chấm dứt session |
| 403 | Có lỗi xảy ra | Lỗi khi bắt đầu bán vé |
| 403 | fail to change session sale mode | Lỗi khi thay đổi chế độ bán |
| 404 | Not found show | Show không tìm thấy |
| 404 | Not found session | Session không tìm thấy |
| 500 | Create Image failed | Lỗi khi tạo ảnh |
| 500 | cannot start ticket | Lỗi khi khởi tạo ticket service |
| 500 | cannot stop ticket | Lỗi khi dừng ticket service |

## 7. Hướng dẫn Triển khai và Vận hành

### 7.1 Yêu cầu hệ thống
- **JVM**: OpenJDK 11+
- **Memory**: Minimum 512MB RAM
- **Database**: MongoDB 4.4+
- **Network**: gRPC port (cấu hình trong config), HTTP port (cấu hình trong config)

### 7.2 Cấu hình môi trường

#### Application Configuration
```hocon
# Database Configuration
db {
    connection: "mongodb://localhost:27017"
    dbName: "metaticket"
    showColName: "show"
    cityColName: "city"
    eventCategoryColName: "event-category"
}

# Ktor Server Configuration
ktor {
    deployment {
        port = 4003  # HTTP port
    }
    application {
        modules = [ metaticket.show.ApplicationKt.module ]
    }
}

# Storage Configuration
storage_path {
    image_path: "./show-image"
    image_path_temp: "./show-image-temp"
    image_prefix: "https://cdn.metaticket.vn"
}

# gRPC Configuration
grpc {
    server {
        port = 4103  # gRPC port
    }
    channel {
        ticketS {
            sid = "ticketS01"
            host = "127.0.0.1"
            port = 4104
        }
        jobrunrS {
            sid = "jobrunrS01"
            host = "127.0.0.1"
            port = 4107
        }
    }
}

# Homepage Configuration (dùng để dựng share link)
homepage {
    base_url: "https://metaticket.vn"
}

# MetaSearch Integration
metasearch {
    baseUrl: "http://localhost:48080/internal/metasearch/"
    createUpdateShowUrl: "shows/create-or-update"
    updateSoldTicketUrl: "shows/update-sold-ticket"
    xApiKey: "your-api-key"
}

# Cloudflare Cache Management
cloudflare {
    baseUrl: "https://api.cloudflare.com/client/v4/zones/"
    bearerToken: "your-cloudflare-token"
    zoneId: "your-zone-id"
    fileBaseUrl: "https://metaticket.vn/"
    tags: [ "show-api" ]
}
```

**Các tham số chính**:
- `ktor.deployment.port`: Port HTTP server (mặc định: 4003)
- `grpc.server.port`: Port gRPC server (mặc định: 4103)
- `db.connection`: MongoDB connection string
- `db.dbName`: Tên database (mặc định: "metaticket")
- `storage_path.image_path`: Thư mục lưu ảnh vĩnh viễn
- `storage_path.image_path_temp`: Thư mục lưu ảnh tạm
- `storage_path.image_prefix`: URL prefix cho ảnh (CDN)
- `homepage.base_url`: Origin của trang chủ (Homepage_Base_URL), dùng làm gốc cho mọi share link sinh ra (booking link / detail link). **Bắt buộc**: được đọc một lần lúc khởi động qua `HomepageConfig`; nếu thiếu hoặc chỉ gồm khoảng trắng thì service sẽ **abort startup** với thông báo lỗi rõ ràng thay vì chạy và sinh link sai. Dấu `/` ở cuối không bị cắt bỏ — việc chuẩn hóa dấu phân tách giữa origin và path do `ShareLinkBuilder.join` xử lý

### 7.3 Build và Deploy

### 7.4 Health Check

#### HTTP Health Check
```bash
curl http://localhost:8080/public/show/fetch/all
```

## 8. Lưu ý Đặc biệt về Quản lý Dữ liệu

### 8.1 Show và Show Session Relationship

#### Embedded Document Pattern
- Show Session được lưu trữ như embedded documents trong Show
- Ưu điểm: Atomic operations, consistent reads
- Nhược điểm: MongoDB giới hạn kích thước tối đa cho một document là 16MB. Điều này có thể gây ra vấn đề khi số lượng sessions trong một show tăng lên quá nhiều, hoặc khi sessions chứa nhiều dữ liệu phức tạp như hình ảnh, metadata

#### Version Control
- Mỗi update tăng version number
- Sử dụng optimistic locking để tránh conflict
- Track lastModified timestamp

### 8.2 Status Management

#### State Transition Rules
```kotlin
// Chỉ cho phép chuyển trạng thái theo luồng định sẵn
val allowedTransitions = mapOf(
    Created to listOf(ApprovalWait),
    ApprovalWait to listOf(OpeningWait, Rejected),
    Rejected to listOf(ApprovalWait, Terminated),
    OpeningWait to listOf(Open),
    Open to listOf(Paused, Stopped),
    Paused to listOf(Open, Stopped),
    Stopped to listOf(Terminated)
)
```

#### Business Rules
- Không thể xóa Show có Session đang Open
- Session Terminated không thể thay đổi trạng thái
- Session Free không cần cấu hình payment

### 8.3 Data Consistency

#### Cross-Service Consistency
- Sử dụng JobRunr để sync data với MetaSearch
- Event-driven updates cho Ticket Service
- Eventual consistency model

#### Image Management
- Upload vào temp folder trước
- Move vào permanent folder sau khi validate
- Cleanup orphaned images định kỳ

#### Data Validation
```kotlin
// Input sanitization
fun validateShowCode(code: String): Boolean {
    return code.matches(Regex("^[A-Z0-9_]{3,20}$"))
}

// File upload validation
fun validateImageFile(file: ByteArray): Boolean {
    val allowedTypes = listOf("image/jpeg", "image/png", "image/webp")
    val maxSize = 5 * 1024 * 1024 // 5MB
    return file.size <= maxSize && detectMimeType(file) in allowedTypes
}
```

#### Access Control
- Public APIs chỉ return non-sensitive data
- Private APIs require proper authentication
- Rate limiting cho public endpoints

## 9. Tích hợp với các Service khác

### 9.1 Ticket Gateway
- **Mục đích**: Quản lý vé và seat plan
- **Các operations**:
  - `startTicket(showId, sessionId)`: Khởi tạo ticket service cho session
  - `stopTicket(showId, sessionId)`: Dừng ticket service cho session

### 9.2 JobRunr Gateway
- **Mục đích**: Chạy background jobs và đồng bộ dữ liệu
- **Các operations**:
  - `onShowChange(showId)`: Trigger sync data khi show thay đổi

### 9.3 MetaSearch Client
- **Mục đích**: Đồng bộ dữ liệu show lên MetaSearch service
- **Slug trong payload đồng bộ**: Payload gửi lên MetaSearch (`MetaSearchCreateUpdateShowRequest` và `MetaSearchShowSession`) bao gồm trường `slug` cho cả show và session. Slug được map từ document đã lưu; khi vắng mặt (`null`) sẽ gửi chuỗi rỗng `""`. Nhờ vậy chỉ mục tìm kiếm luôn có slug để dựng link thân thiện URL trỏ về các route slug.

### 9.4 Cloudflare Client
- **Mục đích**: Clear cache sau khi đồng bộ dữ liệu

---

**Tài liệu này được cập nhật lần cuối**: 03/06/2026
**Version**: 1.1
**Tác giả**: MetaTicket Development Team
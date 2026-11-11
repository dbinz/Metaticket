# Tài liệu Kỹ thuật - Notification Service

## Tổng quan

Notification Service là microservice trong hệ thống bán vé sự kiện online MetaTicket, chịu trách nhiệm xử lý và gửi các thông báo qua ba kênh:
- **Email**: Gửi email qua SMTP (Simple Java Mail)
- **Zalo (ZNS)**: Gửi tin nhắn qua Zalo Notification Service
- **SMS**: Tin nhắn SMS như failover từ ZNS

Service được xây dựng bằng Kotlin với framework Ktor và gRPC.

---

## Mục lục

1. [Kiến trúc Tổng thể](#kiến-trúc-tổng-thể)
2. [Cấu trúc Thư mục](#cấu-trúc-thư-mục)
3. [Flow xử lý Notification](#flow-xử-lý-notification)
4. [Plugin System](#plugin-system)
5. [gRPC Services](#grpc-services)
6. [REST API](#rest-api)
7. [Cấu hình](#cấu-hình)
8. [Bảo mật](#bảo-mật)
9. [Cache Layer](#cache-layer)
10. [Database](#database)
11. [Monitoring](#monitoring)

---

## Kiến trúc Tổng thể

### Layered Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      Controller Layer                            │
│                  (REST API - MailController)                     │
├─────────────────────────────────────────────────────────────────┤
│                      Service Layer                               │
│    (MailNotificationServiceImpl, ZaloNotificationServiceImpl)   │
├─────────────────────────────────────────────────────────────────┤
│                    Processor Layer                               │
│         (SendMailProcessor, ZaloMessageProcessor)               │
├─────────────────────────────────────────────────────────────────┤
│                     Plugin Layer                                 │
│    (PluginMailTemplateService, PluginZNSTemplateService)        │
├────────────────────┬────────────────────┬───────────────────────┤
│   Gateway Layer   │   Database Layer   │     Cache Layer       │
│ ZaloNotification  │  MailTemplateDB    │  JobRequestCache      │
│ ServiceGateway    │      Service       │  MailTemplateCache    │
│                   │                    │  ImageCache           │
└────────────────────┴────────────────────┴───────────────────────┘
```

### Technology Stack

| Component | Technology |
|-----------|------------|
| Framework | Ktor (HTTP Server) |
| Language | Kotlin |
| gRPC | gRPC-Kotlin |
| Dependency Injection | Koin |
| Email Client | Simple Java Mail |
| Database | MongoDB |
| Async Processing | Kotlin Coroutines (Channel + Semaphore) |
| Serialization | Jackson |

---

## Cấu trúc Thư mục

```
service.notification/
├── src/
│   ├── grpc/
│   │   └── proto/                    # gRPC protocol definitions
│   │       ├── mail_service.proto
│   │       ├── zns.service.proto
│   │       └── ...
│   └── main/kotlin/metaticket/notification/
│       ├── Application.kt            # Entry point chính
│       ├── ApplicationDI.kt          # Dependency Injection (Koin)
│       ├── Route.kt                  # REST routing
│       ├── StartUp.kt                # Khởi động service
│       ├── Launcher.kt               # Main launcher
│       ├── cache/                    # Cache layer
│       │   ├── ImageCache.kt         # Cache images cho email
│       │   ├── JobRequestCache.kt    # Cache job requests
│       │   └── MailTemplateCache.kt  # Cache mail templates
│       ├── configuration/            # Configuration classes
│       │   └── Environment.kt        # Environment variables
│       ├── controller/               # REST API controllers
│       │   └── MailController.kt     # Mail template CRUD
│       ├── database/                 # Database layer
│       │   └── service/
│       │       └── MailTemplateDBService.kt
│       ├── gateway/                  # External service gateways
│       │   └── ZaloNotificationServiceGateway.kt
│       ├── mail/                     # Email handling
│       │   └── client/
│       │       └── MailClient.kt
│       ├── plugin/                   # Plugin system
│       │   └── template/
│       │       └── service/
│       │           ├── PluginMailTemplateService.kt
│       │           └── PluginZNSTemplateService.kt
│       ├── processor/                # Message processors
│       │   ├── SendMailProcessor.kt
│       │   └── ZaloMessageProcessor.kt
│       └── service/                  # gRPC service implementations
│           ├── MailNotificationServiceImpl.kt
│           ├── ZaloNotificationServiceImpl.kt
│           └── JobExecutorServiceImpl.kt
├── plugin/                           # Plugin directories (dynamic loading)
│   ├── mail_template/                # Mail template JARs
│   │   ├── plugin.mailtemplate.basic/
│   │   ├── plugin.mailtemplate.qrcode/
│   │   ├── plugin.mailtemplate.offline_payment/
│   │   ├── plugin.mailtemplate.otp/
│   │   └── plugin.mailtemplate.ticketslots/
│   └── zns_template/                 # ZNS template JARs
│       ├── plugin.zns/
│       └── plugin.zns.basic/
├── conf/                             # Configuration files
│   ├── application.conf
│   └── ...
└── resources/
    └── ...
```

---

## Flow xử lý Notification

### 1. Email Flow

```
┌──────────────┐     ┌─────────────────────┐     ┌────────────────────┐
│   Client     │────▶│  MailNotification   │────▶│  SendMailProcessor │
│  (gRPC Call) │     │    ServiceImpl      │     │   (Channel +       │
│              │     │                     │     │    Semaphore)      │
└──────────────┘     └─────────────────────┘     └────────┬─────────┘
                                                             │
                    ┌────────────────────────────────────────┘
                    ▼
┌─────────────────────────────┐     ┌─────────────────────────────┐
│ PluginMailTemplateService   │────▶│    MailTemplateCache        │
│ (Load plugin: QRCode,       │     │    (from MongoDB)           │
│  Basic, OTP, etc.)          │     └─────────────────────────────┘
└─────────────────────────────┘               │
                    │                          ▼
                    │              ┌─────────────────────────────┐
                    │              │  PluginMailTemplate.build   │
                    │              │  Body() + Attachment()      │
                    └─────────────▶│                             │
                                   └──────────────┬──────────────┘
                                                  │
                                                  ▼
                                   ┌─────────────────────────────┐
                                   │    MailClient.sendMail()    │
                                   │    (Simple Java Mail)       │
                                   └──────────────┬──────────────┘
                                                  │
                                                  ▼
                                   ┌─────────────────────────────┐
                                   │        SMTP Server          │
                                   └─────────────────────────────┘
```

**Chi tiết Flow Email:**

1. **Client gọi gRPC** `SendMailOrder` hoặc `SendMailOTP`
2. **MailNotificationServiceImpl**:
   - Tạo `JobRunning` request và lưu vào `JobRequestCache`
   - Submit request vào `SendMailProcessor`
   - Chờ kết quả qua `CompletableDeferred`
3. **SendMailProcessor**:
   - Nhận request từ `Channel`
   - Chờ `Semaphore` (max 5 concurrent requests)
   - Gọi `PluginMailTemplateService.getPlugin()` để lấy plugin tương ứng
4. **PluginMailTemplate**:
   - Lấy `MailTemplateDocument` từ `MailTemplateCache`
   - Gọi `plugin.buildBody()` để tạo HTML body
   - Gọi `plugin.buildAttachment()` để tạo đính kèm (PDF ticket, QR code)
   - Gọi `plugin.buildSubject()` để tạo subject
5. **MailClient**:
   - Gửi email qua SMTP server
   - Trả kết quả về cho processor
6. **Hoàn thành**:
   - Cập nhật `JobRequestCache` với kết quả
   - Response về cho client

**Code Reference:**
- `MailNotificationServiceImpl.kt` - gRPC service implementation
- `SendMailProcessor.kt` - Message queue processing
- `PluginMailTemplateService.kt` - Plugin management
- `MailClient.kt` - SMTP client

---

### 2. Zalo (ZNS) Flow

```
┌──────────────┐     ┌─────────────────────┐     ┌────────────────────┐
│   Client     │────▶│ ZaloNotification    │────▶│ ZaloMessageProcessor│
│  (gRPC Call) │     │    ServiceImpl      │     │  (Channel +        │
│              │     │                     │     │   Semaphore)       │
└──────────────┘     └─────────────────────┘     └────────┬─────────┘
                                                             │
                    ┌────────────────────────────────────────┘
                    ▼
┌─────────────────────────────┐     ┌─────────────────────────────┐
│ PluginZNSTemplateService   │────▶│ PluginZNS.buildTemplateData │
│ (Load plugin: Basic, OTP)  │     │                             │
└─────────────────────────────┘     └──────────────┬──────────────┘
                    │                                 │
                    │         ┌───────────────────────┘
                    ▼         ▼
┌─────────────────────────────────────────────────────────────┐
│           ZaloNotificationServiceGateway                    │
│  - Gọi Zalo API (sendMessageUrl)                            │
│  - Gửi kèm sms_msg cho SMS failover                         │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
              ┌────────────────────────────────┐
              │      Zalo API (FPT)            │
              │  sendMessageUrl endpoint       │
              └────────────┬───────────────────┘
                           │
                           ▼ (nếu Zalo fail, dùng SMS)
```

**Chi tiết Flow Zalo:**

1. **Client gọi gRPC** `SendZaloMessage` với:
   - `metadata`: ref_id, plugin, template_id, phone, failover
   - `template_data`: Map<String, String> chứa dữ liệu template

2. **ZaloNotificationServiceImpl**:
   - Tạo `ZaloMessageRequest` với `CompletableDeferred` cho async response
   - Gọi `ZaloMessageProcessor.sendMessage()`

3. **ZaloMessageProcessor**:
   - Nhận message từ Channel
   - Chờ Semaphore (max 5 concurrent)
   - Gọi `PluginZNSTemplateService.getPlugin()` để lấy plugin

4. **PluginZNS**:
   - Gọi `plugin.buildTemplateData()` để format dữ liệu theo template
   - Gọi `plugin.buildSMSTemplateMsg()` để tạo SMS message (dùng cho failover)

5. **ZaloNotificationServiceGateway**:
   - Gửi HTTP POST đến Zalo API endpoint
   - Include headers: `app-id`, `secret-key`
   - Body chứa: `phone`, `template_id`, `ref_id`, `template_data`, `failover`, **`sms_msg`**

6. **Xử lý Failover**:
   - Nếu Zalo gửi thành công → OK
   - Nếu Zalo fail và `failover=true` → SMS được gửi tự động (qua `sms_msg` field)

**Code Reference:**
- `ZaloNotificationServiceImpl.kt` - gRPC service
- `ZaloMessageProcessor.kt` - Message queue processing
- `PluginZNSTemplateService.kt` - Plugin management
- `ZaloNotificationServiceGateway.kt` - External API call

---

### 3. SMS Flow (Failover từ ZNS)

```
┌─────────────┐      ┌──────────────┐      ┌─────────────────┐
│   Zalo API  │─────▶│  Success     │─────▶│    Done         │
│   Response  │      │  (code = 0)  │      │                 │
└─────────────┘      └──────────────┘      └───────────���─────┘
       │
       │ code != 0
       ▼
┌──────────────┐      ┌──────────────┐
│  Check       │─────▶│  sms_msg     │
│  failover    │      │  sent to SMS │
│  flag        │      │  gateway     │
└──────────────┘      └──────────────┘
```

**Đặc điểm SMS:**
- Không có service SMS riêng biệt
- SMS chỉ được gửi khi Zalo thất bại VÀ `failover=true`
- Tin nhắn SMS được tạo từ file `conf/sms.template.json` trong plugin
- Cú pháp template: `<KEY>` được thay thế bằng giá trị tương ứng

**Example sms.template.json:**
```json
[
  {
    "order_confirm": "MetaTicket: Ma don hang cua ban la <order_code>. Tong tien: <total_price>VND. Cam on quy khach!"
  },
  {
    "otp": "MetaTicket: Ma xac nhan cua ban la <otp>. Het han sau 5 phut."
  }
]
```

---

## Plugin System

### Giới thiệu

Notification Service sử dụng **plugin architecture** để cho phép mở rộng tính năng mà không cần sửa code core. Plugins được load động từ JAR files.

**Thư mục Plugins:**
- Mail Template: `./plugin/mail_template/`
- ZNS Template: `./plugin/zns_template/`

**Cơ chế load:**
- Sử dụng Java `ServiceLoader` với `URLClassLoader`
- Đọc file `META-INF/services/<interface_name>` để discover plugins

---

### Mail Template Plugins

**Interface:** `PluginMailTemplate`

```kotlin
interface PluginMailTemplate {
    suspend fun buildSubject(data: Any): String
    suspend fun buildBody(metaData: PluginMailMetaData, data: Any, document: MailTemplateDocument): String
    suspend fun buildAttachment(metaData: PluginMailMetaData, data: Any, document: MailTemplateDocument): AttachmentDocument?
}
```

**Các Plugin có sẵn:**

| Plugin Name | Mô tả | Attachment |
|-------------|-------|------------|
| `PluginMailTemplateBasic` | Template email cơ bản | Không |
| `PluginMailTemplateQRCode` | Template với QR code cho vé điện tử | PDF Ticket |
| `PluginMailTemplateOfflinePayment` | Template cho thanh toán offline | Không |
| `PluginMailTemplateOTP` | Template gửi mã OTP | Không |
| `PluginMailTemplateTicketSlots` | Template thông báo slot vé | Không |

**PluginMailMetaData:**
```kotlin
data class PluginMailMetaData(
    val endPoint: String,      // https://metaticket.com.vn
    val supporter: String,     // support@metaticket.com.vn
    val hotline: String,       // 1900 3392
    val getImage: (suspend (url: String) -> String)? = null,
    val stubProvider: ((KClass<*>) -> Any)? = null
)
```

**MailTemplateDocument (MongoDB):**
```kotlin
data class MailTemplateDocument(
    val id: String,
    val templateName: String,
    val templateBody: String,     // HTML template với placeholders
    val templateSubject: String,
    val attachment: AttachmentDocument? = null,
    // ...
)
```

---

### ZNS Template Plugins

**Interface:** `PluginZNS`

```kotlin
interface PluginZNS {
    suspend fun buildTemplateData(metadata: ZaloMessageMetadata, data: Map<String, String>): Map<String, String>
    suspend fun buildSMSTemplateMsg(metadata: ZaloMessageMetadata, data: Map<String, String>): String
}
```

**Các Plugin có sẵn:**

| Plugin Name | Mô tả | SMS Template |
|-------------|-------|--------------|
| `PluginZNSBasic` | ZNS template cơ bản | Có (sms.template.json) |
| `PluginZNSOTP` | ZNS cho gửi OTP | Có |

**ZaloMessageMetadata:**
```kotlin
data class ZaloMessageMetadata(
    val ref_id: String,           // Unique request ID
    val plugin: String,           // Plugin name (e.g., "PluginZNSBasic")
    val template_id: String,      // Zalo template ID
    val phone: String,            // Số điện thoại người nhận
    val failover: Boolean = false // Cho phép gửi SMS khi Zalo fail
)
```

---

### Cách tạo Plugin mới

**Bước 1: Tạo class implement interface**

```kotlin
// PluginMailTemplate example
class PluginMailTemplateMyCustom : PluginMailTemplate {
    override suspend fun buildSubject(data: Any): String {
        return "Thông báo từ MetaTicket"
    }

    override suspend fun buildBody(metaData: PluginMailMetaData, data: Any, document: MailTemplateDocument): String {
        // Xử lý data và thay thế placeholders trong document.templateBody
        return document.templateBody
    }

    override suspend fun buildAttachment(metaData: PluginMailMetaData, data: Any, document: MailTemplateDocument): AttachmentDocument? {
        return null
    }
}
```

**Bước 2: Tạo file service descriptor**

```
src/main/resources/META-INF/services/metaticket.plugin.mailtemplate.PluginMailTemplate
```

Nội dung:
```
metaticket.plugin.mycustom.PluginMailTemplateMyCustom
```

**Bước 3: Build và deploy**

```bash
# Build JAR và copy vào thư mục plugin
./gradlew :plugin.mailtemplate.mycustom:build
cp build/libs/plugin.mailtemplate.mycustom.jar ../service.notification/plugin/mail_template/
```

---

## gRPC Services

### Cấu hình

| Environment | Port |
|-------------|------|
| Production | 3108 |
| Staging | 4108 |

### 1. MailNotificationService

**Proto:** `mail_service.proto`

```protobuf
service MailNotificationService {
  rpc SendMailOrder(SendMailOrderRequest) returns (google.protobuf.Empty);
  rpc SendMailOTP(SendMailOTPRequest) returns (google.protobuf.Empty);
}
```

**SendMailOrderRequest:**
```protobuf
message SendMailOrderRequest {
  string req_id = 1;
  OrderSummary order_summary = 2;
  string mail_plugin = 3;        // Tên plugin: PluginMailTemplateQRCode
  string mail_template = 4;      // Template ID từ MongoDB
}

message OrderSummary {
  string order_code = 1;
  string email = 2;
  string show_name = 3;
  string show_date = 4;
  string seat_info = 5;
  // ...
}
```

**SendMailOTPRequest:**
```protobuf
message SendMailOTPRequest {
  string req_id = 1;
  string email = 2;
  string otp = 3;
  string mail_plugin = 4;
  string mail_template = 5;
}
```

---

### 2. ZaloNotificationService

**Proto:** `zns.service.proto`

```protobuf
service ZaloNotificationService {
  rpc SendZaloMessage(SendZaloMessageRequest) returns (ZaloMessageResponse);
}
```

**SendZaloMessageRequest:**
```protobuf
message SendZaloMessageRequest {
  ZaloMessageMetadata metadata = 1;
  map<string, string> template_data = 2;
}

message ZaloMessageMetadata {
  string ref_id = 1;
  string plugin = 2;           // PluginZNSBasic, PluginZNSOTP
  string template_id = 3;      // Zalo template ID
  string phone = 4;
  bool failover = 5;           // Gửi SMS khi Zalo fail
}
```

**ZaloMessageResponse:**
```protobuf
message ZaloMessageResponse {
  int32 code = 1;              // 0 = thành công
  string message = 2;
  string data = 3;
}
```

---

### 3. JobExecutorService

**Proto:** `jobrunr_service.proto` (từ service.jobrunr)

```protobuf
service JobExecutorService {
  rpc executeJob(ExecuteJobRequest) returns (google.protobuf.Empty);
}
```

**Chức năng:** Xử lý các job được schedule để gửi lại email khi thất bại. Loại job được định nghĩa trong enum `NotificationJobType`:

| Job Type | Mô tả |
|----------|-------|
| `ResendMailOrder` | Gửi lại email xác nhận đơn vé (ticket order) khi lần gửi trước thất bại |
| `ResendMailAccessoryOrder` | Gửi lại email xác nhận đơn phụ kiện/sản phẩm (accessory order) khi lần gửi trước thất bại |

---

## REST API

### Base URL

| Environment | URL |
|-------------|-----|
| Development | http://localhost:3108 |
| Staging | https://api-staging.metaticket.vn |
| Production | https://api.metaticket.vn |

### Endpoints

#### GET /mail/templates
Lấy danh sách tất cả mail templates từ MongoDB.

**Response:**
```json
[
  {
    "id": "template_001",
    "templateName": "Order Confirmation",
    "templateBody": "<html>...</html>",
    "templateSubject": "Xác nhận đơn hàng #{{order_code}}"
  }
]
```

#### GET /mail/template/{id}
Lấy chi tiết một mail template.

#### POST /mail/template
Tạo mail template mới.

**Request Body:**
```json
{
  "templateName": "Order Confirmation",
  "templateBody": "<html>...</html>",
  "templateSubject": "Xác nhận đơn hàng #{{order_code}}",
  "attachment": {
    "ticketPdf": { "filename": "ticket.pdf" }
  }
}
```

#### PUT /mail/template/{id}
Cập nhật mail template.

#### GET /mail/plugins
Lấy danh sách các mail template plugins đã được load.

**Response:**
```json
{
  "plugins": [
    "PluginMailTemplateBasic",
    "PluginMailTemplateQRCode", 
    "PluginMailTemplateOfflinePayment",
    "PluginMailTemplateOTP",
    "PluginMailTemplateTicketSlots"
  ]
}
```

---

## Cấu hình

### File cấu hình chính

`service.notification/conf/application.conf`

```hocon
# Server Configuration
ktor {
    deployment {
        port = 3108
    }
}

# gRPC Server
grpc {
    server {
        port = 3108
    }
    channel {
        qrcodeS {
            host = "localhost"
            port = 3110
        }
        ticketSlotS {
            host = "localhost"
            port = 3115
        }
    }
}

# Mailer Configuration
mailer {
    sender = "info@metaticket.com.vn"
    supporter = "support@metaticket.com.vn"
    hotline = "1900 3392"
    endPoint = "https://metaticket.com.vn"
}

# Mail Processor
mailProcessor {
    maxProcessor = 5
}

# ZNS Configuration
zns {
    appId = "${ZNS_APP_ID}"
    secretKey = "${ZNS_SECRET_KEY}"
    sendMessageUrl = "https://api-fns.fpt.work/api/send-message"
}

# Database
mongodb {
    connectionString = "${MONGODB_CONNECTION_STRING}"
    dbName = "metaticket_notification"
}

# Secret Key (16-bit)
secretKey = "${SECRET_KEY}"
```

### Các biến môi trường

| Variable | Mô tả |
|----------|-------|
| `MONGODB_CONNECTION_STRING` | MongoDB connection string |
| `ZNS_APP_ID` | Zalo API App ID |
| `ZNS_SECRET_KEY` | Zalo API Secret Key |
| `SECRET_KEY` | Secret key cho encryption |

---

## Bảo mật

### 1. Email Security

- **SMTP Authentication**: Username/password được cấu hình trong mailer config
- **SSL/TLS**: Hỗ trợ SSL/TLS cho SMTP connection
- **Timeout**: Có cấu hình timeout cho email sending

### 2. ZNS Security

- **App ID + Secret Key**: Authentication với Zalo API qua HTTP headers
- **HTTPS**: Giao tiếp qua HTTPS
- **Request Logging**: Log request/response để debug

### 3. HTTP Client Security

```kotlin
val trust = object : X509TrustManager {
    override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {}
    override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {}
    override fun getAcceptedIssuers(): Array<X509Certificate>? = null
}
```

⚠️ **Cảnh báo**: Hiện tại HTTP client accept tất cả certificates (dev only). Cần config proper trust manager cho production.

### 4. CORS Configuration

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

⚠️ **Cảnh báo**: CORS hiện cho phép tất cả origins (`*`). Cần restrict cho production.

### 5. Secret Key Management

- Secret key được load từ environment variables
- Sử dụng cho encryption/decryption (16-bit)
- Khác nhau giữa các môi trường

---

## Cache Layer

### 1. JobRequestCache

**Chức năng:** Cache trạng thái của các job đang xử lý

```kotlin
class JobRequestCache {
    fun add(uuid: UUID, job: JobRunning)
    fun getAndRelease(uuid: UUID): JobRunning
}
```

**JobRunning:**
```kotlin
data class JobRunning(
    val htmlJob: CompletableDeferred<Boolean>  // Deferred result cho async operation
)
```

### 2. MailTemplateCache

**Chức năng:** Cache mail templates từ MongoDB để giảm database queries

```kotlin
class MailTemplateCache(private val dbService: MailTemplateDBService) {
    fun get(templateId: String): MailTemplateDocument?
    fun invalidate(templateId: String)
}
```

### 3. ImageCache

**Chức năng:** Cache images được nhúng trong email templates

```kotlin
class ImageCache(private val httpClient: HttpClient) {
    suspend fun getImage(url: String): String  // Returns base64 hoặc URL
}
```

---

## Database

### MongoDB Collections

#### mailtemplate Collection

**Schema:**
```json
{
  "_id": "ObjectId",
  "id": "String",
  "templateName": "String",
  "templateBody": "String",       // HTML với placeholders
  "templateSubject": "String",
  "attachment": {
    "ticketPdf": {
      "filename": "String"
    },
    "files": [
      {
        "filename": "String",
        "data": "Binary"
      }
    ]
  }
}
```

**Codec Registry:**
- PojoCodecProvider cho POJO mapping
- EnumCodecProvider cho Enum serialization

---

## Monitoring

### Hiện tại chưa có

Theo tài liệu hiện tại, các tính năng monitoring sau cần được implement:

- [ ] Health check endpoints
- [ ] Metrics collection (Prometheus, Datadog, etc.)
- [ ] Distributed tracing (Jaeger, Zipkin)
- [ ] Error alerting (Slack, PagerDuty)

### Log Levels

- **DEBUG**: Chi tiết request/response
- **INFO**: Thông tin chung (order processed, mail sent)
- **WARN**: Cảnh báo (channel full, service stopping)
- **ERROR**: Lỗi (send fail, plugin not found)

### Logging Format

```kotlin
format { call ->
    val status = call.response.status()
    val httpMethod = call.request.httpMethod.value
    val userAgent = call.request.headers["User-Agent"]
    val params = call.request.queryParameters.formUrlEncode()
    val path = call.request.path()
    "$status, $httpMethod $path, $userAgent, $params"
}
```

---

## Dependency Injection (Koin)

### Modules

```kotlin
val appModules = listOf(
    configurationModule,    // App config, ObjectMapper
    mailerModule,           // Mailer, MailClient
    pluginModule,           // Plugin services
    httpClientModule,       // HTTP client với security
    grpcChannelModule,      // gRPC stubs (QRCode, TicketSlots)
    grpcServiceModule,      // gRPC service implementations
    controllerModule,       // REST controllers
    cacheModule,            // Caches
    processorModule,        // Processors
    gatewayModule,          // External gateways
    databaseModule          // MongoDB
)
```

---

## Performance Optimization

### 1. Concurrent Processing

- **Semaphore**: Giới hạn 5 concurrent requests cho cả mail và ZNS
- **Channel**: Buffered channel cho message queuing

```kotlin
private val semaphore = Semaphore(maxConcurrentRequests) // maxProcessor = 5
private val messageChannel = Channel<...>(Channel.BUFFERED)
```

### 2. Async Processing

- Kotlin Coroutines cho non-blocking I/O
- CompletableDeferred cho async response

### 3. Caching

- Multi-layer caching: JobRequest, MailTemplate, Images
- Giảm database calls và external requests

### 4. Connection Pooling

- MongoDB connection pooling
- HTTP client connection reuse

---

## Troubleshooting

### Common Issues

| Issue | Possible Cause | Solution |
|-------|----------------|----------|
| Mail not sent | SMTP config wrong | Check mailer config |
| Plugin not found | JAR not in plugin dir | Check plugin/mail_template/ |
| ZNS send failed | Wrong appId/secretKey | Verify ZNS credentials |
| Template not found | Template ID invalid | Check MongoDB mailtemplate |
| Memory full | Channel buffer full | Check maxProcessor config |

### Debug Steps

1. Kiểm tra logs với level DEBUG
2. Verify plugin đã được load thành công
3. Check MongoDB connection
4. Test ZNS API với Postman/cURL
5. Verify SMTP credentials

---

## Appendix

### A. Plugin Directory Structure

```
plugin/
├── mail_template/
│   ├── plugin.mailtemplate.basic/
│   │   ├── libs/
│   │   │   └── plugin.mailtemplate.basic.jar
│   ├── plugin.mailtemplate.qrcode/
│   │   ├── libs/
│   └── ...
└── zns_template/
    ├── plugin.zns/
    │   ├── libs/
    │   │   └── plugin.zns.jar
    └── ...
```

### B. Sample Email Template

```html
<!DOCTYPE html>
<html>
<head>
    <style>
        body { font-family: Arial, sans-serif; }
        .header { background: #1a73e8; color: white; padding: 20px; }
        .content { padding: 20px; }
        .footer { background: #f5f5f5; padding: 10px; text-align: center; }
    </style>
</head>
<body>
    <div class="header">
        <h1>MetaTicket - Xác nhận đơn hàng</h1>
    </div>
    <div class="content">
        <p>Kính chào {{customer_name}},</p>
        <p>Cảm ơn bạn đã đặt vé tại MetaTicket!</p>
        
        <h3>Thông tin đơn hàng:</h3>
        <ul>
            <li>Mã đơn hàng: <strong>{{order_code}}</strong></li>
            <li>Tên sự kiện: {{show_name}}</li>
            <li>Ngày: {{show_date}}</li>
            <li>Ghế: {{seat_info}}</li>
            <li>Tổng tiền: <strong>{{total_price}} VND</strong></li>
        </ul>
    </div>
    <div class="footer">
        <p>Hotline: {{hotline}}</p>
        <p>Email hỗ trợ: {{supporter}}</p>
    </div>
</body>
</html>
```

---

**Tài liệu này được cập nhật lần cuối**: 04/06/2026
**Version**: 1.0
**Tác giả**: MetaTicket Development Team
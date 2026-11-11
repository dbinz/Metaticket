# Tài Liệu Kỹ Thuật - Service Customer User

## Tổng Quan

**Service Customer User** chịu trách nhiệm quản lý toàn bộ thông tin và quy trình liên quan đến người dùng khách hàng trong hệ thống bán vé sự kiện online MetaTicket.

## Mục Đích và Chức Năng

- **Đăng ký tài khoản mới**: Xử lý quy trình đăng ký với xác thực OTP qua Zalo (ZNS) và Email
- **Quản lý thông tin cá nhân**: Lưu trữ và cập nhật thông tin người dùng
- **Reset mật khẩu**: Xử lý quy trình đặt lại mật khẩu với OTP
- **Cung cấp gRPC API**: Để các service khác (đặc biệt là Customer Auth) xác minh thông tin người dùng

## Kiến Trúc và Cấu Trúc

```
service.customer.user/
├── src/main/kotlin/metaticket/customer/user/
│   ├── Application.kt              # Entry point và cấu hình Ktor
│   ├── ApplicationDI.kt            # Dependency injection
│   ├── Launcher.kt                 # Main launcher
│   ├── StartUp.kt                  # Startup logic
│   ├── controller/
│   │   └── UserCtrl.kt            # REST API controller
│   ├── gateway/
│   │   ├── CaptchaGateway.kt      # Tích hợp reCAPTCHA
│   │   └── NotificationGateway.kt  # Tích hợp Zalo notification & Email
│   ├── model/
│   │   ├── SessionModel.kt        # Session models và enums
│   │   ├── UserModel.kt           # User data models
│   │   └── ZNSConfig.kt           # Zalo notification config
│   ├── repository/
│   │   ├── SessionRepository.kt   # Redis session management
│   │   └── UserRepository.kt      # MongoDB user operations
│   ├── request/
│   │   └── request.kt             # Request DTOs
│   ├── response/
│   │   └── response.kt            # Response DTOs
│   ├── route/
│   │   └── user_routing.kt        # REST API routing
│   ├── service/
│   │   └── CustomerUserServiceImpl.kt # gRPC service implementation
│   └── utils/
│       └── utils.kt               # Utility functions
└── src/grpc/proto/
    ├── customer_user_message.proto # gRPC message definitions
    └── customer_user_service.proto # gRPC service definitions
```

## Mối Liên Hệ Với Service Customer Auth

Service Customer User cung cấp gRPC API để **Service Customer Auth** và các service khác có thể xác minh thông tin người dùng.

**Luồng chính**: Khi người dùng đăng nhập qua Customer Auth Service, Customer Auth sẽ gọi gRPC đến Customer User Service để xác thực credentials.

Xem chi tiết: [SERVICE_CUSTOMER_AUTH_TECHNICAL_DOCUMENTATION.md](./SERVICE_CUSTOMER_AUTH_TECHNICAL_DOCUMENTATION.md)

## API Endpoints

### REST API Endpoints

**Public Endpoints (Đăng ký người dùng):**

| Method | Endpoint | Mô Tả |
|--------|----------|-------|
| GET | `/public/customer-user/captcha-setting` | Lấy cấu hình CAPTCHA |
| POST | `/public/customer-user/register/init-session` | Khởi tạo session đăng ký |
| POST | `/public/customer-user/register/send-otp` | Gửi OTP đăng ký (phone + email) |
| POST | `/public/customer-user/register/resend-otp` | Gửi lại OTP |
| POST | `/public/customer-user/register/verify-otp` | Xác thực OTP |
| POST | `/public/customer-user/register` | Tạo tài khoản mới |

**Public Endpoints (Reset mật khẩu):**

| Method | Endpoint | Mô Tả |
|--------|----------|-------|
| POST | `/public/customer-user/reset-pass/init-session` | Khởi tạo session reset |
| POST | `/public/customer-user/reset-pass/send-otp` | Gửi OTP reset |
| POST | `/public/customer-user/reset-pass/resend-otp` | Gửi lại OTP reset |
| POST | `/public/customer-user/reset-pass/verify-otp` | Xác thực OTP reset |
| POST | `/public/customer-user/reset-pass` | Đặt lại mật khẩu |

**Protected Endpoints (Yêu cầu JWT từ Customer Auth):**

| Method | Endpoint | Mô Tả |
|--------|----------|-------|
| GET | `/customer-user/profile` | Lấy thông tin profile người dùng |
| POST | `/customer-user/update-profile/init-session` | Khởi tạo session cập nhật profile |
| POST | `/customer-user/update-profile/send-otp` | Gửi OTP cập nhật profile |
| POST | `/customer-user/update-profile/verify-otp` | Xác thực OTP cập nhật profile |
| POST | `/customer-user/update-profile` | Cập nhật thông tin cá nhân |

### gRPC Service

**CustomerUserService** cung cấp các methods sau cho các service khác (đặc biệt là Customer Auth):

| Method | Request | Response | Mô Tả |
|--------|---------|----------|-------|
| `GetCustomerUserById` | GetCustomerUserByIdRequest | CustomerUserProto | Lấy thông tin user theo ID |
| `GetCustomerUserByPhone` | GetCustomerUserByPhoneRequest | CustomerUserProto | Lấy thông tin user theo số điện thoại |
| `VerifyPassword` | VerifyPasswordRequest | CustomerUserProto | Xác thực phone + password |

## Database Schema

### MongoDB Collection: `customer.user`

```kotlin
data class CustomerUserPOJO(
    val phone: String,        // Số điện thoại (unique index)
    val password: String,     // Mật khẩu đã hash (SHA-256 + salt)
    val name: String,         // Tên người dùng
    val email: String,        // Email (index)
    val avatar: String? = null, // Avatar URL (optional)
    @BsonId val _id: ObjectId = ObjectId(),
    val lastModified: Date? = Date(),
    val createdAt: Date? = Date(),
)
```

**Indexes:**
- `phone_1` (unique) - Đảm bảo số điện thoại duy nhất
- `email_1` - Tối ưu tìm kiếm theo email

### Redis Cache Structure

**Session Management:**
- Key pattern: `{SessionType}_session:{sessionId}`
- Values: Session status và metadata (JSON)
- TTL: Configurable per session type

**Session Types:**
- `REGISTERED_USER` - Session đăng ký
- `RESET_PASSWORD` - Session reset mật khẩu
- `UPDATE_PROFILE` - Session cập nhật profile

**Session Status:**
- `CREATED` - Session được tạo
- `OTP_SENT` - OTP đã gửi
- `OTP_VERIFY_FAILED` - Xác thực OTP thất bại
- `OTP_VERIFIED` - OTP đã xác thực thành công

**Session Data:**
```kotlin
data class SessionData(
    val status: SessionStatus,
    val phone: String? = null,
    val email: String? = null,
    val currentTarget: OtpTarget? = null,  // PHONE or EMAIL
    val otp: String? = null,
    val failedCount: Int = 0,
    val expiredTime: Long = 0,
    val isPhoneVerified: Boolean = false,
    val isEmailVerified: Boolean = false
)
```

## Business Logic Flow

### Quy Trình Đăng Ký

```
┌─────────────┐     POST /register/init-session    ┌──────────────────┐
│   Client    │ ─────────────────────────────────▶  │ Customer User    │
│             │   (captchaToken)                    │ Service          │
└─────────────┘                                     └────────┬─────────┘
                                                              │ 1. Validate CAPTCHA
                                                              │ 2. Create sessionId
                                                              │ 3. Save to Redis (CREATED)
                                                              ▼
                                                     ┌──────────────────┐
                                                     │ Set cookie       │
                                                     │ REGISTERED_USER_ │
                                                     │ SessionId        │
                                                     └──────────────────┘
                                                              │
┌─────────────┐     POST /register/send-otp         ▼
│   Client    │ ─────────────────────────────────▶ ┌──────────────────┐
│             │   phone + email                      │ 1. Validate      │
└─────────────┘                                     │    session       │
                                                       │ 2. Check phone  │
                                                       │    not exists   │
                                                       │ 3. Send OTP     │
                                                       │    (Zalo +      │
                                                       │    Email)       │
                                                       │ 4. Update       │
                                                       │    session      │
                                                       └────────┬─────────┘
                                                                │
┌─────────────┐     POST /register/verify-otp          ▼
│   Client    │ ─────────────────────────────────▶ ┌──────────────────┐
│             │   phone + otp                          │ Verify OTP      │
└─────────────┘                                        │ (phone first,   │
                                                       │ then email)     │
                                                       │ Update session  │
                                                       │ isPhoneVerified │
                                                       │ isEmailVerified │
                                                       └────────┬─────────┘
                                                                │
┌─────────────┐     POST /register                  ▼
│   Client    │ ─────────────────────────────────▶ ┌──────────────────┐
│             │   phone + email + password + name   │ 1. Validate     │
└─────────────┘                                     │    session       │
                                                       │ 2. Hash password│
                                                       │ 3. Save to      │
                                                       │    MongoDB      │
                                                       │ 4. Delete       │
                                                       │    session      │
                                                       └──────────────────┘
```

**Chi tiết từng bước:**

1. **Khởi tạo Session**: Client gọi `/register/init-session`
   - Validate reCAPTCHA (nếu enabled)
   - Tạo sessionId, lưu vào Redis với status CREATED
   - Set cookie `REGISTERED_USER_SessionId`

2. **Gửi OTP**: Client gọi `/register/send-otp` với phone + email
   - Kiểm tra phone chưa được đăng ký trong MongoDB
   - Gửi OTP qua Zalo (phone) và Email (email)
   - Cập nhật session với status OTP_SENT

3. **Xác thực OTP**: Client gọi `/register/verify-otp` với từng OTP
   - Xác thực phone OTP trước
   - Sau đó xác thực email OTP
   - Cập nhật session: isPhoneVerified, isEmailVerified

4. **Tạo tài khoản**: Client gọi `/register` với thông tin đầy đủ
   - Kiểm tra session đã verify cả phone và email
   - Hash password với SHA-256 + salt
   - Tạo user trong MongoDB
   - Xóa session

### Quy Trình Reset Mật Khẩu

```
┌─────────────┐     POST /reset-pass/init-session   ┌──────────────────┐
│   Client    │ ─────────────────────────────────▶  │ Customer User    │
│             │   (captchaToken)                    │ Service          │
└─────────────┘                                     └────────┬─────────┘
                                                              │
                                                              ▼
                                                     ┌──────────────────┐
                                                     │ Create session   │
                                                     │ RESET_PASSWORD   │
                                                     └────────┬─────────┘
                                                              │
┌─────────────┐     POST /reset-pass/send-otp        ▼
│   Client    │ ─────────────────────────────────▶ ┌──────────────────┐
│             │   phone                               │ 1. Check phone  │
└─────────────┘                                       │    exists       │
                                                       │ 2. Send OTP     │
                                                       │    via Zalo     │
                                                       │ 3. Update       │
                                                       │    session      │
                                                       └────────┬─────────┘
                                                                │
┌─────────────┐     POST /reset-pass/verify-otp       ▼
│   Client    │ ─────────────────────────────────▶ ┌──────────────────┐
│             │   phone + otp                          │ Verify OTP      │
└─────────────┘                                        │ Update session  │
                                                       │ isPhoneVerified │
                                                       └────────┬─────────┘
                                                                │
┌─────────────┐     POST /reset-pass                 ▼
│   Client    │ ─────────────────────────────────▶ ┌──────────────────┐
│             │   phone + password                   │ 1. Validate     │
└─────────────┘                                       │    session      │
                                                       │ 2. Hash new     │
                                                       │    password     │
                                                       │ 3. Update       │
                                                       │    MongoDB      │
                                                       │ 4. Delete       │
                                                       │    session      │
                                                       └──────────────────┘
```

**Lưu ý**: Sau khi reset password thành công, user phải đăng nhập lại (sử dụng Customer Auth Service).

### gRPC Service Flow (Cho Customer Auth)

Khi Customer Auth Service gọi để xác thực đăng nhập:

```
Customer Auth                   Customer User Service
     │                                  │
     │── gRPC VerifyPassword ─────────▶│
     │   (phone + password)            │
     │                                  │ 1. normalizePhoneNumber(phone)
     │                                  │ 2. findByPhone in MongoDB
     │                                  │ 3. hashPassword(password, salt)
     │                                  │ 4. compare with stored hash
     │                                  │
     │◀── CustomerUserProto ───────────│
     │   (id, phone, name, email)      │
     │     OR                          │
     │◀── UNAUTHENTICATED exception ───│
```

## gRPC Protocol Definitions

### Proto Files

**customer_user_message.proto:**
```protobuf
message CustomerUserProto {
  string id = 1;
  string phone = 2;
  string name = 3;
  string email = 4;
}

message GetCustomerUserByIdRequest {
  string id = 1;
}

message GetCustomerUserByPhoneRequest {
  string phone = 1;
}

message VerifyPasswordRequest {
  string phone = 1;
  string password = 2;
}
```

**customer_user_service.proto:**
```protobuf
service CustomerUserService {
  rpc GetCustomerUserById(GetCustomerUserByIdRequest) returns (CustomerUserProto);
  rpc GetCustomerUserByPhone(GetCustomerUserByPhoneRequest) returns (CustomerUserProto);
  rpc VerifyPassword(VerifyPasswordRequest) returns (CustomerUserProto);
}
```

## Tích Hợp External Services

- **Zalo Notification Service (ZNS)**: Gửi OTP qua Zalo
- **Email Service**: Gửi OTP qua email
- **reCAPTCHA**: Bảo vệ chống spam và bot
- **Redis Cluster**: Quản lý session và cache
- **MongoDB**: Lưu trữ thông tin người dùng
- **Customer Auth Service**: Gọi gRPC để xác thực đăng nhập

## Cấu Hình

| Config | Mô Tả |
|--------|-------|
| `bcrypt.salt` | Salt cho việc hash password |
| `captcha.enable` | Bật/tắt xác thực reCAPTCHA |
| `captcha.captchaGateway` | Loại captcha (recaptcha v2, etc.) |
| `session.expiredTimeInS` | Thời gian hết hạn session (giây) |
| `session.maxOtpAttempts` | Số lần thử OTP tối đa |

## Bảo Mật

1. **Password Security**: Sử dụng SHA-256 với salt để hash password
2. **Phone Normalization**: Chuẩn hóa số điện thoại (hỗ trợ country code 84)
3. **Session Security**: HttpOnly, Secure, SameSite=Lax cookies
4. **OTP Rate Limiting**: Giới hạn số lần thử OTP, xóa session sau khi vượt quá
5. **CAPTCHA Protection**: reCAPTCHA v2 cho các endpoint nhạy cảm (init-session)

## Kết Luận

Service Customer User là thành phần cốt lõi trong việc quản lý người dùng khách hàng của hệ thống MetaTicket. Service này được thiết kế để xử lý toàn bộ lifecycle của người dùng từ đăng ký đến quản lý thông tin cá nhân, đồng thời cung cấp gRPC API để các service khác (đặc biệt là Customer Auth) có thể xác thực và lấy thông tin người dùng một cách hiệu quả.

**Điểm quan trọng**: Customer User Service cung cấp gRPC methods để Customer Auth Service có thể xác thực credentials. Khi đăng nhập, Auth Service gọi `VerifyPassword` để kiểm tra phone + password trước khi tạo JWT token.

---

**Tài liệu này được cập nhật lần cuối**: 04/06/2026
**Version**: 1.0
**Tác giả**: MetaTicket Development Team
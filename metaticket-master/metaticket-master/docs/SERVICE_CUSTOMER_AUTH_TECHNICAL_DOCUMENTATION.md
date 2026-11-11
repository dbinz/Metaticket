# Tài Liệu Kỹ Thuật - Service Customer Auth

## Tổng Quan

**Service Customer Auth** chịu trách nhiệm xử lý xác thực và quản lý phiên đăng nhập cho khách hàng trong hệ thống bán vé sự kiện online MetaTicket.

## Mục Đích và Chức Năng

- **Đăng nhập**: Xác thực thông tin đăng nhập và tạo JWT token
- **Xác thực token**: Kiểm tra tính hợp lệ của JWT token (cho các service khác)
- **Refresh token**: Gia hạn token khi hết hạn
- **Đăng xuất**: Vô hiệu hóa token và session

## Kiến Trúc và Cấu Trúc

```
service.customer.auth/
├── src/main/kotlin/metaticket/customer/auth/
│   ├── Application.kt              # Entry point và cấu hình Ktor
│   ├── ApplicationDI.kt            # Dependency injection
│   ├── JWTUtil.kt                  # JWT utility functions
│   ├── Launcher.kt                 # Main launcher
│   ├── StartUp.kt                  # Startup logic
│   ├── controller/
│   │   └── AuthCtrl.kt            # REST API controller
│   ├── gateway/
│   │   ├── CaptchaGateway.kt      # Tích hợp reCAPTCHA
│   │   └── CustomerUserGateway.kt  # Tích hợp Customer User Service (gRPC)
│   ├── pojo/
│   │   └── request.kt             # Request/Response DTOs
│   ├── repository/
│   │   └── UserTokenRepository.kt # Redis token management
│   ├── response/
│   │   └── response.kt            # Response DTOs
│   └── route/
│       └── auth_routing.kt        # REST API routing
```

## Mối Liên Hệ Với Service Customer User

Service Customer Auth phụ thuộc vào **Service Customer User** để xác thực thông tin đăng nhập. Khi người dùng đăng nhập, Customer Auth gọi Customer User qua gRPC để kiểm tra credentials.

Xem chi tiết: [SERVICE_CUSTOMER_USER_TECHNICAL_DOCUMENTATION.md](./SERVICE_CUSTOMER_USER_TECHNICAL_DOCUMENTATION.md)

## API Endpoints

### REST API Endpoints

**Public Endpoints:**
- `GET /public/customer-auth/captcha-setting` - Lấy cấu hình CAPTCHA
- `POST /public/customer-auth/login` - Đăng nhập
- `POST /public/customer-auth/refresh-token` - Gia hạn token
- `POST /public/customer-auth/logout` - Đăng xuất

**Internal Endpoints:**
- `GET /internal/customer-auth/authenticate` - Xác thực token (cho internal services)

## JWT Token Management

### JWT Structure

```json
{
  "iss": "metaticket-customer-auth",
  "aud": "metaticket-customer",
  "userId": "user_id_here",
  "phone": "user_phone_here",
  "exp": 1234567890
}
```

### Token Types

1. **Access Token**: Thời gian sống ngắn (configurable), dùng cho API calls
2. **Refresh Token**: Thời gian sống dài hơn, dùng để gia hạn access token

## Redis Token Storage

**Token Storage Pattern:**
- Access Token: `customer_user_token:{userId}`
- Refresh Token: `customer_user_refresh_token:{userId}`
- TTL: Configurable per token type
- **Note**: Mỗi user chỉ có một token active tại một thời điểm

## Business Logic Flow

### Quy Trình Đăng Nhập

```
┌─────────────┐     POST /login      ┌──────────────────┐     gRPC VerifyPassword      ┌──────────────────┐
│   Client    │ ──────────────────▶  │ Customer Auth    │ ─────────────────────────▶  │ Customer User    │
│             │   phone + password   │ Service          │   phone + password          │ Service          │
└─────────────┘                      └────────┬─────────┘                               └────────┬─────────┘
                                               │                                               │
                                               │  ┌─────────────────────────────────────────────┘
                                               ▼                                               │
                                    ┌──────────────────┐     ┌──────────────────────────────┘
                                    │ 1. Validate      │     │ 2. Find user by phone
                                    │    CAPTCHA       │     │    Compare password (hash)
                                    │ 2. Call gRPC     │     │    Return CustomerUserProto
                                    │    VerifyPassword│     │    if valid
                                    │ 3. Generate JWT  │
                                    │    tokens        │
                                    │ 4. Save to Redis │
                                    └────────┬─────────┘
                                             │
                                    ┌────────▼─────────┐
                                    │ Return tokens    │
                                    │ to client        │
                                    └──────────────────┘
```

**Chi tiết từng bước:**

1. **Nhận thông tin**: Client gửi phone + password + captchaToken (nếu enabled)
2. **Xác thực CAPTCHA**: Kiểm tra reCAPTCHA (nếu enabled)
3. **Gọi gRPC VerifyPassword**: 
   - Customer Auth Service gọi `CustomerUserGateway.verifyPassword(phone, password)`
   - Customer User Service tìm user trong MongoDB theo phone
   - So sánh password đã hash với password gửi lên
   - Trả về `CustomerUserProto` (id, phone, name, email) nếu thành công
   - Ném exception `UNAUTHENTICATED` nếu thất bại
4. **Tạo token**: Tạo JWT access token và refresh token
5. **Lưu token**: Lưu tokens vào Redis với TTL tương ứng
6. **Trả về**: Gửi access token cho client, set refreshToken cookie

### Quy Trình Refresh Token

```
┌─────────────┐     POST /refresh-token    ┌──────────────────┐
│   Client    │ ────────────────────────▶  │ Customer Auth    │
│             │   (refreshToken cookie)    │ Service          │
└─────────────┘                             └────────┬─────────┘
                                                      │
                                                      │ 1. Decode token
                                                      │ 2. Check expiration
                                                      │ 3. Verify in Redis
                                                      ▼
                                             ┌──────────────────┐
                                             │ Generate new     │
                                             │ tokens           │
                                             │ Save to Redis    │
                                             └────────┬─────────┘
                                                      │
                                             ┌────────▼─────────┐
                                             │ Return new       │
                                             │ access token     │
                                             └──────────────────┘
```

**Chi tiết từng bước:**

1. **Nhận refresh token**: Client gửi refresh token qua cookie
2. **Xác thực refresh token**: 
   - Decode JWT để lấy userId và phone
   - Kiểm tra token chưa hết hạn
   - Kiểm tra token tồn tại trong Redis (`customer_user_refresh_token:{userId}`)
3. **Tạo token mới**: Tạo access token và refresh token mới
4. **Cập nhật Redis**: Thay thế tokens cũ bằng tokens mới
5. **Trả về**: Gửi access token mới cho client, cập nhật refreshToken cookie

### Quy Trình Đăng Xuất

```
┌─────────────┐     POST /logout          ┌──────────────────┐
│   Client    │ ────────────────────────▶  │ Customer Auth    │
│             │   Authorization: Bearer   │ Service          │
└─────────────┘                             └────────┬─────────┘
                                                      │
                                                      │ 1. Decode JWT
                                                      │ 2. Get userId
                                                      │ 3. Delete tokens from Redis
                                                      │ 4. Clear cookie
                                                      ▼
                                             ┌──────────────────┐
                                             │ Return 200 OK    │
                                             └──────────────────┘
```

**Chi tiết từng bước:**

1. **Nhận token**: Lấy token từ Authorization header
2. **Decode JWT**: Lấy userId từ token
3. **Xóa tokens**: Xóa cả access token và refresh token khỏi Redis
4. **Xóa cookie**: Clear refreshToken cookie
5. **Trả về**: Status thành công

### Quy Trình Xác Thực Token (Cho Internal Services)

Các service khác (Order, Ticket, Payment) gọi endpoint này để xác thực user:

```
┌─────────────┐     GET /internal/        ┌──────────────────┐
│   Other     │ ────────────────────────▶  │ Customer Auth    │
│   Service   │   customer-auth/          │ Service          │
│             │   authenticate            │                  │
│             │   Authorization: Bearer   │                  │
└─────────────┘                             └────────┬─────────┘
                                                      │
                                                      │ 1. Decode JWT
                                                      │ 2. Verify signature
                                                      │ 3. Check expiration
                                                      │ 4. Check Redis
                                                      ▼
                                             ┌──────────────────┐
                                             │ 200 OK +         │
                                             │ X-Customer-Phone │
                                             │ X-Customer-User-Id│
                                             │ OR 401 Unauthorized│
                                             └──────────────────┘
```

## Tích Hợp với Customer User Service

### gRPC Communication

**Customer Auth → Customer User:**

Customer Auth sử dụng `CustomerUserGateway` để gọi các gRPC methods:

```kotlin
class CustomerUserGateway(
    private val userStub: CustomerUserServiceCoroutineStub,
) {
    suspend fun verifyPassword(phone: String, password: String): CustomerUserProto {
        return userStub.verifyPassword(
            CustomerUserMessage.VerifyPasswordRequest.newBuilder()
                .setPhone(phone)
                .setPassword(password)
                .build()
        )
    }
}
```

**Các gRPC Methods được sử dụng:**

| Method | Mục đích |
|--------|----------|
| `VerifyPassword` | Xác thực credentials khi đăng nhập |
| `GetCustomerUserById` | Lấy thông tin user theo ID |
| `GetCustomerUserByPhone` | Lấy thông tin user theo số điện thoại |

### Data Flow Example

```
User Login Flow:
Client → Customer Auth Service
├── POST /login (phone + password)
│   └── gRPC call → Customer UserService.VerifyPassword
│       ├── normalizePhoneNumber(phone)
│       ├── findByPhone in MongoDB
│       ├── hashPassword(password) and compare
│       └── return CustomerUserProto if valid
├── Generate JWT tokens (HMAC256)
├── Save to Redis with TTL
└── Return token to client
```

## Cấu Hình và Deployment

### Environment Configuration
- **HTTP Port**: Port chạy service
- **gRPC Client**: Kết nối đến Customer User Service
- **Redis**: Cluster nodes cho token storage
- **JWT**: Secret keys và expiration settings

### JWT Configuration

| Config | Mô Tả |
|--------|-------|
| `jwt.secret` | Secret key cho HMAC256 signing |
| `jwt.issuer` | JWT issuer ("metaticket-customer-auth") |
| `jwt.audience` | JWT audience ("metaticket-customer") |
| `jwt.tokenExpirationInM` | Thời gian hết hạn access token (phút) |
| `jwt.refreshTokenExpirationInH` | Thời gian hết hạn refresh token (giờ) |

### Security Considerations

1. **JWT Security**: HMAC256 signing với secret rotation
2. **Redis Security**: Cluster authentication và encryption
3. **API Security**: CORS configuration và rate limiting
4. **CAPTCHA Protection**: reCAPTCHA integration cho anti-bot
5. **Token Security**: Secure token storage và transmission (httpOnly, secure cookies)

## Integration with Other Services

**Services sử dụng Customer Auth:**
- Service Order: Xác thực customer khi đặt vé
- Service Ticket: Verify ownership của tickets
- Service Payment: Authenticate payment requests
- Frontend Applications: User session management

## Kết Luận

Service Customer Auth là thành phần quan trọng trong kiến trúc bảo mật của hệ thống MetaTicket. Service này đảm bảo rằng chỉ những khách hàng đã được xác thực mới có thể truy cập vào các tính năng của hệ thống, đồng thời cung cấp cơ chế quản lý session hiệu quả thông qua JWT tokens và Redis storage.

**Điểm quan trọng**: Customer Auth phụ thuộc vào Customer User Service để xác thực credentials. Khi đăng nhập, Auth Service gọi User Service qua gRPC để kiểm tra phone + password, sau đó mới tạo JWT token.

Việc tách biệt authentication logic khỏi user management cho phép hệ thống có thể scale và maintain dễ dàng hơn, đồng thời đảm bảo security best practices được áp dụng một cách nhất quán across toàn bộ platform.

---

**Tài liệu này được cập nhật lần cuối**: 04/06/2026
**Version**: 1.0
**Tác giả**: MetaTicket Development Team
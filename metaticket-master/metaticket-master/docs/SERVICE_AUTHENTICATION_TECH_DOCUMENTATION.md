# Tài liệu Kỹ thuật - Authentication Service

## Tổng quan

Authentication Service là một microservice trong hệ thống bán vé sự kiện online MetaTicket, chuyên trách quản lý xác thực và ủy quyền cho các operation user (người dùng nội bộ). Service được xây dựng bằng Kotlin và sử dụng framework Ktor.

## Kiến trúc Tổng thể

### Công nghệ sử dụng
- **Framework**: Ktor Server (Netty)
- **Ngôn ngữ**: Kotlin
- **Database**: MongoDB
- **Cache**: Guava Cache (in-memory)
- **Authentication**: JWT + Session-based
- **Encryption**: RSA (PKCS1Padding), BCrypt
- **Dependency Injection**: Koin
- **JSON Processing**: Jackson

### Cấu trúc thư mục
```
src/main/kotlin/metaticket/authentication/
├── Application.kt              # Cấu hình Ktor server, plugins, routing
├── ApplicationDI.kt           # Dependency injection setup (Koin)
├── DatabaseService.kt         # Database operations (MongoDB)
├── Launcher.kt               # Entry point
├── Route.kt                  # API routing definitions
├── controller/
│   └── AuthenticationController.kt  # Business logic handlers
├── pojo/
│   ├── db/                   # Database models
│   │   └── AuthInformationDocument.kt
│   ├── request/              # Request DTOs
│   │   └── request.kt (RegisterRequest, LoginRequest, RefreshTokenRequest)
│   ├── response/             # Response DTOs
│   │   └── response.kt (LoginResponse, RegisterResponse, RefreshTokenResponse)
│   └── session/              # Session models
│       └── UserSession.kt
├── redis/
│   └── RedisClient.kt        # Token cache (Guava Cache - in-memory)
└── utils/
    ├── BcryptUtils.kt        # Password hashing (BCrypt)
    ├── JWTUtils.kt          # JWT token operations
    ├── RSAEncryption.kt     # RSA encryption (ECB/PKCS1Padding)
    └── Utils.kt             # Utility functions
```

## API Endpoints

### Public Endpoints (Không yêu cầu authentication)

#### 1. Đăng ký người dùng
- **POST** `/public/authentication/register`
- **Request Body**: `RegisterRequest`
  ```json
  {
    "email": "string",
    "password": "string"
  }
  ```
- **Response**: `RegisterResponse`
  ```json
  {
    "userId": "string",
    "token": "string",
    "refresh_token": "string"
  }
  ```
- **Business Logic**:
  1. Validate email không trùng lặp trong MongoDB
  2. Hash password với BCrypt sử dụng salt từ config
  3. Lưu user vào MongoDB collection `auth-information`
  4. Generate JWT access token và refresh token (UUID)
  5. Lưu tokens vào Guava Cache với key là userId
  6. Set session cookie `user_session`
  7. Return tokens cho client

#### 2. Đăng nhập
- **POST** `/public/authentication/login`
- **Request Body**: `LoginRequest`
  ```json
  {
    "email": "string",
    "password": "string"
  }
  ```
- **Response**: `LoginResponse`
  ```json
  {
    "token": "string",
    "refreshToken": "string"
  }
  ```
- **Business Logic**:
  1. Validate email và password (BCrypt verify)
  2. Generate new tokens (JWT access + UUID refresh)
  3. Lưu tokens vào Guava Cache
  4. Set session cookie
  5. Return tokens
- **Lưu ý**: Không gọi `updateUserLoggedIn` trong DB (method tồn tại nhưng không sử dụng)

#### 3. Làm mới token
- **POST** `/public/authentication/refresh-token`
- **Authentication**: Required (session token hoặc Bearer token)
- **Request Body**: `RefreshTokenRequest`
  ```json
  {
    "refreshToken": "string"
  }
  ```
- **Response**: `RefreshTokenResponse`
  ```json
  {
    "token": "string",
    "refreshToken": "string"
  }
  ```
- **Business Logic**:
  1. Validate session token từ header hoặc cookie
  2. Decode JWT để lấy userId
  3. Kiểm tra refresh token trong cache khớp với request
  4. Nếu không khớp: xóa tokens (security - potential token reuse attack)
  5. Nếu khớp: generate new tokens, lưu vào cache
  6. Return new tokens

#### 4. Đăng xuất
- **GET** `/public/authentication/logout`
- **Authentication**: Required
- **Response**: `200 OK`
  ```json
  "Logged out successfully"
  ```
- **Business Logic**:
  1. Decode token để lấy userId
  2. Remove tokens từ cache
  3. Clear session cookie
  4. Return success message

### Protected Endpoints (Yêu cầu authentication)

#### 1. Kiểm tra trạng thái đăng nhập
- **GET** `/internal/authentication/check-login`
- **Authentication**: Required (session hoặc Bearer token)
- **Response**: 
  - **200 OK**: `"OK: Session is valid"` với header `X-OP-User-Id`
  - **401 Unauthorized**: `"Unauthorized: No session token"` hoặc `"Unauthorized: Invalid token"` hoặc `"Unauthorized: Session expired or token mismatch"`
- **Business Logic**:
  1. Lấy token từ session hoặc Authorization header
  2. Decode JWT
  3. Kiểm tra tokens trong cache khớp với token từ request
  4. Nếu không khớp: clear session, return 401
  5. Nếu khớp: append `X-OP-User-Id` header, return 200

#### 2. Lấy thông tin người dùng
- **GET** `authentication/fetch-user-info`
- **Authentication**: Required (session hoặc Bearer token)
- **Response**: `AuthInformationDocument`
  ```json
  {
    "id": "string",
    "email": "string",
    "password": "string (hashed)",
    "loggedIn": boolean,
    "lastModified": "datetime"
  }
  ```
- **Business Logic**:
  1. Validate và decode token
  2. Lấy username (email) từ JWT payload
  3. Query MongoDB để lấy thông tin user
  4. Return user document

## Cơ chế Bảo mật

### 1. Authentication Strategy
Service sử dụng dual authentication strategy:
- **JWT Authentication**: Cho API access và payload chứa user info
- **Session Authentication**: Cho web session management qua Ktor Sessions

### 2. Token Management

#### Access Token (JWT)
- **Algorithm**: HMAC256
- **Claims**:
  - `userId`: MongoDB ObjectId
  - `username`: Email của user
  - `hash`: RSA-encrypted string (userId | timestamp | random)
  - `autoRenew`: Boolean flag
  - `aud`: Audience từ config
  - `iss`: Issuer từ config
  - `exp`: Expiration timestamp
- **Expiration**: 120,000,000 ms (~33.3 giờ)

#### Refresh Token
- **Type**: UUID v4 string
- **Storage**: Guava Cache với key là userId

#### Token Flow
1. **Registration/Login**: Generate cả access và refresh token
2. **Refresh**: Validate refresh token → generate new access + refresh
3. **Check Login**: Validate access token tồn tại trong cache
4. **Logout**: Invalidate tokens trong cache

### 3. Password Security
- **Hashing**: BCrypt
- **Salt**: `........` (fixed salt từ config)
- **Verification**: BCrypt.checkpw()

### 4. RSA Encryption
- **Purpose**: Mã hóa sensitive data trong JWT hash claim
- **Algorithm**: RSA/ECB/PKCS1Padding
- **Key Format**: Base64-encoded X.509 (public) / PKCS8 (private)
- **Hash Content**: `userId|timestamp|random4Chars`

### 5. Session Management
- **Session Cookie**: `user_session` (Jackson serialized)
- **Storage**: Browser cookie
- **Max Age**: Tương đương JWT expiration
- **Serializer**: Custom Jackson serializer

### 6. CORS Configuration
```kotlin
allowHost("*", listOf("http", "https"))
allowOrigins: ["http://localhost", "https://devlocal.metaticket.vn", "https://admin.devlocal.metaticket.vn", "*"]
allowMethod: GET, POST, OPTIONS
allowCredentials: true
allowHeader: ContentType
```

## Database Schema

### AuthInformationDocument
```kotlin
data class AuthInformationDocument(
    @BsonId
    @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String = ObjectId().toHexString(),

    val email: String,
    val password: String,  // BCrypt hashed
    val loggedIn: Boolean = false,
    val lastModified: Date? = null
)
```

### Collection: `auth-information`
- **Purpose**: Lưu trữ thông tin xác thực người dùng
- **Indexes**: `email` (unique)

### DatabaseService Methods
- `registerUser(entity: AuthInformationDocument): String` - Insert và return ID
- `getUserByEmail(email: String): AuthInformationDocument?` - Query by email
- `loadUser(userId: String): AuthInformationDocument?` - Query by ObjectId
- `updateUserLoggedIn(userId: String): AuthInformationDocument?` - Update loggedIn = true
- `updateUserLogout(userId: String): AuthInformationDocument?` - Update loggedIn = false
- **Lưu ý**: `updateUserLoggedIn` và `updateUserLogout` hiện không được gọi trong controller

## Cache Structure (Guava Cache)

### UserAuthTokens
```kotlin
data class UserAuthTokens(
    val accessToken: String,
    val refreshToken: String
)
```

### Cache Configuration
- **Implementation**: Google Guava Cache
- **Max Size**: 100,000 entries
- **Expiration**: Theo `jwt.expiration_time` config (120,000,000 ms)

### Cache Operations
| Method | Description |
|--------|-------------|
| `saveUserTokens(userId, accessToken, refreshToken)` | Lưu tokens |
| `getUserTokens(userId): UserAuthTokens?` | Lấy tokens |
| `removeUserTokens(userId)` | Xóa tokens |
| `doUserTokensExist(userId): Boolean` | Kiểm tra tồn tại |
| `isAccessTokenActive(token): Boolean` | Tìm token trong cache |
| `removeSessionByAccessToken(token): Boolean` | Xóa bằng access token |
| `clearAllTokens()` | Clear toàn bộ cache |

## Dependency Injection

### Koin Modules

1. **configurationModule**: ApplicationEnvironment, ObjectMapper, JsonNodeFactory
2. **redisClientModule**: RedisClient (Guava Cache wrapper)
3. **controllerModule**: AuthenticationController
4. **databaseModule**: MongoDB connection, Collection, DatabaseService

### Key Dependencies Injection
```kotlin
AuthenticationController(
    ktorEnv: ApplicationEnvironment,
    db: DatabaseService,
    jsonMapper: ObjectMapper,
    redisClient: RedisClient
)
```

## Configuration

### application.conf Structure
```hocon
db {
  connection: "mongodb://devlocal.metaticket.vn:47018"
  dbName: "metaticket"
  authInformationColName: "auth-information"
}

ktor {
  deployment {
    port = 4030
  }
  application {
    modules = [metaticket.authentication.ApplicationKt.module]
  }
}

bcrypt {
  salt = ".........."
}

rsaEncypt {
  privateKey: "<base64-encoded-private-key>"
  publicKey: "<base64-encoded-public-key>"
}

jwt {
  secret = "..."
  issuer = "http://devlocal.metaticket.vn"
  audience = "http://devlocal.metaticket.vn"
  realm = "Access to 'authentication'"
  expiration_time = 120000000
}
```

### Port Configuration
- **Default Port**: 4030

## Plugins Cài đặt

| Plugin | Mục đích |
|--------|----------|
| CORS | Cross-origin resource sharing |
| Compression | Response compression |
| Sessions | Cookie-based session management |
| Authentication | JWT và Session authentication |
| ContentNegotiation | Jackson JSON serialization |
| StatusPages | Error handling |
| CallLogging | Request logging |

## Error Handling

- **400 Bad Request**: Invalid input, user exists, invalid credentials
- **401 Unauthorized**: No token, invalid token, session expired, token mismatch
- **500 Internal Server Error**: Token generation failure

## Dependencies (build.gradle.kts)

```
- Ktor Server: 2.3.x
- MongoDB Driver: 4.11.x
- Koin: 3.5.x
- Guava: 31.x
- BCrypt: 0.4
- JWT (auth0): 4.0.x
- Jackson: 2.14.x
- Kotlin Coroutines: 1.7.x
```

## Business Logic Flow Details

### 1. Token Generation (generateTokens)
```
1. Build string: userId + "|" + timestamp + "|" + random(4)
2. RSA encrypt string với public key
3. Generate JWT với claims: userId, username, hash (encrypted), autoRenew
4. Generate UUID cho refresh token
5. Return Pair(accessToken, refreshToken)
```

### 2. Token Extraction (getTokenFromCall)
```
1. Thử lấy từ session: call.sessions.get<UserSession>()?.token
2. Nếu null, thử Authorization header: "Bearer <token>"
3. Return token hoặc null
```

### 3. Security Notes
- Refresh token mismatch → xóa toàn bộ session (security measure chống token reuse)
- Access token không khớp trong cache → clear session
- Token decode exception → clear session

---

**Tài liệu này được cập nhật lần cuối**: 04/06/2026
**Version**: 1.0
**Tác giả**: MetaTicket Development Team
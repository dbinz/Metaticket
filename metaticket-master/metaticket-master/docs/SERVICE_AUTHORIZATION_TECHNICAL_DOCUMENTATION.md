# Tài liệu Kỹ thuật - Authorization Service (Đang không được sử dụng, cần review và làm lại khi apply)

## 1. Tổng quan về chức năng chính

Authorization Service là một microservice trong hệ thống bán vé sự kiện online MetaTicket, chịu trách nhiệm quản lý phân quyền và kiểm soát truy cập dựa trên **Casbin RBAC** (Role-Based Access Control). Service này đảm bảo chỉ những người dùng có quyền hợp lệ mới có thể truy cập vào các tài nguyên và thực hiện các hành động được phép trong hệ thống.

### Chức năng chính:
- **Kiểm tra quyền truy cập**: Xác thực quyền của user với action và resource cụ thể
- **Quản lý user roles**: Gán, thêm, xóa roles cho người dùng
- **Quản lý resource roles**: Gán, thêm, xóa roles cho tài nguyên
- **Khởi tạo policy**: Load và khởi tạo RBAC policies từ file cấu hình
- **Role hierarchy**: Hỗ trợ hierarchy giữa các actions và resources

## 2. Kiến trúc và các thành phần chính

### 2.1. Cấu trúc thư mục

```
service.authorization/
├── src/
│   ├── api/kotlin/metaticket/authorization/
│   │   └── datastructure/
│   │       └── request.kt              # Request DTOs
│   └── main/kotlin/metaticket/authorization/
│       ├── Application.kt              # Entry point và cấu hình Ktor
│       ├── ApplicationDI.kt            # Dependency injection setup
│       ├── StartUp.kt                  # Startup logic
│       ├── controller/
│       │   └── AuthorizationController.kt  # REST API controller
│       ├── adapter/
│       │   └── CasbinMongoAdapter.kt   # MongoDB adapter cho Casbin
│       └── utils/
│           └── Environment.kt          # Configuration utilities
├── conf/
│   ├── rbac_model.conf                 # RBAC model configuration
│   ├── policy.csv                      # Default policies
│   └── application.conf                # Application configuration
└── build.gradle.kts                    # Build configuration
```

### 2.2. Các thành phần chính

#### Controllers
| Controller | Mô tả |
|------------|-------|
| **AuthorizationController** | Xử lý tất cả các API endpoints về authorization |

#### Adapters
| Adapter | Mô tả |
|---------|-------|
| **CasbinMongoAdapter** | Mở rộng từ MongoAdapter của jCasbin, hỗ trợ batch operations và MongoDB transactions |

#### Core Components
| Component | Mô tả |
|-----------|-------|
| **Casbin Enforcer** | Engine xử lý kiểm tra quyền truy cập |
| **MongoAdapter** | Adapter để lưu trữ policies trong MongoDB |

### 2.3. Công nghệ sử dụng

- **Framework**: Ktor (Netty server)
- **Authorization Engine**: jCasbin
- **Database**: MongoDB
- **Dependency Injection**: Koin

## 3. RBAC Model Configuration

### 3.1. Cấu hình Model

**File**: `conf/rbac_model.conf`

```
[request_definition]
r = sub, act, obj

[policy_definition]
p = sub, act, obj

[role_definition]
g = _, _        # User -> Role mapping
g2 = _, _       # Action hierarchy
g3 = _, _       # Resource hierarchy

[policy_effect]
e = some(where (p.eft == allow)) && !some(where (p.eft == deny))

[matchers]
m = g(r.sub, p.sub) && (p.act == '*' || g2(p.act, r.act)) && (p.obj == '*' || g3(p.obj, r.obj))
```

### 3.2. Role Hierarchy

Service hỗ trợ 3 loại role hierarchy:

| Loại | Mô tả | Ví dụ |
|------|-------|-------|
| **g** | Ánh xạ user -> role | user-admin có role "admin" |
| **g2** | Hierarchy giữa các action | "act-crud" bao gồm "act-create", "act-edit", "act-view", "act-delete" |
| **g3** | Hierarchy giữa các resource | "res-root" -> "res-service" -> "res-service-show" -> "res-show" |

### 3.3. Policy Format

```
p = sub, act, obj
p, admin, *, *                    # Admin có quyền làm mọi thứ
p, user, act-view, res-show       # User có quyền xem show
p, editor, act-edit, res-show     # Editor có quyền chỉnh sửa show

g = _, _
g, john, admin                    # John có role admin
g, alice, user                    # Alice có role user

g2 = _, _
g2, act-crud, act-create          # act-crud bao gồm act-create
g2, act-crud, act-edit            # act-crud bao gồm act-edit
g2, act-crud, act-view            # act-crud bao gồm act-view
g2, act-crud, act-delete          # act-crud bao gồm act-delete

g3 = _, _
g3, res-root, res-service         # res-root bao gồm res-service
g3, res-service, res-show         # res-service bao gồm res-show
```

## 4. Các API chính

### 4.1. API kiểm tra quyền (Internal)

#### POST `/internal/authorization/enforcer`
- **Mô tả**: Kiểm tra quyền truy cập của user
- **Request Body**: `EnforcerRequest`
```kotlin
data class EnforcerRequest(
    val sub: String,  // User ID hoặc role
    val act: String,  // Action (ví dụ: "create", "edit", "view")
    val obj: String   // Resource (ví dụ: "res-show", "res-order")
)
```
- **Response**: Boolean (true = cho phép, false = từ chối)

### 4.2. API User Management (Private)

#### POST `/private/authorization/user/onnew`
- **Mô tả**: Gán role mặc định cho user mới
- **Request Body**: `OnNewUserRequest`
```kotlin
data class OnNewUserRequest(
    val user: String  // User ID
)
```
- **Response**: Success message

#### POST `/private/authorization/user/roles/save`
- **Mô tả**: Lưu (update) roles của user
- **Request Body**: `SaveRolesOfUserRequest`
```kotlin
data class SaveRolesOfUserRequest(
    val user: String,           // User ID
    val oldRoles: List<String>, // Roles cũ
    val newRoles: List<String>  // Roles mới
)
```
- **Response**: Success message

#### POST `/private/authorization/user/roles/fetch`
- **Mô tả**: Lấy danh sách roles của user
- **Request Body**: `LoadRolesOfUserRequest`
```kotlin
data class LoadRolesOfUserRequest(
    val user: String  // User ID
)
```
- **Response**: List<String> - Danh sách roles

#### POST `/private/authorization/user/roles/add`
- **Mô tả**: Thêm roles cho user
- **Request Body**: `AddRolesToUserRequest`
```kotlin
data class AddRolesToUserRequest(
    val user: String,        // User ID
    val roles: List<String>  // Danh sách roles cần thêm
)
```
- **Response**: Success message

#### POST `/private/authorization/user/roles/remove`
- **Mô tả**: Xóa roles của user
- **Request Body**: `RemoveRolesFromUserRequest`
```kotlin
data class RemoveRolesFromUserRequest(
    val user: String,        // User ID
    val roles: List<String>  // Danh sách roles cần xóa
)
```
- **Response**: Success message

### 4.3. API Role Management (Private)

#### POST `/private/authorization/role/users/fetch`
- **Mô tả**: Lấy danh sách users của một role
- **Request Body**: `LoadUsersOfRoleRequest`
```kotlin
data class LoadUsersOfRoleRequest(
    val role: String  // Role name
)
```
- **Response**: List<String> - Danh sách user IDs

### 4.4. API Resource Management (Private)

#### POST `/private/authorization/resource/roles/save`
- **Mô tả**: Lưu roles của resource
- **Request Body**: `SaveRolesOfResourceRequest`
```kotlin
data class SaveRolesOfResourceRequest(
    val resource: String,       // Resource ID
    val oldRoles: List<String>, // Roles cũ
    val newRoles: List<String>  // Roles mới
)
```
- **Response**: Success message

#### POST `/private/authorization/resource/roles/fetch`
- **Mô tả**: Lấy roles của resource
- **Request Body**: `LoadRolesOfResourceRequest`
```kotlin
data class LoadRolesOfResourceRequest(
    val resource: String  // Resource ID
)
```
- **Response**: List<String> - Danh sách roles

#### POST `/private/authorization/resource/roles/add`
- **Mô tả**: Thêm roles cho resource
- **Request Body**: `AddRolesToResourceRequest`
```kotlin
data class AddRolesToResourceRequest(
    val resource: String,       // Resource ID
    val roles: List<String>     // Danh sách roles cần thêm
)
```
- **Response**: Success message

#### POST `/private/authorization/resource/roles/remove`
- **Mô tả**: Xóa roles của resource
- **Request Body**: `RemoveRolesFromResourceRequest`
```kotlin
data class RemoveRolesFromResourceRequest(
    val resource: String,       // Resource ID
    val roles: List<String>     // Danh sách roles cần xóa
)
```
- **Response**: Success message

### 4.5. API Role-Resource Management (Private)

#### POST `/private/authorization/role/resources/fetch`
- **Mô tả**: Lấy danh sách resources của một role
- **Request Body**: `LoadResourcesOfRoleRequest`
```kotlin
data class LoadResourcesOfRoleRequest(
    val role: String  // Role name
)
```
- **Response**: List<String> - Danh sách resource IDs

### 4.6. API Policy Management (Private)

#### POST `/private/authorization/policy/init`
- **Mô tả**: Khởi tạo policy từ file CSV
- **Request Body**: Empty
- **Response**: Success message

## 5. Flow xử lý chính

### 5.1. Flow kiểm tra quyền (Enforce Flow)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         ENFORCE AUTHORIZATION FLOW                          │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │    CLIENT SERVICE   │
                              │  Internal Service   │
                              │ (Order, Ticket,     │
                              │  Show, Payment...)  │
                              └──────────┬──────────┘
                                         │
                                         ▼
                         ┌──────────────────────────────┐
                         │ POST /internal/authorization │
                         │         /enforcer            │
                         │                              │
                         │  EnforcerRequest:            │
                         │  - sub: user_id              │
                         │  - act: action               │
                         │  - obj: resource             │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  AuthorizationController     │
                         │  .enforce()                  │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │   Casbin Enforcer            │
                         │                              │
                         │  1. Check user -> role (g)   │
                         │  2. Check action hierarchy   │
                         │     (g2)                     │
                         │  3. Check resource hierarchy │
                         │     (g3)                     │
                         │  4. Match policy             │
                         └──────────────┬───────────────┘
                                        │
                    ┌───────────────────┴───────────────────┐
                    │                                       │
                    ▼                                       ▼
         ┌─────────────────────┐               ┌─────────────────────┐
         │   ALLOW (true)      │               │   DENY (false)      │
         │                     │               │                     │
         │  - Policy matched   │               │  - No policy match  │
         │  - All conditions   │               │  - Role not found   │
         │    satisfied        │               │  - Action not       │
         │                     │               │    permitted        │
         └─────────────────────┘               └─────────────────────┘
```

### 5.2. Flow quản lý User Roles

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      USER ROLE MANAGEMENT FLOW                              │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │       ADMIN         │
                              │  POST /private/     │
                              │  authorization/user │
                              │  /roles/save        │
                              └──────────┬──────────┘
                                         │
                                         ▼
                         ┌──────────────────────────────┐
                         │  AuthorizationController     │
                         │  .saveUserRoles()            │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Validate Request            │
                         │  - Check user exists         │
                         │  - Validate roles            │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ��──────────────────────────────┐
                         │  CasbinMongoAdapter          │
                         │                              │
                         │  1. Remove old roles (g)     │
                         │  2. Add new roles (g)        │
                         │  3. Use MongoDB transaction  │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  MongoDB                     │
                         │  Collection: rbac-policy     │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │   RESPONSE                   │
                         │   Success/Error message      │
                         └──────────────────────────────┘
```

### 5.3. Flow gán role mặc định cho user mới

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    NEW USER ROLE ASSIGNMENT FLOW                            │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │  CUSTOMER USER      │
                              │     SERVICE         │
                              │                     │
                              │  New user created   │
                              └──────────┬──────────┘
                                         │
                                         ▼
                         ┌──────────────────────────────┐
                         │ POST /internal/authorization │
                         │     /user/onnew              │
                         │                              │
                         │  OnNewUserRequest:           │
                         │  - user: user_id             │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  AuthorizationController     │
                         │  .onNewUser()                │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Get Default Role            │
                         │  From config:                │
                         │  defaultUserRole             │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Casbin Enforcer             │
                         │  .addRoleing()               │
                         │                              │
                         │  g, user_id, default_role    │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  MongoDB                     │
                         │  Collection: rbac-policy     │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │   RESPONSE                   │
                         │   Default role assigned      │
                         └──────────────────────────────┘
```

## 6. Database Schema

### 6.1. Collections

| Collection | Mô tả |
|------------|-------|
| **rbac-policy** | Lưu trữ RBAC policies trong MongoDB |

### 6.2. MongoDB Adapter

**Class**: `CasbinMongoAdapter`

**Features**:
- Mở rộng từ `MongoAdapter` của jCasbin
- Hỗ trợ batch operations:
  - `addPolicies(policies)`
  - `removePolicies(policies)`
  - `updatePolicy(oldPolicy, newPolicy)`
- Sử dụng MongoDB transactions để đảm bảo tính atomicity

### 6.3. Cấu hình kết nối

```hocon
db {
    connection = "mongodb://devlocal.metaticket.vn:47018"
    dbName = "metaticket"
    policyCol = "rbac-policy"
}
```

## 7. Cấu hình

### 7.1. Application Configuration

**File**: `conf/application.conf`

```hocon
db {
    connection = "mongodb://devlocal.metaticket.vn:47018"
    dbName = "metaticket"
    policyCol = "rbac-policy"
}

casbin {
    policyFile = "./conf/policy.csv"
    defaultUserRole = "user-role-default"
}

ktor {
    deployment {
        port = 4031
    }
}
```

### 7.2. Cấu hình RBAC Model

**File**: `conf/rbac_model.conf`

```
[request_definition]
r = sub, act, obj

[policy_definition]
p = sub, act, obj

[role_definition]
g = _, _
g2 = _, _
g3 = _, _

[policy_effect]
e = some(where (p.eft == allow)) && !some(where (p.eft == deny))

[matchers]
m = g(r.sub, p.sub) && (p.act == '*' || g2(p.act, r.act)) && (p.obj == '*' || g3(p.obj, r.obj))
```

## 8. Ví dụ Request và Response

### 8.1. Kiểm tra quyền (Enforce)

**Request:**
```json
POST /internal/authorization/enforcer
{
  "sub": "user_123",
  "act": "view",
  "obj": "res-show"
}
```

**Response:**
```json
true
```

### 8.2. Lấy roles của user

**Request:**
```json
POST /private/authorization/user/roles/fetch
{
  "user": "user_123"
}
```

**Response:**
```json
[
  "admin",
  "editor"
]
```

### 8.3. Thêm roles cho user

**Request:**
```json
POST /private/authorization/user/roles/add
{
  "user": "user_456",
  "roles": ["editor", "viewer"]
}
```

**Response:**
```json
{
  "message": "Roles added successfully"
}
```

### 8.4. Gán role mặc định cho user mới

**Request:**
```json
POST /private/authorization/user/onnew
{
  "user": "new_user_789"
}
```

**Response:**
```json
{
  "message": "Default role assigned"
}
```

## 9. Tích hợp với các Service khác

### 9.1. Các Service sử dụng Authorization

| Service | Port | Mục đích sử dụng |
|---------|------|------------------|
| **Order Service** | 3105/4105 | Kiểm tra quyền tạo/cập nhật order |
| **Ticket Service** | 3104/4104 | Kiểm tra quyền truy cập tickets |
| **Show Service** | 3103/4103 | Kiểm tra quyền quản lý show |
| **Payment Service** | 3106/4106 | Kiểm tra quyền xử lý thanh toán |
| **Admin Service** | 3040/4040 | Kiểm tra quyền admin |

### 9.2. Luồng tích hợp

```
┌─────────────────┐     gRPC      ┌─────────────────────┐
│  Order Service  │ ─────────────▶│ Authorization       │
│                 │                │     Service         │
└─────────────────┘                └──────────┬──────────┘
                                              │
                                              │ Enforce
                                              ▼
                                    ┌─────────────────────┐
                                    │   Casbin Enforcer   │
                                    │   + MongoDB         │
                                    └─────────────────────┘
```

## 10. Bảo mật

### 10.1. Cơ chế bảo mật

- **Internal APIs**: Chỉ các service nội bộ mới có thể gọi `/internal/*` endpoints
- **Private APIs**: Yêu cầu xác thực admin để truy cập `/private/*` endpoints
- **Role-based enforcement**: Mọi quyền truy cập đều được kiểm tra qua Casbin Enforcer

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

| Library | Version | Mục đích |
|---------|---------|----------|
| **jcasbin** | - | Core RBAC engine |
| **jcasbin-mongo-adapter** | - | MongoDB adapter cho Casbin |
| **koin-core** | - | Dependency Injection |
| **ktor-server-netty** | - | HTTP Server |
| **mongodb-driver-sync** | - | MongoDB driver |

## 12. Error Handling

### 12.1. HTTP Status Codes

| Status Code | Mô tả |
|-------------|-------|
| 200 | Thành công |
| 400 | Bad Request - Dữ liệu đầu vào không hợp lệ |
| 401 | Unauthorized - Thiếu xác thực |
| 403 | Forbidden - Không có quyền |
| 500 | Internal Server Error - Lỗi hệ thống |

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
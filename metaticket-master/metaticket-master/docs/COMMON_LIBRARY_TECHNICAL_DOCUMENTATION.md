# Tài liệu Kỹ thuật - Common Library & Quy ước chung của service Kotlin

## 1. Tổng quan

Module `kotlin/common` là thư viện dùng chung **rất mỏng** cho các microservice Kotlin của MetaTicket. Nó chỉ chứa vài tiện ích nhỏ (logger, coroutine helper, checksum/mã hóa, Redis Cluster client, chuẩn hóa số điện thoại, sinh OTP). Phần lớn "khung" của một service — dựng Ktor, Koin DI, MongoDB, gRPC, Kafka, xử lý lỗi, response DTO — **không nằm trong `common`** mà được mỗi service tự viết lại theo cùng một khuôn mẫu.

Tài liệu này gồm hai phần:

- **Phần A (mục 2–5):** cấu trúc build Gradle (root project, `buildSrc`) và những gì `common` thực sự cung cấp.
- **Phần B (mục 6–8):** các quy ước mà mọi service Ktor đang tuân theo (Launcher, `application.conf`, DI, port, routing prefix, header định danh, logging, chia sẻ contract giữa service) — để viết service mới hoặc đọc service cũ cho đúng.

| Thành phần | Đường dẫn | Ghi chú |
|-----------|-----------|---------|
| Root build | `kotlin/build.gradle.kts`, `kotlin/settings.gradle.kts` | Version chung, JVM target, test platform, danh sách module |
| Convention build | `kotlin/buildSrc/src/main/kotlin/{versions.kt,scripts.kt}` | Bảng version thư viện + helper export source set |
| Common library | `kotlin/common/` | 7 source set độc lập, 5 được export |

> `service.news` là Spring Boot (không phải Ktor), **không** phụ thuộc `common` và không theo các quy ước ở Phần B. Xem `docs/SERVICE_DEPLOYMENT.md` mục "Service News".

---

## 2. Cấu trúc build Gradle

### 2.1 Root project (`kotlin/build.gradle.kts`)

```kotlin
plugins {
    kotlin("jvm") version Versions.kotlinVs
    kotlin("plugin.serialization") version Versions.kotlinVs apply false
    kotlin("plugin.spring") version Versions.kotlinVs apply false
    id("com.google.protobuf") version Versions.protobufGradlePlugin apply false
}

allprojects {
    group = "metaticket"
    version = "3.1.0"
    repositories { mavenCentral() }
}

subprojects {
    tasks.withType<KotlinCompile> {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_18) }
    }
    tasks.withType<Test> { useJUnitPlatform() }
}
```

Quy ước rút ra:

- **JVM target 18** cho mọi module → bytecode cần **JRE ≥ 18** để chạy.
- **Mọi task test dùng JUnit Platform** (JUnit 5 / kotest / jqwik đều chạy được). Không cần khai báo lại ở từng module.
- Version project mặc định `3.1.0` (một số module tự override `version = ...`, ví dụ `common` là `1.0.0`).
- Repository duy nhất là Maven Central.

### 2.2 Danh sách module (`kotlin/settings.gradle.kts`)

Root project tên `metaticket-kotlin-root`, gồm `common` + các service (`service.show`, `service.ticket`, `service.order`, `service.payment`, `service.accessory`, `service.jobrunr`, `service.authentication`, `service.authorization`, `service.customer.auth`, `service.customer.user`, `service.admin`, `service.qrcode`, `service.ticketslots`, `service.order.datacollector`, `service.editor.seatplanner`, `service.editor.policydesc`, `service.news`) và các plugin con của `service.notification` (`service.notification:plugin.mailtemplate*`, `service.notification:plugin.zns*`).

Thêm service mới = thêm `include("service.<tên>")` vào file này.

### 2.3 `buildSrc` — bảng version và helper

**`versions.kt`** — object `Versions` là nguồn version duy nhất, các module tham chiếu `${Versions.xxx}` thay vì hardcode:

| Hằng | Giá trị | Dùng cho |
|------|---------|----------|
| `kotlinVs` | 2.1.0 | Kotlin plugin + stdlib |
| `ktorVs` | 3.1.1 | Ktor server/client |
| `requestValidationVs` | 2.3.2 | `ktor-server-request-validation` (lệch major với Ktor 3, xem mục 7) |
| `mongoDriverVS` | 4.10.0 | `mongodb-driver-kotlin-coroutine`, `bson`, `bson-kotlinx` |
| `coroutineVs` | 1.10.1 | kotlinx-coroutines |
| `koinVs` | 4.0.2 | Koin DI |
| `grpcVs` / `grpcKotlinVs` / `protobufVs` | 1.71.0 / 1.4.1 / 4.30.1 | gRPC + protobuf |
| `lettuceCoreVs` | 6.5.5.RELEASE | Redis client |
| `jacksonVs` | 2.14.2 | Jackson |
| `jobrunrVs` | 6.2.3 | JobRunr |
| `kafkaVs` | 3.7.1 | Kafka client |
| `logbackVs` | 1.4.8 | Logback |
| `casbinVs` | 1.40.0 | Casbin (`service.authorization`) |
| `jqwikVs` / `kotestVs` / `testcontainersVs` | 1.9.2 / 5.9.1 / 1.20.4 | Test |

**`scripts.kt`** — object `Script` chứa các extension để **xuất một source set thành artifact riêng** mà module khác có thể phụ thuộc:

| Hàm | Tác dụng |
|-----|---------|
| `creatingArtifactsConf(name)` | Tạo configuration consumable `name`, kế thừa `${name}Implementation` (để dependency của source set đi kèm theo) |
| `gettingArtifactsConf(name)` | Như trên nhưng dùng configuration đã có sẵn |
| `registerJar(name)` | Đăng ký task `${name}Jar` đóng gói output của source set thành `build/libs/<thư-mục-module>-<name>-<version>.jar` |
| `exportSourceSet(name)` | `registerJar` + gắn jar vào artifact của configuration `name` + cho task `build` phụ thuộc jar đó |

Đây là cơ chế mà cả `common` lẫn các service dùng để chia sẻ code: một module khai báo source set (ví dụ `logger`, `api`, `grpc`), export nó, rồi module khác phụ thuộc bằng `project(":<module>", "<sourceSet>")`. Kết quả là trên server, thư mục `lib/` của service có các jar kiểu `common-logger-1.0.0.jar`, `service.order-api-3.1.0.jar`... chứ không phải một jar `common` duy nhất.

---

## 3. Module `common` — các source set

`kotlin/common/build.gradle.kts` khai báo 7 source set độc lập (không có code trong `main`, ngoài `package-info.java`):

| Source set | Package | Export? | Dependency riêng |
|-----------|---------|---------|------------------|
| `logger` | `metaticket.common.logger` | ✅ | slf4j-api 2.0.7, logback-classic/core |
| `coroutine` | `metaticket.common.coroutine` | ✅ | kotlinx-coroutines-core |
| `checksum` | `metaticket.common.checksum` | ✅ | (JDK) |
| `redis` | `metaticket.common.redis` | ✅ | lettuce-core, jackson-module-kotlin, `:common:logger` |
| `utils` | `metaticket.common.utils` | ✅ | (JDK) |
| `mongodb` | `metaticket.common.mongodb.codec` | ❌ | bson |
| `lang` | `metaticket.common.lang` | ❌ | bson |

Vì mỗi source set là một jar riêng, service chỉ kéo đúng phần mình cần (ví dụ `service.authentication` chỉ lấy `logger`).

### 3.1 `logger` — `Logging`

**File:** `common/src/logger/kotlin/metaticket/common/logger/Logging.kt`

```kotlin
interface Logging {
    val logger: Logger
        get() = LoggerFactory.getLogger(loggerName())

    fun loggerName(): String = this.javaClass.name
}
```

- Class nào cần log thì implement `Logging` rồi gọi `logger.info(...)`. Tên logger mặc định là tên class.
- Override `loggerName()` để đặt tên tùy ý, ví dụ logger "APPLICATION" trong `Application.kt` của mọi service:
  ```kotlin
  val logger = (object : Logging { override fun loggerName() = "APPLICATION" }).logger
  ```
- Đây là cách log **chuẩn** của dự án (SLF4J + Logback). Không dùng `println`.

### 3.2 `coroutine` — helper `async`/`launch`/`mapAsync`/`mapLaunch`

**File:** `common/src/coroutine/kotlin/metaticket.common.coroutine/coroutine.kt`

| Hàm | Mô tả |
|-----|-------|
| `suspend fun <T> async(b)` / `async(context, b)` | Chạy `b` trong `CoroutineScope(Dispatchers.Unconfined).async`, trả `Deferred<T?>` |
| `suspend fun launch(b)` / `launch(context, b)` | Tương tự với `launch`, trả `Job` |
| `Iterable<T>.mapAsync(transform)` | `map` mỗi phần tử thành `Deferred` (chạy song song) |
| `Iterable<T>.mapLaunch(transform)` | `map` mỗi phần tử thành `Job` |

Ví dụ dùng thực tế (`service.payment/.../StartUp.kt`): `db.loadPaymentByStatus(...).mapLaunch { ... }.joinAll()`.

> ⚠️ **Lưu ý ngữ nghĩa:** mỗi lời gọi tạo một `CoroutineScope` **mới, tách rời** khỏi coroutine gọi nó. Hệ quả:
> - Hủy (cancel) coroutine cha **không** hủy các coroutine con được tạo bằng helper này.
> - Exception trong `launch { }` không lan lên caller (chỉ tới uncaught exception handler) — phải tự `try/catch` + log bên trong block.
> - `Dispatchers.Unconfined` chạy tiếp trên thread của điểm resume — không phù hợp cho việc CPU nặng hoặc blocking I/O (nên truyền `context = Dispatchers.IO` qua overload có `context`).
>
> Đây gần như tương đương `GlobalScope` về vòng đời. Với code mới cần structured concurrency, dùng `coroutineScope { }` / scope của Ktor `Application` thay vì helper này.

### 3.3 `checksum` — `Checksum`

**File:** `common/src/checksum/kotlin/metaticket/common/checksum/Checksum.kt`

| Hàm | Thuật toán | Dùng để |
|-----|-----------|---------|
| `generate(data, secretKey)` | `SHA-256(data + secretKey)`, hex in hoa, đủ 64 ký tự | Ký URL/tham số |
| `check(data, checksum, secretKey)` | So `generate(...) == checksum` | Kiểm chữ ký |
| `encode(data)` / `decode(entity)` | Base64 URL-safe | Mã hóa ID cho URL (không bí mật) |
| `encode(data, encryptionKey)` / `decode(entity, encryptionKey)` | AES/CBC/PKCS5Padding, **IV = chính key**, kết quả Base64 URL-safe | Che ID (ví dụ payment ID trong URL return) |

Service đang dùng: order, payment, ticket, ticketslots, qrcode, order.datacollector, admin, accessory, jobrunr.

> ⚠️ **Giới hạn bảo mật cần biết khi dùng cho mục đích mới:**
> - `encryptionKey` phải dài đúng 16/24/32 byte (AES-128/192/256) — sai độ dài sẽ ném exception lúc runtime.
> - IV cố định (= key) nên cùng một input luôn ra cùng ciphertext (deterministic). Phù hợp để che ID, **không** phù hợp để mã hóa dữ liệu nhạy cảm.
> - `generate` là `SHA-256(data‖secret)`, không phải HMAC; `check` so sánh chuỗi thường (không constant-time). Với tích hợp mới nên cân nhắc `HmacSHA256` + `MessageDigest.isEqual`.
> - `decode` ném `IllegalArgumentException` (Base64 hỏng) hoặc `BadPaddingException` (sai key) — caller phải bắt và trả 400, không để rơi xuống `StatusPages` thành 500.

### 3.4 `redis` — `RedisClusterManager` + `RedisCustomValueCodec`

**Files:** `common/src/redis/kotlin/metaticket/common/redis/RedisClusterManager.kt`, `.../redis/codec/RedisCustomValueCodec.kt`

`RedisClusterManager<V>(nodes: List<String>)` bọc Lettuce `RedisClusterClient`:

- `nodes` dạng `"host:port"`; node parse lỗi bị bỏ qua (có log), nếu không còn node hợp lệ thì ném `IllegalArgumentException` ngay khi khởi tạo.
- `connect(codec?)` — **bắt buộc gọi trước** khi dùng `asyncCommands` (field `_connection` là `lateinit`). Cấu hình:
  - Topology refresh định kỳ 30 giây + bật mọi adaptive refresh trigger.
  - `autoReconnect = true`, `maxRedirects = 16`.
  - `validateClusterNodeMembership(false)` — cho phép kết nối tới node có địa chỉ announce khác địa chỉ seed (cần cho môi trường proxy/NAT như `nginx-docker` hoặc Redis chạy trong Docker với `cluster-announce-ip`).
- `asyncCommands` — trả `RedisClusterAsyncCommands<String, V>` (API bất đồng bộ, dùng với `.await()` của coroutines).
- `shutdown()` — đóng connection + client, lỗi chỉ được log.

`RedisCustomValueCodec(jsonMapper)` là `RedisCodec<String, Any>`: key là chuỗi (charset mặc định của JVM — từ JDK 18 mặc định là UTF-8), value được serialize bằng Jackson thành JSON; khi đọc trả về `Any` (thường là `LinkedHashMap`/`List`) nên caller phải tự `convertValue` sang kiểu cụ thể. Value `null` được lưu dưới dạng mảng byte rỗng.

Service đang dùng: `service.ticket` (`redis/RedisAsyncClient.kt`, value JSON), `service.customer.auth` (token khách hàng + vote, value String), `service.customer.user`, `service.ticketslots`.

> Lưu ý thiết kế: payload JSON trong Redis gắn chặt với class Kotlin của service ghi. Thêm field mới vào class đọc/ghi Redis phải tương thích ngược (class đọc nên `@JsonIgnoreProperties(ignoreUnknown = true)`), vì khi rollback, bản cũ sẽ đọc dữ liệu do bản mới ghi.

### 3.5 `utils` — số điện thoại và OTP

**`phone.utils.kt` — `normalizePhoneNumber(phoneNumber, countryCode = "84"): String?`**

Chuẩn hóa về dạng `[mã quốc gia][số quốc gia]`, không có `+`:

| Input | Output |
|-------|--------|
| `"0912 345 678"` | `"84912345678"` |
| `"+84912345678"` / `"84912345678"` | `"84912345678"` |
| `"912345678"` | `"84912345678"` (coi là số nội địa) |
| `"+1555..."` (khác mã 84) | `null` |
| `""`, `"0"`, chứa ký tự không phải số | `null` |

Đây là **định dạng chuẩn để lưu và so sánh SĐT** trong toàn hệ thống (customer-auth gắn `X-Customer-Phone` đã chuẩn hóa; customer-user lưu SĐT theo dạng này). Code mới so khớp SĐT phải chuẩn hóa cả hai phía bằng hàm này. Hàm không kiểm tra độ dài số.

**`otp.generator.utils.kt` — `OTPGenerator.generateOTP(length = 6): String`** — chuỗi chữ số ngẫu nhiên dùng `SecureRandom`. Hàm chỉ sinh mã; thời hạn, lưu trữ, giới hạn số lần thử do service gọi tự quản lý.

### 3.6 `mongodb` và `lang` — có code nhưng không export

- `mongodb`: `EnumCodec`/`EnumCodecProvider` (lưu enum dưới dạng `name()`), `DoubleArrayCodec`.
- `lang`: `LangCode` (EN/VI/ZH), `LangPojo`.

Hai source set này **không** được `exportSourceSet` nên không service nào phụ thuộc được. Các service đang dùng `org.bson.codecs.EnumCodecProvider` có sẵn của MongoDB driver (xem `databaseModule` trong `ApplicationDI.kt` của từng service). Nếu muốn dùng lại, phải thêm `creatingArtifactsConf` + `exportSourceSet` cho source set đó.

---

## 4. Cách service phụ thuộc `common`

Khai báo trong `build.gradle.kts` của service:

```kotlin
dependencies {
    implementation(project(":common", "logger"))
    implementation(project(":common", "coroutine"))
    implementation(project(":common", "checksum"))
}
```

Plugin của `service.notification` dùng `compileOnly(project(":common", "logger"))` vì jar `common-logger` đã có sẵn trên classpath của service host khi plugin được nạp. `service.order` khai báo `"apiImplementation"(project(":common", "logger"))` vì source set `api` (được service khác dùng lại) cũng cần logger.

Ma trận phụ thuộc hiện tại:

| Service | logger | coroutine | checksum | redis | utils |
|---------|:---:|:---:|:---:|:---:|:---:|
| service.show | ✅ | ✅ | | | |
| service.ticket | ✅ | ✅ | ✅ | ✅ | ✅ |
| service.order | ✅ (api) | ✅ | ✅ | | |
| service.payment | ✅ | ✅ | ✅ | | |
| service.accessory | ✅ | ✅ | ✅ | | |
| service.admin | ✅ | ✅ | ✅ | | |
| service.jobrunr | ✅ | ✅ | ✅ | | |
| service.notification | ✅ | ✅ | | | |
| service.qrcode | ✅ | ✅ | ✅ | | |
| service.ticketslots | ✅ | ✅ | ✅ | ✅ | ✅ |
| service.order.datacollector | ✅ | ✅ | ✅ | | |
| service.authentication | ✅ | | | | |
| service.authorization | ✅ | | | | |
| service.customer.auth | ✅ | | | ✅ | ✅ |
| service.customer.user | ✅ | ✅ | | ✅ | ✅ |
| service.editor.seatplanner | ✅ | | | | |
| service.editor.policydesc | ✅ | | | | |
| notification plugins (otp, ticketslots, zns.basic) | ✅ (compileOnly) | | | | |

Khi sửa `common`, phải build + deploy lại **mọi** service dùng source set đó (jar `common-*.jar` nằm trong `lib/` của từng service, không có cơ chế chia sẻ jar lúc runtime).

---

## 5. Những gì `common` KHÔNG cung cấp

Các phần sau mỗi service tự có một bản riêng (nhiều chỗ là copy gần giống nhau giữa các service):

| Chủ đề | Nằm ở đâu trong service | Ghi chú |
|--------|------------------------|---------|
| Dựng Ktor (CORS, Compression, StatusPages, CallLogging, Jackson) | `Application.kt` | Xem mục 6.2 |
| Koin DI, MongoDB client, codec registry, gRPC server/channel, HttpClient | `ApplicationDI.kt` | Mỗi service có `abstract class DIContext` riêng |
| Kafka producer/consumer | `service.order` (`kafka/order.succeed.producer.kt`), `service.ticketslots` (`kafka/*`) | Chỉ 2 service dùng Kafka |
| JobRunr client | `gateway/JobrunrGateway.kt` từng service, gọi `service.jobrunr` qua gRPC | |
| Exception nghiệp vụ | `exception/` hoặc khai báo cạnh service (ví dụ `OrderCreationException`, `ReservationException`, `CheckoutException`) | Không có base exception chung |
| Response/error wrapper | `pojo/response/` hoặc source set `api` | **Không có envelope chung**; lỗi thường là text thuần (xem 6.8) |
| Đọc header định danh (`X-OP-User-Id`, `X-Customer-User-Id`) | Controller đọc trực tiếp `call.request.headers[...]` | Không có helper chung |

Nếu cần thêm tiện ích dùng chung thật sự (nhiều service cùng cần, không phụ thuộc domain), đặt nó vào một source set mới của `common` theo đúng pattern ở mục 3 thay vì gom vào source set có sẵn — để service không bị kéo theo dependency thừa.

---

## 6. Quy ước chung của một service Ktor

Ví dụ tham chiếu xuyên suốt: `kotlin/service.payment`.

### 6.1 Cấu trúc thư mục

```
service.<tên>/
├── build.gradle.kts
├── conf-sample/                 # Mẫu config (commit vào git)
│   ├── application.conf         # HOCON: port, db, grpc, redis, jwt, jrunr...
│   ├── config.json              # (một số service) cấu hình nghiệp vụ dạng JSON
│   └── logback.xml
├── conf/                        # Config thật của máy đang chạy — bị .gitignore (**/conf)
└── src/
    ├── api/kotlin/...           # DTO/enum chia sẻ cho service khác (export)
    ├── grpc/proto/*.proto       # Proto chia sẻ (export, sinh stub Kotlin)
    ├── main/kotlin/metaticket/<tên>/
    │   ├── Launcher.kt          # main()
    │   ├── Application.kt       # Application.module(): plugin Ktor + routing
    │   ├── ApplicationDI.kt     # Koin modules + DIContext + ApplicationDI
    │   ├── Route.kt             # Khai báo route → controller
    │   ├── StartUp.kt           # Khởi động gRPC server, job nền, hook shutdown
    │   ├── configuration/       # Environment (biến toàn cục), data class map config.json
    │   ├── controller/          # Xử lý HTTP (đọc request, validate, gọi service)
    │   ├── service/             # Logic nghiệp vụ, impl gRPC service
    │   ├── gateway/             # Client gọi service khác (gRPC/HTTP)
    │   ├── database/service/    # Truy cập Mongo (DBService)
    │   ├── pojo/{db,request,response}/
    │   └── cache/
    └── test/kotlin/...
```

### 6.2 Launcher → EngineMain → `Application.module()`

Mọi service Ktor có cùng chuỗi khởi động:

1. `build.gradle.kts`: `application { mainClass.set("metaticket.<tên>.Launcher") }`.
2. `Launcher.main(args)` → `runKtorServer(args)` (định nghĩa trong `Application.kt`) → `EngineMain.main(args)`; lỗi khởi động được log rồi `exitProcess(1)` để systemd restart.
3. `EngineMain` đọc HOCON (`ktor.deployment.port`, `ktor.application.modules = [ metaticket.<tên>.ApplicationKt.module ]`). Có thể chỉ định file bằng tham số `-config=<path>/application.conf` (cách chạy local).
4. `Application.module()` theo thứ tự: `configureEnvironment()` (đọc secret vào object `Environment`) → tạo `ApplicationDI(environment, monitor)` → `install(CORS)` → `install(Compression)` → routing → `StatusPages` → `ContentNegotiation` (Jackson + `JavaTimeModule` + `KotlinModule`) → `CallLogging` (level DEBUG) → `diContext.startUp.start(this)`.
5. `StartUp.start(scope)` khởi động gRPC server (`io.grpc.Server`), đăng ký `ApplicationStopPreparing` để shutdown gRPC và `ApplicationStopped` để đóng `HttpClient`, rồi `launch` các job nền trong scope của `Application`.

`StatusPages` mặc định bắt `Throwable` → log `"unknown exception..."` + trả `500` với body text `"500: InternalServerError"`. Vài service bắt thêm `RequestValidationException`.

### 6.3 Cấu hình: `application.conf` và `config.json`

`application.conf` (HOCON) có các block lặp lại giữa các service:

```hocon
secretKey = "<16 ký tự>"            # khóa AES cho Checksum.encode/decode (service nào dùng)

db {
  connection = "mongodb://<host>:<port>"
  dbName = "metaticket"
  <xxx>ColName = "<collection>"    # tên collection cấu hình được, không hardcode trong code
}

ktor {
  deployment { port = 40xx }
  application { modules = [ metaticket.<tên>.ApplicationKt.module ] }
}

grpc {
  server { port = 41xx }
  channel {
    <peer>S { sid = "<peer>S01", host = "127.0.0.1", port = 41yy }
  }
}

redis { cluster { nodes = ["<host>:<port>", ...] } }   # service dùng Redis
jwt { secret, issuer, audience, realm, ... }            # service phát/kiểm JWT
jrunr { ... }                                           # retry/interval cho job JobRunr
```

- Tên collection Mongo lấy từ `db.<xxx>ColName` trong `ApplicationDI.databaseModule` — đổi collection không cần sửa code.
- Một số service (payment, order, admin) đọc thêm cấu hình nghiệp vụ từ **`./conf/config.json`** (đường dẫn tương đối với **working directory**) và map vào data class `Configuration` bằng Jackson. Thiếu file này service không khởi động được.
- `conf/` bị `.gitignore` (`**/conf` ở gốc repo). Chỉ `conf-sample/` được commit; giá trị trong `conf-sample` là của môi trường staging/dev, **không** được dùng nguyên cho prod. Secret thật chỉ nằm trên server.
- `build.gradle.kts` của 17 service khai báo `main { resources { srcDirs(files("conf")) } }` — tức là nếu máy build có thư mục `conf/`, các file trong đó **bị đóng gói vào jar** của service. Trước khi build để deploy, kiểm tra máy build không có `conf/` local (hoặc kiểm tra nội dung jar) để không mang config/secret của máy dev lên server.

### 6.4 Dependency Injection (Koin)

- Mỗi service có `abstract class DIContext(ktorEnv, monitor) : KoinComponent` tạo **Koin context riêng** bằng `koinApplication { modules(...) }` (không dùng `startKoin` toàn cục), và `class ApplicationDI : DIContext` expose các controller + `StartUp` qua `by inject()`.
- Các Koin module thường gặp: `configurationModule` (ApplicationEnvironment, ApplicationConfig, `Events`, `ObjectMapper`, `Configuration` từ `config.json`), `httpClientModule` (Ktor CIO client, `expectSuccess = false`), `databaseModule` (codec registry + `MongoDatabase` + từng `MongoCollection` gắn `named(<token>)`), `grpcServiceModule`/`grpcChannelModule`, `gatewayModule`, `controllerModule`, `startUpModule`.
- Mongo codec registry: driver default + `EnumCodecProvider` + `PojoCodecProvider.automatic(true)` đăng ký package `metaticket.<tên>.pojo.db` và mọi class có `@BsonDiscriminator` (quét bằng Guava `ClassPath`).

### 6.5 Port

| Loại | Quy ước | Ví dụ (show) |
|------|---------|--------------|
| HTTP (Ktor) | `x0NN` | staging 4003 / prod 3003 |
| gRPC server | HTTP + 100 | staging 4103 / prod 3103 |
| Tiền tố môi trường | `40xx`/`41xx` = staging (và giá trị trong `conf-sample`), `30xx`/`31xx` = production | |

Bảng port HTTP theo `conf-sample` (staging): show 4003, ticket 4004, order 4005, payment 4006, jobrunr 4007, notification 4008, ticketslots 4009, qrcode 4010, editor.policydesc 4011, editor.seatplanner 4012, accessory 4020, authentication 4030, authorization 4031, order.datacollector 4032 (conf-sample đang ghi 3032), admin 4040, news 4041, customer.user 4050, customer.auth 4051. Ngoại lệ gRPC: `service.customer.user` mở gRPC ở **4150** (không theo +100). Port prod tương ứng nằm trong `nginx/prod/metaticket-frags/metaticket-upstream-server` — xem [NGINX_CONFIGURATION.md](./NGINX_CONFIGURATION.md).

Mọi service bind `0.0.0.0` (mặc định Ktor/gRPC). Việc chặn truy cập trực tiếp vào cổng service phụ thuộc firewall của server, không phải code.

### 6.6 Routing prefix

Đường dẫn trong `Route.kt` quyết định nginx sẽ áp lớp xác thực nào. Quy ước:

| Prefix trong Ktor | Ai gọi | Xác thực tại nginx | Ví dụ |
|-------------------|--------|--------------------|-------|
| `/public/<svc>/...` | Ai cũng gọi được | Không | `/public/show/...`, `/public/authentication/login` |
| `/<svc>/...` (không prefix) | Khách hàng đã đăng nhập (storefront) | `auth_request` tới `service.customer.auth`, tiêm `X-Customer-User-Id`, `X-Customer-Phone` | `/payment/start`, `/order/...` |
| `/private/<svc>/...` | Operator portal admin | `auth_request` tới `service.authentication`, tiêm `X-OP-User-Id` | `/private/payment/pg-account/load` |
| `/internal/<svc>/...` | Chỉ service-to-service (HTTP localhost) hoặc auth subrequest của nginx | **Không được expose qua nginx** | `/internal/authentication/check-login`, `/internal/order/gift/create` |
| Path riêng cho cổng thanh toán | VNPay/9Pay/OnePay | Không auth (kiểm chữ ký trong service) | `/payment/ipn/result/<gw>/{pgaCode}`, `/payment/return/result/...` |

Chi tiết ánh xạ `{domain}/api/...` → service xem [NGINX_CONFIGURATION.md](./NGINX_CONFIGURATION.md). Khi thêm route mới phải đặt đúng prefix — đặt nhầm `/private/...` thành `/public/...` là mở API quản trị ra Internet.

### 6.7 Header định danh

- Danh tính người gọi **chỉ** được lấy từ header do nginx đặt sau `auth_request`: `X-OP-User-Id` (operator), `X-Customer-User-Id` + `X-Customer-Phone` (khách hàng). Nginx dùng `proxy_set_header` nên ghi đè giá trị client tự gửi.
- Controller đọc trực tiếp, ví dụ `call.request.headers["X-Customer-User-Id"]?.trim()?.takeIf { it.isNotEmpty() }` rồi trả `401` nếu thiếu (`service.accessory/.../AccessoryController.kt`, `service.authentication/.../AuthenticationController.kt`).
- Vì service tin header này vô điều kiện, cổng HTTP của service không được lộ ra ngoài nginx. Không bao giờ nhận user ID từ body/query cho mục đích phân quyền.

### 6.8 Response và lỗi

- Thành công: `call.respond(HttpStatusCode.OK, <DTO>)`, DTO là data class trong `pojo/response/` hoặc source set `api`, serialize bằng Jackson.
- Lỗi: phần lớn trả `call.respond(<status>, "<thông điệp tiếng Việt>")` — **body là text thuần**, không có envelope JSON chung. Các status hay dùng: 400, 401, 403, 404, 409, 500. Frontend phải đọc body dạng text (storefront có parser riêng cho vài endpoint trả JSON lỗi).
- Một số service có DTO lỗi riêng (ví dụ `CheckoutValidationError` của accessory). Khi thêm endpoint vào service có sẵn, theo đúng kiểu lỗi service đó đang dùng; không tự đặt format mới.
- Exception không bắt sẽ thành `500: InternalServerError` qua `StatusPages` — luôn bắt lỗi parse input (Base64/AES/ObjectId/JSON) và trả 400.

### 6.9 Logging

- Code: implement `metaticket.common.logger.Logging`.
- Runtime: `conf/logback.xml` (mẫu ở `conf-sample/logback.xml`) với `scan="true"` 30 giây; file log ở `logs/application.log` (+ file riêng như `logs/mongodb.log`), rolling theo ngày, 100MB/file, giữ 15 ngày, tổng tối đa 10GB. Thư mục `logs/` tương đối với working directory của service.

### 6.10 Chia sẻ contract giữa service: source set `api` và `grpc`

Service nào được service khác gọi sẽ export hai source set:

```kotlin
sourceSets {
    val api by creating     // DTO, enum, datastructure dùng chung
    val grpc by creating    // *.proto → stub Java/Kotlin
}
val api by gettingArtifactsConf("api")
val grpc by creatingArtifactsConf("grpc")
exportSourceSet(api.name)
exportSourceSet(grpc.name)
```

Service gọi phụ thuộc: `implementation(project(":service.order", "api"))`, `implementation(project(":service.order", "grpc"))`. Proto được sinh bằng plugin `com.google.protobuf` (`protoc-gen-grpc-java` + `protoc-gen-grpc-kotlin`); task `build` phụ thuộc `generateProto`.

Hệ quả khi deploy: nếu đổi DTO trong `api` hoặc field trong proto, các service phụ thuộc có thể phải build lại cùng commit. Proto: chỉ **thêm** field (không đổi số thứ tự, không xóa) để service cũ vẫn đọc được. DTO JSON: field mới nên nullable/có default; class đọc nên bỏ qua field lạ.

---

## 7. Lưu ý và rủi ro đã biết

| Vấn đề | Chi tiết |
|--------|---------|
| Coroutine helper tách scope | `metaticket.common.coroutine.*` không theo structured concurrency (mục 3.2) |
| `Checksum` không phải HMAC, AES IV cố định | Chỉ dùng để che ID/ký URL, không dùng cho dữ liệu bí mật (mục 3.3) |
| `normalizePhoneNumber` dùng `println` trong `catch` | Lệch quy ước logging; nhánh này gần như không chạy tới |
| `mongodb`/`lang` source set không export | Code chết với phần còn lại của hệ thống (mục 3.6) |
| `requestValidationVs = 2.3.2` dùng cùng Ktor 3.1.1 | Lệch major version; nâng cấp Ktor cần kiểm tra lại plugin này |
| `conf/` được khai báo là resource dir | Config máy build có thể bị đóng gói vào jar (mục 6.3) |
| JVM target 18 | Server phải chạy JRE ≥ 18 |
| `DIContext`/`Application.kt` copy giữa các service | Sửa một hành vi chung (CORS, StatusPages, Jackson) phải sửa ở từng service |

---

## 8. Checklist khi tạo service Ktor mới

1. Tạo thư mục `kotlin/service.<tên>/`, thêm `include("service.<tên>")` vào `settings.gradle.kts`.
2. `build.gradle.kts`: plugin `kotlin("jvm")`, `id("application")` (+ `com.google.protobuf`, `kotlin("plugin.serialization")` nếu cần); `mainClass = "metaticket.<tên>.Launcher"`; dependency `project(":common", "logger")` và các source set khác khi thực sự dùng; version thư viện lấy từ `Versions`.
3. Nếu service khác sẽ gọi nó: tạo source set `api`/`grpc` và export (mục 6.10).
4. Chép khung `Launcher.kt` / `Application.kt` / `ApplicationDI.kt` / `Route.kt` / `StartUp.kt` từ một service gần giống; đổi package.
5. Chọn port: HTTP `40NN` (staging) / `30NN` (prod), gRPC = HTTP + 100; kiểm tra không trùng với bảng mục 6.5 và upstream nginx.
6. Tạo `conf-sample/application.conf` + `logback.xml` (không chứa secret thật); thêm `conf/` local để chạy thử.
7. Đặt route đúng prefix `/public|/private|/internal|/<svc>` (mục 6.6); đọc danh tính từ header nginx (mục 6.7).
8. Thêm upstream + location nginx cho cả staging và prod theo [NGINX_CONFIGURATION.md](./NGINX_CONFIGURATION.md); systemd + deploy theo [SERVICE_DEPLOYMENT.md](./SERVICE_DEPLOYMENT.md).
9. Viết test trong `src/test/kotlin` (JUnit Platform đã bật sẵn ở root) và chạy `./gradlew :service.<tên>:test`.

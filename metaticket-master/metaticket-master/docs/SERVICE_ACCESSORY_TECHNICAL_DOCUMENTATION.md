# Tài Liệu Kỹ Thuật - Accessory Service

## 1. Tổng Quan Về Chức Năng Chính

Accessory Service (`kotlin/service.accessory`) là microservice phục vụ luồng **bán sản phẩm/phụ kiện (merchandise)** của storefront ("Sản phẩm" trên `metaticket-home`, route `/san-pham`). Luồng này **độc lập** với luồng đặt vé: có catalog, giỏ hàng, mã giảm giá, đơn hàng, báo cáo và email xác nhận riêng, chỉ tái sử dụng hạ tầng thanh toán (`service.payment`), gửi mail (`service.notification`) và lập lịch (`service.jobrunr`).

Service được viết bằng **Ktor** (giống phần lớn service core), dùng MongoDB (driver Kotlin coroutine), Koin cho DI, gRPC để giao tiếp liên service. Version module: `3.1.0`.

### Chức năng chính:
- **Catalog**: Accessory Show (nhóm sản phẩm gắn với một sự kiện) → Sản phẩm (single-SKU hoặc có biến thể, tối đa 2 chiều thuộc tính) → tồn kho theo từng ô (sản phẩm hoặc biến thể).
- **Vòng đời show**: `Open` / `Waiting` (chờ mở bán) / `Closed`, tự mở bán tại `saleOpenAt` (JobRunr one-shot + sweep dự phòng).
- **Storefront công khai**: banner ghim, danh sách có xếp hạng, tìm kiếm không dấu, chi tiết show theo id/slug, poll tồn kho một sản phẩm, xem lại đơn.
- **Checkout giỏ hàng**: giỏ hàng thuần frontend, toàn bộ dòng hàng gửi trong một request; server định giá lại từ catalog, **giữ kho ngay khi tạo đơn** (reserve-on-create), áp mã giảm giá, tạo payment với `orderType = ACCESSORY`.
- **Hình thức nhận hàng**: nhận tại show (theo buổi), nhận tại điểm META Ticket, giao tận nơi — bật/tắt theo từng show.
- **Mã giảm giá**: `Percentage`, `FixedAmount`, `Tiered`, `EarlyBird`; giới hạn lượt dùng nguyên tử.
- **Vòng đời đơn**: state machine compare-and-set, hết hạn theo TTL, safety-net cho đơn kẹt ở cổng thanh toán, khách tự hủy đơn.
- **Email xác nhận**: gửi đúng một lần qua RPC riêng `SendMailAccessoryOrder` (plugin `accessory_order`), fallback JobRunr, endpoint gửi lại cho operator.
- **Quản trị & báo cáo**: CRUD show/sản phẩm/mã giảm giá, điều chỉnh kho nguyên tử, bảng ghim, danh sách đơn, thống kê, nguồn dữ liệu xuất Excel, bộ lọc báo cáo chia sẻ cho đối tác qua `reportKey`.
- **Đối soát**: ghi bất thường vào collection `accessory-reconciliation` để xử lý thủ công.

## 2. Kiến Trúc Và Các Thành Phần Chính

### 2.1 Kiến Trúc Tổng Thể

```
metaticket-home (storefront)                portal.admin (operator)
   │ /api/public/accessory/*  (ẩn danh)          │ /private/accessory/*  (auth_request → X-OP-User-Id)
   │ /api/accessory/*         (customer auth → X-Customer-User-Id)
   ▼                                              ▼
                     nginx gateway (upstream accessory_service)
                                   │ HTTP (Ktor)
                                   ▼
                          service.accessory ──── MongoDB (accessory-*)
                           │   ▲        ▲
         gRPC CreatePayment│   │gRPC    │gRPC JobExecutorService
                           ▼   │        │ (OpenShow, NotifyPayment* fallback)
                   service.payment       service.jobrunr ◄── ScheduleJob (OpenShow, ResendMailAccessoryOrder)
                   (NotifyPaymentStarted /
                    NotifyPaymentStatusChanged
                    định tuyến orderType=ACCESSORY)
                           │
       gRPC SendMailAccessoryOrder ──► service.notification (plugin PluginMailTemplateAccessoryOrder)
```

- HTTP: staging `4020`, prod `3020` (upstream `accessory_service` trong `nginx/{staging,prod}/metaticket-frags/metaticket-upstream-server`). gRPC server: `4120` trong `conf-sample` (staging); `service.payment` và `service.jobrunr` trỏ tới cổng này qua khối `accessoryS`.
- Service **không** gọi `service.show`: các "buổi" (sessions) của accessory show là dữ liệu do admin nhập và lưu ngay trên `accessory-show` (city/venue/datetime), không tham chiếu session của luồng vé.
- Không tích hợp metasearch, không dùng Kafka, không dùng Redis.

### 2.2 Các Thành Phần Chính

#### Controller
- **AccessoryController** (`controller/AccessoryController.kt`): toàn bộ HTTP handler (public, customer, admin). Route khai báo tại `Route.kt`.

#### Domain services (`service/`)
- **AccessoryOrderService / AccessoryOrderServiceImpl**: checkout, xử lý callback thanh toán, hết hạn, hủy đơn, xem đơn, đơn của tôi, đơn đang chờ, gửi lại email.
- **OrderStateMachine**: bảng chuyển trạng thái + cập nhật compare-and-set trên Mongo.
- **DiscountCodeService / DiscountCodeServiceImpl**: đánh giá, xem trước, giữ (reserve) và hoàn (revert) lượt dùng mã.
- **OrderCodeService**: sinh mã đơn `<PREFIX><YEAR>-<SEQ4>` bằng bộ đếm nguyên tử.
- **ExpirySweepService**: quét đơn quá TTL và đơn kẹt `PaymentProcessing`.
- **AccessoryShowOpenService**: lập lịch/quét mở bán show `Waiting`.
- **OrderNotificationServiceImpl** (gRPC server): nhận callback từ `service.payment` (cùng proto `OrderNotificationService` của `service.order`).
- **AccessoryJobExecutorServiceImpl** (gRPC server): nhận callback từ `service.jobrunr` (`OpenShow`, `NotifyPayment*`).
- **SlugNormalizer / SlugValidator**: chuẩn hóa/kiểm tra slug (cùng quy tắc `service.show`).

#### Database services (`database/`)
- **CatalogDatabaseService**: `accessory-show`, `accessory` (truy vấn storefront, tìm kiếm, ghim, giữ/nhả kho, điều chỉnh kho).
- **OrderDatabaseService**: `accessory-order` (truy vấn, các "claim" exactly-once).
- **DiscountDatabaseService**: `accessory-discount-code` (CRUD admin).
- **ReconciliationDatabaseService**: `accessory-reconciliation` (append-only).
- **AccessoryReportFilterDatabaseService**: `accessory-report-filter`.

#### Gateways (`gateway/`)
- **PaymentGateway**: `PaymentService.CreatePayment` với `orderType = "ACCESSORY"`.
- **AccessoryMailGateway**: `MailNotificationService.SendMailAccessoryOrder`.
- **JobrunrGateway**: `JobrunrService.ScheduleJob` cho `ResendMailAccessoryOrder` và `OpenShow`.

#### Security (`security/`)
- **PublicId**: mã hóa/giải mã id công khai bằng `Checksum` (AES, `secretKey` 16 byte, dùng chung khóa với `service.payment`).
- **RateLimiting**: Ktor `RateLimit` theo IP client cho các endpoint nhạy cảm.

### 2.3 Cấu Trúc Thư Mục
```
service.accessory/
├── build.gradle.kts            # application, mainClass metaticket.accessory.Launcher; resources từ conf/
├── conf-sample/application.conf, logback.xml
└── src/main/kotlin/metaticket/accessory/
    ├── Launcher.kt / Application.kt   # Ktor module: env, rate limit, CORS, routing, status pages, Jackson
    ├── ApplicationDI.kt               # Koin modules: DB, gRPC channel, gateway, service, gRPC server, controller
    ├── StartUp.kt                     # khởi động gRPC server + 2 vòng quét nền
    ├── Route.kt                       # khai báo route /public/accessory, /private/accessory, /accessory
    ├── configuration/Environment.kt   # secretKey
    ├── controller/                    # AccessoryController
    ├── database/                      # Catalog / Order / Discount / Reconciliation / ReportFilter
    ├── gateway/                       # Payment / Mail / Jobrunr
    ├── jobrunr/AccessoryJobType.kt    # OpenShow + shouldScheduleOpenShowJob
    ├── pojo/db/                       # Document + validation
    ├── pojo/request/                  # DTO request + CheckoutValidator
    ├── pojo/response/                 # DTO response + mapper
    ├── security/                      # PublicId, RateLimiting
    └── service/                       # domain services, exceptions
```

### 2.4 Đường Đi Qua nginx

| Đường dẫn phía client | nginx (frag) | Service nhận | Danh tính |
|---|---|---|---|
| `{domain}/api/public/accessory/<x>` | `location /api/public/accessory` → `proxy_pass .../public/accessory/` (`metaticket-public-api`) | `/public/accessory/<x>` | Không xác thực |
| `{domain}/api/accessory/<x>` | trong `location /api` có `auth_request /internal/customer-authenticate` (`metaticket-api`) | `/accessory/<x>` | `X-Customer-User-Id`, `X-Customer-Phone` do nginx ghi đè |
| `admin.{domain}/private/accessory/<x>` | trong `location /private/` có `auth_request /internal/authenticate` (`metaticket-private-api`) | `/private/accessory/<x>` | `X-OP-User-Id` do nginx ghi đè |

## 3. Mô Hình Dữ Liệu (MongoDB)

Kết nối: `mongodb.connectionString` + `mongodb.dbName` (mẫu dùng `metaticket` — cùng database với các service khác, các collection có tiền tố `accessory-`). Codec: POJO tự động cho package `metaticket.accessory.pojo` + `EnumCodecProvider`. Index được tạo trong `init` của từng DB service khi khởi động.

### 3.1 Collections và Index

| Collection | Document | Index |
|---|---|---|
| `accessory-show` | `AccessoryShowDocument` | unique `code`; unique **partial** `slug` (chỉ khi `slug` là string) |
| `accessory` | `AccessoryDocument` | unique compound `(accessoryShowId, sku)` |
| `accessory-order` | `AccessoryOrderDocument` | unique `code`; `(accessoryShowId asc, createdAt desc)`; `(customerUserId, status, createdAt desc)` |
| `accessory-discount-code` | `DiscountCodeDocument` | unique `code` với collation `locale=en, strength=SECONDARY` (không phân biệt hoa thường); `accessoryShowId` |
| `accessory-order-seq` | `OrderSeqDocument` | `_id` = `"<PREFIX><YEAR>"` |
| `accessory-reconciliation` | `ReconciliationEntry` | — |
| `accessory-report-filter` | `AccessoryReportFilterDocument` | unique `reportKey`; `createdAt desc` |

### 3.2 `accessory-show` — AccessoryShowDocument (`pojo/db/accessory.document.kt`)

| Field | Ý nghĩa |
|---|---|
| `_id` (ObjectId) | Id thô — chỉ lộ ra ở API admin; API công khai dùng id đã mã hóa |
| `code` | Mã show duy nhất, **bất biến** sau khi tạo |
| `codePrefix` | Tiền tố mã đơn (mặc định `"AC"`, ví dụ `TF` → `TF2026-0148`) |
| `name`, `description`, `bannerImageUrl` | Thông tin hiển thị |
| `slug` | Canonical slug `[a-z0-9]+(-[a-z0-9]+)*`, ≤ 200 ký tự, tùy chọn (URL `/san-pham/{slug}`) |
| `sessions[]` | `AccessoryShowSession { id, city{id,name,code}, venue, datetime }` — `sessionId()` = `id` hoặc `"<city>|<venue>|<epochMillis>"` khi `id` rỗng |
| `status` | `Open` \| `Waiting` \| `Closed` |
| `saleOpenAt` | Thời điểm mở bán dự kiến (dùng khi `Waiting`) |
| `pickupNote`, `deliveryNote` | Ghi chú nhận tại show / giao tận nơi |
| `pgAccountCode` | Tài khoản cổng thanh toán; `null` = mặc định của `service.payment` |
| `allowedPaymentMethods[]` | Tên enum `PaymentType` (ví dụ `VietQr`, `QrCode`, `InternationalCard`, `DomesticCard`); rỗng = bộ mặc định đủ 4 phương thức |
| `mailPlugin` | Mặc định `PluginMailTemplateAccessoryOrder` |
| `mailTemplateId` | Template mail; `null` → `accessory-order-confirm-vi` |
| `fulfillment` | `AccessoryFulfillmentSettings { enablePickupAtShow=true, enableMetaTicketPickup=false, enableDelivery=false, pickupAtShowSessionIds[], pickupPoints[{id,name,address,note}] }` — `null` ⇒ mặc định trên |
| `pinOrder` | Thứ hạng ghim (1..5), `null`/vắng = không ghim |
| `version`, `createdAt`, `lastModified` | Metadata |

### 3.3 `accessory` — AccessoryDocument

| Field | Ý nghĩa |
|---|---|
| `accessoryShowId` | FK → `accessory-show` |
| `sku` | Duy nhất trong show |
| `name`, `description`, `imageUrl`, `imageUrls[]` | `imageUrl` = thumbnail; `imageUrls` = gallery cho sản phẩm single-SKU |
| `price` | VND, `> 0`; mọi biến thể dùng chung giá (phase 1) |
| `currency` | Mặc định `VND` |
| `stock` | Tồn **còn bán được** cho sản phẩm single-SKU; `-1` = không giới hạn |
| `variants[]` | `AccessoryVariant { key, label, stock, attr1?, attr2?, imageUrl (bắt buộc), active=true }` |
| `attr1Name`, `attr2Name` | Tên 2 chiều thuộc tính (fallback "Thuộc tính 1/2") |
| `minOrderQty`, `maxOrderQty` | Giới hạn số lượng **mỗi đơn** (cộng mọi biến thể của sản phẩm) |
| `maxPerUserQty` | Giới hạn **trọn đời mỗi khách** (đơn `PaymentSucceed` + đơn đang giữ `Created/WaitingForPayment/PaymentProcessing`) |
| `active` | `false` ⇒ ẩn khỏi storefront, từ chối ở checkout |

Ràng buộc (`accessory.validation.kt`): `1 <= minOrderQty <= maxOrderQty <= maxPerUserQty` (khi có giá trị); key biến thể không rỗng, không trùng; `stock >= -1`.

> **Ngữ nghĩa `stock`**: do mô hình reserve-on-create, `stock` là số lượng **còn có thể bán** sau khi đã trừ các đơn đang giữ, không phải tồn vật lý.

### 3.4 `accessory-order` — AccessoryOrderDocument (`pojo/db/accessory.order.document.kt`)

| Field | Ý nghĩa |
|---|---|
| `accessoryShowId`, `paymentId` | FK show, id payment thô của `service.payment` |
| `code` | Mã đơn duy nhất `<PREFIX><YEAR>-<SEQ4>` |
| `status` | `AccessoryOrderStatus` (mục 3.6) |
| `fulfillment` | `PickupAtShow` \| `MetaTicketPickup` \| `Delivery` |
| `showSessionId`, `showSessionLabel` | Snapshot buổi (PickupAtShow khi show có sessions) |
| `pickupPointId/Name/Address` | Snapshot điểm nhận (MetaTicketPickup) |
| `deliveryAddress` | `{ address, ward, city, note? }` (Delivery; địa chỉ sau sáp nhập, không có quận/huyện) |
| `lines[]` | `AccessoryOrderLine { accessoryId, sku, name, variantKey?, variantLabel?, unitPrice, quantity }` — snapshot lúc checkout |
| `subtotal` | `Σ unitPrice × quantity` |
| `discount` | `{ code, type, amount, discountCodeId }` |
| `total` | `max(0, subtotal − discount.amount)` |
| `customerName/Phone/Email` | Thông tin người nhận |
| `customerUserId` | Chủ đơn (từ `X-Customer-User-Id`); `null` ở đơn cũ |
| `discountReverted`, `stockReleased` | Cờ exactly-once cho việc hoàn mã / nhả kho |
| `emailDispatched`, `emailDispatchedAt` | Cờ exactly-once gửi email |
| `paymentStartedAt` | Thời điểm chuyển sang `PaymentProcessing` |

### 3.5 Các collection còn lại
- **`accessory-discount-code`** — `DiscountCodeDocument { code (UPPERCASE), accessoryShowId, type, value, rewardType?, maxDiscountAmount?, minOrderAmount, tiers[{minOrderAmount, type, value, maxDiscountAmount?}], applyMode (Manual|Auto), usageLimit?, usedCount, startAt?, endAt?, active }`.
- **`accessory-order-seq`** — `{ _id: "TF2026", seq }`.
- **`accessory-reconciliation`** — `ReconciliationEntry { kind, orderId?, detail, resolved=false, createdAt }`, `kind ∈ DISCOUNT_REVERT_FAILED | STOCK_SHORTFALL | EMAIL_DISPATCH_FAILED | UNROUTABLE_CALLBACK`.
- **`accessory-report-filter`** — `{ reportKey (UUID), reportName, username, status (Active|Inactive), showId, filter{showId, startAt?, endAt? (epoch millis)}, version, lastUpdated, createdAt }`.

### 3.6 Trạng thái đơn hàng và chuyển trạng thái (`service/OrderStateMachine.kt`)

```
                 checkout (insert)
                       │
                       ▼
                   Created ──(total = 0)──────────────────────────► PaymentSucceed
                       │ createPayment OK
                       ▼
               WaitingForPayment ──(khách hủy / TTL)──► PaymentCancelled / Expired
                       │ NotifyPaymentStarted
                       ▼
               PaymentProcessing ──(quá paymentProcessingStaleMinutes)──► Expired
                       │ NotifyPaymentStatusChanged
                       ▼
     PaymentSucceed | PaymentFailed | PaymentCancelled
```

| Từ | Được phép tới |
|---|---|
| `Created` | `WaitingForPayment`, `PaymentSucceed`, `PaymentFailed`, `PaymentCancelled`, `Expired` |
| `WaitingForPayment` | `PaymentProcessing`, `PaymentSucceed`, `PaymentFailed`, `PaymentCancelled`, `Expired` |
| `PaymentProcessing` | `PaymentSucceed`, `PaymentFailed`, `PaymentCancelled`, `Expired` |
| Terminal: `PaymentSucceed`, `PaymentFailed`, `PaymentCancelled`, `Expired` | — (không có cạnh ra) |

- `PRE_TERMINAL = {Created, WaitingForPayment, PaymentProcessing}` — nguồn hợp lệ cho callback thanh toán.
- `EXPIRABLE_STATES = {Created, WaitingForPayment}` — nguồn cho TTL sweep **và** cho khách tự hủy. `PaymentProcessing` chỉ hết hạn qua safety-net riêng.
- Mọi chuyển trạng thái là `findOneAndUpdate` với filter `_id + status ∈ from`, tăng `version`; kết quả `null` = no-op (idempotent khi callback lặp, hoặc trạng thái xung đột).

### 3.7 Trạng thái show

| Trạng thái | Storefront | Checkout |
|---|---|---|
| `Open` | Hiển thị | Cho phép |
| `Waiting` | Hiển thị (xếp trước Open), xem được chi tiết | `422` "Sự kiện chưa mở bán…" |
| `Closed` | Hiển thị (xếp cuối), xem được chi tiết | `422` "Sự kiện đã đóng…" |

**Tự mở bán**: show `Waiting` có `saleOpenAt` sẽ được CAS `Waiting → Open` khi `saleOpenAt <= now` bởi:
1. Job one-shot `OpenShow` trên `service.jobrunr` (khi `accessory.scheduleOpenShowOnJobrunr = true`), chỉ lập lịch khi show mới thành `Waiting` hoặc `saleOpenAt` đổi và còn ở tương lai (`shouldScheduleOpenShowJob`). JobId là UUID mới mỗi lần, `retry = 0`, `delayInSecond` làm tròn lên; khi chạy cho phép sớm 2 giây; luôn trả `STOP`. Job cũ (ngày đã đổi) tự no-op.
2. Vòng quét in-process mỗi `accessory.saleOpenSweepIntervalSeconds` (mặc định 15 giây) để bù job bị lỡ (restart, callback lỗi).

Show `Waiting` không có `saleOpenAt` không bao giờ tự mở.

## 4. Các API

> Đường dẫn là đường dẫn **service nhận**. Lỗi nghiệp vụ trả JSON `{ "message": "..." }` (một số kèm field phụ). Exception không bắt được → `500` text `"500: InternalServerError"`.

### 4.1 Public APIs (`/public/accessory`, không xác thực, không rate limit)

Id show/sản phẩm/đơn trong mọi API công khai là id **đã mã hóa `Checksum`** (`PublicId`); id không giải mã được → `400` (catalog) hoặc `404` (đơn).

#### GET `/public/accessory/show/pin/banner`
- **Mô tả**: Các show đang ghim (rank 1..5) theo `pinOrder`, cho slider `/san-pham`. Không lọc theo trạng thái.
- **Response**: `List<AccessoryShowSummaryResponse>`
  ```kotlin
  data class AccessoryShowSummaryResponse(
      val id: String,               // id show đã mã hóa
      val code: String, val slug: String, val name: String,
      val description: String?, val bannerImageUrl: String?,
      val sessions: List<AccessoryShowSessionResponse>, // { id, city, venue, date(epoch ms) }
      val pickupNote: String?, val deliveryNote: String?,
      val status: String, val saleOpenAt: Long?, val pinOrder: Int?,
      val minPrice: Long?,          // giá thấp nhất trong các sản phẩm active
  )
  ```

#### GET `/public/accessory/show/fetch/all`
- **Query**: `page` (0-based, mặc định `0`), `size` (mặc định `6`, kẹp `1..50`).
- **Thứ tự** (`CatalogDatabaseService.rankShowsForStorefront`): show ghim 1..5 (theo `pinOrder`) → `Waiting` (có `saleOpenAt` sớm nhất trước, không có ngày xếp sau theo `createdAt` mới nhất) → `Open` → `Closed` (Open/Closed: nhiều sản phẩm đã bán (`PaymentSucceed`) hơn trước, rồi `createdAt` mới nhất).
- **Response**: `AccessoryShowListPage { content, number, size, totalElements, totalPages, first, last, empty, numberOfElements }` — chỉ tóm tắt show, không kèm sản phẩm.

#### GET `/public/accessory/show/search`
- **Query**: `keyword` (≤ 100 ký tự, nếu dài hơn → `400`; rỗng → trang rỗng), `page` (0-based), `size` (mặc định `6`, kẹp `1..50`).
- **Hành vi**: regex không phân biệt hoa thường **và dấu tiếng Việt** (mỗi chữ cái mở rộng thành lớp ký tự có dấu), keyword cắt còn 64 ký tự. Nhóm 1 khớp `name`/`code` xếp theo: khớp chính xác → khớp tiền tố → khớp khác, rồi `Open < Waiting < Closed`, rồi mới nhất; nhóm 2 chỉ khớp `description`. Tổng tối đa `SEARCH_MATCH_CAP = 200` kết quả.
- **Response**: `AccessoryShowListPage`.

#### GET `/public/accessory/show/fetch`
- **Query**: `id` (id show mã hóa).
- **Mô tả**: Một show (mọi trạng thái) + các sản phẩm `active` kèm biến thể.
- **Response**: `AccessoryShowDetailResponse { id, code, slug, name, description, bannerImageUrl, sessions, pickupNote, deliveryNote, status, saleOpenAt, allowedPaymentMethods, fulfillment{enablePickupAtShow, enableMetaTicketPickup, enableDelivery, pickupAtShowSessionIds, pickupPoints}, accessories: List<AccessoryResponse> }`
  ```kotlin
  data class AccessoryResponse(
      val id: String,                 // id sản phẩm mã hóa (dùng làm accessoryId khi checkout)
      val sku: String, val name: String, val description: String?,
      val imageUrl: String?, val imageUrls: List<String>,
      val price: Long, val currency: String,
      val stock: Int,                 // single-SKU; -1 = không giới hạn
      val hasVariants: Boolean,
      val attributes: List<AccessoryAttributeDefResponse>, // [{ key: "attr1"|"attr2", name }]
      val minOrderQty: Int?, val maxOrderQty: Int?, val maxPerUserQty: Int?,
      val myPurchasedQty: Int?,       // chỉ khi request có X-Customer-User-Id
      val variants: List<AccessoryVariantResponse>, // { key, label, stock, options{attr1,attr2}, imageUrl, active }
  )
  ```
- **Cache-Control**: không có `X-Customer-User-Id` → `public, max-age=5, stale-while-revalidate=10`; có header → `private, max-age=0, no-cache`.
- **Lỗi**: thiếu id / id không giải mã được → `400`; không có show → `404 "Accessory show unavailable"`.

#### GET `/public/accessory/show/fetch/slug`
- **Query**: `slug` (được chuẩn hóa bằng `SlugNormalizer`: lowercase, bỏ dấu, `đ→d`, ký tự khác `[a-z0-9]` → `-`).
- **Response/Lỗi/Cache**: giống `show/fetch`.

#### GET `/public/accessory/fetch`
- **Query**: `id` (id sản phẩm mã hóa).
- **Mô tả**: Một sản phẩm `active` với tồn kho hiện thời — phục vụ vòng poll tồn kho của popup sản phẩm. `myPurchasedQty` chỉ tính khi sản phẩm có `maxPerUserQty`.
- **Response**: `AccessoryResponse`. Cache-Control như `show/fetch`.
- **Lỗi**: `400` id sai; `404 "Accessory unavailable"` (không có hoặc inactive).

#### GET `/public/accessory/order/fetch`
- **Query**: `id` (id đơn mã hóa).
- **Mô tả**: Màn "Hoàn tất" — xem lại một đơn.
- **Response**: `AccessoryOrderView { id, code, status, customerName, customerPhone, customerEmail, fulfillment, fulfillmentSummary, showSessionId, showSessionLabel, pickupPoint*, deliveryAddress, subtotal, discount{code, amount}, total, lines[{display, name, variantLabel, quantity, unitPrice, lineTotal}] }`. `display` dạng `"Áo thun · Size M ×2"` (bỏ nhãn biến thể với single-SKU).
- **Lỗi**: id thiếu/sai/không có đơn → `404`.

#### GET `/public/accessory/report/{reportKey}`
- **Mô tả**: Báo cáo doanh số chia sẻ cho đối tác (không xác thực), chỉ với bộ lọc `Active`.
- **Response**: `PublicSalesReportResponse { reportKey, reportName, showId, showCode, showName, startAt, endAt, summary{totalOrders, paidOrders, unitsSold, grossSales, discountTotal, revenue, byStatus}, byProduct[{accessoryId, sku, name, unitPrice, quantity, grossSales}] }`.
- **Lỗi**: `400` thiếu key / filter không có showId hợp lệ; `404` không có hoặc `Inactive`.

#### GET `/public/accessory/report/{reportKey}/orders`
- **Mô tả**: Nguồn dữ liệu Excel cho đối tác: toàn bộ sản phẩm của show + đơn `PaymentSucceed` trong khoảng thời gian của filter.
- **Response**: `PublicOrderExportResponse { products[{accessoryId, sku, name}], orders[{code, customerName, createdAt, discountCode, discountAmount, total, lines[]}] }` — **không** có SĐT/email/thông tin thanh toán.

### 4.2 Customer APIs (`/accessory`, yêu cầu đăng nhập)

Mọi handler đọc header `X-Customer-User-Id` (nginx chèn sau `auth_request`); thiếu → `401 {"message": "Không xác định người dùng"}` trước khi làm bất kỳ việc gì.

**Rate limit** (chỉ cho `checkout`, `discount/validate`, `order/cancel`): limiter `accessory-public`, mặc định **20 request / 60 giây / IP client** (`accessory.rateLimit.*`); vượt → `429 Too Many Requests` + `Retry-After`, handler không chạy. IP lấy theo thứ tự: phần tử đầu của `X-Forwarded-For` → `X-Real-IP` → địa chỉ socket.

#### POST `/accessory/checkout`
- **Request Body**: `CheckoutInput`
  ```kotlin
  data class CheckoutInput(
      val lines: List<CheckoutLine>,   // CheckoutLine { accessoryId (mã hóa), variantKey?, quantity }
      val customerName: String,
      val customerPhone: String,
      val customerEmail: String,
      val discountCode: String? = null,
      val fulfillment: String? = null, // PickupAtShow (mặc định) | MetaTicketPickup | Delivery
      val showSessionId: String? = null,
      val pickupPointId: String? = null,
      val deliveryAddress: DeliveryAddressInput? = null, // { address, ward, city, note? }
  )
  ```
- **Response 200**: `CheckoutResult`
  ```kotlin
  data class CheckoutResult(
      val encodedPaymentId: String, // "" khi total = 0 (đơn 0đ, bỏ qua bước thanh toán)
      val encodedOrderId: String,
      val orderCode: String,        // ví dụ "TF2026-0148"
      val total: Long,
  )
  ```
- **Lỗi validate payload** (`CheckoutValidator`, trước mọi truy vấn DB): body `{ "message": "checkout payload validation failed", "errors": [{ "code", "field", "message" }] }`; `422` nếu mọi lỗi thuộc `INVALID_CUSTOMER_NAME | INVALID_EMAIL | INVALID_PHONE | INVALID_FULFILLMENT`, ngược lại `400`. Mã lỗi: `EMPTY_CART`, `OUT_OF_RANGE_LINES`, `INVALID_QUANTITY`, `INVALID_ACCESSORY_ID`, `INVALID_VARIANT_SELECTION`, `MISSING_CUSTOMER_FIELD`, `INVALID_CUSTOMER_NAME`, `INVALID_EMAIL`, `INVALID_PHONE`, `SHIPPING_NOT_SUPPORTED` (gửi field cũ `shipping`, `shippingAddress`, `shipping_address`, `shippingMethod`, `shipping_method`), `INVALID_FULFILLMENT`.
- **Lỗi nghiệp vụ**: xem mục 8.1. Luồng chi tiết: mục 5.1.

#### POST `/accessory/discount/validate`
- **Request Body**: `DiscountPreviewRequest { discountCode?, lines: List<CheckoutLine> }`.
- **Hành vi**: tính lại `subtotal` từ catalog (kiểm tra tồn tại / active / cùng một show đang `Open`, **không** kiểm tra biến thể/tồn kho), đánh giá mã **không giữ lượt**.
- **Response 200**: `DiscountPreviewResponse { code, type, value, amount, subtotal, total }` (`value` = % / số tiền / giá trị của tier thắng; `total = max(0, subtotal − amount)`).
- **Lỗi `422`**: giỏ rỗng ("cart must contain at least one line"), subtotal ≤ 0, mã rỗng ("discount code does not exist"), mã không áp dụng được ("discount code is not applicable to this cart"). Lỗi catalog giữ nguyên status (`404`/`422`).

#### POST `/accessory/order/cancel`
- **Query**: `id` (id đơn mã hóa).
- **Hành vi**: kiểm tra theo thứ tự giải mã/tải đơn (`404`) → chủ đơn (`403 "you are not allowed to cancel this order"`; đơn cũ không có `customerUserId` luôn bị từ chối) → đã `PaymentCancelled` thì no-op `200` → CAS `EXPIRABLE_STATES → PaymentCancelled` (nếu không được → `409 "order can no longer be cancelled (current=…)"`) → nhả kho + hoàn mã đúng một lần.
- **Response 200**: `{ "message": "order cancelled" }`.

#### GET `/accessory/order/fetch/mine`
- **Mô tả**: Các đơn `PaymentSucceed` của khách (mới nhất trước) cho trang "Quản lý đơn hàng", kèm tên/banner show (show đã xóa → tên "Sự kiện bán hàng").
- **Response**: `List<CustomerAccessoryOrderItem>` (id mã hóa, code, status, showName, bannerImageUrl, createdAt, thông tin khách, fulfillment + summary, lines, tiền).

#### GET `/accessory/order/pending`
- **Mô tả**: Đơn chưa thanh toán mới nhất (`Created`/`WaitingForPayment`) của khách để tiếp tục thanh toán trên trang sản phẩm.
- **Response**: `PendingAccessoryOrderLookupResponse { order: PendingAccessoryOrderResponse? }` với `{ orderId, paymentId?, orderCode, status, showId, showName, total, expiresAt (= createdAt + orderTtlMinutes), customerName, customerPhone, customerEmail }` (id đều mã hóa).

### 4.3 Admin APIs (`/private/accessory`, cho portal admin)

**Xác thực**: chỉ `order/notify/resend` tự kiểm tra `X-OP-User-Id` (thiếu → `401`). Các route admin còn lại **không** kiểm tra header nào, hoàn toàn dựa vào nginx `auth_request` (xem mục 10). API admin dùng **ObjectId thô** cho show/sản phẩm/mã giảm giá/filter (`id`, `showId` là chuỗi hex); id sai định dạng → `400 "Missing or malformed id"`, không tồn tại → `404`.

#### Accessory Show

| Method & Path | Tham số | Mô tả |
|---|---|---|
| GET `/private/accessory/show/fetch/all` | — | Mọi show (mọi trạng thái), sắp `lastModified desc`, kèm `productCount` |
| GET `/private/accessory/show/fetch` | `id` | Một show (`AdminAccessoryShowListItem`) |
| POST `/private/accessory/show/create` | body `AdminAccessoryShowUpsertRequest` | Tạo show; trùng `code` → `409`; trùng slug → `409 {"message":"Duplicate slug","slug":…}`; sai dữ liệu → `400` |
| POST `/private/accessory/show/update` | `id` + body upsert | Thay toàn bộ field có thể sửa (`code` bất biến); field bỏ trống bị xóa (ví dụ `saleOpenAt`, `slug`) |
| POST `/private/accessory/show/status` | `id` + `{ "status": "Open"\|"Waiting"\|"Closed" }` | Đổi trạng thái |
| POST `/private/accessory/show/pin` | `id` + `{ "pinOrder": 1..5 \| null }` | Đặt/bỏ ghim một show |
| GET `/private/accessory/show/pin/board` | — | `AccessoryPinBoardResponse { slots[{ pinOrder, show{id,name,code,status,bannerImageUrl,pinOrder} }] }` |
| POST `/private/accessory/show/pin/refresh` | — | Ghim 5 show sửa gần nhất (`lastModified desc`) |
| POST `/private/accessory/show/pin/confirm` | `{ slots: [{ pinOrder, showId? }] }` | Ghi lại bảng ghim: xóa toàn bộ `pinOrder` cũ rồi gán `1..n` theo thứ tự; tối đa 5; trùng/sai id → `400` |
| GET `/private/accessory/show/pin/candidates` | `page` (1-based), `size` (mặc định 5, kẹp 1..50), `keyword` | Picker tìm show theo name/code |

`AdminAccessoryShowUpsertRequest`: `code`, `codePrefix`, `name`, `slug`, `description`, `bannerImageUrl`, `sessions[{id, city{id,name,code}, venue, datetime}]`, `status`, `saleOpenAt` (epoch ms), `pickupNote`, `deliveryNote`, `pgAccountCode`, `allowedPaymentMethods[]`, `mailPlugin`, `mailTemplateId`, `fulfillment{enablePickupAtShow, enableMetaTicketPickup, enableDelivery, pickupAtShowSessionIds[], pickupPoints[{id,name,address,note}]}`.

Quy tắc validate (`accessory.admin.request.kt`):
- `code` (khi tạo), `codePrefix`, `name` bắt buộc; slug đúng định dạng nếu có.
- `status = Waiting` kèm `saleOpenAt > 0` thì `saleOpenAt` phải ở tương lai.
- Mỗi session cần city name hoặc code, `venue`, `datetime > 0`.
- Phải bật ít nhất một hình thức nhận hàng; bật PickupAtShow thì `pickupAtShowSessionIds` không rỗng và chỉ chứa session id có trong payload; bật MetaTicketPickup thì `pickupPoints` không rỗng, mỗi điểm có `name`, `address`.
- Tạo mới không gửi `status` → mặc định `Open`.
- Sau create/update/status, `AccessoryShowOpenService.afterShowPersisted` quyết định có lập lịch job `OpenShow` không.

#### Sản phẩm

| Method & Path | Tham số | Mô tả |
|---|---|---|
| GET `/private/accessory/fetch/all` | `showId` | Mọi sản phẩm của show (active + inactive) |
| GET `/private/accessory/fetch` | `id` | Một sản phẩm (`AdminAccessoryListItem`) |
| POST `/private/accessory/create` | body `AdminAccessoryUpsertRequest` | Tạo sản phẩm; trùng `(show, sku)` → `409` |
| POST `/private/accessory/update` | `id` + body `AdminAccessoryCatalogUpdateRequest` | Sửa catalog, **không ghi tồn kho** |
| POST `/private/accessory/active` | `id` + `{ "active": bool }` | Bật/tắt bán |
| POST `/private/accessory/stock/adjust` | `id` + `{ variantKey?, delta? \| setTo? }` | Điều chỉnh tồn nguyên tử |

Quy tắc:
- Tạo: `sku`, `name`, `imageUrl` bắt buộc, `price > 0`; tồn ban đầu phải **hữu hạn `>= 0`** (admin không tạo được `-1`); biến thể cần `key` duy nhất, `label`, `imageUrl`.
- Sửa: chỉ cập nhật field catalog bằng `$set/$unset` (không `replaceOne`, để không đè tồn đang bị giữ); key biến thể **bất biến** — không xóa được biến thể (`409 "cannot remove variants…"`), không thêm biến thể vào sản phẩm single-SKU (`409`), được thêm biến thể mới (được `$push`).
- Điều chỉnh kho: đúng một trong `delta` (≠ 0, `$inc` có guard không âm) hoặc `setTo` (`>= 0`); sản phẩm có biến thể bắt buộc `variantKey`, single-SKU không được gửi. Kết quả: ô không giới hạn với `delta` → `409` (phải dùng `setTo`); giảm quá số còn lại → `409 "Không đủ tồn để giảm (có thể đang bị giữ bởi đơn chờ thanh toán)"`; không tìm thấy ô → `404`; sai hình dạng → `400`.

#### Mã giảm giá

| Method & Path | Tham số | Mô tả |
|---|---|---|
| GET `/private/accessory/discount/fetch/all` | `showId` | Danh sách mã của show |
| GET `/private/accessory/discount/fetch` | `id` | Một mã (`AdminDiscountListItem`) |
| POST `/private/accessory/discount/create` | body `AdminDiscountUpsertRequest` | Tạo; trùng mã (không phân biệt hoa thường) → `409` |
| POST `/private/accessory/discount/create/batch` | `{ items: [...] }` | Tạo hàng loạt, tối đa **200** mã, mọi mã cùng `accessoryShowId`; thành công một phần: `{ created[], failures[{code, message}] }` |
| POST `/private/accessory/discount/update` | `id` + body | Thay field; giữ nguyên `code`, `usedCount` |
| POST `/private/accessory/discount/active` | `id` + `{ "active": bool }` | Bật/tắt |
| POST `/private/accessory/discount/delete` | `id` | Xóa cứng **chỉ khi `usedCount = 0`**, ngược lại `409 "Không xóa được mã đã sử dụng… Hãy tắt Active thay vì xóa."` |

`AdminDiscountUpsertRequest`: `code`, `accessoryShowId` (bắt buộc), `type` (`Percentage|FixedAmount|Tiered|EarlyBird`), `value`, `rewardType`, `maxDiscountAmount`, `minOrderAmount`, `tiers[]`, `applyMode`, `usageLimit`, `startAt`, `endAt` (epoch ms), `active`. Mã lưu dạng UPPERCASE.

#### Đơn hàng, thống kê, xuất dữ liệu

| Method & Path | Tham số | Mô tả |
|---|---|---|
| GET `/private/accessory/order/fetch/all` | `showId` | `AdminOrderListResponse { stats{totalOrders, paidOrders, revenue, byStatus}, orders[] }` — mọi trạng thái; doanh thu chỉ tính `PaymentSucceed` |
| GET `/private/accessory/order/stats` | `showId`, `startAt?`, `endAt?` (epoch ms, `endAt` exclusive, phải > `startAt`) | `AdminSalesStatisticResponse { summary, byProduct }` (gom theo `accessoryId|sku|unitPrice`) |
| GET `/private/accessory/order/export` | như trên | `AdminOrderExportResponse { products[], orders[] }` — đơn `PaymentSucceed`, có SĐT/email/địa chỉ/`paymentId` (đã mã hóa) |
| POST `/private/accessory/order/notify/resend` | `id` (id đơn **mã hóa**) | Gửi lại email xác nhận; cần `X-OP-User-Id` (`401`); không có đơn → `404`; đơn không `PaymentSucceed` → `409`; thành công `200 {"message":"confirmation email resend dispatched"}` |

`AdminOrderListItem`: `id`, `code`, `status`, `fulfillment`, `paymentId` (mã hóa), `customerUserId`, session/pickup point, `subtotal`, `discountAmount`, `discountCode`, `total`, thông tin khách, `createdAt`, địa chỉ giao, `lines`.

#### Bộ lọc báo cáo (shareable)

| Method & Path | Tham số | Mô tả |
|---|---|---|
| GET `/private/accessory/report-filter/fetch/all` | `showId?` | Danh sách filter (mới nhất trước) |
| GET `/private/accessory/report-filter/fetch` | `reportKey` | Một filter |
| POST `/private/accessory/report-filter/create` | `{ reportName, username?, showId, startAt?, endAt? }` | Tạo filter `Active`, trả `{ "reportKey": "<uuid>" }` để copy link `/public/accessory/report/{reportKey}` |
| POST `/private/accessory/report-filter/enable` | `{ reportId }` | `Active` |
| POST `/private/accessory/report-filter/disable` | `{ reportId }` | `Inactive` (link công khai trả `404`) |
| POST `/private/accessory/report-filter/delete` | `{ reportId }` | Xóa cứng |

## 5. Flow Xử Lý Chính

### 5.1 Checkout (`AccessoryOrderServiceImpl.checkout`)

1. **Gate payload** (`CheckoutValidator`): số dòng `minLines..maxLines`, số lượng `minLineQuantity..maxLineQuantity`, `accessoryId` giải mã ra ObjectId, `variantKey` nếu có thì không rỗng, tên 1..100 ký tự, SĐT `^\+?\d{8,15}$`, email ≤ 254 ký tự dạng `x@y.z`, không có field shipping cũ, fulfillment hợp lệ + field đi kèm.
2. **Tải catalog**: mọi `accessoryId` phải tồn tại (`404`), đều `active` (`422`), cùng **một** show (`422`), show phải `Open` (`422`).
3. **Hình thức nhận hàng** (theo `show.fulfillment`):
   - `PickupAtShow`: phải được bật; nếu show có sessions thì `showSessionId` phải khớp một session (giới hạn trong `pickupAtShowSessionIds` nếu danh sách này không rỗng) → snapshot `showSessionId/Label`; sai → `400`.
   - `MetaTicketPickup`: phải được bật; `pickupPointId` phải có trong `pickupPoints` → snapshot tên/địa chỉ.
   - `Delivery`: phải được bật; `deliveryAddress.address/ward/city` bắt buộc.
   - Hình thức chưa bật → `422`.
4. **Định giá từng dòng** (`priceLine`): sản phẩm có biến thể bắt buộc `variantKey` khớp biến thể (`400`), biến thể phải `active` (`422`); single-SKU không được gửi `variantKey` (`400`); `unitPrice` luôn lấy từ catalog.
5. **Kiểm tra tồn gộp** (`validateAggregateStock`): cộng số lượng theo từng ô `(accessoryId, variantKey)` qua mọi dòng, vượt tồn (trừ ô `-1`) → `409 "Hết hàng: <sku> (<variant>)"`.
6. **Giới hạn số lượng sản phẩm** (`validateProductQuantityLimits`): `minOrderQty`/`maxOrderQty` mỗi đơn, `maxPerUserQty` cộng cả đơn trước của khách → `422` kèm câu tiếng Việt (ví dụ "Sản phẩm 'X' chỉ được mua tối đa 2 / người (còn có thể mua thêm 1).").
7. **Giữ kho** (`reserveStockOrThrow`): mỗi ô gọi `$inc: -qty` có guard `stock >= qty` (điểm tuần tự hóa chống bán quá); ô `-1` bỏ qua; ô nào không đủ → nhả các ô đã giữ, `409`.
8. **Mã giảm giá**: nếu khách gửi mã thì mã **bắt buộc** còn áp dụng và giữ được lượt; không áp dụng → nhả kho, `422`; hết lượt → nhả kho, `409`. Không bao giờ âm thầm rơi về giá gốc.
9. **Tạo đơn**: `total = max(0, subtotal − discount)`; sinh mã qua `OrderCodeService.allocateWithRetry(show.codePrefix, năm hiện tại)` và insert ở `Created` (thử lại tối đa 3 lần khi trùng mã). Insert lỗi → nhả kho + hoàn mã, ném lỗi.
10. **Đơn 0đ** (`total == 0`): CAS `Created → PaymentSucceed`, gửi email nền, trả `encodedPaymentId = ""`.
11. **Tạo payment**: `PaymentGateway.createPayment(orderId, orderCode, amount = total, pgAccountCode, paymentMethods, orderType = "ACCESSORY")` trong **30 giây**. Lỗi gRPC / timeout / id rỗng → CAS sang `PaymentFailed`, nhả kho + hoàn mã, trả `502 "Tạo thanh toán thất bại: …"`.
12. **Thành công**: lưu `paymentId`, CAS `Created → WaitingForPayment`, trả id payment/đơn đã mã hóa.

### 5.2 Thanh toán và callback (`OrderNotificationServiceImpl`, `AccessoryJobExecutorServiceImpl`)

1. Storefront dùng `encodedPaymentId` với luồng `service.payment` như vé (`/api/payment/...`).
2. Trước khi redirect khách sang cổng, `service.payment` gọi gRPC **`NotifyPaymentStarted`** → `onPaymentStarted`: CAS `WaitingForPayment → PaymentProcessing` + ghi `paymentStartedAt`. Đã `PaymentProcessing` → coi như thành công (idempotent). Đơn không còn thanh toán được (đã `Expired`/`PaymentCancelled`...) → gRPC `FAILED_PRECONDITION`, `service.payment` đánh dấu payment Failed và không cho khách thanh toán đơn chết.
3. Khi có kết quả, `service.payment` gọi gRPC **`NotifyPaymentStatusChanged`** (định tuyến theo `orderType = ACCESSORY`, xem `SERVICE_PAYMENT_TECHNICAL_DOCUMENTATION.md` mục 2.4). Chỉ `Succeed`/`Failed`/`Cancelled` được xử lý; trạng thái khác là no-op.
4. Đường dự phòng bất đồng bộ: `service.payment` lập job JobRunr với `service = AccessoryService`, `jobName ∈ OrderJobType.NotifyPaymentSucceed|Failed|Cancelled|Processing`, `jobDesc = orderId`. Executor map sang trạng thái tương ứng (`Processing` → bỏ qua), lỗi → trả `CONTINUE` để JobRunr thử lại.
5. **Succeed** (`handleSucceed`): CAS `PRE_TERMINAL → PaymentSucceed`; **không đổi kho** (đã giữ từ lúc tạo đơn); gửi email trên coroutine nền (không chặn ack callback). Đã `PaymentSucceed` → no-op; trạng thái khác (ví dụ `Expired`) → ghi `UNROUTABLE_CALLBACK`, giữ nguyên đơn.
6. **Failed/Cancelled** (`handleFailOrCancel`): CAS `PRE_TERMINAL → PaymentFailed/PaymentCancelled`, rồi nhả kho + hoàn mã đúng một lần. Callback lặp cùng trạng thái → no-op; xung đột trạng thái terminal khác → ghi `UNROUTABLE_CALLBACK`.
7. Callback cho `orderId` không tồn tại trong `accessory-order` → ghi `UNROUTABLE_CALLBACK`.

### 5.3 Nhả kho / hoàn mã exactly-once (`releaseReservation`)
- `OrderDatabaseService.tryClaimStockRelease` lật nguyên tử `stockReleased: false → true`; chỉ caller thắng mới `$inc` trả kho (guard `stock >= 0` để không chạm ô `-1`).
- `tryClaimRevert` lật `discountReverted`; caller thắng gọi `DiscountCodeService.revert` (`usedCount − 1`, guard `usedCount > 0`) tối đa **3 lần**, backoff tuyến tính `200ms × lần`; thất bại hết → ghi `DISCOUNT_REVERT_FAILED`, đơn vẫn giữ trạng thái terminal.
- Áp dụng chung cho: tạo payment lỗi, payment Failed/Cancelled, khách hủy, hết TTL, safety-net.

### 5.4 Hết hạn (`ExpirySweepService`, chạy in-process)
- Vòng lặp nền trên thread riêng, mỗi `accessory.expirySweepIntervalSeconds` (mẫu: 300 giây); lỗi một lượt chỉ log, lượt sau chạy tiếp. **Không** dùng JobRunr.
- **Đơn chưa thanh toán**: `status ∈ {Created, WaitingForPayment}` và `createdAt < now − orderTtlMinutes` (mẫu: 5 phút) → CAS sang `Expired` + nhả kho/hoàn mã.
- **Safety-net PaymentProcessing**: `status = PaymentProcessing` và `paymentStartedAt < now − paymentProcessingStaleMinutes` (mặc định 60; `0` = tắt) → CAS sang `Expired` + nhả kho/hoàn mã, log `warn`. Giá trị phải lớn hơn hẳn thời gian phiên cổng thanh toán + cửa sổ inquiry của `service.payment` (`maxTimeQueryForNotfoundInM`).
- Do sweep chạy theo chu kỳ, đơn thực tế có thể sống tới `orderTtlMinutes + expirySweepIntervalSeconds`.

### 5.5 Email xác nhận (`dispatchConfirmationEmail`)
1. Claim nguyên tử `emailDispatched: false → true`; không thắng claim → no-op.
2. Tải show (lấy tên show, pickupNote, plugin, template). Không còn show → ghi `EMAIL_DISPATCH_FAILED`, nhả claim.
3. Gọi `SendMailAccessoryOrder` tối đa **3 lần**, backoff `500ms × lần`. Thành công → ghi `emailDispatchedAt`.
4. Hết lượt → lập job JobRunr `ResendMailAccessoryOrder` (`service = NotificationService`, `jobId = mã đơn`, `jobDesc` = request proto dạng JSON, `retry = jrunr.sendEmail.retry`, mẫu `-1` = thử mãi) để `service.notification` tự thử lại; ghi `EMAIL_DISPATCH_FAILED`, nhả claim. Nếu lập job cũng lỗi → ghi `EMAIL_DISPATCH_FAILED`, nhả claim.
5. Đơn luôn giữ `PaymentSucceed` dù email lỗi; operator dùng `POST /private/accessory/order/notify/resend` (reset claim rồi gửi lại qua đúng đường trên).

Request `SendMailAccessoryOrderRequest`: `reqId = orderCode`, `mailPlugin`, `mailTemplate`, `recipientEmail`, `orderCode`, `showName`, `lines[{productName, variantLabel, quantity, unitPrice, lineTotal}]`, `subtotal`, `hasDiscount`, `discountCode`, `discountAmount`, `total`, `pickupNote` (= `fulfillmentSummary`: ghi chú nhận tại show + buổi, hoặc điểm META Ticket, hoặc địa chỉ giao), `customerName`, `customerPhone`.

## 6. Mã Giảm Giá (`DiscountCodeServiceImpl`)

### 6.1 Điều kiện áp dụng (`isApplicable`)
- `active = true`; `now` nằm trong `[startAt, endAt]` (nếu có).
- `accessoryShowId` khớp show của giỏ (nếu mã có gắn show).
- `usageLimit = null` hoặc `usedCount < usageLimit`.
- `Percentage`/`FixedAmount`/`EarlyBird`: `subtotal >= minOrderAmount`; `EarlyBird` cần `rewardType ∈ {Percentage, FixedAmount}`.
- `Tiered`: có ít nhất một tier với `minOrderAmount <= subtotal`.
- Số tiền tính ra phải `> 0`.

### 6.2 Cách tính (`computeAmount`)

| Loại | Công thức |
|---|---|
| `Percentage` | `min(subtotal × value / 100, maxDiscountAmount ?: ∞)` |
| `FixedAmount` | `min(value, subtotal)` |
| `EarlyBird` | như `Percentage` hoặc `FixedAmount` theo `rewardType` |
| `Tiered` | chọn tier có `minOrderAmount` lớn nhất mà `<= subtotal`, rồi áp công thức theo `tier.type` |

Kết quả luôn kẹp trong `[0, subtotal]`.

### 6.3 Ràng buộc dữ liệu (`accessory.discount.validation.kt`)
- `Percentage`: `1 <= value <= 100`; `FixedAmount`: `value > 0`.
- `EarlyBird`: bắt buộc `accessoryShowId`, `startAt`, `endAt`, `rewardType` hợp lệ.
- `Tiered`: ít nhất một tier, `minOrderAmount` các tier không trùng; tier chỉ nhận `Percentage|FixedAmount`.
- `maxDiscountAmount > 0` (nếu có), `minOrderAmount >= 0`, `usageLimit > 0` (nếu có), `startAt <= endAt`.

### 6.4 Chế độ áp dụng
- Mọi loại mã đều là **nhập tay** (`resolveBest` chỉ đánh giá mã khách gõ). `applyMode = Auto` vẫn lưu được nhưng checkout/preview không quét mã Auto.
- **Giữ lượt** khi checkout: `updateOne` với filter `active = true` và (`usageLimit = null` hoặc `$expr usedCount < usageLimit`), `$inc usedCount +1`. Preview không giữ lượt.

## 7. Cấu Hình

### 7.1 `application.conf` (mẫu `conf-sample/application.conf`)

| Khóa | Giá trị mẫu / mặc định | Ý nghĩa |
|---|---|---|
| `ktor.deployment.port` | `4020` (staging; prod `3020`) | Port HTTP, khớp upstream `accessory_service` |
| `ktor.application.modules` | `metaticket.accessory.ApplicationKt.module` | Ktor module |
| `mongodb.connectionString` | `mongodb://<host>:<port>` | Chuỗi kết nối MongoDB |
| `mongodb.dbName` | `metaticket` | Database |
| `grpc.server.port` | `4120` | gRPC server (OrderNotificationService + JobExecutorService) |
| `grpc.channel.payment.host/port` | `127.0.0.1` / `4106` | `service.payment` |
| `grpc.channel.notification.host/port` | `127.0.0.1` / `4108` | `service.notification` |
| `grpc.channel.jobrunr.host/port` | `127.0.0.1` / `4107` | `service.jobrunr` |
| `jrunr.sendEmail.retry` | `-1` | Số lần retry của job `ResendMailAccessoryOrder` (`-1` = vô hạn); **bắt buộc** |
| `secretKey` | `<16-BYTE-SECRET>` | Khóa AES 16 byte cho `Checksum`; **phải trùng `secretKey` của `service.payment` cùng môi trường** |
| `accessory.orderTtlMinutes` | `5` | TTL đơn chưa thanh toán (bắt buộc) |
| `accessory.expirySweepIntervalSeconds` | `300` | Chu kỳ quét hết hạn (bắt buộc) |
| `accessory.paymentProcessingStaleMinutes` | `60` (mặc định khi vắng) | Safety-net cho `PaymentProcessing`; `0` = tắt |
| `accessory.saleOpenSweepIntervalSeconds` | `15` (mặc định khi vắng) | Chu kỳ quét mở bán show `Waiting` |
| `accessory.scheduleOpenShowOnJobrunr` | `true` (mặc định khi vắng) | Lập job `OpenShow` trên JobRunr; đặt `false` khi chạy hybrid local trỏ JobRunr remote (callback không quay về máy local) |
| `accessory.rateLimit.limit` | `20` | Số request / cửa sổ / IP |
| `accessory.rateLimit.windowSeconds` | `60` | Cửa sổ rate limit (giây) |
| `accessory.checkout.minLines` | `1` | Số dòng giỏ tối thiểu |
| `accessory.checkout.maxLines` | `5` | Số dòng giỏ tối đa (mặc định code `5`) |
| `accessory.checkout.minLineQuantity` | `1` | Số lượng tối thiểu mỗi dòng |
| `accessory.checkout.maxLineQuantity` | `99` | Số lượng tối đa mỗi dòng (nên khớp giới hạn giỏ phía FE) |

Khóa `accessory.checkout.*`, `rateLimit.*`, `paymentProcessingStaleMinutes`, `saleOpenSweepIntervalSeconds`, `scheduleOpenShowOnJobrunr` vắng hoặc sai định dạng sẽ dùng mặc định trong code; các khóa còn lại thiếu sẽ làm service không khởi động.

### 7.2 Hằng số trong code

| Hằng số | Giá trị | Vị trí |
|---|---|---|
| `ACCESSORY_PIN_SLOT_MAX` | 5 | `pojo/response/accessory.admin.response.kt` |
| `PUBLIC_SHOW_LIST_DEFAULT_SIZE` / `MAX_SIZE` | 6 / 50 | `AccessoryController` |
| `PUBLIC_STOCK_CACHE_CONTROL` | `public, max-age=5, stale-while-revalidate=10` | `AccessoryController` |
| `SEARCH_MATCH_CAP` | 200 | `CatalogDatabaseService` |
| `PAYMENT_CREATE_TIMEOUT_MS` | 30.000 | `AccessoryOrderServiceImpl` |
| `DISCOUNT_REVERT_MAX_ATTEMPTS` / delay | 3 / 200ms × lần | `AccessoryOrderServiceImpl` |
| `EMAIL_DISPATCH_MAX_ATTEMPTS` / delay | 3 / 500ms × lần | `AccessoryOrderServiceImpl` |
| `MAX_ALLOCATION_ATTEMPTS` (mã đơn) | 3 | `OrderCodeService` |
| `AdminDiscountBatchCreateRequest.MAX_BATCH_SIZE` | 200 | `accessory.discount.admin.request.kt` |
| `DEFAULT_PAYMENT_METHODS` | `VietQr, QrCode, InternationalCard, DomesticCard` | `PaymentGateway` |
| `DEFAULT_MAIL_TEMPLATE` | `accessory-order-confirm-vi` | `AccessoryMailGateway` |
| Sớm cho phép của job `OpenShow` | 2 giây | `AccessoryJobExecutorServiceImpl` |

### 7.3 Cấu hình phía service khác
- **`service.payment`**: khối `grpc.channel.accessoryS { sid, host, port = <gRPC accessory> }` — đích callback cho payment `orderType = ACCESSORY`.
- **`service.jobrunr`**: khối `grpc.channel.accessoryS` (cùng cổng gRPC accessory) cho `JobExecutorServiceName.AccessoryService`.
- **`service.notification`**: plugin jar `plugin.mailtemplate.accessory_order` (lớp `PluginMailTemplateAccessoryOrder`) trong thư mục `plugin/mail_template/`, và template `accessory-order-confirm-vi` (hoặc `mailTemplateId` của show) phải có trong kho template — thiếu plugin/template thì mỗi lần gửi ném lỗi và rơi vào JobRunr fallback.

## 8. Xử Lý Lỗi

### 8.1 `CheckoutException` (`service/CheckoutException.kt`)

| Lớp | HTTP | Message |
|---|---|---|
| `EmptyCart` | 400 | "Giỏ hàng không được trống" |
| `UnknownAccessory` | 404 | "Sản phẩm không tồn tại: <ids>" |
| `NotSingleOpenShow` | 422 | "Vui lòng check lại sản phẩm trong giỏ hàng: <lý do>" |
| `ShowNotOnSale` | 422 | "Sự kiện chưa mở bán. Vui lòng quay lại khi đến giờ mở bán." / "Sự kiện đã đóng, không thể đặt hàng." |
| `InvalidVariant` | 400 | "Lựa chọn phụ kiện <id> không hợp lệ: <lý do>" |
| `InactiveVariant` | 422 | "Phụ kiện <id> không khả dụng: <variant>" |
| `OutOfStock` | 409 | "Hết hàng: <sku> (<variant>)" |
| `ProductQuantityLimit` | 422 | Câu tiếng Việt về min/max mỗi đơn hoặc mỗi người |
| `InvalidShowSession` | 400 | "Lựa chọn phiên sự kiện không hợp lệ: …" |
| `InvalidFulfillment` | 400 | "Lựa chọn giao hàng không hợp lệ: …" |
| `FulfillmentNotEnabled` | 422 | "Giao hàng '<mode>' không khả dụng cho show này" |
| `InvalidPickupPoint` | 400 | "Điểm nhận hàng không hợp lệ: …" |
| `InvalidDeliveryAddress` | 400 | "Địa chỉ giao hàng không hợp lệ: …" |
| `DiscountNotApplicable` | 422 | "Mã giảm giá '<code>' không còn áp dụng được cho đơn này…" |
| `DiscountExhausted` | 409 | "Mã giảm giá '<code>' đã hết lượt sử dụng…" |
| `InvalidOrderTotal` | 422 | (còn khai báo nhưng không còn được ném — đơn 0đ đã được chấp nhận) |
| `PaymentCreationFailed` | 502 | "Tạo thanh toán thất bại: <lý do>" |

### 8.2 `OrderAccessException` (`service/OrderAccessException.kt`)

| Lớp | HTTP | Message |
|---|---|---|
| `OrderNotFound` | 404 | "no order matches the requested identifier" |
| `ResendNotAllowed` | 409 | "order is not in PaymentSucceed status (current=…); resend not permitted" |
| `CancelNotAllowed` | 409 | "order can no longer be cancelled (current=…)" |
| `NotOrderOwner` | 403 | "you are not allowed to cancel this order" |

### 8.3 Lỗi khác
- `401 "Không xác định người dùng"`: thiếu `X-Customer-User-Id` (customer API) hoặc `X-OP-User-Id` (resend).
- `429` + `Retry-After`: vượt rate limit.
- `OrderCodeAllocationException`: trùng mã đơn sau 3 lần → `500`.
- `AccessoryValidationException` khi lưu document admin → `400` với danh sách lỗi.
- Jackson cấu hình `FAIL_ON_UNKNOWN_PROPERTIES = false`: field thừa trong body không gây lỗi.

### 8.4 Đối soát (`accessory-reconciliation`)

| `kind` | Khi nào ghi |
|---|---|
| `UNROUTABLE_CALLBACK` | Callback thanh toán cho `orderId` không tồn tại; `Succeed` tới khi đơn ở trạng thái khác `PaymentSucceed` không phải pre-terminal (ví dụ đã `Expired`); `Failed/Cancelled` tới khi đơn ở trạng thái terminal khác |
| `DISCOUNT_REVERT_FAILED` | Hoàn mã thất bại sau 3 lần |
| `EMAIL_DISPATCH_FAILED` | Không còn show, hoặc gửi trực tiếp thất bại (kèm việc đã/không lập được job fallback) |
| `STOCK_SHORTFALL` | Còn trong enum nhưng **không còn được ghi** (mô hình cũ trừ kho khi thanh toán thành công) |

Không có API đọc/đánh dấu `resolved` — vận hành phải truy vấn Mongo trực tiếp.

## 9. Triển Khai

- Build/deploy như các service Ktor khác (`./gradlew :service.accessory:distTar` / `installDist`, chỉ thay `bin/` + `lib/`, `conf/application.conf` sửa tay trên server): xem **[SERVICE_DEPLOYMENT.md](./SERVICE_DEPLOYMENT.md)** (mục "Quy trình Build", "Quy trình Deploy", "Quản lý Systemd").
- Thứ tự khi bật tính năng lần đầu ở một môi trường: `service.payment` (có định tuyến `orderType`, cấu hình `accessoryS`), `service.notification` (plugin + template), `service.jobrunr` (cấu hình `accessoryS`), rồi mới tới `service.accessory`; cuối cùng là nginx (`/api/public/accessory`, `/api/accessory`, `/private/accessory`) và frontend.
- `secretKey` phải trùng với `service.payment` cùng môi trường, nếu không id payment trả về storefront sẽ không dùng được.
- Test: `./gradlew :service.accessory:test` (property-based với kotest/jqwik, mockk; một số test tích hợp dùng Testcontainers MongoDB).

## 10. Các Lưu Ý Đặc Biệt / Hạn Chế Đã Biết

1. **Route admin không tự xác thực.** Ngoài `order/notify/resend`, mọi `/private/accessory/*` (kể cả sửa giá, điều chỉnh kho, tạo mã giảm giá, xuất dữ liệu có SĐT/email) không kiểm tra `X-OP-User-Id`, chỉ dựa vào nginx `auth_request`. Port HTTP 3020/4020 tuyệt đối không được lộ ra Internet.
2. **Rate limit có thể bị lách.** Khóa limiter là phần tử **đầu** của `X-Forwarded-For`, nhưng nginx dùng `$proxy_add_x_forwarded_for` (nối thêm vào header client gửi), nên client tự đặt `X-Forwarded-For` giả để đổi bucket. Limiter cũng là in-memory theo từng instance.
3. **`X-Customer-User-Id` trên API công khai do client kiểm soát.** Các endpoint `/public/accessory/show/fetch*` và `/fetch` đọc header này để tính `myPurchasedQty`, nhưng nhánh `/api/public` của nginx không ghi đè/xóa header — ai cũng có thể gửi id của người khác để biết số lượng đã mua của họ với sản phẩm đó (rò rỉ mức thấp; checkout vẫn dùng header do nginx chèn).
4. **`/public/accessory/order/fetch` không kiểm tra chủ đơn**: ai có id đơn đã mã hóa (link "Hoàn tất") đều xem được tên, SĐT, email người nhận.
5. **Thanh toán thành công muộn sau safety-net.** Nếu đơn `PaymentProcessing` bị chuyển `Expired` (đã nhả kho/hoàn mã) rồi cổng mới báo `Succeed`, đơn giữ nguyên `Expired` và chỉ có một bản ghi `UNROUTABLE_CALLBACK` — khách đã trả tiền nhưng không có đơn hợp lệ, phải xử lý thủ công (giao hàng bù hoặc hoàn tiền). Cần đặt `paymentProcessingStaleMinutes` đủ lớn.
6. **Đơn `PaymentProcessing` cũ không có `paymentStartedAt`** (tạo trước khi có field này) không bao giờ khớp safety-net và có thể giữ kho vô thời hạn.
7. **KDoc lệch với code.** Interface `AccessoryOrderService.checkout`, phần mô tả lớp `AccessoryOrderServiceImpl` và `ReconciliationDatabaseService` vẫn ghi "không trừ kho khi checkout / trừ kho khi thanh toán thành công + `STOCK_SHORTFALL`"; code hiện tại là reserve-on-create và không ghi `STOCK_SHORTFALL`. Comment `ApplicationDI` gọi database là `metaticket_accessory`, trong khi mẫu cấu hình dùng `metaticket`. Comment enum `AccessoryShowStatus` nói chỉ show `Open` hiển thị công khai, nhưng listing/chi tiết/tìm kiếm trả cả `Waiting`/`Closed` (chỉ checkout bị chặn). Comment `CheckoutValidationCode`/`CheckoutInput` nói 1..50 dòng, mặc định thực tế là 5. Comment `exportOrdersForAdmin`/`AdminOrderExportItem` nói "mọi trạng thái" và `paymentId` thô, thực tế chỉ `PaymentSucceed` và `paymentId` đã mã hóa.
8. **Hiệu năng listing.** `GET /public/accessory/show/fetch/all` tải **toàn bộ** show và **toàn bộ** đơn `PaymentSucceed` (để xếp hạng theo số đã bán) mỗi request, không có cache server — ổn với khối lượng hiện tại nhưng sẽ chậm dần theo số đơn.
9. **Điều chỉnh kho khi có đơn đang giữ.** `stock` là số còn bán được; `setTo` ghi đè giá trị này. Khi đơn đang giữ hết hạn/hủy, số lượng được cộng trả lại lên giá trị mới, nên `setTo` theo tồn vật lý trong lúc có đơn chờ có thể làm thừa kho. Dùng `delta` an toàn hơn.
10. **Đổi trạng thái qua `/show/status` không validate `saleOpenAt`.** Chuyển một show sang `Waiting` mà `saleOpenAt` đã qua thì vòng quét 15 giây sẽ mở bán lại ngay.
11. **Sweep chạy trên mọi instance.** Expiry sweep và sale-open sweep chạy in-process; nhiều instance cùng quét được nhờ CAS nhưng làm việc trùng lặp.
12. **CORS rất rộng**: `allowOrigins` có `"*"` cùng `allowCredentials = true`. Thực tế trình duyệt gọi qua cùng domain nginx nên ít ảnh hưởng, nhưng không nên dựa vào CORS làm lớp bảo vệ.
13. **Mã đơn theo năm của server** (`LocalDate.now()`, múi giờ JVM), chuỗi số tăng liên tục theo `(prefix, năm)` và có thể vượt 4 chữ số.
14. **Xóa/ẩn**: không có API xóa show/sản phẩm (chỉ `active = false` / `Closed`); mã giảm giá chỉ xóa được khi chưa dùng.

---

**Tài liệu này được cập nhật lần cuối**: 09/10/2026
**Version**: 1.0
**Tác giả**: MetaTicket Development Team

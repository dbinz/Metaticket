# Tài liệu Kỹ thuật Order Service

## 1. Tổng quan về chức năng chính

Order Service là dịch vụ quản lý quy trình đặt hàng trong hệ thống bán vé sự kiện online MetaTicket. Service này chịu trách nhiệm:

- **Tạo đơn hàng**: Xử lý việc tạo đơn hàng từ reservation đã được đặt trước
- **Quản lý trạng thái đơn hàng**: Theo dõi và cập nhật 11 trạng thái khác nhau của đơn hàng
- **Xử lý thanh toán**: Tích hợp với Payment Service để xử lý các giao dịch thanh toán
- **Quản lý thông tin khách hàng**: Lưu trữ và cập nhật thông tin người mua và người nhận
- **Xử lý khuyến mãi**: Áp dụng các mã giảm giá và chương trình khuyến mãi (plugin-based)
- **Thông báo**: Gửi email và thông báo về trạng thái đơn hàng
- **Báo cáo**: Cung cấp thống kê và báo cáo về doanh thu, đơn hàng
- **Quản lý sự kiện thất bại**: Xử lý và retry các event Kafka thất bại

## 2. Kiến trúc và các thành phần chính

### 2.1. Cấu trúc thư mục
```
src/main/kotlin/metaticket/order/
├── controller/          # Các REST API controllers
├── service/            # Business logic implementations
├── database/service/   # Database access layer
├── gateway/           # External service integrations
├── pojo/              # Data models (request/response/db)
├── datastructure/     # Domain models, enums
├── configuration/     # Configuration classes
├── cache/             # Caching layer
├── kafka/             # Event messaging
├── calculate/         # Promotion calculation plugins
├── validation/        # Input validation
├── exception/         # Custom exceptions
├── storage/           # Storage layer (failed events)
└── utils/             # Utility functions
```

### 2.2. Các thành phần chính

#### Controllers
| Controller | Mô tả |
|------------|-------|
| **OrderController** | Xử lý các API chính về đơn hàng (create, update, fetch, payment) |
| **NotifyOrderController** | Xử lý thông báo và cập nhật trạng thái thanh toán |
| **PromotionController** | Quản lý khuyến mãi (create, import, fetch) |
| **ReportController** | Báo cáo và thống kê doanh thu |
| **FailedEventController** | Xử lý các sự kiện Kafka thất bại |
| **OrderFailedHandler** | Xử lý rollback khi đơn hàng thất bại |

#### Database Services
| Service | Mô tả |
|---------|-------|
| **OrderDBService** | CRUD operations cho đơn hàng |
| **PromotionDBService** | Quản lý dữ liệu khuyến mãi |
| **PromotionCodeQuantityDBService** | Quản lý số lượng mã giảm giá |
| **PromotionComboQuantityDBService** | Quản lý số lượng combo khuyến mãi |
| **OrderCustomFieldMappingDBService** | Quản lý custom fields theo show/session |

#### Gateways (External Integrations)
| Gateway | Mô tả |
|---------|-------|
| **PaymentGateway** | Tích hợp với Payment Service |
| **TicketGateway** | Tích hợp với Ticket Service |
| **NotificationGateway** | Gửi email và thông báo |
| **ReservationGateway** | Tích hợp với Reservation Service |
| **ShowGateway** | Tích hợp với Show Service |
| **JobrunrGateway** | Quản lý background jobs |
| **OrderDataCollectorGateway** | Tích hợp data collector |

### 2.3. Trạng thái đơn hàng (11 trạng thái)

| # | Trạng thái | Mô tả |
|---|------------|-------|
| 1 | **Created** | Đơn hàng đã được tạo thành công |
| 2 | **WaitingForPayment** | Chờ thanh toán, đã tạo payment request |
| 3 | **PaymentProcessing** | Khách hàng đang thực hiện thanh toán |
| 4 | **PaymentPending** | Thanh toán offline, chờ xác nhận thủ công |
| 5 | **PaymentSucceed** | Thanh toán thành công |
| 6 | **PaymentFailed** | Thanh toán thất bại |
| 7 | **PaymentCancelled** | Thanh toán bị hủy |
| 8 | **PaymentRefunded** | Đã hoàn tiền |
| 9 | **Expired** | Đơn hàng hết hạn (không thanh toán trong thời gian quy định) |
| 10 | **Failed** | Xử lý đơn hàng thất bại |
| 11 | **Canceled** | Đơn hàng bị hủy |

## 3. Các API chính

### 3.1. API Đơn hàng (Public)

#### Tạo đơn hàng
- **Endpoint**: `POST /order/create`
- **Headers**: `X-Customer-User-Id` (required)
- **Request Body**: CreateOrderRequest
- **Response**: OrderResponse

#### Tạo thanh toán
- **Endpoint**: `POST /order/payment/create`
- **Request Body**: OrderIdOnlyRequest
- **Response**: Encoded Payment ID

#### Cập nhật thông tin đơn hàng
- **Endpoint**: `POST /order/update-info`
- **Request Body**: UpdateOrderInfoRequest
- **Response**: OrderResponse

#### Lấy thông tin đơn hàng
- **Endpoint**: `GET /order/fetch?id={orderId}`
- **Parameters**: id (encoded order ID)
- **Response**: OrderResponse

#### Lấy tóm tắt đơn hàng
- **Endpoint**: `GET /order/fetch/summary?id={orderId}`
- **Parameters**: id (encoded order ID)
- **Response**: OrderSummary

#### Tính giá tạm thời
- **Endpoint**: `POST /order/price/tentative`
- **Request Body**: CalculateTentativePriceRequest
- **Response**: TentativePriceResponse

#### Lấy custom field template
- **Endpoint**: `POST /order/custom-field/fetch`
- **Request Body**: LoadCustomFieldRequest
- **Response**: List<CustomFieldTmpl>

### 3.2. API Private (Admin)

#### Lấy đơn hàng theo show và session
- **Endpoint**: `POST /private/order/fetch/all`
- **Request Body**: LoadOrderByShowAndSessionRequest
- **Response**: List<OrderSummary>

#### Lấy đơn hàng thanh toán offline
- **Endpoint**: `POST /private/order/offline-payment/fetch/summary`
- **Request Body**: LoadOrderByShowAndSessionRequest
- **Response**: List<OrderSummary>

#### Lấy đơn hàng theo reservation IDs
- **Endpoint**: `POST /private/order/fetch/reservation`
- **Request Body**: LoadOrderByReservationRequest
- **Response**: List<OrderSummary>

#### Xác nhận thanh toán offline
- **Endpoint**: `POST /private/order/offline-payment/confirm`
- **Request Body**: OrderIdOnlyRequest
- **Response**: Success message

#### Hủy thanh toán offline
- **Endpoint**: `POST /private/order/offline-payment/cancel`
- **Request Body**: OrderIdOnlyRequest
- **Response**: Success message

#### Hủy đơn hàng
- **Endpoint**: `POST /private/order/cancel`
- **Request Body**: OrderIdOnlyRequest
- **Response**: Success message

### 3.3. API Báo cáo và Thống kê

#### Thống kê hóa đơn (Private)
- **Endpoint**: `POST /private/order/statistic/load`
- **Request Body**: LoadInvoiceStatisticsRequest
- **Response**: Invoice statistics

#### Thống kê hóa đơn (Internal)
- **Endpoint**: `POST /internal/order/statistic/load`
- **Request Body**: LoadInvoiceStatisticsRequest
- **Response**: Invoice statistics

#### Báo cáo chi tiết (Private)
- **Endpoint**: `POST /private/order/report/load`
- **Request Body**: LoadReportRequest
- **Response**: List<OrderResponse>

#### Báo cáo chi tiết (Internal)
- **Endpoint**: `POST /internal/order/report/load`
- **Request Body**: LoadReportRequest
- **Response**: List<OrderResponse>

### 3.4. API Khuyến mãi

#### Tạo khuyến mãi
- **Endpoint**: `POST /private/order/promotion/create`
- **Request Body**: CreatePromotionRequest
- **Response**: Promotion document

#### Lấy danh sách khuyến mãi
- **Endpoint**: `GET /private/order/plugin/promotions?showId={showId}&sessionId={sessionId}`
- **Parameters**: showId, sessionId
- **Response**: List of promotions

#### Cập nhật khuyến mãi
- **Endpoint**: `POST /private/order/promotions/update`
- **Request Body**: UpdateOrderPromotionRequest
- **Response**: Success/Failure

#### Import promotion codes
- **Endpoint**: `POST /private/order/promotion/import`
- **Response**: Success

#### Reload promotion plugin
- **Endpoint**: `GET /private/order/plugin/promotion/reload?className={className}`
- **Response**: Success

### 3.5. API Custom Fields

#### Cập nhật custom field mapping
- **Endpoint**: `POST /private/order/custom/fields/mapping/update`
- **Request Body**: UpdateOrderCustomFieldsRequest
- **Response**: Updated custom fields list

### 3.6. API Thông báo

#### Gửi lại thông báo
- **Endpoint**: `POST /private/order/notify/resend`
- **Request Body**: List<String> (encoded order IDs)
- **Response**: Map of order ID to send result

### 3.7. API Failed Events (Quản lý sự kiện thất bại)

#### Lấy danh sách failed events
- **Endpoint**: `GET /private/order/failed-events?limit={limit}`
- **Parameters**: limit (1-100, default: 50)
- **Response**: List of failed events

#### Lấy chi tiết failed event
- **Endpoint**: `GET /private/order/failed-events/{id}`
- **Response**: Failed event details

#### Retry failed event
- **Endpoint**: `POST /private/order/failed-events/{id}/retry`
- **Response**: Retry result with Kafka metadata

#### Disable failed event
- **Endpoint**: `PATCH /private/order/failed-events/{id}/disable`
- **Response**: Success message

#### Lấy thống kê failed events
- **Endpoint**: `GET /private/order/failed-events/stats`
- **Response**: Statistics (total, by retry count, by status, oldest/newest)

## 4. Flow xử lý chính

### 4.1. Flow tạo đơn hàng (Create Order)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         CREATE ORDER FLOW                                   │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │      CLIENT         │
                              │  POST /order/create │
                              │  X-Customer-User-Id │
                              └──────────┬──────────┘
                                         │
                                         ▼
                         ┌──────────────────────────────┐
                         │   OrderController.createOrder │
                         │  - Validate header            │
                         │  - Map DTO → CreateOrderInput │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │ OrderCreationService          │
                         │  .executeCreateOrder()        │
                         └──────────────┬───────────────┘
                                        │
                ┌───────────────────────┼───────────────────────┐
                │                       │                       │
                ▼                       ▼                       ▼
   ┌────────────────────┐  ┌────────────────────┐  ┌────────────────────┐
   │ ReservationGateway │  │    ShowCache       │  │ TicketGateway      │
   │ - getReservation() │  │ - getShow()        │  │ - getTicketsByIds()│
   └─────────┬──────────┘  └─────────┬───────────┘  └─────────┬──────────┘
             │                        │                        │
             └────────────────────────┼────────────────────────┘
                                      │
                                      ▼
                         ┌──────────────────────────────┐
                         │  Promotion Calculation        │
                         │  CalculatePromotionPlugin     │
                         │  - discountByDates            │
                         │  - discountByTicketAmounts    │
                         │  - promotionCodes             │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │     OrderDBService            │
                         │  - createOrder()              │
                         │  - MongoDB: order collection  │
                         └──────────────┬───────────────┘
                                        │
                ┌───────────────────────┼───────────────────────┐
                │                       │                       │
                ▼                       ▼                       ▼
   ┌────────────────────┐  ┌────────────────────┐  ┌────────────────────┐
   │  total > 0         │  │  total = 0         │  │  PaymentPending    │
   │  Status: Created   │  │  Status: Payment   │  │  (offline payment) │
   └─────────┬──────────┘  │  Pending           │  └─────────┬──────────┘
             │             └────────────────────┘            │
             │                                                │
             └────────────────────────┬───────────────────────┘
                                      │
                                      ▼
                         ┌──────────────────────────────┐
                         │    Notification & Events      │
                         │  - Email (NotificationGateway)│
                         │  - Job (JobrunrGateway)       │
                         │  - Kafka (OrderSucceedEvent)  │
                         └───────────────────────────────┘
```

### 4.2. Flow tạo thanh toán (Create Payment)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         CREATE PAYMENT FLOW                                 │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │      CLIENT         │
                              │ POST /order/payment │
                              │       /create       │
                              └───���──────┬──────────┘
                                         │
                                         ▼
                         ┌──────────────────────────────┐
                         │  OrderController.createPayment│
                         │  - Decode orderId             │
                         │  - Validate status = Created  │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │      OrderDBService           │
                         │  - loadOrderById()            │
                         └──────────────┬───────────────┘
                                        │
           ┌────────────────────────────┼────────────────────────────┐
           │                            │                            │
           ▼                            ▼                            ▼
┌─────────────────────┐    ┌─────────────────────┐    ┌─────────────────────┐
│ReservationGateway   │    │    ShowCache        │    │  TicketGateway      │
│ - getReservation()  │    │ - getShowSession()  │    │ - getTicketsByIds() │
└─────────┬───────────┘    └──────────┬──────────┘    └──────────┬──────────┘
          │                           │                           │
          └───────────────────────────┼───────────────────────────┘
                                      │
                                      ▼
                         ┌──────────────────────────────┐
                         │    PaymentGateway             │
                         │    .createPayment()           │
                         │  - Payment Service            │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │      OrderDBService           │
                         │  - setPaymentId()             │
                         │  - Status: WaitingForPayment  │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │   RESPONSE                   │
                         │   Encoded Payment ID         │
                         └──────────────────────────────┘
```

### 4.3. Flow xác nhận thanh toán offline

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    OFFLINE PAYMENT CONFIRM FLOW                             │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │       ADMIN         │
                              │ POST /offline-      │
                              │ payment/confirm     │
                              └──────────┬──────────┘
                                         │
                                         ▼
                         ┌──────────────────────────────┐
                         │ NotifyOrderController        │
                         │ .onOfflinePaymentChangeStatus│
                         │ - Decode orderId             │
                         │ - Check processing lock      │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │      OrderDBService           │
                         │  - loadOrderById()            │
                         │  - Validate status            │
                         └──────────────┬───────────────┘
                                        │
                ┌───────────────────────┼───────────────────────┐
                │                       │                       │
                ▼                       ▼                       ▼
   ┌────────────────────┐  ┌────────────────────┐  ┌────────────────────┐
   │  Status = Canceled │  │ PaymentSucceed     │  │ From PaymentPending│
   │  ────────────────  │  │  ────────────────  │  │  ───────────────── │
   │ OrderFailedHandler │  │ ReservationGateway │  │ ReservationGateway │
   │  .onOrderFailed()  │  │  .notifyStatus()   │  │  .notifyStatus()   │
   └────────────────────┘  └─────────┬──────────┘  └─────────┬──────────┘
                                     │                        │
                                     ▼                        ▼
                          ┌────────────────────┐  ┌────────────────────┐
                          │  OrderDBService    │  │  OrderDBService    │
                          │  - updateStatus()  │  │  - updateStatus()  │
                          │  PaymentSucceed    │  │  PaymentSucceed    │
                          └─────────┬──────────┘  └─────────┬──────────┘
                                    │                       │
                                    └───────────┬───────────┘
                                                │
                                                ▼
                                   ┌────────────────────────┐
                                   │  OrderSucceedProducer  │
                                   │  - Kafka Event         │
                                   │  - Email Notification  │
                                   └────────────────────────┘
```

### 4.4. Flow xử lý thanh toán thất bại

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       ORDER FAILED HANDLING FLOW                            │
└─────────────────────────────────────────��───────────────────────────────────┘

                              ┌─────────────────────┐
                              │  TRIGGER EVENTS     │
                              │  - PaymentFailed    │
                              │  - Expired          │
                              │  - Canceled         │
                              │  - Manual Cancel    │
                              └──────────┬──────────┘
                                         │
                                         ▼
                         ┌──────────────────────────────┐
                         │   OrderFailedHandler          │
                         │    .onOrderFailed()           │
                         └──────────────┬───────────────┘
                                        │
                ┌───────────────────────┼───────────────────────┐
                │                       │                       │
                ▼                       ▼                       ▼
   ┌────────────────────┐  ┌────────────────────┐  ┌────────────────────┐
   │ Promotion Plugin   │  │   OrderDBService   │  │  Notification      │
   │                    │  │                    │  │                    │
   │ revertPromotion    │  │ updateStatus()     │  │ sendMailOrder()    │
   │ Discount()         │  │ Failed/Expired/    │  │ (async)            │
   │                    │  │ Canceled           │  │                    │
   │ - Increment        │  └─────────┬──────────┘  └─────────┬──────────┘
   │   promotion code   │            │                       │
   │   quantity         │            │                       │
   │ - Increment        │            │                       │
   │   combo quantity   │            │                       │
   └─────────┬──────────┘            │                       │
             │                       │                       │
             └───────────────────────┼───────────────────────┘
                                     │
                                     ▼
                         ┌──────────────────────────────┐
                         │   ReservationGateway         │
                         │   .notifyOrderStatusChanged() │
                         │   - Failed/Expired/Canceled   │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │   JobrunrGateway (fallback)  │
                         │   - Schedule retry if direct │
                         │     notification fails       │
                         └──────────────────────────────┘
```

### 4.5. Flow quản lý Failed Events

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       FAILED EVENTS MANAGEMENT FLOW                         │
└─────────────────────────────────────────────────────────────────────────────┘

         ┌─────────────────────────────┐        ┌─────────────────────────────┐
         │    KAFKA EVENT FAILURE      │        │    MANUAL INTERVENTION     │
         │                             │        │                             │
         │ OrderSucceedProducer        │        │ FailedEventController      │
         │   .sendEvent() fails        │        │   - getFailedEvents()      │
         └──────────────┬──────────────┘        │   - retryFailedEvent()     │
                        │                        │   - disableFailedEvent()   │
                        ▼                        └────────────┬──────────────┘
         ┌─────────────────────────────┐                       │
         │ FailedOrderSucceedEvent     │                       │
         │ Storage                     │                       │
         │   .saveFailedEvent()        │                       │
         │                             │                       │
         │ - failureReason             │                       │
         │ - retryCount                │                       │
         │ - nextRetryAt               │                       │
         │ - status (PENDING/DISABLED) │                       │
         └──────────────┬──────────────┘                       │
                        │                                      │
                        ▼                                      │
         ┌─────────────────────────────┐                       │
         │  OrderSucceedRetryService   │◄──────────────────────┘
         │  (Background Job)           │
         │                             │
         │  1. Scan failed events      │
         │  2. Exponential backoff     │
         │     (5min → 10min → ...     │
         │        → 24h max)           │
         │  3. Retry send to Kafka     │
         │  4. Mark as PROCESSED       │
         │     if success              │
         └──────────────┬──────────────┘
                        │
                        ▼
         ┌─────────────────────────────┐
         │      RESPONSE               │
         │                             │
         │  GET /failed-events         │
         │  GET /failed-events/{id}    │
         │  POST /failed-events/{id}   │
         │           /retry            │
         │  PATCH /failed-events/{id}  │
         │           /disable          │
         │  GET /failed-events/stats   │
         └─────────────────────────────┘
```

## 5. Cơ chế xác thực và bảo mật

### 5.1. Phân loại API

| Loại | Yêu cầu | Ví dụ |
|------|---------|-------|
| **Public APIs** | X-Customer-User-Id header | /order/create, /order/fetch |
| **Private APIs** | Admin role | /private/order/* |
| **Internal APIs** | Service-to-service | /internal/order/* |

### 5.2. Xác thực người dùng

- **Header yêu cầu**: `X-Customer-User-Id`
- **Mã hóa ID**: Sử dụng Checksum class để encode/decode các ID nhạy cảm
- **Secret Key**: Được cấu hình trong `application.conf` (16 ký tự)

### 5.3. Bảo mật dữ liệu

- **Mã hóa ID**: Tất cả ID được encode trước khi trả về client
- **Validation**: Kiểm tra đầu vào nghiêm ngặt cho tất cả API
- **Error Handling**: Không expose thông tin nhạy cảm trong error messages
- **Database Security**: Sử dụng MongoDB với connection string được bảo mật

### 5.4. Bảo vệ thông tin thanh toán

- **Payment Gateway**: Tích hợp với Payment Service riêng biệt
- **No Direct Payment Data**: Order Service không lưu trữ thông tin thẻ tín dụng
- **Payment ID Encryption**: Mã hóa Payment ID khi trả về

## 6. Exception Handling

### 6.1. Custom Exceptions

| Exception | HTTP Status | Mô tả |
|-----------|-------------|-------|
| **OrderValidationException** | 400 Bad Request | Dữ liệu đầu vào không hợp lệ |
| **ResourceNotFoundException** | 404 Not Found | Không tìm thấy tài nguyên |
| **OrderCreationException** | 500 Internal Server Error | Lỗi khi tạo đơn hàng |

### 6.2. Xử lý lỗi trong Controller

```kotlin
try {
    val orderDoc = orderCreationService.executeCreateOrder(createOrderInput)
    call.respond(orderDoc.toResponse())
} catch (e: ResourceNotFoundException) {
    call.respond(HttpStatusCode.NotFound, e.message ?: "Yêu cầu đặt vé không tồn tại")
} catch (e: OrderValidationException) {
    call.respond(HttpStatusCode.BadRequest, e.message ?: "Dữ liệu không hợp lệ")
} catch (e: OrderCreationException) {
    call.respond(HttpStatusCode.InternalServerError, e.message ?: "Tạo đơn hàng thất bại")
} catch (e: Throwable) {
    orderFailedHandler.onCreateOrderFailed(createOrderInput.reservationId)
    call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống không mong muốn")
}
```

## 7. Database Schema

### 7.1. Collections

| Collection | Mô tả |
|------------|-------|
| **order** | Lưu trữ thông tin đơn hàng |
| **order-promotion-plugin-config** | Cấu hình plugin khuyến mãi |
| **promotion-code-quantity** | Số lượng mã giảm giá |
| **promotion-combo-quantity** | Số lượng combo khuyến mãi |
| **order-custom-field-mapping** | Mapping custom fields theo show/session |
| **failed-order-succeed-event** | Lưu trữ failed Kafka events |

### 7.2. Indexes (tự động tạo)

```kotlin
// Order collection
- code (unique): Mã đơn hàng duy nhất
- reservationId (unique): Mỗi reservation chỉ có một order
```

## 8. Cấu hình

### 8.1. Order Configuration (application.conf)

```json
{
  "orderConfig": {
    "expireInMinute": 5,
    "scanExpireInMinute": 1
  }
}
```

- **expireInMinute**: Thời gian hết hạn của đơn hàng chờ thanh toán
- **scanExpireInMinute**: Tần suất quét các đơn hàng hết hạn

### 8.2. Caching

| Cache | Mô tả |
|-------|-------|
| **ShowCache** | Cache thông tin show và session |
| **CalculatePromotionClassesCache** | Cache instance của promotion plugins |

## 9. Kafka Integration

### 9.1. Topics

| Topic | Producer | Mô tả |
|-------|----------|-------|
| **order.succeed.topic** | OrderSucceedProducer | Event khi order thành công |

### 9.2. Failed Event Handling

- **Retry Strategy**: Exponential backoff (5min, 10min, 20min, ..., max 24h)
- **Max Retries**: Không giới hạn (có thể disable thủ công)
- **Storage**: MongoDB collection `failed-order-succeed-event`

## 10. Ví dụ Request và Response

### 10.1. Tạo đơn hàng

**Request:**
```json
POST /order/create
Headers: {
  "X-Customer-User-Id": "user123",
  "Content-Type": "application/json"
}

{
  "reservationId": "reservation123",
  "name": "Nguyễn Văn A",
  "phone": "0901234567",
  "email": "nguyenvana@email.com",
  "delivery": {
    "deliveryName": "Nguyễn Văn A",
    "deliveryPhone": "0901234567",
    "provinceName": "Hồ Chí Minh",
    "districtName": "Quận 1",
    "wardName": "Phường Bến Nghé",
    "address": "123 Đường ABC"
  },
  "customFields": [
    {
      "id": "field1",
      "value": "Giá trị tùy chỉnh"
    }
  ],
  "promotionCode": "DISCOUNT10"
}
```

**Response:**
```json
{
  "id": "encoded_order_id",
  "code": "ORD001234",
  "status": "Created",
  "reservationId": "reservation123",
  "showId": "show123",
  "sessionId": "session123",
  "customerName": "Nguyễn Văn A",
  "customerPhone": "0901234567",
  "customerEmail": "nguyenvana@email.com",
  "tickets": [
    {
      "ticketId": "ticket123",
      "capacityId": "capacity123",
      "quantity": 2,
      "price": 500000
    }
  ],
  "total": 450000,
  "createdAt": 1703123456789,
  "delivery": {
    "deliveryName": "Nguyễn Văn A",
    "deliveryPhone": "0901234567",
    "provinceName": "Hồ Chí Minh",
    "districtName": "Quận 1",
    "wardName": "Phường Bến Nghé",
    "address": "123 Đường ABC"
  }
}
```

### 10.2. Xác nhận thanh toán offline

**Request:**
```json
POST /private/order/offline-payment/confirm
{
  "orderId": "encoded_order_id"
}
```

**Response:**
```json
{
  "message": "Đơn hàng đã được xác nhận",
  "orderId": "encoded_order_id",
  "status": "PaymentSucceed"
}
```

### 10.3. Retry Failed Event

**Request:**
```
POST /private/order/failed-events/failed_event_id/retry
```

**Response:**
```json
{
  "message": "Event đã được gửi thành công lên Kafka",
  "orderCode": "ORD001234",
  "topic": "order.succeed.topic",
  "partition": 0,
  "offset": 12345
}
```

### 10.4. Lấy thống kê Failed Events

**Request:**
```
GET /private/order/failed-events/stats
```

**Response:**
```json
{
  "total": 10,
  "byRetryCount": {
    "0": 3,
    "1": 5,
    "2": 2
  },
  "byStatus": {
    "PENDING": 8,
    "RETRYING": 1,
    "PROCESSED": 1
  },
  "oldestEvent": "2026-06-01T10:00:00Z",
  "newestEvent": "2026-06-03T15:30:00Z"
}
```

## 11. Dependencies

| Service | Loại tích hợp | Mục đích |
|---------|---------------|----------|
| **Payment Service** | Gateway | Tạo và quản lý thanh toán |
| **Ticket Service** | Gateway | Lấy thông tin vé |
| **Show Service** | Gateway + Cache | Lấy thông tin sự kiện |
| **Reservation Service** | Gateway | Quản lý reservation |
| **Notification Service** | Gateway | Gửi email |
| **JobRunr Service** | Gateway | Background jobs |

## 12. Hệ thống Khuyến mãi (Promotion System)

### 12.1. Tổng quan Kiến trúc Plugin

Hệ thống khuyến mãi được xây dựng theo kiến trúc Plugin, cho phép mở rộng linh hoạt các loại khuyến mãi mới mà không cần sửa code core.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PROMOTION PLUGIN ARCHITECTURE                            │
└─────────────────────────────────────────────────────────────────────────────┘

                            ┌─────────────────────┐
                            │ CalculatePromotion  │ (Interface)
                            │      (Interface)    │
                            └──────────┬──────────┘
                                       │
                   ┌───────────────────┼───────────────────┐
                   │                   │                   │
                   ▼                   ▼                   ▼
        ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
        │  MetaPromoPlugin │ │  Other Plugin 1  │ │  Other Plugin 2  │
        │  (Implementation)│ │  (Future)        │ │  (Future)        │
        └──────────────────┘ └──────────────────┘ └──────────────────┘
                   │
                   ▼
        ┌──────────────────────────────────────────────────────────────┐
        │                    Plugin Configuration                       │
        │  ┌─────────────────┐  ┌─────────────────┐  ┌───────────────┐  │
        │  │ discountByDates │  │ discountByTicket│  │ promotionCodes│  │
        │  │   (Early Bird)  │  │    Amounts      │  │    (Coupon)   │  │
        │  └─────────────────┘  └─────────────────┘  └───────────────┘  │
        └──────────────────────────────────────────────────────────────┘
```

### 12.2. CalculatePromotion Interface

```kotlin
interface CalculatePromotion {
    // Tính toán giảm giá tạm thời (preview)
    suspend fun calculatePromotionDiscount(input: CalculatePromotionInput): PromotionData?
    
    // Áp dụng giảm giá vào đơn hàng (decrement quantity)
    suspend fun applyPromotionDiscount(input: ApplyPromotionInput)
    
    // Hoàn lại giảm giá khi order thất bại (increment quantity)
    suspend fun revertPromotionDiscount(input: RevertPromotionInput)
    
    // Tạo khuyến mãi mới
    suspend fun createPromotion(input: CreatePromotionInput): Boolean
    
    // Xóa khuyến mãi
    suspend fun deletePromotion(input: DeletePromotionInput): Boolean
}
```

### 12.3. Các loại Khuyến mãi

#### 12.3.1. Khuyến mãi theo ngày (DiscountByDate - Early Bird)

Loại khuyến mãi này áp dụng giảm giá dựa trên thời gian mua vé trước ngày diễn ra sự kiện.

| Thuộc tính | Kiểu | Mô tả |
|------------|------|-------|
| name | String | Tên khuyến mãi |
| date | Long | Thời điểm kết thúc (timestamp) |
| discountPercent | Long? | Giảm giá theo % |
| discountPrice | Long? | Giảm giá theo số tiền cố định |

**Ví dụ configuration:**
```json
{
  "discountByDates": [
    {
      "name": "Early Bird 10%",
      "date": 1704067200000,
      "discountPercent": 10,
      "discountPrice": null
    },
    {
      "name": "Early Bird Giảm 50k/vé",
      "date": 1704153600000,
      "discountPercent": null,
      "discountPrice": 50000
    }
  ]
}
```

**Logic xử lý:**
- So sánh `currentDate` với `date` trong configuration
- Chỉ áp dụng nếu `date > currentDate` (khuyến mãi còn hiệu lực)
- Tính discount: `discountPercent * priceOriginal / 100` hoặc `totalTicket * discountPrice`

#### 12.3.2. Khuyến mãi theo số lượng vé (DiscountByTicketAmount - Combo)

Loại khuyến mãi này áp dụng giảm giá khi mua số lượng vé nhất định.

| Thuộc tính | Kiểu | Mô tả |
|------------|------|-------|
| ticketAmountBelow | Int | Số lượng vé tối thiểu |
| ticketAmountAbove | Int? | Số lượng vé tối đa (nullable = không giới hạn) |
| discountPercent | Long? | Giảm giá theo % |
| discountPrice | Long? | Giảm giá theo số tiền cố định |
| needCheckQuantity | Boolean? | Có kiểm tra số lượng khuyến mãi không |
| originalQuantity | Int? | Số lượng khuyến mãi ban đầu |
| quantityType | QuantityType | Loại tính số lượng (Ticket/Combo) |
| id | String? | ID của PromotionComboQuantity |

**QuantityType:**
- `Ticket`: Mỗi vé được tính là 1 đơn vị
- `Combo`: Toàn bộ đơn hàng được tính là 1 combo

**Ví dụ configuration:**
```json
{
  "discountByTicketAmounts": [
    {
      "ticketAmountBelow": 2,
      "ticketAmountAbove": 5,
      "discountPercent": 5,
      "discountPrice": null,
      "needCheckQuantity": true,
      "originalQuantity": 100,
      "quantityType": "Ticket"
    },
    {
      "ticketAmountBelow": 6,
      "ticketAmountAbove": null,
      "discountPercent": null,
      "discountPrice": 100000,
      "needCheckQuantity": false,
      "quantityType": "Combo"
    }
  ]
}
```

**Logic xử lý:**
1. Tính tổng số vé trong đơn hàng (`totalTicket`)
2. Kiểm tra điều kiện:
   - Nếu có `ticketAmountAbove`: `ticketAmountBelow <= totalTicket <= ticketAmountAbove`
   - Nếu không có `ticketAmountAbove`: `totalTicket >= ticketAmountBelow`
3. Nếu `needCheckQuantity = true`: kiểm tra số lượng khuyến mãi còn trong `PromotionComboQuantity`
4. Chọn discount cao nhất nếu có nhiều rule khớp

#### 12.3.3. Khuyến mãi theo loại vé (DiscountBySingleCatAmount)

Loại khuyến mãi này áp dụng giảm giá khi mua số lượng vé của một hoặc nhiều loại vé (price category) nhất định.

| Thuộc tính | Kiểu | Mô tả |
|------------|------|-------|
| ticketAmountBelow | Int | Số lượng vé tối thiểu |
| ticketAmountAbove | Int? | Số lượng vé tối đa |
| priceCats | List<String> | Danh sách mã loại vé (empty = any) |
| discountPercent | Long? | Giảm giá theo % |
| discountPrice | Long? | Giảm giá theo số tiền cố định |

**Ví dụ configuration:**
```json
{
  "discountBySingleCatAmounts": [
    {
      "ticketAmountBelow": 3,
      "ticketAmountAbove": null,
      "priceCats": ["VIP", "NORMAL"],
      "discountPercent": 15,
      "discountPrice": null
    },
    {
      "ticketAmountBelow": 2,
      "ticketAmountAbove": 4,
      "priceCats": [],
      "discountPercent": null,
      "discountPrice": 30000
    }
  ]
}
```

**Logic xử lý:**
1. Nếu `priceCats` empty: áp dụng cho bất kỳ loại vé nào
2. Nếu `priceCats` có giá trị: chỉ áp dụng khi có đủ các loại vé trong giỏ
3. Tính discount dựa trên tổng giá trị của các vé khớp điều kiện

#### 12.3.4. Khuyến mãi theo mã giảm giá (PromotionCode - Coupon)

Loại khuyến mãi này áp dụng khi khách hàng nhập mã giảm giá.

| Thuộc tính | Kiểu | Mô tả |
|------------|------|-------|
| code | String | Mã giảm giá |
| name | String | Tên khuyến mãi |
| discountPercent | Long? | Giảm giá theo % |
| discountPrice | Long? | Giảm giá theo số tiền cố định |
| needCheckQuantity | Boolean? | Có giới hạn số lượng mã không |
| originalQuantity | Int? | Số lượng mã ban đầu |

**Ví dụ configuration:**
```json
{
  "promotionCodes": [
    {
      "code": "WELCOME50",
      "name": "Giảm 50k cho đơn hàng đầu tiên",
      "discountPercent": null,
      "discountPrice": 50000,
      "needCheckQuantity": true,
      "originalQuantity": 1000
    },
    {
      "code": "VIP10",
      "name": "VIP giảm 10%",
      "discountPercent": 10,
      "discountPrice": null,
      "needCheckQuantity": false
    }
  ]
}
```

**Logic xử lý:**
1. So sánh `input.promotionCode` với các code trong config (case-insensitive, trim)
2. Nếu `needCheckQuantity = true`: kiểm tra số lượng còn trong `PromotionCodeQuantity`
3. Tính discount:
   - `discountPrice`: giảm trực tiếp số tiền
   - `discountPercent`: `discountPercent * priceTotal / 100` (tính trên giá sau các khuyến mãi khác)

### 12.4. Data Models

#### 12.4.1. Promotion (Collection: order-promotion-plugin-config)

```kotlin
data class Promotion(
    var id: String,                    // ObjectId
    var showId: String,                // ObjectId - Show ID
    val sessionId: String,             // ObjectId - Session ID  
    val pluginName: String,            // Tên class plugin (e.g., "metaticket.order.calculate.promotion.meta_promo.MetaPromoPlugin")
    val configuration: String          // JSON configuration
)
```

#### 12.4.2. PromotionCodeQuantity (Collection: promotion-code-quantity)

```kotlin
data class PromotionCodeQuantity(
    var id: String,                    // ObjectId
    var showId: String,                // ObjectId
    val sessionId: String,             // ObjectId
    val code: String,                  // Mã giảm giá
    val originalQuantity: Int,         // Số lượng ban đầu
    val quantity: Int,                 // Số lượng còn lại
    val lastModified: Date             // Thời gian cập nhật cuối
)
```

#### 12.4.3. PromotionComboQuantity (Collection: promotion-combo-quantity)

```kotlin
data class PromotionComboQuantity(
    var id: String,                    // ObjectId
    var showId: String,                // ObjectId
    val sessionId: String,             // ObjectId
    val originalQuantity: Int,         // Số lượng combo ban đầu
    val quantity: Int,                 // Số lượng combo còn lại
    val lastModified: Date             // Thời gian cập nhật cuối
)
```

### 12.5. Flow xử lý Khuyến mãi

#### 12.5.1. Flow tính giá tạm thời (Calculate Tentative Price)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                  CALCULATE TENTATIVE PRICE FLOW                             │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │       CLIENT        │
                              │ POST /order/price   │
                              │   /tentative        │
                              └──────────┬──────────┘
                                         │
                                         ▼
                         ┌──────────────────────────────┐
                         │ OrderController              │
                         │ .calculateTentativePrice()   │
                         └──────────────┬───────────────┘
                                        │
          ┌─────────────────────────────┼─────────────────────────────┐
          │                             │                             │
          ▼                             ▼                             ▼
┌─────────────────────┐    ┌─────────────────────┐    ┌─────────────────────┐
│   TicketGateway     │    │   PromotionDBService│    │  ShowCache          │
│ - getTicketsByIds() │    │ - loadPromotions()  │    │  - getShow()        │
└─────────┬───────────┘    └─────────┬────────────┘    └─────────────────────┘
          │                          │                              │
          └──────────────────────────┼──────────────────────────────┘
                                     │
                                     ▼
                         ┌──────────────────────────────┐
                         │  PROMOTION CALCULATION        │
                         │  Loop through promotions      │
                         │  - Load plugin (reflection)   │
                         │  - Create CalculatePromotion  │
                         │    Input                      │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────���
                         │  MetaPromoPlugin             │
                         │  .calculatePromotionDiscount │
                         └──────────────┬───────────────┘
                                        │
      ┌─────────────────────────────────┼─────────────────────────────────┐
      │                                 │                                 │
      ▼                                 ▼                                 ▼
┌───────────────────┐     ┌────────────────────┐          ┌────────────────────┐
│  discountByDates  │     │discountByTicket    │          │  promotionCodes    │
│  (Early Bird)     │     │Amounts (Combo)     │          │  (Coupon)          │
│                   │     │                    │          │                    │
│ - Check date      │     │ - Check ticket     │          │ - Match code       │
│   > currentDate   │     │   amount           │          │ - Validate qty     │
│ - Apply discount  │     │ - Apply discount   │          │ - Apply discount   │
└─────────┬─────────┘     └─────────┬───────────┘          └─────────┬─────────┘
          │                         │                                  │
          └─────────────────────────┼──────────────────────────────────┘
                                    │
                                    ▼
                         ┌──────────────────────────────┐
                         │  SELECT BEST PROMOTION        │
                         │  - Compare priceDiscount      │
                         │  - Choose max discount        │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │   RESPONSE                   │
                         │   TentativePriceResponse     │
                         │   - priceOriginal            │
                         │   - priceDiscount            │
                         │   - priceTotal               │
                         │   - promotionDetails         │
                         └──────────────────────────────┘
```

#### 12.5.2. Flow áp dụng khuyến mãi khi tạo đơn hàng

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    APPLY PROMOTION FLOW                                     │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │  ORDER CREATION     │
                              │  SUCCESS            │
                              └──────────┬──────────┘
                                         │
                                         ▼
                         ┌──────────────────────────────┐
                         │  MetaPromoPlugin             │
                         │  .applyPromotionDiscount()   │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  Validate PromotionData      │
                         │  - Check for errors          │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  LOOP PromotionDetails       │
                         └──────────────┬───────────────┘
                                        │
          ┌─────────────────────────────┼─────────────────────────────┐
          │                             │                             │
          ▼                             ▼                             │
┌─────────────────────┐     ┌─────────────────────┐                   │
│   Type = COUPON     │     │   Type = COMBO      │                   │
│                     │     │                     │                   │
│ PromotionCodeQuantity│    │ PromotionComboQuantity                   │
│         DBService   │     │        DBService   │                   │
│                     │     │                     │                   │
│ .decrementPromo    │     │ .decrementPromo    │                   │
│    CodeQuantity()  │     │    ComboQuantity() │                   │
└─────────┬──────────┘     └──────────┬──────────┘                   │
          │                           │                               │
          └───────────────────────────┼───────────────────────────────┘
                                      │
                                      ▼
                         ┌──────────────────────────────┐
                         │  CHECK FAILED INDEX          │
                         │  - If failedIndex > -1       │
                         │    → ROLLBACK                │
                         │  - Else → Success            │
                         └──────────────┬───────────────┘
                                        │
          ┌─────────────────────────────┼─────────────────────────────┐
          │                             │                             │
          ▼                             ▼                             ▼
┌─────────────────────���     ┌─────────────────────┐    ┌─────────────────────┐
│   ROLLBACK:        │     │   SUCCESS:          │    │   FAILED:           │
│                     │     │                     │    │                     │
│  Increment back     │     │  Set applyPromo    │    │  applyPromoFailed   │
│  - COUPON qty      │     │  Failed = false    │    │  = true             │
│  - COMBO qty       │     │                     │    │                     │
└─────────────────────┘     └─────────────────────┘    └─────────────────────┘
```

#### 12.5.3. Flow hoàn lại khuyến mãi (Revert)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    REVERT PROMOTION FLOW                                    │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────────┐
                              │  TRIGGER EVENTS     │
                              │  - PaymentFailed    │
                              │  - Expired          │
                              │  - Canceled         │
                              │  - Failed           │
                              └──────────┬──────────┘
                                         │
                                         ▼
                         ┌──────────────────────────────┐
                         │  OrderFailedHandler          │
                         │  .onOrderFailed()            │
                         │  .onOrderExpired()           │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  OrderDBService              │
                         │  - loadOrderById()           │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  PromotionDBService          │
                         │  - loadPromotion()           │
                         │  - Get pluginConfigId        │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  CalculatePromotionClasses   │
                         │  .get(pluginName)            │
                         │  - Load/Create plugin        │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  MetaPromoPlugin             │
                         │  .revertPromotionDiscount()  │
                         └──────────────┬───────────────┘
                                        │
                                        ▼
                         ┌──────────────────────────────┐
                         │  LOOP OrderPromotionDetails  │
                         └──────────────┬───────────────┘
                                        │
          ┌─────────────────────────────┼─────────────────────────────┐
          │                             │                             │
          ▼                             ▼                             │
┌─────────────────────┐     ┌─────────────────────┐                   │
│  has promoCode      │     │  has promoCombo     │                   │
│  QuantityId         │     │  QuantityId         │                   │
│                     │     │                     │                   │
│  PromotionCode      │     │  PromotionCombo     │                   │
│  QuantityDBService  │     │  QuantityDBService  │                   │
│                     │     │                     │                   │
│  .increment         │     │  .increment         │                   │
│  PromotionCode      │     │  PromotionCombo     │                   │
│  Quantity()         │     │  Quantity()         │                   │
└─────────────────────┘     └─────────────────────┘                   │
                                                                        │
          ┌─────────────────────────────┴─────────────────────────────┐
          │                                                              │
          ▼                                                              │
┌─────────────────────┐                                                   │
│  ORDER UPDATED      │                                                   │
│  - promotionDetails │                                                   │
│    reverted         │                                                   │
│  - quantity restored│                                                   │
└─────────────────────┘                                                   │
```
   └── Duyệt qua order.promotionDetails:
       ├── Nếu có promotionCodeQuantityId:
       │   └── Gọi promotionCodeQuantityDBService.incrementPromotionCodeQuantity()
       │
       └── Nếu có promotionComboQuantityId:
           └── Gọi promotionComboQuantityDBService.incrementPromotionComboQuantity()
```

### 12.6. Database Services

| Service | Mô tả |
|---------|-------|
| **PromotionDBService** | CRUD cho Promotion collection |
| **PromotionCodeQuantityDBService** | CRUD cho PromotionCodeQuantity, decrement/increment quantity |
| **PromotionComboQuantityDBService** | CRUD cho PromotionComboQuantity, decrement/increment quantity |

**Các method chính:**

```kotlin
// PromotionDBService
suspend fun createPromotion(entity: Promotion): String?
suspend fun loadPromotions(showId: String, sessionId: String): List<Promotion>
suspend fun loadPromotion(id: String): Promotion?
suspend fun deletePromotion(id: String)

// PromotionCodeQuantityDBService
suspend fun createPromotionCodeQuantity(entity: PromotionCodeQuantity): String?
suspend fun loadPromotionCodeQuantity(showId, sessionId, code): PromotionCodeQuantity?
suspend fun decrementPromotionCodeQuantity(id: String): PromotionCodeQuantity?
suspend fun incrementPromotionCodeQuantity(id: String): Boolean

// PromotionComboQuantityDBService
suspend fun createPromotionComboQuantity(entity: PromotionComboQuantity): String?
suspend fun loadPromotionComboQuantity(id: String): PromotionComboQuantity?
suspend fun decrementPromotionComboQuantity(id: String, amount: Int): PromotionComboQuantity?
suspend fun incrementPromotionComboQuantity(id: String, amount: Int): Boolean
```

### 12.7. API liên quan đến Khuyến mãi

| Endpoint | Method | Mô tả |
|----------|--------|-------|
| `/private/order/promotion/create` | POST | Tạo khuyến mãi mới |
| `/private/order/plugin/promotions` | GET | Lấy danh sách khuyến mãi theo show/session |
| `/private/order/promotions/update` | POST | Cập nhật khuyến mãi (create + delete) |
| `/private/order/plugin/promotion/reload` | GET | Reload plugin class |
| `/private/order/promotion/import` | POST | Import promotion codes |

### 12.8. Tạo khuyến mãi (Create Promotion)

**Request:**
```json
POST /private/order/promotion/create
{
  "showId": "show123",
  "sessionId": "session123",
  "pluginName": "metaticket.order.calculate.promotion.meta_promo.MetaPromoPlugin",
  "configuration": "{\"discountByDates\": [...], \"discountByTicketAmounts\": [...], \"promotionCodes\": [...]}"
}
```

**CreatePromotionData - Các loại type:**
- `discountByTicketAmount`: Khuyến mãi theo số lượng vé
- `discountByDate`: Khuyến mãi theo ngày (Early Bird)
- `discountByPromotionCode`: Khuyến mãi theo mã (Coupon đơn lẻ)
- `discountByMultiPromotionCode`: Khuyến mãi theo mã (nhiều mã cùng lúc)

**Input data example:**
```json
[
  {
    "type": "discountByTicketAmount",
    "ticketAmountMin": 3,
    "ticketAmountMax": 5,
    "discountOption": "DiscountPercent",
    "discountValue": 10,
    "needCheckQuantity": true,
    "originalQuantity": 100,
    "quantityOption": "Ticket"
  },
  {
    "type": "discountByPromotionCode",
    "discountOption": "DiscountPrice",
    "discountValue": 50000,
    "needCheckQuantity": true,
    "originalQuantity": 500,
    "code": "SUMMER2024",
    "name": "Giảm 50k mùa hè"
  }
]
```

### 12.9. Caching

- **CalculatePromotionClassesCache**: Cache các instance của plugin
  - Key: pluginName (e.g., "metaticket.order.calculate.promotion.meta_promo.MetaPromoPlugin")
  - Value: CalculatePromotion instance
  - Tránh load lại class qua reflection nhiều lần

### 12.10. Error Handling

| Error | Nguyên nhân | Xử lý |
|-------|-------------|-------|
| "Mã đã được sử dụng hết" | promotionCode quantity = 0 | Hiển thị lỗi, không apply discount |
| "ClassNotFoundException" | Plugin class không tồn tại | Return 400 Bad Request |
| Apply promotion failed | Decrement quantity thất bại | Rollback tất cả đã apply |

---

## 13. Lưu ý đặc biệt

### 13.1. Order Processing Lock

- Sử dụng `isOrderProcessing()` / `releaseOrderProcessing()` để prevent duplicate processing
- Áp dụng cho các operation thay đổi trạng thái order

### 13.2. Compensation Logic

Khi tạo order thất bại:
1. Gọi `orderFailedHandler.onCreateOrderFailed(reservationId)`
2. Notify Reservation Service về status = Failed
3. Schedule retry notification nếu notify fail

---

**Tài liệu này được cập nhật lần cuối**: 03/06/2026
**Version**: 1.2
**Tác giả**: MetaTicket Development Team
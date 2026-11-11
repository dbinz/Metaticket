# Flow Đặt Vé Chi Tiết - MetaTicket System

## Tổng quan Flow Đặt Vé

Flow đặt vé trong hệ thống MetaTicket bao gồm 4 giai đoạn chính:
1. **Ticket Access & Selection** - Truy cập và chọn vé
2. **Ticket Reservation** - Đặt chỗ vé
3. **Order Creation** - Tạo đơn hàng
4. **Payment Processing** - Xử lý thanh toán

## Chi tiết từng bước

### Bước 1: Ticket Access & Selection

#### 1.1 Truy cập thông tin sự kiện
**Service:** `service.show`
**Action:** 
- User truy cập trang sự kiện
- Gọi `ShowService.GetShowById()` để lấy thông tin sự kiện
- Gọi `ShowService.GetSessionById()` để lấy thông tin phiên diễn
- Hiển thị thông tin vé available

**API Calls:**
```
GET /show/{showId}
GET /session/{sessionId}
GRPC: ShowService.GetShowById()
GRPC: ShowService.GetSessionById()
```

#### 1.2 Kiểm tra vé có sẵn
**Service:** `service.ticket`
**Action:**
- Gọi `TicketService.GetTicketByShowAndSession()` để lấy danh sách vé
- Kiểm tra số lượng vé còn lại
- Hiển thị các loại vé và giá

**API Calls:**
```
GRPC: TicketService.GetTicketByShowAndSession()
```

**Cases:**
- **Success:** Hiển thị danh sách vé available
- **No tickets:** Hiển thị "Hết vé" hoặc "Chưa mở bán"
- **Error:** Hiển thị lỗi hệ thống

### Bước 2: Ticket Reservation

#### 2.1 Tạo reservation
**Service:** `service.ticket`
**Action:**
- User chọn loại vé và số lượng
- Gọi `ReservationService.CreateReservation()` để tạo reservation
- Hệ thống hold vé trong thời gian nhất định (thường 15 phút)

**API Calls:**
```
GRPC: ReservationService.CreateReservation()
```

**Input:**
- `showId`: ID sự kiện
- `sessionId`: ID phiên diễn
- `quantity`: Số lượng
- `userId`: ID người dùng

**Cases:**
- **Success:** Trả về `reservationId`, chuyển sang bước tạo order
- **Insufficient tickets:** Lỗi "Không đủ vé"
- **Reservation expired:** Lỗi "Hết thời gian đặt chỗ"
- **Error:** Lỗi hệ thống

#### 2.2 Reserve reservation
**Service:** `service.ticket`
**Action:**
- Gọi `ReservationService.ReserveReservation()` để confirm reservation
- Cập nhật trạng thái reservation thành RESERVED

**API Calls:**
```
GRPC: ReservationService.ReserveReservation()
```

### Bước 3: Order Creation

#### 3.1 Thu thập thông tin đơn hàng
**Service:** `service.order.datacollector`
**Action:**
- Nếu sự kiện yêu cầu thông tin bổ sung, gọi `OrderDataCollectorService`
- Thu thập thông tin khách hàng (tên, email, phone, etc.)
- Validate thông tin

**API Calls:**
```
POST /data/validate
POST /data/save
GRPC: OrderDataCollectorService.CheckOrderDataCollectorIsValid()
```

#### 3.2 Tạo đơn hàng
**Service:** `service.order`
**Action:**
- Gọi `OrderService.CreateOrder()` để tạo đơn hàng
- Tính toán tổng tiền (bao gồm phí, thuế, khuyến mãi)
- Lưu thông tin đơn hàng vào database

**API Calls:**
```
POST /order/create
GRPC: OrderService.CreateOrder()
```

**Input:**
- `reservationId`: ID reservation
- `customerInfo`: Thông tin khách hàng
- `promotionCode`: Mã khuyến mãi (nếu có)

**Cases:**
- **Success:** Trả về `orderId`, chuyển sang payment
- **Reservation expired:** Hủy order, release reservation
- **Invalid promotion:** Lỗi mã khuyến mãi
- **Error:** Lỗi hệ thống, rollback reservation

#### 3.3 Xử lý khuyến mãi (nếu có)
**Service:** `service.order`
**Action:**
- Gọi `OrderService.CreateOrderPromotion()` nếu có mã khuyến mãi
- Tính toán lại tổng tiền sau khuyến mãi

**API Calls:**
```
GRPC: OrderService.CreateOrderPromotion()
```

### Bước 4: Payment Processing

#### 4.1 Tạo payment
**Service:** `service.payment`
**Action:**
- Gọi `PaymentService.CreatePayment()` để tạo payment record
- Chọn payment gateway (9Pay, VNPay, OnePay)
- Tạo payment URL

**API Calls:**
```
GRPC: PaymentService.CreatePayment()
```

**Input:**
- `orderId`: ID đơn hàng
- `amount`: Số tiền
- `paymentMethod`: Phương thức thanh toán
- `returnUrl`: URL trả về sau thanh toán

#### 4.2 Redirect đến Payment Gateway
**Action:**
- Redirect user đến payment gateway
- User thực hiện thanh toán trên gateway

#### 4.3 Xử lý callback từ Payment Gateway
**Service:** `service.payment`
**Action:**
- Payment gateway gọi callback URL
- Xử lý response từ gateway
- Cập nhật trạng thái payment

**API Calls:**
```
POST /payment/gateway/return/9pay
POST /payment/gateway/return/vnpay
POST /payment/gateway/return/onepay
```

**Cases:**
- **Payment Success:** Chuyển sang bước hoàn tất đơn hàng
- **Payment Failed:** Chuyển sang bước hủy đơn hàng
- **Payment Pending:** Chờ xác nhận từ gateway

#### 4.4 Thông báo kết quả payment cho Order
**Service:** `service.payment` → `service.order`
**Action:**
- Gọi `OrderNotificationService.NotifyPaymentStarted()` khi bắt đầu payment
- Gọi `OrderNotificationService.NotifyPaymentStatusChanged()` khi có thay đổi trạng thái

**API Calls:**
```
GRPC: OrderNotificationService.NotifyPaymentStarted()
GRPC: OrderNotificationService.NotifyPaymentStatusChanged()
```

### Bước 5: Post-Payment Processing

#### 5.1 Xử lý khi Payment Success
**Services:** `service.order`, `service.ticket`, `service.jobrunr`
**Action:**
1. Cập nhật trạng thái order thành PAID
2. Gọi `TicketService.StartTicket()` để kích hoạt vé
3. Gọi `ReservationService.NotifyOrderStatusChanged()` để cập nhật reservation
4. Schedule job gửi email confirmation qua `JobrunrService`

**API Calls:**
```
GRPC: TicketService.StartTicket()
GRPC: ReservationService.NotifyOrderStatusChanged()
GRPC: JobrunrService.scheduleJob()
```

#### 5.2 Xử lý khi Payment Failed/Cancelled
**Services:** `service.order`, `service.ticket`
**Action:**
1. Cập nhật trạng thái order thành CANCELLED
2. Gọi `ReservationService.ReleaseReservation()` để giải phóng vé
3. Schedule job gửi email thông báo hủy

**API Calls:**
```
GRPC: ReservationService.ReleaseReservation()
GRPC: JobrunrService.scheduleJob()
```

## JobRunner và Background Jobs

### Job Types

#### 1. Email Notification Jobs
**Trigger:** Sau khi payment success/failed
**Service:** `service.jobrunr` → `service.notification`
**Action:**
- Schedule job gửi email confirmation
- Gọi `MailNotificationService.SendMailOrder()`
- Tạo QR code cho vé (nếu cần)

**Job Flow:**
```
JobrunrService.scheduleJob() → JobExecutorService.executeJob() → MailNotificationService.SendMailOrder()
```

#### 2. Send Mail Notification Jobs
**Trigger:** Sau khi payment success
**Service:** `service.jobrunr` → `service.notification`
**Action:**
- Schedule job gửi email qua SMTP

#### 3. Reservation Cleanup Jobs
**Trigger:** Định kỳ hoặc sau timeout
**Service:** `service.jobrunr` → `service.ticket`
**Action:**
- Dọn dẹp các reservation hết hạn
- Giải phóng vé cho reservation timeout

#### 4. Order Retry Jobs
**Trigger:** Khi có lỗi trong quá trình xử lý
**Service:** `service.jobrunr` → `service.order`
**Action:**
- Retry các order bị lỗi
- Xử lý các case edge case

## Notification Strategy

### Khi nào gửi Email

#### 1. Order Confirmation Email
**Trigger:** Payment success
**Content:** 
- Thông tin đơn hàng
- QR code vé (cho vé điện tử)
- Thông tin sự kiện
- Hướng dẫn sử dụng vé

#### 2. Payment Failed Email
**Trigger:** Payment failed
**Content:**
- Thông báo thanh toán thất bại
- Link để thử lại thanh toán
- Thông tin hỗ trợ

#### 3. Order Cancelled Email
**Trigger:** Order bị hủy
**Content:**
- Thông báo hủy đơn hàng
- Lý do hủy
- Thông tin hoàn tiền (nếu có)

### Khi nào gửi Zalo ZNS

#### 1. Gửi thông báo OPT
**Trigger:** User đăng ký,đổi mật khẩu, chuyển nhượng vé
**Content:**
- Gửi mã OTP xác thực

#### 2. Gửi thông báo được tặng vé
**Trigger:** Khi được một user tặng vé
**Content:**
- Thông báo nhận được vé vủa sự kiện

## Payment Gateway Integration

### Flow xử lý với Payment Gateway

#### 1. Khởi tạo Payment
```
service.payment → Payment Gateway
1. Tạo payment request với thông tin:
   - Merchant ID
   - Order ID
   - Amount
   - Return URL
   - Cancel URL
   - Notify URL
2. Nhận payment URL từ gateway
3. Redirect user đến payment URL
```

#### 2. Xử lý Payment Response
```
Payment Gateway → service.payment
1. User hoàn tất thanh toán trên gateway
2. Gateway gọi notify URL với kết quả
3. service.payment verify signature
4. Cập nhật payment status
5. Thông báo cho service.order
```

#### 3. Handle Payment Return
```
Payment Gateway → Frontend → service.payment
1. User được redirect về return URL
2. Frontend gọi API check payment status
3. Hiển thị kết quả cho user
```

### Payment Gateway Specific Handling

#### 9Pay Integration
**Endpoint:** `POST /payment/gateway/return/9pay`
**Verification:** 
- Verify signature với secret key
- Check transaction status
- Validate amount và order ID

#### VNPay Integration
**Endpoint:** `POST /payment/gateway/return/vnpay`
**Verification:**
- Verify secure hash
- Check response code
- Validate transaction info

#### OnePay Integration
**Endpoint:** `POST /payment/gateway/return/onepay`
**Verification:**
- Verify hash value
- Check transaction response
- Validate merchant reference

## Loại Vé và QR Code Strategy

### Phân loại Vé

#### 1. Vé Đính kèm Thư Điện Tử (Classic Ticket)
**Đặc điểm:**
- Không cần in vé vật lý
- Sử dụng QR code trên email
- Check-in bằng QR code

**Sử dụng:** 
- **QR Code trong Email:** Cho các sự kiện không yêu cầu vé vật lý
- **Service:** `service.qrcode` tạo QR, gửi qua email

#### 2. Vé Vật Lý (Classic Ticket)
**Đặc điểm:**
- Có vé vật lý được in.
- Hẹ thống không hỗ trợ checkin, ban tổ chức chịu trách nhiệm với vé vật lý và checkin

**Sử dụng:**
- **Ticket:** Cho các sự kiện yêu cầu vé vật lý

#### 3. Vé Điện tử Slot (Smart Ticket)
**Đặc điểm:**
- Có chỗ ngồi cố định
- QR code chứa thông tin chỗ ngồi
- Cần sơ đồ chỗ ngồi

**Sử dụng:**
- **Ticket Slots:** Kết hợp quản lý slot và sơ đồ chỗ ngồi
- **Service:** `service.ticketslots`

### Khi nào sử dụng Ticket Slots

#### Use Cases:
1. **Sự kiện có chỗ ngồi cố định**
   - Concert, theater, cinema
   - Cần quản lý từng slot cụ thể

2. **Sự kiện có giới hạn số lượng nghiêm ngặt**
   - VIP events, limited edition
   - Cần tracking chính xác từng vé

#### Implementation:
```
service.ticketslots:
- Tạo slot cho từng vé
- Quản lý trạng thái slot
- Xử lý transfer vé giữa các user
- Quét QR code tại sự kiện
```

#### Implementation:
```
service.qrcode + service.notification:
- Tạo QR code chứa thông tin vé
- Embed QR vào email template
- Gửi email với QR code
- Verify QR tại sự kiện (nếu cần)
```

## Sơ đồ Flow Chi Tiết

```
┌─────────────────┐
│   USER ACCESS   │
│     TICKET      │
└─────────┬───────┘
          │
          ▼
┌─────────────────┐    ┌─────────────────┐
│ service.show    │───▶│ service.ticket  │
│ GetShowById()   │    │ GetTicketBy...()│
│ GetSessionById()│    │                 │
└─────────────────┘    └─────────┬───────┘
                                 │
                                 ▼
                       ┌─────────────────┐
                       │ TICKET AVAILABLE│
                       │    CHECK        │
                       └─────────┬───────┘
                                 │
                    ┌────────────┴────────────┐
                    │                         │
                    ▼                         ▼
          ┌─────────────────┐        ┌─────────────────┐
          │   NO TICKETS    │        │  TICKETS FOUND  │
          │   AVAILABLE     │        │                 │
          └─────────────────┘        └─────────┬───────┘
                                               │
                                               ▼
                                     ┌─────────────────┐
                                     │ USER SELECT     │
                                     │ TICKET & QTY    │
                                     └─────────┬───────┘
                                               │
                                               ▼
                                     ┌─────────────────┐
                                     │ service.ticket  │
                                     │ CreateReserv... │
                                     └─────────┬───────┘
                                               │
                                  ┌────────────┴────────────┐
                                  │                         │
                                  ▼                         ▼
                        ┌─────────────────┐        ┌─────────────────┐
                        │ RESERVATION     │        │ RESERVATION     │
                        │    FAILED       │        │    SUCCESS      │
                        └─────────────────┘        └─────────┬───────┘
                                                             │
                                                             ▼
                                                   ┌─────────────────┐
                                                   │ service.ticket  │
                                                   │ ReserveReserv...│
                                                   └─────────┬───────┘
                                                             │
                                                             ▼
                                                   ┌─────────────────┐
                                                   │ DATA COLLECTION │
                                                   │ (if required)   │
                                                   └─────────┬───────┘
                                                             │
                                                             ▼
                                                   ┌─────────────────┐
                                                   │ service.order   │
                                                   │ CreateOrder()   │
                                                   └─────────┬───────┘
                                                             │
                                                ┌────────────┴────────────┐
                                                │                         │
                                                ▼                         ▼
                                      ┌─────────────────┐        ┌─────────────────┐
                                      │  ORDER FAILED   │        │ ORDER SUCCESS   │
                                      │ Release Reserv. │        │                 │
                                      └─────────────────┘        └─────────┬───────┘
                                                                           │
                                                                           ▼
                                                                 ┌─────────────────┐
                                                                 │ service.payment │
                                                                 │ CreatePayment() │
                                                                 └─────────┬───────┘
                                                                           │
                                                                           ▼
                                                                 ┌─────────────────┐
                                                                 │ REDIRECT TO     │
                                                                 │ PAYMENT GATEWAY │
                                                                 └─────────┬───────┘
                                                                           │
                                                              ┌────────────┴────────────┐
                                                              │                         │
                                                              ▼                         ▼
                                                    ┌─────────────────┐        ┌─────────────────┐
                                                    │ PAYMENT FAILED  │        │ PAYMENT SUCCESS │
                                                    └─────────┬───────┘        └─────────┬───────┘
                                                              │                          │
                                                              ▼                          ▼
                                                    ┌─────────────────┐        ┌─────────────────┐
                                                    │ Cancel Order    │        │ Complete Order  │
                                                    │ Release Reserv. │        │ Start Ticket    │
                                                    │ Send Cancel     │        │ Send Confirm    │
                                                    │ Email/Zalo      │        │ Email/Zalo      │
                                                    └─────────────────┘        └─────────┬───────┘
                                                                                         │
                                                                                         ▼
                                                                               ┌─────────────────┐
                                                                               │ GENERATE QR     │
                                                                               │ & SEND TICKET   │
                                                                               └─────────────────┘
```

## Error Handling và Retry Mechanism

### Timeout Handling
- **Reservation Timeout:** 5 phút
- **Payment Timeout:** 30 phút
- **Job Retry:** 3 lần với exponential backoff

### Rollback Strategy
- **Payment Failed:** Release reservation, cancel order
- **Order Failed:** Release reservation
- **System Error:** Retry với JobRunr, log error

### Monitoring và Alerting
- **Payment Gateway Response Time**
- **Order Success Rate**
- **Email/Zalo Delivery Rate**
- **Reservation Timeout Rate**

Flow này đảm bảo tính nhất quán dữ liệu và trải nghiệm người dùng tốt trong hệ thống bán vé MetaTicket.

---

**Tài liệu này được cập nhật lần cuối**: 03/06/2026
**Version**: 1.0
**Tác giả**: MetaTicket Development Team
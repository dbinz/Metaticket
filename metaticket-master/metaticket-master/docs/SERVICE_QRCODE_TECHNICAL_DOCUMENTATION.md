# QR Code Service - Tài Liệu Kỹ Thuật

## Tổng Quan

QR Code Service là một microservice trong hệ thống MetaTicket, chịu trách nhiệm tạo và quản lý mã QR cho vé sự kiện. Service này cung cấp cả REST API và gRPC API để tích hợp với các service khác trong hệ thống.

## Cấu Trúc Thư Mục

```
service.qrcode/
├── src/main/kotlin/metaticket/qrcode/
│   ├── Application.kt                 # Entry point của ứng dụng
│   ├── configuration/
│   │   └── Environment.kt            # Cấu hình môi trường và secret keys
│   ├── constants/
│   │   └── constants.kt              # Các hằng số của service
│   ├── controller/
│   │   └── QRCodeController.kt       # REST API controller
│   ├── db/
│   │   ├── pojo/
│   │   │   └── qrcode.document.kt    # Document model cho MongoDB
│   │   └── DatabaseService.kt        # Service xử lý database operations
│   ├── ApplicationDI.kt          # Dependency injection configuration
│   ├── gateway/
│   │   └── TicketSlotsGateway.kt     # Gateway tích hợp với Ticket Slots Service
│   ├── grpc/
│   │   └── QRCodeServiceImpl.kt      # gRPC service implementation
│   ├── pojo/
│   │   ├── request.kt                # Request DTOs
│   │   └── response.kt               # Response DTOs
│   └── routing/
│       └── Route.kt                  # REST API routing configuration
├── src/main/proto/
│   ├── qrcode_message.proto          # gRPC message definitions
│   └── qrcode_service.proto          # gRPC service definitions
└── build.gradle.kts                  # Build configuration
```

## API Documentation

### REST API Endpoints

#### 1. Generate Link Scanner
- **Endpoint**: `GET /generate-link-scanner`
- **Controller**: `QRCodeController.generateLinkScanner()`
- **Mô tả**: Tạo link scanner cho QR code

#### 2. Verify Scanner
- **Endpoint**: `GET /verify-scanner`
- **Controller**: `QRCodeController.verifyLinkScanner()`
- **Mô tả**: Xác thực scanner link

#### 3. Scan QR Code
- **Endpoint**: `POST /scan`
- **Controller**: `QRCodeController.scanCode()`
- **Request Body**: `ScanQRCodeRequest`
- **Mô tả**: Quét và xử lý mã QR

#### 4. Fetch Order QR Code
- **Endpoint**: `POST /order/fetch`
- **Controller**: `QRCodeController.fetchOrderQRCode()`
- **Response**: `FetchOrderQRCodeResponse`
- **Mô tả**: Lấy thông tin QR code của đơn hàng

#### 5. Disable Order QR Code
- **Endpoint**: `POST /order/disable`
- **Controller**: `QRCodeController.disableOrderQrCode()`
- **Request Body**: `DisableOrderQRCodeRequest`
- **Mô tả**: Vô hiệu hóa QR code của đơn hàng

#### 6. Enable Order QR Code
- **Endpoint**: `POST /order/enable`
- **Controller**: `QRCodeController.enableOrderQrCode()`
- **Request Body**: `EnableOrderQRCodeRequest`
- **Mô tả**: Kích hoạt lại QR code của đơn hàng

### gRPC API

#### GenerateQRCode
- **Service**: `QRCodeService`
- **Method**: `GenerateQRCode`
- **Request**: `GenerateQRCodeRequest`
  - `showId`: ID của show
  - `sessionId`: ID của session
  - `orderId`: ID của đơn hàng
- **Response**: `GenerateQRCodeResponse`
- **Implementation**: `QRCodeServiceImpl.generateQRCode()`

## Database Schema

### QRCodeDocument
```kotlin
data class QRCodeDocument(
    val showId: String,
    val sessionId: String, 
    val orderId: String,
    val scanned: Boolean = false,
    val disable: Boolean = false
    // Các trường khác...
)
```

## Business Logic

### QR Code Generation Process
1. **Input Validation**: Kiểm tra tính hợp lệ của `showId`, `sessionId`, `orderId`
2. **Database Check**: Kiểm tra xem QR code đã tồn tại chưa
3. **QR Code Creation**: 
   - Tạo checksum từ dữ liệu đầu vào và secret key
   - Sử dụng thư viện QRCode để tạo mã QR
   - Lưu thông tin vào database
4. **Error Handling**: Xử lý các lỗi và logging

### QR Code Scanning Flow
1. **QR Code Parsing**: Phân tích nội dung QR code
2. **Validation**: Xác thực checksum và trạng thái
3. **Database Update**: Cập nhật trạng thái đã quét
4. **Integration**: Gọi TicketSlotsGateway để xử lý slot

### QR Code Types
1. **Regular QR Code**: Prefix `MTTKQR:`
2. **Slot QR Code**: Prefix `MTTKSLOTQR:`

## Security Mechanisms

### Checksum Validation
- Sử dụng `secretKey` và `qrcodeSecretKey` từ Environment
- Tạo checksum để đảm bảo tính toàn vẹn của QR code
- Ngăn chặn việc tạo QR code giả mạo

### Session Verification
- Xác thực session trước khi xử lý QR code
- Kiểm tra quyền truy cập của người dùng

### CORS Configuration
- Cấu hình CORS trong Application.kt
- Kiểm soát truy cập từ các domain khác nhau

## Gateway Services

### TicketSlotsGateway
- **File**: `TicketSlotsGateway.kt`
- **Method**: `scanTicketSlot()`
- **Mô tả**: Tích hợp với Ticket Slots Service để xử lý việc quét slot
- **Technology**: gRPC với coroutine support

## Dependency Injection

### ApplicationDI Configuration
- **Framework**: Koin
- **Controllers**: QRCodeController
- **Services**: DatabaseService
- **Gateways**: TicketSlotsGateway
- **gRPC**: Channel configuration với plaintext

## Configuration

### Environment Variables
- `secretKey`: Key chung cho hệ thống
- `qrcodeSecretKey`: Key riêng cho QR code service

### Constants
- `prefixQRCode`: "MTTKQR:"
- `prefixSlotQRCode`: "MTTKSLOTQR:"

### Dependencies
- Ktor framework
- MongoDB driver
- gRPC libraries
- QRCode generation libraries
- Koin for DI

## Security Best Practices

1. **Secret Management**: Sử dụng environment variables cho sensitive data
2. **Input Validation**: Validate tất cả input từ client
3. **Checksum Verification**: Đảm bảo tính toàn vẹn của QR code
4. **Access Control**: Kiểm tra authorization cho các operations
5. **Secure Communication**: Sử dụng HTTPS cho REST API

## Integration với Các Service Khác

### Ticket Slots Service
- gRPC integration qua TicketSlotsGateway
- Xử lý việc quét ticket slots
- Cập nhật trạng thái slot

### Order Service
- Nhận thông tin đơn hàng để tạo QR code
- Cập nhật trạng thái đơn hàng sau khi quét

### Show Service
- Lấy thông tin show để validation
- Kiểm tra tính hợp lệ của session

## Kết Luận

QR Code Service là một component quan trọng trong hệ thống MetaTicket, đảm bảo việc tạo và quản lý mã QR một cách an toàn và hiệu quả. Service được thiết kế với kiến trúc microservice, hỗ trợ cả REST và gRPC API, và tích hợp chặt chẽ với các service khác trong hệ thống.

---

**Tài liệu này được cập nhật lần cuối**: 03/06/2026
**Version**: 1.0
**Tác giả**: MetaTicket Development Team
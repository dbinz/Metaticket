# SERVICE JOBRUNR - TÀI LIỆU KỸ THUẬT

## 1. TỔNG QUAN HỆ THỐNG

### 1.1 Mục đích
Service JobRunr là một hệ thống quản lý và thực thi job nền (background job) trong kiến trúc microservices của MetaTicket. Service này hoạt động như một cơ chế callback trung gian, cho phép các service khác lên lịch và thực thi các job một cách bất đồng bộ.

### 1.2 Vai trò trong hệ thống
- **Job Scheduler**: Lên lịch thực thi các job từ các service khác
- **Callback Mechanism**: Gọi lại các service tương ứng khi job được thực thi
- **Queue Management**: Quản lý hàng đợi job để tránh quá tải hệ thống
- **Retry Handler**: Xử lý retry khi job thất bại
- **Stateless Service**: Có thể chạy nhiều instance và load balance

### 1.3 Kiến trúc tổng thể
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Ticket        │    │   Order         │    │   Payment       │
│   Service       │    │   Service       │    │   Service       │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
          │ gRPC Schedule Job    │                      │
          └──────────────────────┼──────────────────────┘
                                 │
                    ┌─────────────▼─────────────┐
                    │     JobRunr Service       │
                    │  ┌─────────────────────┐  │
                    │  │   JobRunr Library   │  │
                    │  │   (Job Scheduler)   │  │
                    │  └─────────────────────┘  │
                    │  ┌─────────────────────┐  │
                    │  │   MongoDB Storage   │  │
                    │  │   (Job Database)    │  │
                    │  └─────────────────────┘  │
                    └───────────────────────────┘
                                 │
                                 │ Callback Execution
                                 ▼
                    ┌─────────────────────────────┐
                    │     Target Services         │
                    │  (Execute actual job logic) │
                    └─────────────────────────────┘
```

## 2. CẤU TRÚC DỰ ÁN

### 2.1 Cấu trúc thư mục
```
service.jobrunr/
├── src/
│   ├── api/kotlin/metaticket/jobrunr/
│   │   └── datastructure/
│   │       └── datastructure.kt              # JobExecutorServiceName enum
│   ├── grpc/proto/                           # Protocol Buffer definitions
│   └── main/kotlin/metaticket/jobrunr/
│       ├── Application.kt                    # Ktor application setup
│       ├── ApplicationDI.kt                  # Dependency injection
│       ├── StartUp.kt                        # gRPC server startup
│       ├── gateway/                          # Service gateways
│       │   ├── JobExecutorGateway.kt         # Base gateway class
│       │   ├── ShowGateway.kt               # Show service gateway
│       │   ├── TicketGateway.kt             # Ticket service gateway
│       │   ├── OrderGateway.kt              # Order service gateway
│       │   ├── PaymentGateway.kt            # Payment service gateway
│       │   └── NotificationGateway.kt       # Notification service gateway
│       ├── jobrunr/                         # JobRunr core components
│       │   ├── JobrunrActivator.kt          # Job activator
│       │   ├── JobrunrBackgroundService.kt  # Background job handler
│       │   └── JobrunrRecurrentlyService.kt # Recurring job handler
│       ├── request/
│       │   └── jobrunr.request.kt           # JobRequest data class
│       ├── service/
│       │   └── JobrunrServiceImpl.kt        # gRPC service implementation
│       └── utils/
│           └── jobscheduler.kt              # Job scheduling utilities
```

### 2.2 Các thành phần chính

#### 2.2.1 JobRunr Core Components
- **JobrunrBackgroundService**: Xử lý job một lần (one-time jobs)
- **JobrunrRecurrentlyService**: Xử lý job định kỳ (recurring jobs)
- **JobrunrActivator**: Kích hoạt và quản lý job instances

#### 2.2.2 Gateway Layer
- **JobExecutorGateway**: Base class cho tất cả service gateways
- **Specific Gateways**: ShowGateway, TicketGateway, OrderGateway, PaymentGateway, NotificationGateway

#### 2.2.3 Data Models
- **JobRequest**: Chứa thông tin job cần thực thi
- **JobExecutorServiceName**: Enum định nghĩa các service có thể thực thi job

## 3. API VÀ ENDPOINTS

### 3.1 gRPC Service Interface

#### 3.1.1 JobrunrService
```protobuf
service JobrunrService {
    rpc scheduleJob(JobrunrRequest) returns (google.protobuf.Empty);
}

message JobrunrRequest {
    string jobId = 1;
    string service = 2;
    string jobName = 3;
    string jobDesc = 4;
    int32 retry = 5;
    int64 delayInSecond = 6;
    optional int64 intervalInSecond = 7;
}
```

#### 3.1.2 JobExecutorService (Interface cho các service khác)
```protobuf
service JobExecutorService {
    rpc executeJob(JobExecutionRequest) returns (JobExecutionResponse);
}

message JobExecutionRequest {
    string jobId = 1;
    string jobName = 2;
    string jobDesc = 3;
}

message JobExecutionResponse {
    JobNextAction nextAction = 1;
}

enum JobNextAction {
    DO_NOTHING = 0;
    CONTINUE = 1;
    STOP = 2;
}
```

### 3.2 HTTP Endpoints

#### 3.2.1 JobRunr Dashboard
- **URL**: `http://localhost:{dashboard.port}`
- **Mục đích**: Monitoring và quản lý job qua web interface
- **Tính năng**:
  - Xem danh sách job đang chạy
  - Xem lịch sử job
  - Retry job thất bại
  - Xem thống kê performance

## 4. MODELS VÀ DATA STRUCTURES

### 4.1 JobRequest
```kotlin
data class JobRequest(
    val jobId: UUID,                        // ID duy nhất của job
    val service: JobExecutorServiceName,    // Service sẽ thực thi job
    val jobName: String,                    // Tên loại job
    val jobDesc: String,                    // Mô tả job (serialized object)
    var retry: Int = 0,                     // Số lần retry (< 0 = retry vô hạn)
    val delayInSecond: Long = 0,            // Delay trước khi thực thi
    val intervalInSecond: Long? = null,     // Interval cho recurring job
)
```

### 4.2 JobExecutorServiceName
```kotlin
enum class JobExecutorServiceName {
    ShowService,
    TicketService,
    OrderService,
    PaymentService,
    NotificationService
}
```

### 4.3 JobNextAction
```kotlin
enum class JobNextAction {
    DO_NOTHING,    // Không làm gì
    CONTINUE,      // Tiếp tục job (retry hoặc schedule lại)
    STOP           // Dừng job
}
```

## 5. BUSINESS LOGIC

### 5.1 Job Scheduling Flow
```
1. Service gọi scheduleJob() qua gRPC
   ↓
2. JobrunrServiceImpl nhận request
   ↓
3. Convert JobrunrRequest → JobRequest
   ↓
4. Gọi scheduleRequest() utility
   ↓
5. Phân loại job:
   - Recurring job → JobrunrRecurrentlyService
   - One-time job → JobrunrBackgroundService
   ↓
6. JobRunr library lưu job vào MongoDB
   ↓
7. Job được schedule theo thời gian
```

### 5.2 Job Execution Flow
```
1. JobRunr library trigger job execution
   ↓
2. JobrunrActivator kích hoạt service tương ứng
   ↓
3. JobrunrBackgroundService.submit() được gọi
   ↓
4. Xác định target service từ JobExecutorServiceName
   ↓
5. Gọi gateway.executeJob() tương ứng
   ↓
6. Gateway gọi target service qua gRPC
   ↓
7. Nhận JobNextAction response:
   - DO_NOTHING: Không làm gì
   - CONTINUE: Schedule lại job
   - STOP: Xóa job khỏi database
   ↓
8. Xử lý exception và retry logic
```

### 5.3 Retry Mechanism
```kotlin
// Retry logic trong JobrunrBackgroundService
try {
    val jobNextAction = client.executeJob(jr)
    // Xử lý response
} catch (t: Throwable) {
    if (jr.intervalInSecond == null && (jr.retry < 0 || jr.retry-- > 0)) {
        storageProvider.deletePermanently(jr.jobId)
        scheduleRequest(jr)  // Retry job
    }
}
```

### 5.4 Recurring Job Logic
```kotlin
// Trong JobrunrRecurrentlyService
BackgroundJob.scheduleRecurrently<JobrunrBackgroundService>(
    jr.jobId.toString(),
    Duration.ofSeconds(jr.intervalInSecond!!)
) { service ->
    service.submit(jr)
}
```

## 7. TÍCH HỢP VỚI CÁC SERVICE KHÁC

### 7.1 Service Dependencies
```
JobRunr Service
├── → Show Service (port 3103/4103)
├── → Ticket Service (port 3104/4104)
├── → Order Service (port 3105/4105)
├── → Payment Service (port 3106/4106)
└── → Notification Service (port 3108/4108)
```

### 7.2 gRPC Channel Configuration
```kotlin
// Ví dụ cấu hình cho Ticket Service
single<JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub>(
    named(JobExecutorServiceName.TicketService)
) {
    val config: ApplicationConfig by inject()
    val host = config.property("grpc.channel.ticketS.host").getString()
    val port = config.property("grpc.channel.ticketS.port").getString().toInt()
    val channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build()
    JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub(channel)
}
```

### 7.3 Service Integration Examples

#### 7.3.1 Ticket Service Integration
```kotlin
// Ticket service schedule một job
val request = JobrunrMessage.JobrunrRequest.newBuilder()
    .setJobId(UUID.randomUUID().toString())
    .setService("TicketService")
    .setJobName("EXPIRE_RESERVED_TICKETS")
    .setJobDesc(ticketReservationJson)
    .setRetry(3)
    .setDelayInSecond(1800) // 30 phút
    .build()

jobrunrServiceStub.scheduleJob(request)
```

#### 7.3.2 Order Service Integration
```kotlin
// Order service schedule recurring job
val request = JobrunrMessage.JobrunrRequest.newBuilder()
    .setJobId("DAILY_ORDER_REPORT")
    .setService("OrderService")
    .setJobName("GENERATE_DAILY_REPORT")
    .setJobDesc(reportConfigJson)
    .setIntervalInSecond(86400) // 24 giờ
    .build()

jobrunrServiceStub.scheduleJob(request)
```

### 8.2 JobRunr Configuration
```kotlin
val jobServerConfiguration = BackgroundJobServerConfiguration
    .usingStandardBackgroundJobServerConfiguration()
    .andPollIntervalInSeconds(5)  // Poll interval

val runr = JobRunr.configure()
    .useJsonMapper(JacksonJsonMapper(objectMapper))
    .useJobActivator(jobActivator)
    .useStorageProvider(storageProvider)
    .useBackgroundJobServer(jobServerConfiguration)
    .useDashboard(dashboardPort)
    .initialize()
    .jobScheduler
```
---

**Tài liệu này được cập nhật lần cuối**: 03/06/2026
**Version**: 1.0
**Tác giả**: MetaTicket Development Team
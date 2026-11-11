# METATICKET-HOMEBACKEND - TÀI LIỆU KỸ THUẬT

## TỔNG QUAN HỆ THỐNG

### Thông tin cơ bản
- **Tên service**: metaticket-homebackend (metasearch)
- **Mục đích**: Service tìm kiếm và quản lý thông tin show/sự kiện cho trang chủ MetaTicket
- **Công nghệ chính**: Spring Boot 3.2.2, Kotlin, Elasticsearch
- **Kiến trúc**: RESTful API với Elasticsearch làm search engine
- **Port**: 8080 (production), 48080 (development)

### Kiến trúc tổng thể
```
┌─────────────────────────────────────────────────────────────┐
│                    metaticket-homebackend                   │
├─────────────────────────────────────────────────────────────┤
│  Controllers (REST API)                                     │
│  ├── PublicShowController (/public/metasearch/shows)       │
│  └── ShowController (/internal/metasearch/shows)           │
├─────────────────────────────────────────────────────────────┤
│  Services Layer                                             │
│  ├── ShowServiceWithRepository                             │
│  └── ApiKeyAuthenticationService                           │
├─────────────────────────────────────────────────────────────┤
│  Repository Layer                                           │
│  └── ShowRepository (Elasticsearch)                        │
├─────────────────────────────────────────────────────────────┤
│  Models & DTOs                                              │
│  ├── Show, ShowSession                                     │
│  └── Request/Response DTOs                                 │
├─────────────────────────────────────────────────────────────┤
│  Configuration & Infrastructure                             │
│  ├── ElasticsearchConfig                                   │
│  ├── WebConfig (CORS, Interceptors)                       │
│  └── LoggingInterceptor                                    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │  Elasticsearch  │
                    │   Search Engine │
                    └─────────────────┘
```

## CẤU TRÚC DỰ ÁN

### Cấu trúc thư mục
```
src/main/kotlin/meta/metasearch/
├── MetasearchApplication.kt          # Main application class
├── config/
│   ├── ElasticsearchConfig.kt        # Cấu hình Elasticsearch
│   └── WebConfig.kt                  # Cấu hình Web (CORS, Interceptors)
├── controllers/
│   └── ShowController.kt             # REST Controllers
├── dtos/
│   ├── CreateOrUpdateShowRequest.kt  # DTO cho tạo/cập nhật show
│   ├── SearchShowsRequest.kt         # DTO cho tìm kiếm
│   ├── UpdateSoldTicketRequest.kt    # DTO cho cập nhật vé bán
│   ├── GetHighlightShowsRequest.kt   # DTO cho show nổi bật
│   └── PinShowRequest.kt             # DTO cho ghim show
├── interceptor/
│   └── LoggingInterceptor.kt         # Interceptor ghi log
├── models/
│   └── Show.kt                       # Entity models
├── repositories/
│   └── ShowRepository.kt             # Elasticsearch repository
└── services/
    ├── ShowServiceWithRepository.kt  # Business logic cho Show
    └── ApiKeyAuthenticationService.kt # Xác thực API key
```

### Cấu hình môi trường
```
src/main/resources/
├── application-dev.properties        # Cấu hình development
├── application-staging.properties    # Cấu hình staging
├── application-prod.properties       # Cấu hình production
└── static/
    └── es-settings.json             # Cấu hình Elasticsearch analyzer
```

## MODELS VÀ ENTITIES

### Show Entity
```kotlin
@Document(indexName = "#{@environment.getProperty('elasticsearch.show.index.name')}")
data class Show(
    @Id var id: String,
    var showName: String,              // Tên show
    var code: String,                  // Mã show
    var slug: String,                  // Slug thân thiện URL của show (rỗng nếu chưa đặt)
    var status: String,                // Trạng thái
    var eventCategoryCode: String,     // Mã danh mục sự kiện
    var eventCategory: String,         // Tên danh mục sự kiện
    var detailImageUrl: String?,       // URL ảnh chi tiết
    var thumbnailUrl: String,          // URL ảnh thumbnail
    var bannerImageUrl: String?,       // URL ảnh banner
    var logoHostUrl: String?,          // URL logo host
    var logoShowUrl: String?,          // URL logo show
    var createdAt: Long,               // Thời gian tạo
    var description: String?,          // Mô tả
    var ranking: Int,                  // Thứ hạng
    var pinned: Boolean,               // Đã ghim hay chưa
    var soldTicket: Long,              // Số vé đã bán
    val showInHome: Boolean,           // Hiển thị ở trang chủ
    var sessions: List<ShowSession>,   // Danh sách session
    var waitingTime: Long,             // Thời gian chờ
    var startYear: Int,                // Năm bắt đầu
    var startMonth: Int,               // Tháng bắt đầu
    var startDay: Int,                 // Ngày bắt đầu
    val hasAllSessionsStopped: Boolean // Tất cả session đã dừng
)
```

### ShowSession Entity
```kotlin
data class ShowSession(
    @Id var id: String,
    var venue: String,                 // Địa điểm
    val code: String?,                 // Mã session
    var slug: String,                  // Slug thân thiện URL của session (rỗng nếu chưa đặt)
    val status: String,                // Trạng thái session
    var startTime: Long,               // Thời gian bắt đầu
    var endTime: Long,                 // Thời gian kết thúc
    val sessionName: String?,          // Tên session
    val cityName: String?,             // Tên thành phố
    val cityCode: String?,             // Mã thành phố
    val isFree: Boolean,               // Miễn phí hay không
    val showInHome: Boolean            // Hiển thị ở trang chủ
)
```

## CONTROLLERS VÀ API ENDPOINTS

### PublicShowController (/public/metasearch/shows)
Controller công khai cho frontend, không yêu cầu xác thực:

#### 1. GET /top-banner
- **Mục đích**: Lấy danh sách show cho banner trang chủ
- **Parameters**:
  - `size`: Số lượng show tối đa (default: 10, tự động clamp về khoảng `1..MAX_PAGE_SIZE`)
- **Response**: `Page<ShowListItem>` (chuyển đổi từ `Show` qua `ShowListItem.from(...)`)
- **Logic**: Lấy các show được ghim và chưa dừng hết session

#### 2. GET /suggestion
- **Mục đích**: Tìm kiếm gợi ý show theo từ khóa
- **Parameters**:
  - `keyword`: Từ khóa tìm kiếm
  - `page`: Số trang (default: 0)
  - `size`: Kích thước trang (default: 10)
- **Response**: `Page<Show>`
- **Logic**: Tìm kiếm full-text trên các trường: showName, code, description, venue, cityName

#### 3. GET /filter
- **Mục đích**: Tìm kiếm show với bộ lọc
- **Parameters**:
  - `page`, `size`: Phân trang
  - `eventCategoryCode`: Mã danh mục sự kiện (bắt buộc)
  - `cities`: Danh sách mã thành phố
  - `startTime`, `endTime`: Khoảng thời gian
  - `isFree`: Miễn phí hay không
- **Response**: `Page<Show>`
- **Sorting**: hasAllSessionsStopped ASC, waitingTime ASC, soldTicket DESC

#### 4. GET /highlight
- **Mục đích**: Lấy danh sách show nổi bật
- **Parameters**:
  - `page`, `size`: Phân trang
  - `cities`: Danh sách thành phố (optional)
- **Response**: `Page<Show>`
- **Sorting**: waitingTime ASC, soldTicket DESC

#### 5. GET /get-all
- **Mục đích**: Lấy tất cả show (không phân trang)
- **Response**: `List<Show>`

### ShowController (/internal/metasearch/shows)
Controller nội bộ, yêu cầu API key authentication:

#### 1. POST /create-or-update
- **Mục đích**: Tạo mới hoặc cập nhật show
- **Headers**: `X-API-KEY`
- **Body**: `CreateOrUpdateShowRequest`
- **Response**: `Show`
- **Logic**: 
  - Xác thực API key
  - Tính toán waitingTime từ session có startTime lớn nhất
  - Xác định showInHome và hasAllSessionsStopped
  - Lưu vào Elasticsearch

#### 2. POST /update-sold-ticket
- **Mục đích**: Cập nhật số vé đã bán
- **Headers**: `X-API-KEY`
- **Body**: `UpdateSoldTicketRequest`
- **Response**: `Show`

#### 3. POST /pin-show
- **Mục đích**: Ghim/bỏ ghim show
- **Headers**: `X-API-KEY`
- **Body**: `PinShowRequest`
- **Response**: `Show`

#### 4. POST /refresh-all-shows
- **Mục đích**: Làm mới tất cả show (tính toán lại waitingTime)
- **Headers**: `X-API-KEY`
- **Response**: `List<Show>`

## SERVICES LAYER

### ShowServiceWithRepository
Service chính xử lý business logic cho Show:

#### Các phương thức chính:

1. **createOrUpdateShowIndex(event: Show): Show**
   - Tạo mới hoặc cập nhật show trong Elasticsearch
   - Sử dụng upsert operation

2. **getTopBanner(pageSize: Int = 10): Page<ShowListItem>**
   - Lấy show được ghim và chưa dừng hết session
   - `pageSize` được clamp về khoảng `1..MAX_PAGE_SIZE` trước khi truy vấn
   - Kết quả `Show` được chuyển đổi sang `ShowListItem` (qua `toListItemPage`) trước khi trả về
   - Sắp xếp theo waitingTime và soldTicket

3. **searchShowWithOffset(...): Page<Show>**
   - Tìm kiếm show với các bộ lọc phức tạp
   - Sử dụng Elasticsearch bool query
   - Hỗ trợ lọc theo: eventCategoryCode, cities, timeRange, isFree

4. **searchByKeyword(keyword: String, pageable: Pageable): Page<Show>**
   - Tìm kiếm full-text với autocomplete
   - Tìm trên các trường: showName, code, description, venue, cityName

5. **getHighlightShows(cities: List<String>, pageable: PageRequest): Page<Show>**
   - Lấy show nổi bật theo thành phố
   - Lọc show có showInHome = true

6. **pinShow(id: String, pinned: Boolean): Show**
   - Cập nhật trạng thái ghim của show

7. **updateSoldTicketShow(id: String, soldTicket: Long): Show**
   - Cập nhật số vé đã bán

### ApiKeyAuthenticationService
Service xác thực API key:

```kotlin
@Service
class ApiKeyAuthenticationService(
    @Value("\${api.key}") private val apiKey: String
) {
    fun isValidApiKey(providedApiKey: String): Boolean {
        return apiKey == providedApiKey
    }
}
```

## REPOSITORY LAYER

### ShowRepository
```kotlin
@Repository
interface ShowRepository : ElasticsearchRepository<Show, String> {
    fun findByPinnedAndHasAllSessionsStopped(
        pinned: Boolean, 
        hasAllSessionsStopped: Boolean, 
        pageable: Pageable
    ): Page<Show>
}
```

- Kế thừa từ `ElasticsearchRepository`
- Cung cấp các phương thức CRUD cơ bản
- Custom query cho tìm show theo pinned và hasAllSessionsStopped

## CONFIGURATION

### ElasticsearchConfig
```kotlin
@Configuration
@EnableElasticsearchRepositories
class ElasticsearchConfig(
    @Value("\${elasticsearch.host}") private val esHost: String,
    @Value("\${elasticsearch.port}") private val esPort: Int
) : ElasticsearchConfiguration() {
    
    override fun clientConfiguration(): ClientConfiguration {
        return ClientConfiguration.builder()
            .connectedTo("$esHost:$esPort")
            .build()
    }
}
```

### WebConfig
```kotlin
@Configuration
@EnableWebMvc
class WebConfig : WebMvcConfigurer {
    
    // CORS Configuration
    @Bean
    fun corsConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                registry.addMapping("/**")
                    .allowedOrigins("*")
                    .allowedMethods("GET", "POST", "PUT", "DELETE")
            }
        }
    }
    
    // Logging Interceptor
    @Bean
    fun addInterceptors(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addInterceptors(registry: InterceptorRegistry) {
                registry.addInterceptor(loggingInterceptor)
            }
        }
    }
}
```

### Elasticsearch Settings
```json
{
  "analysis": {
    "analyzer": {
      "autocomplete": {
        "filter": ["lowercase"],
        "tokenizer": "autocomplete"
      },
      "autocomplete_search": {
        "tokenizer": "lowercase"
      }
    },
    "tokenizer": {
      "autocomplete": {
        "type": "edge_ngram",
        "min_gram": 1,
        "max_gram": 20,
        "token_chars": ["letter"]
      }
    }
  }
}
```

## INTERCEPTORS VÀ MIDDLEWARE

### LoggingInterceptor
```kotlin
@Component
class LoggingInterceptor : HandlerInterceptor {
    
    override fun preHandle(
        request: HttpServletRequest, 
        response: HttpServletResponse, 
        handler: Any
    ): Boolean {
        logger.info("Before handling - ${request.method} ${request.requestURI}")
        return true
    }
    
    override fun postHandle(...) {
        logger.info("After handling - ${response.status}")
    }
    
    override fun afterCompletion(...) {
        logger.info("Request complete - ${request.method} ${request.requestURI}")
        if (ex != null) {
            logger.error("Exception occurred", ex)
        }
    }
}
```

## TÍNH NĂNG CHÍNH

### 1. Tìm kiếm Show
- **Full-text search**: Tìm kiếm trên nhiều trường với autocomplete
- **Filtered search**: Lọc theo danh mục, thành phố, thời gian, giá
- **Suggestion search**: Gợi ý tìm kiếm theo từ khóa
- **Highlight shows**: Show nổi bật theo thành phố

### 2. Quản lý Show
- **CRUD operations**: Tạo, đọc, cập nhật show
- **Pin/Unpin**: Ghim show lên banner
- **Sold ticket tracking**: Theo dõi số vé đã bán
- **Session management**: Quản lý các session của show

### 3. Tính toán thông minh
- **Waiting time**: Tính thời gian chờ từ session gần nhất
- **Show visibility**: Tự động xác định hiển thị ở trang chủ
- **Session status**: Theo dõi trạng thái tất cả session

### 4. API Security
- **API Key authentication**: Bảo vệ internal endpoints
- **CORS support**: Hỗ trợ cross-origin requests
- **Request logging**: Ghi log tất cả requests

## ELASTICSEARCH INTEGRATION

### Index Configuration
- **Dynamic index name**: Theo environment (dev_shows, staging_shows, shows)
- **Custom analyzer**: Autocomplete với edge n-gram tokenizer
- **Field mapping**: Tối ưu cho tìm kiếm và lọc

### Query Strategies
1. **Bool Query**: Kết hợp nhiều điều kiện lọc
2. **Terms Query**: Lọc theo danh sách giá trị
3. **Range Query**: Lọc theo khoảng thời gian
4. **Multi-field Search**: Tìm kiếm trên nhiều trường

### Performance Optimization
- **Pagination**: Hỗ trợ phân trang cho tất cả queries
- **Sorting**: Tối ưu thứ tự hiển thị
- **Field selection**: Chỉ lấy các trường cần thiết

## KẾT LUẬN

Service **metaticket-homebackend** là một component quan trọng trong hệ thống MetaTicket, chuyên trách việc tìm kiếm và quản lý thông tin show/sự kiện. Với kiến trúc dựa trên Spring Boot và Elasticsearch, service cung cấp:

### Điểm mạnh:
1. **Performance cao**: Sử dụng Elasticsearch cho tìm kiếm nhanh
2. **Scalability**: Kiến trúc microservice dễ mở rộng
3. **Flexibility**: Hỗ trợ nhiều loại tìm kiếm và lọc
4. **Security**: API key authentication cho internal endpoints
5. **Monitoring**: Comprehensive logging và error handling

### Tính năng chính:
1. **Public APIs**: Cho frontend tìm kiếm và hiển thị show
2. **Internal APIs**: Cho các service khác quản lý show data
3. **Smart calculations**: Tự động tính toán waiting time và visibility
4. **Advanced search**: Full-text search với autocomplete
5. **Flexible filtering**: Lọc theo nhiều tiêu chí

Service này đóng vai trò then chốt trong việc cung cấp trải nghiệm tìm kiếm mượt mà cho người dùng MetaTicket và hỗ trợ các operations nội bộ của hệ thống.
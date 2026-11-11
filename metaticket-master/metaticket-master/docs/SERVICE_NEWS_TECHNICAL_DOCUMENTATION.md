# Tài Liệu Kỹ Thuật - News Service

## 1. Tổng Quan Về Chức Năng Chính

News Service (`kotlin/service.news`) là một **BFF (Backend For Frontend)** đứng trước một **WordPress headless** (repo `metaticket-wordpress`). Nó là thành phần **duy nhất** trong hệ thống được phép gọi thẳng WordPress REST API; storefront (`metaticket-home`), trang render bài viết và portal quản trị (`metaticket/ts` → `portal.admin`) đều đi qua service này.

Khác với hầu hết service còn lại (Ktor), News Service dùng **Spring Boot 3.2** (Kotlin, `spring-boot-starter-web` + `spring-boot-starter-cache` + Caffeine), không có database riêng — toàn bộ dữ liệu nằm trong WordPress.

### Chức năng chính:
- **API công khai cho storefront**: danh mục, danh sách bài (offset), bài nổi bật (ghim), bài liên quan, chi tiết theo slug, tìm kiếm — contract khớp `News_API_Datatypes_Samples` phía `metaticket-home`.
- **Render trang bài viết SSR**: `/news/{slug}.html` được nginx chuyển thẳng tới service để trả một trang HTML hoàn chỉnh (SEO meta, Open Graph, JSON-LD `NewsArticle`, CSS/JS plugin WordPress, navbar hydrate phía client).
- **Proxy tài nguyên nội dung**: gom CSS theme/plugin và manifest script plugin từ mu-plugin WordPress để hiển thị đúng HTML bài viết ngoài WordPress.
- **API quản trị (private)**: liệt kê/xem bài (mọi trạng thái), ẩn/hiện, ghim bài nổi bật toàn cục và ghim theo danh mục, picker chọn bài để ghim. API tạo/sửa bài vẫn tồn tại nhưng theo kiến trúc hiện tại portal **redirect** sang wp-admin trên subdomain `news.*` để soạn nội dung.
- **Cache**: cache in-process (Caffeine) cho các truy vấn WordPress + `Cache-Control` cho trình duyệt/CDN; endpoint nội bộ để xóa toàn bộ cache.
- **Bảo mật đọc công khai**: mọi lệnh đọc công khai dùng client WordPress **ẩn danh**, nên WordPress tự chặn bài nháp/riêng tư/hẹn giờ/có mật khẩu kể cả khi tham số bị chèn.

## 2. Kiến Trúc Và Các Thành Phần Chính

### 2.1 Kiến Trúc Tổng Thể

```
Trình duyệt (storefront / trang bài viết)        Portal admin (admin.*)
        │  /api/public/news/...                         │  /private/news/...
        │  /news/{slug}.html                            │  (nginx auth_request, X-OP-User-Id)
        ▼                                               ▼
                  nginx gateway (upstream news_service)
                                │
                                ▼
                   service.news (Spring Boot, Caffeine cache)
                     │                    │                     │
      wordPressPublicClient    wordPressRestClient     wordPressRootClient
      (ẩn danh, /wp-json/wp/v2) (Basic Auth Application    (gốc site, không /wp-json)
                     │           Password, /wp-json/wp/v2)  ?mt_content_styles / scripts
                     ▼                    ▼                     ▼
                         WordPress headless (news.<domain>)
                         + mu-plugin metaticket-news.php
```

- Port theo môi trường: local/dev `8082` (mặc định trong `application.properties`), staging `4041`, prod `3041` (khớp upstream `news_service` trong `nginx/{staging,prod}/metaticket-frags/metaticket-upstream-server`).
- WordPress REST bắt buộc HTTPS: WordPress tắt Application Password trên HTTP thường, nên `wordpress.base-url` ở staging/prod phải là URL `https://`.

### 2.2 Các Thành Phần Chính

#### Controllers
- **PublicNewsController** (`/public/news`): API công khai cho storefront + endpoint render HTML + proxy CSS/JS nội dung.
- **PrivateNewsController** (`/private/news`): API quản trị cho portal (bài viết, danh mục, bảng ghim).
- **InternalNewsController** (`/internal/news`): xóa cache, bảo vệ bằng shared secret `X-News-Internal-Key`. Không publish qua nginx.

#### Services
- **NewsService**: logic đọc công khai (list/featured/related/slug/search/categories), đánh dấu `@Cacheable`.
- **NewsRenderService**: dựng trang HTML từ template classpath `templates/news-article.html` + script `mt-chrome.js`.
- **AdminNewsService**: liệt kê/xem/tạo/sửa/ẩn-hiện/ghim một bài (dùng client có xác thực).
- **NewsPinService**: bảng ghim nổi bật (featured) và ghim theo danh mục, picker ứng viên.
- **NewsMapper / AdminNewsMapper**: map `WpPost` → DTO, rewrite URL media/anchor, tính `displayDate`, `readTime`.
- **ArticleSort / NewsPins**: quy tắc xếp hạng ghim và giới hạn số slot.

#### Client
- **WordPressClient**: bọc 3 `RestClient` (xem mục 4.1). Mọi giá trị query được bind dưới dạng **biến URI template** để chống chèn tham số.

#### Config
- **AppConfig**: tạo 3 `RestClient`, `CaffeineCacheManager`, CORS cho `/public/news/**`.
- **NewsProperties / WordPressProperties**: `@ConfigurationProperties` prefix `news.*` và `wordpress.*`.

### 2.3 Cấu Trúc Thư Mục
```
service.news/
├── build.gradle.kts                  # Spring Boot 3.2.2, Java 17, mainClass meta.news.NewsApplicationKt
├── conf-sample/
│   ├── metaticket-service.news.service          # systemd prod
│   ├── metaticket-service.news.staging.service  # systemd staging
│   └── service.news.env                          # mẫu biến môi trường (secret)
├── docs/API.md                       # contract API công khai (phía FE)
└── src/main/
    ├── kotlin/meta/news/
    │   ├── NewsApplication.kt        # @SpringBootApplication @EnableCaching
    │   ├── client/                   # WordPressClient, WordPressModels (WpPost, WpCategory...)
    │   ├── config/                   # AppConfig, NewsProperties
    │   ├── controllers/              # Public / Private / Internal
    │   ├── models/                   # NewsModels (public DTO), AdminNewsModels
    │   └── services/                 # NewsService, NewsRenderService, AdminNewsService,
    │                                 # NewsPinService, NewsMapper, AdminNewsMapper, ArticleSort
    └── resources/
        ├── application.properties            # profile mặc định dev, port 8082
        ├── application-{dev,staging,prod}.properties
        ├── templates/news-article.html       # shell HTML SEO cho trang bài viết
        └── mt-chrome.js                      # hydrate navbar/related/featured phía client
```

### 2.4 Đường Đi Qua nginx

| Đường dẫn phía trình duyệt | nginx | Đường dẫn service nhận |
|---|---|---|
| `{domain}/api/public/news/<x>` | `location /api/public/news/` + `rewrite` bỏ `/api` (`metaticket-public-api`) | `/public/news/<x>` |
| `{domain}/news/<slug>.html` | `location ~ ^/news/(?!category/)([^/]+)\.html/?$` → rewrite (`metaticket-root`), thêm header `X-Render-By: service.news` | `/public/news/render/<slug>` |
| `{domain}/news/<slug>` (không `.html`) | `301` → `/news/<slug>.html` | — |
| `admin.{domain}/private/news/...` | `location /private/news` dưới `location /private/` có `auth_request /internal/authenticate` (`metaticket-private-api`) | `/private/news/...` |
| `/internal/news/...` | **Không** route qua nginx | chỉ gọi từ chính server |
| `/api/public/wordpress/` | `deny all` (`metaticket-wordpress`) — chặn gọi thẳng WP REST qua edge | — |

## 3. Các API

> Đường dẫn dưới đây là đường dẫn **service nhận** (sau khi nginx rewrite). Lỗi trả về dạng JSON lỗi mặc định của Spring Boot (`timestamp`, `status`, `error`, `path`) do `ResponseStatusException`.

### 3.1 Public APIs (`/public/news`, không xác thực)

CORS chỉ bật cho `/public/news/**`: origin pattern `https://*.metaticket.vn`, `https://*.metaticket.com.vn`, `https://devlocal.metaticket.vn`, `https://admin.devlocal.metaticket.vn`, `http://localhost:*`, `http://127.0.0.1:*`; method `GET, OPTIONS`; không credentials; `maxAge 3600`.

#### GET `/public/news/category/fetch/all`
- **Mô tả**: Danh sách danh mục WordPress có bài (`hide_empty=true`), bỏ `uncategorized`.
- **Response**: `List<NewsCategoryItem>`
  ```kotlin
  data class NewsCategoryItem(
      val id: String,    // "cat-<slug>"
      val name: String,
      val code: String,  // = slug
      val slug: String,
  )
  ```
- FE tự thêm mục "Tất cả tin tức" (`categoryCode=all`).
- **Cache**: `categories`.

#### GET `/public/news/fetch/all`
- **Mô tả**: Danh sách bài theo offset.
- **Query**:

  | Tham số | Mặc định | Ghi chú |
  |---|---|---|
  | `offset` | `0` | `< 0` bị kẹp về `0` |
  | `size` | `9` | kẹp `1..50` |
  | `categoryCode` | — | slug, `cat-<slug>`, hoặc id số; bỏ trống / `all` = không lọc |
  | `sort` | `createdAt,desc` | `createdAt|date|publishedAt` → `date`, `modifiedAt|modified` → `modified`, `title`, `relevance`; khác → `date`. **Bị bỏ qua khi có `categoryCode`** |
  | `excludeSlug` | — | bỏ bài đang mở khỏi kết quả |

- **Hành vi**:
  - Không lọc danh mục: gọi WP `/posts?offset=&per_page=&status=publish&orderby=&order=` trực tiếp.
  - Có lọc danh mục: tải **toàn bộ** bài đã publish của danh mục (`listAllPublished`, tối đa 20 trang × 100 bài), xếp **ghim danh mục 1..3 trước, rồi `modified` giảm dần**, sau đó cắt offset trong bộ nhớ. `categoryCode` không tồn tại → trang rỗng (không lỗi).
- **Response**: `NewsOffsetPage`
  ```kotlin
  data class NewsOffsetPage(
      val content: List<NewsItem>,
      val totalElements: Long,
      val offset: Int,
      val size: Int,
      val last: Boolean,
      val empty: Boolean,
  )

  data class NewsItem(
      val id: String,            // id bài WP
      val thumbnail: String,     // URL ảnh đại diện đã rewrite sang media-base-url
      val createdAt: String,     // ISO-8601 UTC (ưu tiên date_gmt)
      val displayDate: String,   // "HH:mm Thứ X, dd/MM/yyyy" theo giờ Asia/Ho_Chi_Minh
      val title: String,         // đã strip HTML
      val summary: String,       // excerpt đã strip HTML
      val slug: String,
      val categoryId: String,    // "cat-<slug>" của danh mục chính
      val categoryCode: String,
      val categoryName: String,
      val author: String,        // tên tác giả WP, mặc định "MetaTicket"
  )
  ```
- **Cache**: `articles` (key gồm offset/size/categoryCode/sort/excludeSlug).

#### GET `/public/news/fetch/featured`
- **Mô tả**: Bài được ghim nổi bật (khối "Bài viết nổi bật"/hero hub). Chỉ trả bài đang có `mt_pin_order` hợp lệ, sắp theo thứ tự ghim; slot trống bị bỏ, **không** lấp bằng bài mới nhất.
- **Query**: `excludeSlug` (tùy chọn).
- **Response**: `NewsHighlightPage { content: List<NewsItem>, totalElements: Long }`
- **Cache**: `featured`.

#### GET `/public/news/fetch/related`
- **Mô tả**: Bài liên quan cho trang chi tiết.
- **Query**: `slug` (bắt buộc), `size` (mặc định `6`, kẹp `1..20`), `excludeSlug` (tùy chọn).
- **Thuật toán**: (1) bài có nhiều tag trùng nhất (tie-break theo ngày publish mới nhất), (2) lấp thêm bằng bài mới nhất cùng danh mục; dừng khi đủ `size` hoặc hết ứng viên.
- **Response**: `NewsHighlightPage`. `slug` không tồn tại → `404`.
- **Cache**: `articles`.

#### GET `/public/news/fetch/slug`
- **Mô tả**: Chi tiết một bài đã publish.
- **Query**: `slug` (bắt buộc; được chuẩn hóa: trim, lowercase, khoảng trắng → `-`, gộp `-`).
- **Response**: `NewsDetail`
  ```kotlin
  data class NewsDetail(
      val id: String, val thumbnail: String, val createdAt: String, val displayDate: String,
      val title: String, val summary: String, val slug: String,
      val categoryId: String, val categoryCode: String, val categoryName: String,
      val content: String,                 // HTML đã rewrite URL media + anchor
      val author: String,
      val readTime: String,                // "N min read", 200 từ/phút, tối thiểu 1
      val tags: List<String>,
      val highlightNews: List<NewsItem>,   // luôn rỗng — dùng API related/featured riêng
      val highlightEventIds: List<String>, // dự phòng, luôn rỗng
      val highlightEvents: List<Any>,      // dự phòng, luôn rỗng
  )
  ```
- **Lỗi**: không tìm thấy → `404` ("Article not found: <slug>") → FE chuyển `/not-found`.
- **Cache**: `article` (key = slug thô).

#### GET `/public/news/search`
- **Mô tả**: Tìm kiếm bài theo từ khóa (WP `search`, sắp theo `date desc`).
- **Query**: `keyword` (bắt buộc, sau trim không được rỗng → `400 "keyword is required"`), `page` (bắt buộc, 0-based), `size` (bắt buộc, kẹp `1..50`), `excludeSlug` (tùy chọn).
- **Response**: `NewsSearchPage` dạng Spring Page (`content`, `pageable{pageNumber,pageSize,offset,paged,unpaged,sort}`, `totalPages`, `totalElements`, `size`, `number`, `first`, `last`, `empty`).
- **Cache**: `articles`.

#### GET `/public/news/render/{slug}`
- **Mô tả**: Trả **trang HTML hoàn chỉnh** cho một bài (nginx map `/news/{slug}.html` vào đây).
- **Response**: `text/html;charset=UTF-8`, header `Cache-Control: public, max-age=<news.render-cache-seconds>`.
- **Nội dung trang** (`NewsRenderService.fillTemplate`):
  - Thay token trong `templates/news-article.html`: `__PAGE_TITLE__` (`<title> | META Ticket`), `__META_DESC__` (summary ≤ 200 ký tự), `__CANONICAL__` (`{frontend-base-url}/news/{slug}.html`), `__OG_IMAGE__`, `__JSON_LD__` (schema.org `NewsArticle`), `__FRONTEND__`, `__CATEGORY_NAME__`, `__DISPLAY_DATE__`, `__H1_TITLE__`, `__HERO_SRC__`, `__WP_CSS__`, `__WP_SCRIPTS__`, `__HYDRATE_BOOT__`, `__ARTICLE_TAGS__`; `__ARTICLE_HTML__` được thay **cuối cùng** để nội dung bài không đụng tên token.
  - Text do người dùng nhập (title, summary, category, tag) được escape HTML/JSON; **HTML bài viết từ WordPress được nhúng nguyên văn** (tin tưởng nội dung CMS).
  - Script plugin WP được phát theo đúng thứ tự in của WordPress: inline `before` → `<script src>` → inline `after`.
  - `window.__MT_CHROME__` (frontend, `api: "/api"`, slug, categoryCode, loginHref, iconBase) + nội dung `mt-chrome.js` để hydrate navbar, menu tài khoản, tìm kiếm, khối featured/related/danh sách sau first paint (gọi lại chính các API `/api/public/news/...`, `/api/public/show/...`, `/api/public/metasearch/...`).
- **Lỗi**: slug không tồn tại → `404` (propagate từ `getNewsBySlug`).
- **Cache**: `articleHtml` (key = slug).

#### GET `/public/news/content-styles`
- **Mô tả**: Proxy `GET {wordpress.base-url}/?mt_content_styles=1` — bundle CSS theme + plugin đang bật (mu-plugin `metaticket-news.php`).
- **Response**: `text/css;charset=UTF-8`, `Cache-Control: public, max-age=300` (cố định 5 phút).
- **Cache**: `contentStyles`.

#### GET `/public/news/content-scripts`
- **Mô tả**: Proxy `GET {wordpress.base-url}/?mt_content_scripts=1` — manifest JSON `{ "scripts": [{handle, src, deps}], "inline": [{handle, position, code}] }`.
- **Response**: `application/json`, `Cache-Control: public, max-age=300`. WordPress trả body rỗng → `{"scripts":[],"inline":[]}`.
- **Cache**: `contentScripts`.

### 3.2 Private APIs (`/private/news`, cho portal admin)

**Xác thực**: service **không tự kiểm tra** header nào; việc xác thực operator hoàn toàn dựa vào nginx (`location /private/` với `auth_request /internal/authenticate`, chèn `X-OP-User-Id`). Vì vậy port của service tuyệt đối không được lộ ra ngoài. CORS không bật cho nhánh này.

#### GET `/private/news/articles`
- **Query**: `page` (0-based, mặc định `0`), `size` (mặc định `20`, kẹp `1..100`), `search`, `category` (id số hoặc slug).
- **Hành vi**: WP `/posts?status=any&orderby=modified&order=desc` qua client có xác thực; trong **mỗi trang** sắp lại theo ghim nổi bật rồi `modified`.
- **Response**: `PageResponse<AdminArticleRow>`
  ```kotlin
  data class AdminArticleRow(
      val id: Long, val title: String, val slug: String,
      val visible: Boolean,          // status == "publish"
      val pinOrder: Int?,            // ghim nổi bật hiệu lực
      val categories: List<String>,  // tên danh mục
      val modifiedAt: String,
  )
  data class PageResponse<T>(val content: List<T>, val page: Int, val size: Int,
                             val totalElements: Long, val totalPages: Int)
  ```

#### GET `/private/news/articles/{id}`
- **Response**: `AdminArticleForm { id, title, slug, excerpt, contentHtml, visible, pinOrder, categoryIds }`. Không tồn tại → `404`.

#### POST `/private/news/articles` — tạo bài
#### PUT `/private/news/articles/{id}` — sửa bài
- **Request Body**: `SaveArticleRequest`
  ```kotlin
  data class SaveArticleRequest(
      val title: String,
      val slug: String,              // rỗng → để WP tự sinh
      val excerpt: String = "",
      val contentHtml: String = "",
      val visible: Boolean = false,  // true → status "publish", false → "draft"
      val pinOrder: Int? = null,     // chỉ nhận 1..FEATURED_PIN_MAX
      val categoryIds: List<Long> = emptyList(),
  )
  ```
- **Hành vi khi sửa**: giữ nguyên ghim danh mục (`mt_category_pins`), ảnh đại diện (`featured_media`) và ghim nổi bật cũ nếu request không gửi `pinOrder` hợp lệ.
- **Response**: `AdminArticleForm`. Sửa bài không tồn tại → `404`.
- **Ghi chú**: luồng hiện tại của portal soạn/sửa nội dung trực tiếp trên wp-admin (`news.*`), không dùng 2 endpoint này.

#### PATCH `/private/news/articles/{id}/visibility`
- **Body**: `{ "visible": true | false }` → `publish` / `draft`.
- **Response**: `AdminArticleRow`. Xem lưu ý ở mục 7 (ghi lại nội dung đã render).

#### PATCH `/private/news/articles/{id}/pin`
- **Body**: `{ "pinOrder": <1..FEATURED_PIN_MAX> | null }` — giá trị ngoài khoảng được coi là bỏ ghim.
- **Response**: `AdminArticleRow`.

#### GET `/private/news/categories`
- **Response**: `List<AdminCategoryOption { id, name, slug }>` — gồm cả danh mục rỗng (`hide_empty=false`).

#### GET `/private/news/pin/featured`
- **Response**: `NewsPinBoard { slots: List<NewsPinSlot{ pinOrder, article: NewsPinSummary? }>, maxSlots }` — chỉ slot đang có bài; tính trên **mọi trạng thái** bài (để thấy cả bài đã ghim rồi bị chuyển nháp). `maxSlots = FEATURED_PIN_MAX`.
  ```kotlin
  data class NewsPinSummary(val id: Long, val title: String, val slug: String,
                            val status: String, val thumbnailUrl: String, val modifiedAt: String)
  ```

#### POST `/private/news/pin/featured/refresh`
- **Mô tả**: Ghim lại `FEATURED_PIN_MAX` bài đã publish mới sửa gần nhất (theo `modified desc`), bỏ ghim mọi bài khác.

#### POST `/private/news/pin/featured/confirm`
- **Body**: `ConfirmNewsPinsRequest { slots: List<NewsPinSlotInput{ pinOrder, articleId? }> }`.
- **Quy tắc**: sắp theo `pinOrder`, bỏ slot `articleId = null`, **nén lại** thành thứ hạng `1..n`; bài rời bảng bị unset `mt_pin_order`.
- **Lỗi `400`**: quá số slot ("At most N pinned articles"), `articleId <= 0` ("Malformed articleId"), trùng bài ("Duplicate article on the pin board"), bài không tồn tại ("Article not found: <id>").

#### GET `/private/news/pin/category/{code}`
#### POST `/private/news/pin/category/{code}/refresh`
#### POST `/private/news/pin/category/{code}/confirm`
- Giống bảng featured nhưng theo **một danh mục**, tối đa `CATEGORY_PIN_MAX = 3` slot, lưu vào map `mt_category_pins` (`{slug: rank}`) của từng bài; chỉ key của danh mục đang sửa bị thay đổi, ghim ở danh mục khác giữ nguyên.
- `code`: slug, `cat-<slug>` hoặc id số. Rỗng → `400 "categoryCode is required"`; không tồn tại → `400 "Unknown category: <code>"`.

#### GET `/private/news/pin/candidates`
- **Query**: `page` (**1-based**, mặc định `1`, khớp UI ghim của accessory), `size` (mặc định `10`, kẹp `1..50`), `keyword`, `categoryCode`.
- **Response**: `NewsPinCandidatePage { content: List<NewsPinSummary>, page, size, totalElements, totalPages }` (mọi trạng thái bài).

### 3.3 Internal API

#### POST `/internal/news/cache/invalidate`
- **Mô tả**: Xóa sạch **toàn bộ** cache Caffeine (mọi cache name).
- **Header**: `X-News-Internal-Key: <NEWS_INTERNAL_API_KEY>`.
- **Response**: `204 No Content`. Thiếu/sai key, hoặc server chưa cấu hình key → `401 "Invalid internal key"` (key rỗng phía server = endpoint luôn bị khóa).
- Không route qua nginx; chỉ gọi từ chính server, ví dụ (chạy trên server, điền đúng port của môi trường):
  ```bash
  curl -X POST -H "X-News-Internal-Key: <NEWS_INTERNAL_API_KEY>" \
       http://127.0.0.1:<port>/internal/news/cache/invalidate
  ```
- Hiện **chưa có webhook** nào phía WordPress gọi endpoint này (mu-plugin `metaticket-news.php` không đăng ký hook gọi ra ngoài) — muốn nội dung mới hiện ngay phải gọi tay hoặc chờ hết TTL.

## 4. Tích Hợp Với WordPress

### 4.1 Ba REST client (`AppConfig`, `WordPressClient`)

| Bean | Base URL | Xác thực | Dùng cho |
|---|---|---|---|
| `wordPressPublicClient` | `{base-url}{rest-path}` | **Không** gửi `Authorization` | Mọi lệnh đọc công khai: `listPostsOffset`, `listPosts`, `listAllPublished`, `getPostBySlug`, `listCategories` |
| `wordPressRestClient` | `{base-url}{rest-path}` | `Authorization: Basic base64(username:application-password)` — chỉ thêm khi cả hai giá trị khác rỗng | Admin: `listPostsAdmin`/`listAllAdmin` (`status=any`), `getPostById`, `createPost`, `updatePost`, `updatePostFields`, `listCategoriesAdmin` |
| `wordPressRootClient` | `{base-url}` (không `/wp-json`) | Không | `?mt_content_styles=1`, `?mt_content_scripts=1` |

- Cả ba dùng `SimpleClientHttpRequestFactory` với `wordpress.connect-timeout-ms` / `wordpress.read-timeout-ms`.
- Lý do tách client ẩn danh: dù một tham số như `status=draft` có lọt vào query, WordPress vẫn chỉ trả những gì khách chưa đăng nhập được xem (bài publish, không mật khẩu). Các lệnh đọc công khai còn cố định `status=publish`.
- **Chống chèn tham số**: `getList` dựng URI bằng `UriBuilder` với từng giá trị là biến template `{qN}` (RestClient encode chặt) — keyword kiểu `x&status=draft` vẫn nằm trong tham số `search`. Không được nối chuỗi giá trị vào URI rồi đưa cho `uri(String)` (sẽ bị parse lại thành template). Có test hồi quy `src/test/kotlin/meta/news/client/WordPressClientTest.kt`.
- `_embed=wp:featuredmedia,wp:term,author` để lấy ảnh, danh mục/tag, tác giả trong một lần gọi. Tổng số bản ghi lấy từ header `X-WP-Total` / `X-WP-TotalPages`.

### 4.2 Application Password
- User dịch vụ WordPress dạng content-only, tạo bằng `metaticket-wordpress/scripts/setup-news-user.php`; BFF dùng **Application Password** (không phải mật khẩu đăng nhập).
- Truyền qua biến môi trường `WORDPRESS_USERNAME` / `WORDPRESS_APPLICATION_PASSWORD` (file `/etc/metaticket/service.news.env`, systemd `EnvironmentFile`). Không commit giá trị thật.
- Profile `dev` để trống cả hai → mọi client chạy ẩn danh (đủ cho đọc công khai, API admin sẽ chỉ thấy bài publish hoặc bị WP từ chối khi ghi).

### 4.3 Trường mở rộng của mu-plugin `metaticket-news.php`

| Trường REST | Kiểu | Ý nghĩa |
|---|---|---|
| `mt_pin_order` | `integer \| null` | Thứ hạng ghim nổi bật toàn cục (nhỏ = cao hơn). Alias `mt_home_pin_order` |
| `mt_category_pins` | `object {slug: int}` | Thứ hạng ghim trong từng danh mục (1..3), một bài có thể ghim ở nhiều danh mục |
| `mt_news_featured` | `boolean` | Trường cũ, BFF không dùng |
| `/?mt_content_styles=1` | CSS | Bundle CSS theme + plugin (alias `mt_global_styles=1`) |
| `/?mt_content_scripts=1` | JSON | Manifest script plugin theo thứ tự phụ thuộc của `WP_Scripts` |

Khi ghi ghim, BFF chỉ PUT đúng trường cần đổi (`WpFeaturedPinWrite` / `WpCategoryPinsWrite`) để không đè trường kia.

### 4.4 Rewrite URL trong nội dung (`NewsMapper`)
- Ảnh đại diện: cắt từ `/wp-content/` hoặc `/wp-includes/` và ghép vào `news.media-base-url`.
- HTML bài viết: thay host `https?://(admin|news).<host>/`, đường dẫn tương đối `/wp-content|/wp-includes`, `localhost:8081`, `127.0.0.1:8081` → `news.media-base-url`.
- Anchor mục lục (Easy TOC): `href="<permalink WP>#id"` và `href="#id"` → `/news/<slug>.html#id`, vì storefront dùng `<base href="/">` nên `#id` trần sẽ trỏ về trang chủ.

## 5. Mô Hình Dữ Liệu Và Quy Tắc Nghiệp Vụ

Service không có MongoDB; "dữ liệu" là post/category/tag của WordPress (`WpPost`, `WpCategory`, `WpTerm`, `WpMedia`, `WpAuthor` trong `client/WordPressModels.kt`).

### 5.1 Trạng thái bài viết
- `visible = true` ⇔ `status == "publish"`; ẩn = chuyển sang `draft`.
- API công khai chỉ thấy `publish` (client ẩn danh + `status=publish`). API admin thấy mọi trạng thái (`status=any`).

### 5.2 Ghim

| Hằng số (`services/ArticleSort.kt`) | Giá trị | Phạm vi |
|---|---|---|
| `FEATURED_PIN_MAX` (`NewsPins.FEATURED_MAX`) | **6** | Ghim nổi bật toàn cục (`mt_pin_order` hợp lệ trong `1..6`) |
| `CATEGORY_PIN_MAX` (`NewsPins.CATEGORY_MAX`) | **3** | Ghim theo danh mục (`mt_category_pins[slug]` hợp lệ trong `1..3`) |

- Giá trị ngoài khoảng bị bỏ qua khi đọc (coi như không ghim).
- Bảng featured (`/fetch/featured`) trả các bài ghim theo thứ tự; danh sách theo danh mục (`/fetch/all?categoryCode=`) xếp ghim danh mục trước rồi `modified desc`.
- Admin list và bảng ghim đều dựa trên quét toàn bộ bài (`listAllAdmin` / `listAllPublished`, tối đa 20 trang × 100 = 2.000 bài) — phù hợp khối lượng tin tức nhỏ.

### 5.3 Ngày hiển thị và thời gian đọc
- `createdAt`: ưu tiên `date_gmt` (UTC); nếu thiếu dùng `date` coi như giờ Việt Nam.
- `displayDate`: `HH:mm <Thứ>, dd/MM/yyyy` theo `Asia/Ho_Chi_Minh` (ví dụ `10:20 Thứ Tư, 15/01/2025`).
- `readTime`: `max(1, ceil(số từ / 200))` + `" min read"`.

## 6. Cấu Hình

### 6.1 Bảng khóa cấu hình

| Khóa | Mặc định trong code | dev | staging | prod | Ý nghĩa |
|---|---|---|---|---|---|
| `spring.profiles.active` | `dev` (`application.properties`) | — | `staging` (systemd) | `prod` (systemd) | Chọn profile |
| `server.port` | `8082` | `8082` | `4041` | `3041` | Port HTTP, khớp upstream `news_service` |
| `wordpress.base-url` | `http://localhost:8081` | URL HTTPS của WP staging (đọc công khai) | `https://news.<staging-domain>/` | `https://news.<prod-domain>/` | Gốc site WordPress |
| `wordpress.rest-path` | `/wp-json/wp/v2` | = | = | = | Đường dẫn REST |
| `wordpress.connect-timeout-ms` | `3000` | `3000` | `3000` | `3000` | Timeout kết nối |
| `wordpress.read-timeout-ms` | `10000` | `10000` | `10000` | `10000` | Timeout đọc |
| `wordpress.username` | `""` | `""` (ẩn danh) | `${WORDPRESS_USERNAME:}` | `${WORDPRESS_USERNAME:}` | User dịch vụ WP |
| `wordpress.application-password` | `""` | `""` | `${WORDPRESS_APPLICATION_PASSWORD:}` | `${WORDPRESS_APPLICATION_PASSWORD:}` | `<WP_APP_PASSWORD>` |
| `news.internal-api-key` | `""` (đọc qua `@Value`) | `""` | `${NEWS_INTERNAL_API_KEY:}` | `${NEWS_INTERNAL_API_KEY:}` | Secret cho `/internal/news/cache/invalidate` |
| `news.media-base-url` | `https://news.devlocal.metaticket.vn` | WP staging | `https://news.<staging-domain>/` | `https://news.<prod-domain>/` | Host thay thế cho URL media trong nội dung |
| `news.frontend-base-url` | `https://devlocal.metaticket.vn` | storefront staging | `https://<staging-domain>/` | `https://<prod-domain>/` | Dựng canonical, link navbar, logo trong trang render |
| `news.public-base-url` | `https://devlocal.metaticket.vn/news` | có khai báo | có khai báo | có khai báo | **Không được code sử dụng** (xem mục 9) |
| `news.cache-ttl-seconds` | `60` | `120` | `120` | `300` | TTL `expireAfterWrite` của **mọi** cache Caffeine |
| `news.render-cache-seconds` | `300` | `300` | `300` | `600` | `Cache-Control: max-age` của `/public/news/render/{slug}` (cache trình duyệt/CDN, không phải cache in-process) |

Biến môi trường (mẫu `conf-sample/service.news.env`, đặt ở `/etc/metaticket/service.news.env`, `chmod 640`, owner `root:metaticket`):
```
WORDPRESS_USERNAME=<wp-service-user>
WORDPRESS_APPLICATION_PASSWORD=<WP_APP_PASSWORD>
NEWS_INTERNAL_API_KEY=<long-random-secret>
```

### 6.2 Cache in-process (Caffeine)

`CaffeineCacheManager` với 7 cache, cùng một policy `expireAfterWrite(news.cache-ttl-seconds)` + `maximumSize(500)` **mỗi cache**:

| Cache | Điền bởi | Key |
|---|---|---|
| `categories` | `getNewsCategoryList` | mặc định |
| `articles` | `getNewsList`, `getRelatedNews`, `searchNews` | `list-…`, `related-…`, `search-…` |
| `article` | `getNewsBySlug` | slug |
| `featured` | `getFeaturedNews` | `pins-<excludeSlug>` |
| `articleHtml` | `renderArticle` | slug |
| `contentStyles` | `getContentStyles` | `bundle` |
| `contentScripts` | `getContentScriptsJson` | `bundle` |

**Evict khi ghi** (`@CacheEvict(allEntries = true)`):
- `AdminNewsService.createArticle/updateArticle/setVisibility/setPin`: `articles`, `article`, `categories`, `featured`.
- `NewsPinService.refresh*/confirm*`: `articles`, `article`, `featured`.
- **Không** thao tác nào evict `articleHtml`, `contentStyles`, `contentScripts` — chỉ hết hạn theo TTL hoặc qua `/internal/news/cache/invalidate`.

Cache là **cục bộ theo từng process**; nếu chạy nhiều instance, mỗi instance có cache riêng và endpoint invalidate phải gọi trên từng instance.

## 7. Xử Lý Lỗi

| Tình huống | HTTP | Nguồn |
|---|---|---|
| Bài không tồn tại (slug/id) | `404` "Article not found: …" | `NewsService`, `AdminNewsService` |
| Thiếu `keyword` khi search | `400` "keyword is required" | `NewsService.searchNews` |
| Thiếu tham số bắt buộc (`slug`, `page`, `size`...) | `400` | Spring MVC binding |
| Lỗi bảng ghim (quá slot, trùng, id sai, danh mục lạ) | `400` | `NewsPinService` |
| Sai/thiếu internal key | `401` "Invalid internal key" | `InternalNewsController` |
| WordPress trả lỗi khi đọc list/category/styles/scripts | `502` "WordPress request failed (<status>)…" | `WordPressClient` |
| WordPress `404` hoặc `400 rest_post_invalid_page_number` khi list | trả **trang rỗng**, không lỗi | `WordPressClient.getList` |
| Lỗi WordPress khi `createPost`/`updatePost`/`updatePostFields` | `500` (exception không được bọc) | `WordPressClient` |

Lỗi WP được log kèm path, tên tham số (không log giá trị) và tối đa 200 ký tự body.

## 8. Triển Khai

- Build bằng `./gradlew :service.news:bootDistTar` (không dùng `distTar`), chạy local bằng `bootRun`. Quy trình upload/systemd, đường dẫn cài đặt, port theo môi trường: xem **[SERVICE_DEPLOYMENT.md — mục "Service News (Spring Boot)"](./SERVICE_DEPLOYMENT.md#service-news-spring-boot)**.
- WordPress CMS: `metaticket-wordpress/docs/STAGING_DEPLOY.md`.
- Thay đổi nginx liên quan (`metaticket-public-api`, `metaticket-private-api`, `metaticket-root`, `metaticket-upstream-server`) là frag dùng chung cho mọi domain — backup trước khi sửa, `nginx -t` trước khi `reload`.

## 9. Các Lưu Ý Đặc Biệt / Hạn Chế Đã Biết

1. **Ẩn bài không làm mất trang render ngay.** `setVisibility` không evict `articleHtml`, nên `/news/{slug}.html` của bài vừa ẩn vẫn được phục vụ từ cache in-process tới hết `news.cache-ttl-seconds`, cộng thêm tối đa `news.render-cache-seconds` ở trình duyệt/CDN (`Cache-Control: public`). Tương tự sau khi sửa nội dung trên wp-admin. Cách xử lý hiện tại: gọi `/internal/news/cache/invalidate` (và purge CDN nếu có).
2. **Ẩn/hiện và ghim qua `PATCH .../visibility` / `.../pin` ghi lại nội dung đã render.** Hai thao tác này PUT lại cả bài với `content = content.rendered`, `excerpt = excerpt.rendered` và `title` đã strip HTML (dữ liệu đọc ở context `view`). Hệ quả: markup block Gutenberg/shortcode trong nội dung gốc bị thay bằng HTML đã render, tiêu đề mất định dạng HTML. Bảng ghim mới (`/pin/featured|category/...`) không bị vấn đề này vì chỉ PUT trường `mt_pin_order` / `mt_category_pins`.
3. **Số slot ghim nổi bật không thống nhất.** Code dùng `FEATURED_PIN_MAX = 6` (refresh ghim 6 bài, bảng trả `maxSlots = 6`, public featured chấp nhận rank `1..6`), trong khi comment trong code, `docs/API.md` và schema của mu-plugin vẫn ghi `1..3`. `NewsPinBoard.maxSlots` mặc định `3` nhưng mọi nơi khởi tạo đều truyền hằng số thực.
4. **Rewrite URL media hơi rộng.** Regex `https?://(admin|news)\.<host>/` trong `rewriteContentUrls` thay **mọi** host bắt đầu bằng `admin.` hoặc `news.`, kể cả link ngoài (ví dụ một trang tin bên thứ ba có subdomain `news.`) — link đó sẽ bị chuyển sang `news.media-base-url`.
5. **Danh sách admin chỉ sắp ghim trong từng trang**, không phải toàn cục (WP phân trang trước theo `modified`, BFF sắp lại trong trang).
6. **`searchNews` không trừ `excludeSlug` khỏi `totalElements`**; `getNewsList` (không lọc danh mục) có trừ 1 dù bài bị loại có thể không nằm trong tập kết quả.
7. **Không có webhook invalidate cache từ WordPress**; comment của `InternalNewsController` nhắc port `8082`, trên staging/prod phải dùng `4041`/`3041`.
8. **`news.public-base-url` không được sử dụng** ở đâu trong code (README mô tả là dùng cho SEO, thực tế canonical dựng từ `news.frontend-base-url`).
9. **API private không tự xác thực**: dựa hoàn toàn vào nginx `auth_request`. Nếu port 3041/4041 lộ ra ngoài, bất kỳ ai cũng gọi được API ghim/ẩn/sửa bài bằng quyền Application Password của user dịch vụ.
10. **Nhiều instance**: cache và các lệnh ghim "đọc-toàn-bộ-rồi-ghi" không có khóa phân tán; hai admin xác nhận bảng ghim cùng lúc có thể ghi chồng (last-write-wins).
11. Thư mục module có một số file thử nghiệm/rác không thuộc build (`Test.class`, `TestRegex.java/.class`, `TestTemplate.java/.class`, `TestMapper.kts`, `api.json`, `bff.json`, `post.json`, `bin/`) — không ảnh hưởng `bootDistTar` nhưng không nên coi là nguồn tham chiếu.

---

**Tài liệu này được cập nhật lần cuối**: 09/10/2026
**Version**: 1.0
**Tác giả**: MetaTicket Development Team

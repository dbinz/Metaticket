# TÌM KIẾM HỢP NHẤT (UNIFIED SEARCH) — TÀI LIỆU TỔNG HỢP

> **Phiên bản:** 1.0  
> **Dự án:** MetaTicket-Home  
> **Cập nhật:** 2026-10-09  
> **Trạng thái:** Đã có trên `master` — ô tìm kiếm navbar (desktop + mobile) và trang `/search`.  
> **Liên quan:** [NEWS.md](./NEWS.md) · [METATICKET_HOME_TECHNICAL_DOCUMENTATION.md](./METATICKET_HOME_TECHNICAL_DOCUMENTATION.md) (mục ACCESSORY STOREFRONT)

---

## MỤC LỤC

1. [Tổng quan & hiện trạng](#1-tổng-quan--hiện-trạng)
2. [Cấu trúc thư mục & routing](#2-cấu-trúc-thư-mục--routing)
3. [Kiến trúc & luồng dữ liệu](#3-kiến-trúc--luồng-dữ-liệu)
4. [Data models & state](#4-data-models--state)
5. [Đặc tả API](#5-đặc-tả-api)
6. [Luồng Frontend theo màn hình](#6-luồng-frontend-theo-màn-hình)
7. [Edge cases & lưu ý](#7-edge-cases--lưu-ý)
8. [Mở rộng: thêm một nguồn tìm kiếm mới](#8-mở-rộng-thêm-một-nguồn-tìm-kiếm-mới)
9. [Checklist & FAQ](#9-checklist--faq)

---

## 1. TỔNG QUAN & HIỆN TRẠNG

Một ô tìm kiếm duy nhất trên navbar tìm song song trên **3 nguồn độc lập**, mỗi nguồn là một backend khác nhau:

| Nhóm (cột) | Nguồn dữ liệu | Backend | Engine |
|------------|---------------|---------|--------|
| **Sự kiện** | Show (read-model) | `metaticket-homebackend` (metasearch) | Elasticsearch |
| **Sản phẩm** | Accessory show | `service.accessory` (`metaticket/kotlin`) | MongoDB regex |
| **Tin tức** | Bài viết | `service.news` (BFF) → WordPress REST | WordPress search |

| Hạng mục | Hiện trạng |
|----------|------------|
| Navbar desktop | `app-input-search` — dropdown 3 cột (laptop), 1 cột (mobile) |
| Navbar mobile | `app-input-search-mobile` — cùng panel, mở khi bấm icon tìm kiếm |
| Gợi ý | Tối thiểu **2 ký tự**, debounce **300ms**, 5 sự kiện / 3 sản phẩm / 3 tin |
| Ô trống | Hiện "Top sự kiện nổi bật" (6 show highlight, tải sẵn khi navbar khởi tạo) |
| Trang kết quả | `/search?key=&tab=&page=` — tab Tất cả / Sự kiện / Sản phẩm / Tin tức, phân trang |
| SEO | `/search` luôn gắn `robots: noindex, follow` |

**Nguyên tắc thiết kế:**

- Mỗi nguồn **render độc lập**: nguồn nhanh (ES) hiện trước, nguồn chậm (WordPress) không làm trễ nguồn khác.
- Mỗi nguồn có **timeout + trạng thái lỗi riêng** — một nguồn lỗi chỉ hiện "Không tải được" ở cột đó.
- Tầng `SearchService` là facade **thuần fan-out**, không cache, không giữ state; quyết định "keyword quá ngắn / rỗng" thuộc về caller.

---

## 2. CẤU TRÚC THƯ MỤC & ROUTING

```
src/app/
├── lib/
│   ├── components/
│   │   ├── input-search/                 # ô tìm kiếm navbar desktop + dropdown
│   │   ├── input-search-mobile/          # bản mobile (cùng logic, khác layout)
│   │   ├── search-suggest-panel/         # panel 3 cột dùng chung (presentational)
│   │   └── navbar/navbar.component.ts    # debounce + gọi SearchService.suggest()
│   ├── services/
│   │   ├── search/search.service.ts      # facade fan-out 3 nguồn
│   │   ├── show/show.service.ts          # searchShowByKeyword()
│   │   ├── accessory/accessory.service.ts# searchAccessoryShows()
│   │   └── news/news.service.ts          # searchNews()
│   ├── dto/search.dto.ts                 # SearchGroup<T>, SearchTab, factory
│   ├── utils/search-suggest.util.ts      # flattenSuggestGroups, hasSuggestContent
│   ├── utils/show-link.util.ts           # showDetailPath()  → /s/{slug} | /show/{id}
│   ├── utils/product-link.util.ts        # productDetailPath() → /san-pham/{slug|id}
│   └── constants/news.constants.ts       # newsArticleHref() → /news/{slug}.html
└── pages/search/                         # trang kết quả /search
```

| Route | Component | Query |
|-------|-----------|-------|
| `/search` | `SearchComponent` (lazy) | `key` (bắt buộc để có kết quả), `tab` = `all`\|`event`\|`product`\|`news` (mặc định `all`), `page` ≥ 1 (mặc định 1) |

`routeName.search = 'search'` (`lib/constants/route-name.ts`). Không có guard — trang công khai.

**Hằng số:**

| Hằng | Giá trị | File |
|------|---------|------|
| `MIN_SEARCH_KEYWORD_LENGTH` | `2` | `navbar.component.ts` (chỉ áp dụng cho dropdown) |
| debounce | `300` ms | `navbar.component.ts` |
| `SUGGEST_EVENT_SIZE` / `SUGGEST_PRODUCT_SIZE` / `SUGGEST_NEWS_SIZE` | `5` / `3` / `3` | `search.service.ts` |
| `EVENT_TIMEOUT_MS` / `PRODUCT_TIMEOUT_MS` / `NEWS_TIMEOUT_MS` | `6000` / `6000` / `8000` | `search.service.ts` |
| `ALL_PREVIEW_SIZE` | `6` (mỗi nhóm ở tab Tất cả) | `pages/search/search.component.ts` |
| `EVENT_PAGE_SIZE` / `PRODUCT_PAGE_SIZE` / `NEWS_PAGE_SIZE` | `12` / `12` / `10` | `pages/search/search.component.ts` |
| `COUNT_ONLY_SIZE` | `1` (chỉ để lấy `total` cho tab không active) | `pages/search/search.component.ts` |

---

## 3. KIẾN TRÚC & LUỒNG DỮ LIỆU

```
NavbarComponent                                     SearchComponent (/search)
  (handlerOnChanged) → _searchSubject                 queryParamMap
     map(trim) → debounceTime(300)                      → parse key/tab/page
     → distinctUntilChanged()                           → switchMap(_loadActiveTab$)
     → switchMap(len >= 2 ? suggest() : empty)                │
              │                                               │
              ▼                                               ▼
        SearchService.suggest(keyword)          searchEvents / searchProducts / searchNews
          combineLatest([                          (size theo tab, page = page-1)
            searchEvents(k, 0, 5),                  combineLatest([...])
            searchProducts(k, 0, 3),
            searchNews(k, 0, 3) ])
              │
   mỗi nhánh: map(toGroup) → timeout(N) → catchError(failedGroup) → startWith(loadingGroup)
              │
              ▼
   ShowService            AccessoryService              NewsService
   GET public/metasearch  GET public/accessory/         GET public/news/search
   /shows/suggestion      show/search                   (decode HTML entities)
```

**Vì sao `combineLatest` + `startWith(loading)`:** mỗi nhánh phát ngay giá trị `loading` nên `combineLatest` phát lần đầu tức thì, sau đó phát lại mỗi khi **một** nhánh resolve. Kết quả: cột Sự kiện (ES, nhanh) hiện trước cột Tin tức (WordPress, chậm) thay vì chờ nguồn chậm nhất như `forkJoin`.

**Vì sao `switchMap`:** gõ tiếp (navbar) hoặc đổi `key/tab/page` (trang `/search`) sẽ **hủy** các request đang bay của keyword cũ — không có race "kết quả cũ ghi đè kết quả mới".

**Vì sao timeout Tin tức dài hơn (8s):** WordPress chậm hơn hẳn 2 nguồn còn lại; vì các nguồn render độc lập nên budget rộng không làm trễ nguồn khác, chỉ quyết định lúc nào request chậm-nhưng-còn-sống bị coi là "Không tải được".

---

## 4. DATA MODELS & STATE

**File:** `src/app/lib/dto/search.dto.ts`

### `SearchGroup<T>` — state của một nhóm

| Trường | Kiểu | Mô tả |
|--------|------|-------|
| `items` | `T[]` | Kết quả trang hiện tại |
| `total` | `number` | `totalElements` từ backend (dùng cho đếm tab + phân trang) |
| `loading` | `boolean` | Đang gọi |
| `failed` | `boolean` | Lỗi/timeout → hiện "Không tải được" thay vì "Không có kết quả" |

Factory: `emptySearchGroup()`, `loadingSearchGroup()`, `failedSearchGroup()`.

### `SearchSuggestGroups` (`search.service.ts`)

```typescript
interface SearchSuggestGroups {
  events: SearchGroup<SearchShowResponse>;     // lib/entities/response/show/show.response.ts
  products: SearchGroup<AccessoryShowResponse>; // lib/entities/response/accessory/accessory.response.ts
  news: SearchGroup<NewsItem>;                  // lib/entities/news.response.ts
}
```

### `SearchTab`

`'all' | 'event' | 'product' | 'news'` — giá trị `tab` lạ trên URL được coi là `all`.

### State theo component

| Nơi giữ | State | Ghi chú |
|---------|-------|---------|
| `NavbarComponent` | `suggestGroups`, `highlightResults`, `searchValue` | Truyền xuống `app-input-search(-mobile)` qua `@Input` |
| `InputSearchComponent` / `InputSearchMobileComponent` | `showResults`, `activeIndex` | `activeIndex` là chỉ số trong danh sách **đã làm phẳng** (Sự kiện → Sản phẩm → Tin tức) |
| `SearchComponent` | `key`, `tab`, `page`, `searchBoxValue`, `eventsGroup`, `productsGroup`, `newsGroup` | URL là nguồn sự thật; `searchBoxValue` chỉ là bản nháp tới khi submit |

Không dùng Akita store; không lưu `localStorage` (trừ `ShowService.saveToLocalStorage(show)` khi chọn một sự kiện — danh sách "show đã xem" có sẵn của app).

---

## 5. ĐẶC TẢ API

Base URL: `{environment.metaDomain}/{environment.metaPrefix}/...` (qua `HttpClientService`), ví dụ `https://<storefront-domain>/api/public/...`. Cả 3 endpoint đều **public** (không cần đăng nhập).

> Hành vi backend dưới đây đọc từ code local của các repo `metaticket-homebackend`, `metaticket/kotlin` tại thời điểm viết. Nhánh đang chạy trên staging/prod có thể khác — xem gap đã biết của metasearch trong `docs/analysis/00-TONG-HOP-LIEN-KET.md` (thư mục `docs/` ở workspace gốc).

### 5.1. Danh sách endpoint

| # | apiUrl key | HTTP | Path | Service FE | Nhóm |
|---|------------|------|------|------------|------|
| 1 | `searchShowByKeyword` | GET | `public/metasearch/shows/suggestion` | `ShowService.searchShowByKeyword` | Sự kiện |
| 2 | `searchAccessoryShows` | GET | `public/accessory/show/search` | `AccessoryService.searchAccessoryShows` | Sản phẩm |
| 3 | `searchNews` | GET | `public/news/search` | `NewsService.searchNews` | Tin tức |
| 4 | `getHighlightShow` | GET | `public/metasearch/shows/highlight` | `ShowService.getHighlightShow` | "Top sự kiện nổi bật" (ô trống) |

### 5.2. `searchShowByKeyword` (metasearch)

```http
GET /api/public/metasearch/shows/suggestion?page=0&size=5&keyword=hoang
```

| Query | Bắt buộc | Mặc định BE | Ghi chú |
|-------|----------|-------------|---------|
| `keyword` | Có | — | Thiếu → 400 (Spring `@RequestParam`) |
| `page` | Không | `0` | 0-based |
| `size` | Không | `10` | FE luôn gửi |

Response: Spring `Page<ShowListItem>` → FE type `GetHighlightShowResponse` (`content: SearchShowResponse[]`, `totalElements`, `totalPages`, `number`, `size`, `first`, `last`, `empty`, ...). FE chỉ đọc `content` + `totalElements`.

Hành vi BE: `multi_match` kiểu `cross_fields`, toán tử AND (mọi từ phải khớp ở đâu đó) trên `showName^5`, `code^4`, `eventCategory`, `sessions.venue^2`, `sessions.sessionName`, `sessions.cityName^2`, `sessions.cityCode`, `descriptionText^0.5`; chỉ lấy show và session có `showInHome = true`.

### 5.3. `searchAccessoryShows` (service.accessory)

```http
GET /api/public/accessory/show/search?keyword=ao&page=0&size=3
```

| Query | Bắt buộc | Mặc định BE | Ghi chú |
|-------|----------|-------------|---------|
| `keyword` | Không | `""` | Rỗng → trang rỗng (200, không lỗi). > 100 ký tự → 400 |
| `page` | Không | `0` | 0-based, < 0 bị kẹp về 0 |
| `size` | Không | `6` | Kẹp `1..50` |

Response: `AccessoryShowListPage`

```typescript
interface AccessoryShowListPage {
  content: AccessoryShowResponse[]; // tóm tắt show (id đã Checksum-encode, slug?, name, bannerImageUrl?, status, minPrice?...)
  number: number;
  size: number;
  totalElements: number;            // BE kẹp tối đa 200
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
  numberOfElements: number;
}
```

Hành vi BE: không phân biệt hoa thường **và dấu tiếng Việt** (regex mở rộng từng chữ cái thành lớp ký tự có dấu); keyword cắt còn 64 ký tự. Khớp `name`/`code` xếp trước (khớp chính xác → bắt đầu bằng → còn lại), khớp chỉ ở `description` xếp sau; trong cùng hạng xếp theo trạng thái `Open` → `Waiting` → `Closed`, rồi mới nhất trước. Kết quả gồm cả show `Waiting`/`Closed` — FE hiện chip "Sắp mở bán / Đang bán / Đã đóng" ở trang `/search`.

### 5.4. `searchNews` (service.news)

```http
GET /api/public/news/search?keyword=concert&page=0&size=3
```

| Query | Bắt buộc | Ghi chú |
|-------|----------|---------|
| `keyword` | Có | Rỗng sau trim → 400 `keyword is required` |
| `page` | **Có** | 0-based (BE chuyển sang `page+1` của WordPress) |
| `size` | **Có** | Kẹp `1..50` |
| `excludeSlug` | Không | FE không dùng |

Response: `NewsSearchPage` (`content: NewsItem[]`, `totalElements`, `totalPages`, `number`, `size`, `first`, `last`, `empty`). `NewsService` decode HTML entity trong `title`/`summary`/`subdesc` trước khi trả cho UI.

Hành vi BE: gọi WordPress `search=` sắp theo ngày giảm dần, có cache Spring (`articles`, key theo keyword/page/size) — bài mới có thể chưa xuất hiện ngay.

### 5.5. Mã lỗi & cách FE xử lý

| Tình huống | Kết quả trên UI |
|------------|-----------------|
| `200` | Render nhóm |
| `4xx` / `5xx` / lỗi mạng | `failedSearchGroup` → "Không tải được" ở cột đó |
| Quá timeout (6s / 6s / 8s) | Như lỗi |
| Cả 3 nhóm lỗi (dropdown) | "Không tải được kết quả. Vui lòng thử lại." |

---

## 6. LUỒNG FRONTEND THEO MÀN HÌNH

### 6.1. Navbar — dropdown gợi ý

1. `NavbarComponent.ngOnInit` → `_loadHighlightResults()` gọi `getHighlightShow({ page: 0, size: 6 })` **một lần**, để "Top sự kiện nổi bật" sẵn sàng khi người dùng focus ô.
2. Mỗi lần gõ: `InputSearchComponent.onInputChange()` → emit `handlerOnChanged` → `NavbarComponent.onSearch()` → `_searchSubject.next()`.
3. Pipeline: `trim` → `debounceTime(300)` → `distinctUntilChanged` → `switchMap(len >= 2 ? suggest() : emptySuggestGroups())`.
4. Template dropdown (`input-search.component.html`) có 3 trạng thái:

| Điều kiện | Hiển thị |
|-----------|----------|
| `value.trim() === ''` | "Top sự kiện nổi bật" (`highlightResults`) |
| `0 < length < 2` | "Nhập tối thiểu 2 ký tự để tìm kiếm" |
| `length >= 2` và `hasSuggestContent()` | `app-search-suggest-panel` 3 cột |
| `length >= 2` và **không có kết quả nào** (đã resolve hết) | Dropdown **ẩn hẳn** (cố ý: không chèn empty-state khi đang gõ; empty-state chỉ có ở `/search`) |

5. Panel (`SearchSuggestPanelComponent`, presentational, `OnPush`): cột chỉ render khi có item **hoặc** nhóm đó lỗi. Có nút "Xem tất cả kết quả cho \"{keyword}\"".

**Điều hướng khi chọn:**

| Loại | Cách điều hướng | Đích |
|------|-----------------|------|
| Sự kiện | `NavigationService.navigateTo(showDetailPath(show))` + `ShowService.saveToLocalStorage(show)` | `/s/{slug}` nếu có slug, ngược lại `/show/{id}` |
| Sản phẩm | `navigateTo(productDetailPath(show))` | `/san-pham/{slug}` nếu có slug, ngược lại `/san-pham/{id}` |
| Tin tức | `<a href>` thường / `window.location.href` | `/news/{slug}.html` — trang bài viết là HTML do `service.news` render, **không** phải route Angular |
| Xem tất cả / Enter khi không chọn mục nào | `navigateTo('search', { queryParams: { key } })` | `/search?key=...` |

**Bàn phím:** `ArrowDown/ArrowUp` di chuyển `activeIndex` vòng tròn trên danh sách phẳng (`flattenSuggestGroups`); `Enter` mở mục đang chọn hoặc sang `/search`; `Escape` đóng panel. Click ra ngoài (`document:click`) đóng panel; mở panel chỉ do `(focus)`.

**Mobile:** `InputSearchMobileComponent` chỉ render khi `visibleSearch = true` (bấm icon tìm kiếm); panel giới hạn `max-h-[70vh]` có cuộn; nút suffix chuyển thành icon xóa khi có chữ.

### 6.2. Trang kết quả `/search`

1. `queryParamMap` → `_parseParams()`: `key` trim; `tab` không hợp lệ → `all`; `page` không phải số dương → `1`.
2. `_updateSeo()`: title `Kết quả tìm kiếm cho "{key}" | META Ticket`, description, `robots: noindex, follow` (gỡ ở `ngOnDestroy`).
3. `key` rỗng → reset cả 3 nhóm, không gọi API. Ngược lại `_loadActiveTab$()` gọi **cả 3 nguồn mỗi lần**:

| Tab | Nhóm active | Nhóm không active |
|-----|-------------|-------------------|
| `all` | cả 3: `page 0`, `size 6` | — |
| `event` | `page-1`, `size 12` | `size 1` (chỉ lấy `total` cho số đếm trên tab) |
| `product` | `page-1`, `size 12` | `size 1` |
| `news` | `page-1`, `size 10` | `size 1` |

4. Tab "Tất cả": mỗi nhóm hiện tối đa 6 item + link "Xem tất cả →" khi `total > items.length`. Nhóm không có kết quả (hoặc lỗi) bị **ẩn**.
5. Tab đơn: grid + pager số trang (`pageList`, không cửa sổ — giống `/news`). `page` vượt quá `totalPages` → `_clampPageIfNeeded()` điều hướng `replaceUrl` về trang cuối hợp lệ.
6. `hasNoResultAtAll` (cả 3 đã resolve và `total === 0`) → "Không tìm thấy kết quả nào cho ..." kèm link `/su-kien`, `/san-pham`, `/news`.
7. Ô tìm kiếm trong hero: Enter → `router.navigate(['/search'], { queryParams: { key } })` (reset `tab`/`page`).

Hiển thị: Sự kiện dùng `GridEventTicketComponent`; Tin tức dùng `NewsCardComponent` (link `.html`); Sản phẩm render card nội tuyến trong `search.component.html`.

---

## 7. EDGE CASES & LƯU Ý

- **Ngưỡng 2 ký tự chỉ áp ở dropdown.** `/search?key=a` vẫn gọi cả 3 API với keyword 1 ký tự và có thể trả về rất nhiều kết quả kém liên quan. Muốn đồng nhất với dropdown thì phải thêm kiểm tra ở `SearchComponent._parseParams()` / `ngOnInit`.
- **Lỗi bị hiển thị như "không có kết quả" ở `/search`.** Khác dropdown, trang `/search` không phân biệt `failed`: nhóm lỗi có `total = 0` nên bị ẩn ở tab Tất cả, hiện "Không tìm thấy ..." ở tab đơn, và nếu cả 3 lỗi thì hiện "Không tìm thấy kết quả nào". Số đếm trên tab cũng thành `0`.
- **Tổng kết quả sản phẩm bị kẹp 200** (`SEARCH_MATCH_CAP` ở BE) — số đếm/phân trang không vượt quá 200.
- **`size` > 50** bị BE accessory/news kẹp về 50 — nếu tăng `*_PAGE_SIZE` vượt 50, phân trang FE sẽ lệch.
- **Đếm tab tốn request:** mỗi lần đổi tab/trang gọi lại cả 3 nguồn (2 nguồn với `size=1`). Chấp nhận được vì rẻ, nhưng là 3 request/lần điều hướng.
- **Dropdown ẩn khi không có kết quả:** người dùng không thấy thông báo "không có kết quả" khi gõ — có chủ đích (`hasSuggestContent`), vẫn bấm Enter để sang `/search`.
- **Link Tin tức là `/news/{slug}.html`:** trên `ng serve` thuần, link này không có nginx/BFF phía sau. Muốn thử local phải chạy `server.ts` (proxy tới BFF qua biến môi trường `NEWS_RENDER_BASE`) hoặc đi qua nginx.
- **`saveToLocalStorage` dùng key `'shows'` trực tiếp trên `localStorage`** (không qua `LocalStorageService`) — đã có từ trước, search chỉ tái dùng.
- **Không có hủy request khi đóng dropdown:** đóng panel không hủy `suggest()` đang bay; kết quả về vẫn được gán vào `suggestGroups` (vô hại, chỉ hiển thị lần focus sau).
- **XSS:** tất cả text hiển thị qua interpolation Angular (`{{ }}`), không dùng `innerHTML`; `NewsService` chỉ decode entity cho text thuần.

---

## 8. MỞ RỘNG: THÊM MỘT NGUỒN TÌM KIẾM MỚI

Ví dụ thêm nhóm "Địa điểm" (`venue`):

1. **API & service nguồn:** thêm key vào `lib/constants/api-url.ts` (camelCase, bắt đầu bằng động từ, ví dụ `searchVenues`) và method trong service domain tương ứng, trả về page có `content` + `totalElements`. Backend nên: keyword rỗng → trang rỗng hoặc 400 rõ ràng, kẹp `size`, có cache ngắn.
2. **`SearchService`:** thêm `searchVenues(keyword, page, size)` theo đúng mẫu `map(toGroup) → timeout → catchError(failedSearchGroup) → startWith(loadingSearchGroup)`; thêm hằng `SUGGEST_VENUE_SIZE`, `VENUE_TIMEOUT_MS`; thêm nhánh vào `combineLatest` của `suggest()`, field mới vào `SearchSuggestGroups` và `emptySuggestGroups()`.
3. **Utils:** thêm `kind: 'venue'` vào `SearchFlatItem`, nối vào `flattenSuggestGroups()` **đúng thứ tự hiển thị**, và thêm điều kiện vào `hasSuggestContent()` (cả `loading`, `items.length` lẫn điều kiện "tất cả đều lỗi").
4. **Panel:** thêm cột trong `search-suggest-panel.component.html`; cập nhật `hasAnyResult` / `isAnyLoading` / `isAllFailed` và hàm `isXxxActive(index)` (offset = tổng số item các nhóm đứng trước). Đổi `laptop:grid-cols-3` nếu số cột thay đổi.
5. **Input components:** thêm nhánh trong `_pickFlat()` và output `pickXxx` ở **cả** `InputSearchComponent` và `InputSearchMobileComponent` (hai component đang lặp logic — sửa một quên một là lỗi điều hướng bằng bàn phím).
6. **Trang `/search`:** thêm giá trị vào `SearchTab` + `_parseParams()`, hằng `VENUE_PAGE_SIZE`, nhóm `venuesGroup`, nhánh trong `_loadActiveTab$()` (kể cả `COUNT_ONLY_SIZE` khi không active), tab + section trong template, và cộng vào `hasNoResultAtAll` + tổng số kết quả ở hero/tab "Tất cả".
7. Cập nhật tài liệu này (mục 1, 2, 5).

---

## 9. CHECKLIST & FAQ

### Checklist smoke

- [ ] Focus ô trống → "Top sự kiện nổi bật" (≤ 6 show)
- [ ] Gõ 1 ký tự → "Nhập tối thiểu 2 ký tự"
- [ ] Gõ ≥ 2 ký tự → 3 cột hiện dần, Sự kiện thường xuất hiện trước Tin tức
- [ ] Chặn 1 endpoint (DevTools) → chỉ cột đó "Không tải được"
- [ ] Mũi tên lên/xuống + Enter mở đúng mục; Escape đóng
- [ ] Chọn sự kiện có slug → `/s/{slug}`; không slug → `/show/{id}`
- [ ] Chọn sản phẩm → `/san-pham/{slug|id}`; chọn tin → `/news/{slug}.html`
- [ ] "Xem tất cả" → `/search?key=...`, số đếm 4 tab đúng
- [ ] Tab đơn có pager; sửa `page=999` trên URL → tự về trang cuối
- [ ] View source `/search` có `<meta name="robots" content="noindex, follow">`
- [ ] Mobile 390px: panel cuộn được, không tràn ngang

### FAQ

**Q: Vì sao cột Sản phẩm có show đã đóng bán?**  
BE accessory trả cả `Waiting`/`Closed` (xếp sau `Open`); FE gắn chip trạng thái ở `/search`. Dropdown không hiện chip.

**Q: Gõ đúng tên bài viết nhưng Tin tức không ra?**  
Kiểm tra bài đã publish trên WordPress, và cache `articles` của `service.news` (kết quả search được cache theo keyword/page/size).

**Q: Sự kiện không ra dù có trên trang chủ?**  
Metasearch chỉ trả show có `showInHome = true` ở cả show lẫn session, và yêu cầu mọi từ của keyword đều khớp. Show mới phải được `service.show` đẩy sang metasearch (JobRunr) trước.

**Q: Muốn đổi số gợi ý mỗi cột?**  
Sửa `SUGGEST_*_SIZE` trong `search.service.ts`. Lưu ý dropdown desktop cố định rộng `900px` (`input-search.component.html`), mobile giới hạn `70vh`.

---

*Tài liệu phản ánh code `master` tại 2026-10-09. Khi đổi contract API của một nguồn, cập nhật mục 5; khi thêm nguồn, làm theo mục 8.*

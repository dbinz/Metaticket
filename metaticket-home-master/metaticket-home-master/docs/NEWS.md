# PHÂN HỆ TIN TỨC (NEWS) — TÀI LIỆU TỔNG HỢP

> **Phiên bản:** 2.0  
> **Dự án:** MetaTicket-Home  
> **Cập nhật:** 2026-07-30  
> **Trạng thái:**  Hybrid SSR cho `/news*` trên staging.  
> **Liên quan:** [DEPLOYMENT.md](./DEPLOYMENT.md)

Gộp từ: `NEWS_API_SPECIFICATION.md` · `NEWS_MODULE_DOCUMENTATION.md` · `NEWS_SSR_CURRENT_STATE.md`

---

## MỤC LỤC

1. [Tổng quan & hiện trạng](#1-tổng-quan--hiện-trạng)
2. [Cấu trúc thư mục & routing](#2-cấu-trúc-thư-mục--routing)
3. [Quy tắc đặt tên](#3-quy-tắc-đặt-tên)
4. [Data models](#4-data-models)
5. [Đặc tả API](#5-đặc-tả-api)
6. [Luồng Frontend theo màn hình](#6-luồng-frontend-theo-màn-hình)
7. [Hybrid SSR & deploy](#7-hybrid-ssr--deploy)
8. [Bảo mật HTML](#8-bảo-mật-html)
9. [Checklist & FAQ](#9-checklist--faq)

---

## 1. TỔNG QUAN & HIỆN TRẠNG

| Hạng mục | Hiện trạng |
|----------|------------|
| List | `/news` — grid card, 9 tin đầu, **Xem thêm** (+6) |
| Detail | `/news/:slug` — layout 70/30, HTML content, related, sự kiện ghim |
| Navbar | Dropdown **Tin tức** theo category (`Tất cả` + API categories) |
| Data | **API thật** qua `NewsService` + `HttpClientService` |
| Staging render | Nginx `:80` → proxy `/news*` → Node `:4000` SSR |
| Prod / Dev compose | CSR qua Nginx (`Dockerfile` cũ) |

**Công nghệ:** Angular 17+ Standalone, TailwindCSS, Ng-Zorro, RxJS.

**Component tái sử dụng:**

- `NewsCardComponent` — thẻ bài viết (chung cho list / sidebar / related)
- `GridEventTicketComponent` — sự kiện nổi bật (highlight)
- `SafeHtmlPipe` — sanitize HTML trước `[innerHTML]`

**Base URL API:**

```
{environment.metaDomain}/{environment.metaPrefix}/...
```

Ví dụ staging: `https://staging.metaticket.vn/api/public/news/...`

---

## 2. CẤU TRÚC THƯ MỤC & ROUTING

```
src/app/
├── lib/
│   ├── entities/news.response.ts
│   ├── constants/news.constants.ts      # page size, ALL category code
│   ├── constants/api-url.ts             # news endpoints
│   ├── components/news-card/
│   ├── pipes/safe-html/
│   └── services/news/news.service.ts
└── pages/news/
    ├── news-home/
    └── news-detail/

server.ts                                # Express hybrid: SSR /news*, CSR còn lại
src/main.server.ts
src/app/app.config.server.ts
```

| Route | Component | Query / Param |
|-------|-----------|---------------|
| `/news` | `NewsHomeComponent` | `?categoryCode=&categoryName=` |
| `/news/:slug` | `NewsDetailComponent` | `slug` |

Lazy load trong `app.routes.ts`.

**Hằng số** (`news.constants.ts`):

| Key | Value |
|-----|-------|
| `NEWS_INITIAL_PAGE_SIZE` | `9` |
| `NEWS_LOAD_MORE_PAGE_SIZE` | `6` |
| `NEWS_ALL_CATEGORY_CODE` | `'all'` (client-side; không gửi filter khi = all) |

---

## 3. QUY TẮC ĐẶT TÊN

### 3.1. API (`apiUrl` key & service method)

| Quy tắc | Ví dụ đúng |
|---------|------------|
| camelCase | `getNewsList` |
| Bắt đầu bằng động từ | `get`, `search`, `add`, `update`, `delete` |
| Động từ + danh từ | `getNewsBySlug` |

### 3.2. Field

| Quy tắc | Ví dụ đúng |
|---------|------------|
| camelCase | `createdAt`, `readTime` |
| ID viết `Id` | `newsId`, `categoryId` |
| Mảng số nhiều | `relatedNews`, `relatedEventIds` |

### 3.3. HTTP path (Backend)

- kebab-case, nhóm resource: `public/news/...`
- Không đặt động từ trong path

```
GET  public/news/fetch/all      → apiUrl.getNewsList
GET  public/news/fetch/slug     → apiUrl.getNewsBySlug
```

---

## 4. DATA MODELS

**File:** `src/app/lib/entities/news.response.ts`

### `NewsCategoryItem`

| Trường | Kiểu | Mô tả |
|--------|------|-------|
| `id` | `string` | Mã danh mục |
| `name` | `string` | Tên hiển thị |
| `code` | `string` | Filter API |
| `slug` | `string` | Slug URL |

### `NewsItem`

| Trường | Kiểu | Mô tả |
|--------|------|-------|
| `id` | `string` | ID bài |
| `thumbnail` | `string` | URL ảnh |
| `createdAt` | `string` | ISO 8601 |
| `displayDate` | `string` | Chuỗi hiển thị UI |
| `title` | `string` | Tiêu đề |
| `summary` | `string` | Tóm tắt |
| `slug` | `string` | Unique |
| `categoryId` / `categoryCode` / `categoryName` | `string` | Danh mục |

### `NewsDetailResponse` extends `NewsItem`

| Trường | Kiểu | Bắt buộc | Mô tả |
|--------|------|----------|-------|
| `content` | `string` | Có | HTML (sanitize server + `SafeHtmlPipe`) |
| `author` | `string` | Có | Tác giả |
| `readTime` | `string` | Có | vd. `"4 min read"` |
| `relatedNews` | `NewsItem[]` | Không | (optional; FE có thể tự fetch) |
| `relatedEventIds` | `string[]` | Không | (optional; FE dùng highlight show) |

### `GetNewsListRequest` / `GetNewsListResponse`

```typescript
interface GetNewsListRequest {
  offset: number;
  size: number;
  categoryCode?: string;
  sort?: string; // mặc định FE: createdAt,desc
}

interface GetNewsListResponse {
  content: NewsItem[];
  totalElements: number;
  offset: number;
  size: number;
  last: boolean;
  empty: boolean;
}
```

---

## 5. ĐẶC TẢ API

### 5.1. Danh sách endpoint

| # | apiUrl key | HTTP | Path | Màn hình |
|---|------------|------|------|----------|
| 1 | `getNewsCategoryList` | GET | `public/news/category/fetch/all` | Navbar |
| 2 | `getNewsList` | GET | `public/news/fetch/all` | News home / detail side |
| 3 | `getNewsBySlug` | GET | `public/news/fetch/slug` | News detail |
| 4 | `getHighlightNews` | GET | `public/news/fetch/highlight` | (đề xuất / tương lai) |
| 5 | `searchNews` | GET | `public/news/search` | Tương lai |
| 6 | `getRelatedNews` | GET | `public/news/fetch/related` | Tùy chọn |
| 7 | `getNewsAdvertisement` | GET | `public/news/advertisement/fetch` | Tương lai |

**Show tái sử dụng:**

| apiUrl | Path | Dùng cho |
|--------|------|----------|
| `getHighlightShow` | `public/metasearch/shows/highlight` | **Sự kiện nổi bật** trên detail (đã dùng) |
| `getShowDetail` | `public/show/fetch` | Fallback |

### 5.2. `getNewsCategoryList`

- Auth: không
- Request: không query
- Response: `NewsCategoryItem[]`
- FE thêm mục **"Tất cả tin tức"** (`code=all`) ở client

### 5.3. `getNewsList`

| Lần gọi | `offset` | `size` |
|---------|----------|--------|
| Lần đầu | `0` | `9` |
| Xem thêm | số tin đã load | `6` |

Query: `offset`, `size`, `categoryCode?`, `sort?`  
Dừng khi `last === true`.

```http
GET /api/public/news/fetch/all?offset=0&size=9&sort=createdAt,desc
GET /api/public/news/fetch/all?offset=0&size=9&categoryCode=am-thuc&sort=createdAt,desc
GET /api/public/news/fetch/all?offset=9&size=6&categoryCode=am-thuc&sort=createdAt,desc
```

### 5.4. `getNewsBySlug`

```http
GET /api/public/news/fetch/slug?slug=quan-ha-noi-ban-dac-san
```

- `404` / lỗi → FE redirect `/not-found` (browser)

### 5.5. Mã lỗi

| HTTP | Ý nghĩa | FE |
|------|---------|-----|
| `200` | OK | Render |
| `400` | Request sai | Lỗi / redirect |
| `404` | Không tìm thấy slug | `/not-found` |
| `500` | Server | Toast / retry |

---

## 6. LUỒNG FRONTEND THEO MÀN HÌNH

### 6.1. Navbar

1. `NewsService.getNewsCategoryList()`
2. Dropdown → `/news` hoặc `/news?categoryCode=&categoryName=`

### 6.2. News home (`/news`)

1. `getNewsList({ offset: 0, size: 9, categoryCode?, sort: 'createdAt,desc' })`
2. **Xem thêm** → `size: 6`, `offset` = số tin đã có
3. Render `NewsCardComponent`

### 6.3. News detail (`/news/:slug`)

```
1. getNewsBySlug(slug)
   └── title, content, metadata

2. Song song (forkJoin):
   ├── getNewsList(size≈4)          → "Có thể bạn quan tâm" (2 bài, exclude bài hiện tại)
   ├── getNewsList(categoryCode)    → "Bài viết liên quan" (cùng category)
   └── ShowService.getHighlightShow({ page:0, size:2 })
                                    → "Sự kiện nổi bật"
```

- Navbar luôn render (kể cả khi đang load)
- Quảng cáo sidebar: **tạm ẩn**
- Card related dùng chung template với listing (`NewsCardComponent`)
- Lưới related = cùng grid với sự kiện (`grid-cols-2` / `laptop:grid-cols-3`)

### 6.4. `NewsService` map

| Method | apiUrl |
|--------|--------|
| `getNewsCategoryList()` | `getNewsCategoryList` |
| `getNewsList(request)` | `getNewsList` |
| `getNewsBySlug(slug)` | `getNewsBySlug` |

SSR dùng URL tuyệt đối qua `HttpClientService` (`metaDomain` + `metaPrefix`).

---

## 7. HYBRID SSR & DEPLOY

### 7.1. `server.ts`

- `/news`, `/news/*` → `CommonEngine.render()` (SSR)
- Static assets → `express.static`
- Route khác → `index.html` (CSR)

### 7.2. SSR-safe

- `provideClientHydration()`, `provideHttpClient(withFetch(), ...)`
- `LocalStorageService` / `NavigationService` guard `isPlatformBrowser`
- Fetch trong `ngOnInit`; browser-only trong `afterNextRender`
- `angular.json`: `prerender: false`, `ssr.entry: server.ts`

### 7.3. Staging architecture

| | Staging hiện tại |
|--|------------------|
| Public | Nginx `:80` (host **44200**) |
| `/news*` | Nginx proxy → Node `127.0.0.1:4000` SSR |
| Khác | Nginx static CSR |
| Log | Volume nginx log |

Files: `Dockerfile.staging`, `nginx.staging.conf`, `docker/entrypoint.staging.sh`, `docker-compose.staging.yml`.

### 7.4. Chạy local

```bash
# CSR
yarn startDev

# Hybrid SSR
yarn build:staging
# PowerShell:
$env:PORT=4000; yarn serve:ssr:mt-Ticket
```

### 7.5. Deploy staging

```bash
docker context use metaticket-staging
docker compose -f docker-compose.staging.yml up -d --build
```

Smoke: `http://<host>:44200/news`

### 7.6. Ma trận môi trường

| Môi trường | News data | `/news` render |
|------------|-----------|----------------|
| Local CSR | API | CSR |
| Local Hybrid SSR | API | **SSR** |
| Staging | API | **SSR** (Nginx → Node) |
| Prod | API (khi bật) | CSR Nginx |

---

## 8. BẢO MẬT HTML

- Backend nên sanitize HTML CMS trước khi trả
- Frontend: `[innerHTML]="content | safeHtml"` (`SafeHtmlPipe` / DomSanitizer)
- Không render HTML thô từ user input

---

## 9. CHECKLIST & FAQ

### Checklist smoke

- [ ] List 9 tin mới nhất
- [ ] Xem thêm +6, ẩn nút khi `last`
- [ ] Filter `categoryCode`
- [ ] Detail theo slug; 404 → not-found
- [ ] Sidebar 2 bài quan tâm
- [ ] Related cùng category
- [ ] 2 sự kiện highlight
- [ ] HTML an toàn qua `SafeHtmlPipe`
- [ ] SSR View Source `/news` có nội dung

### FAQ

**Q: List trống sau deploy?**  
Kiểm tra `metaDomain` / CORS / shape response (`content`, `last`, `categoryCode`).

**Q: Staging đã SSR chưa?**  
Có — Nginx proxy `/news*` → Node `:4000`.

**Q: Vì sao vẫn giữ Nginx?**  
Cổng public, reverse proxy, static nhanh, log volume — Node không expose ra host.

**Q: Mock còn không?**  
Đã xóa `news.mock.ts` và flag `USE_NEWS_MOCK`. Chỉ còn `news.constants.ts` (page size / all code).

---

*Tài liệu phản ánh code tại 2026-07-30. Khi đổi contract API hoặc kiến trúc SSR, cập nhật mục 5 và 7.*

# service.news — Public API (customer / MetaTicket-Home)

Contract aligned with **News_API_Datatypes_Samples** (v1.1).

Base (local): `https://devlocal.metaticket.vn/api/public/news`

## Endpoints

### `GET /category/fetch/all` — getNewsCategoryList

No query params. Returns `newsCategoryItem[]`.

```json
[
  { "id": "cat-am-thuc", "name": "Ẩm thực", "code": "am-thuc", "slug": "am-thuc" }
]
```

FE adds "Tất cả tin tức" (`categoryCode=all`) on the client.

### `GET /fetch/all` — getNewsList

| Query | Default | Description |
|-------|---------|-------------|
| `offset` | `0` | 0-based start index |
| `size` | `9` | Page size (max 50) |
| `categoryCode` | — | Category code/slug; omit or `all` = no filter |
| `sort` | `createdAt,desc` | Ignored when `categoryCode` is set (category pins 1..3 then `modifiedAt`) |
| `excludeSlug` | — | Omit this article from the result |

When `categoryCode` is set, ranking is **category pin 1..3 then `modifiedAt` desc** (FE `sort` is ignored so pins win). Omit/`all` keeps WP `sort`.

Each item also includes `author`.

`excludeSlug` (optional) drops that article from the page (open-article rails).

### `GET /fetch/featured` — getFeaturedNews

Featured pins **1..3 only** (hub hero / “Bài viết nổi bật”). Empty slots are omitted — never filled by recency.

| Query | Default | Description |
|-------|---------|-------------|
| `excludeSlug` | — | Omit the currently open article |

```json
{ "content": [ { "id": "5", "title": "...", "slug": "...", "author": "MetaTicket" } ], "totalElements": 2 }
```

### `GET /fetch/related` — getRelatedNews

Same-category fill after tag overlap. Returns up to `size` items (default 6); fewer is OK if pools run out.

| Query | Default | Description |
|-------|---------|-------------|
| `slug` | required | Current article |
| `size` | `6` | Max items (1–20) |
| `excludeSlug` | — | Extra slug to omit |

Order: (1) most shared tags, then (2) newest published in the same category. Stops at `size` or when both pools are exhausted. Empty only when nothing matched.

```json
{ "content": [ /* NewsItem × 6 */ ], "totalElements": 6 }
```

### `GET /fetch/slug` — getNewsBySlug

```json
{
  "content": [
    {
      "id": "5",
      "thumbnail": "https://...",
      "createdAt": "2025-01-15T10:20:00Z",
      "displayDate": "10:20 Thứ Tư, 15/01/2025",
      "title": "...",
      "summary": "...",
      "slug": "...",
      "categoryId": "cat-am-nhac",
      "categoryCode": "am-nhac",
      "categoryName": "Âm nhạc"
    }
  ],
  "totalElements": 34,
  "offset": 0,
  "size": 9,
  "last": false,
  "empty": false
}
```

### `GET /fetch/slug` — getNewsBySlug

| Query | Required |
|-------|----------|
| `slug` | yes (server normalizes) |

404 when missing → FE `/not-found`.

```json
{
  "id": "1",
  "thumbnail": "...",
  "createdAt": "...",
  "displayDate": "...",
  "title": "...",
  "summary": "...",
  "slug": "...",
  "content": "<p>...</p>",
  "author": "MetaTicket",
  "readTime": "4 min read",
  "tags": ["Âm nhạc", "Festival"],
  "highlightNews": [],
  "highlightEventIds": [],
  "highlightEvents": []
}
```

`highlightEventIds` / `highlightEvents` are reserved (empty until show-pin meta is wired).

### `GET /search` — searchNews

| Query | Required |
|-------|----------|
| `keyword` | yes |
| `page` | yes (0-based) |
| `size` | yes |
| `excludeSlug` | no |

Spring-style page body with `pageable`, `totalPages`, `first`, `last`, `empty`.

## FE path map

```
getNewsCategoryList  -> GET  public/news/category/fetch/all
getNewsList          -> GET  public/news/fetch/all?offset=&size=&categoryCode=&excludeSlug=
getFeaturedNews      -> GET  public/news/fetch/featured?excludeSlug=
getRelatedNews       -> GET  public/news/fetch/related?slug=&size=&excludeSlug=
getNewsBySlug        -> GET  public/news/fetch/slug?slug=
searchNews           -> GET  public/news/search
```

Full URL: `{metaDomain}/api/{path}`

# service.news

Spring Boot BFF: homepage / admin → `service.news` → WordPress REST.

## Ports

| Env | Port | nginx upstream |
|-----|------|----------------|
| local (dev) | `8082` | nginx-docker `NEWS_BACKEND_LISTEN_PORT` |
| staging | `4041` | `news_service` |
| prod | `3041` | `news_service` |

## Local

Yêu cầu: WordPress đang chạy (`metaticket-wordpress`, port 8081).

```bash
cd metaticket/kotlin
./gradlew :service.news:bootRun --args='--spring.profiles.active=dev'
# http://localhost:8082
```

## Build / deploy (giống các service khác)

Spring Boot dùng **`bootDistTar`** (thay vì Ktor `distTar`):

```bash
./gradlew :service.news:bootDistTar
# → service.news/build/distributions/service.news-boot-3.0.0.tar
```

Giải nén lên `/staging|opt/metaticket/service/service.news/`, copy systemd từ `conf-sample/`, secrets từ `conf-sample/service.news.env`.

Chi tiết: `docs/SERVICE_DEPLOYMENT.md` (mục service.news) và `metaticket-wordpress/docs/STAGING_DEPLOY.md`.

## API

Xem [docs/API.md](./docs/API.md).

## Config

| Key | Ghi chú |
|-----|---------|
| `wordpress.base-url` | Local WP / `127.0.0.1:8081` trên server |
| `WORDPRESS_USERNAME` / `WORDPRESS_APPLICATION_PASSWORD` | Env trên server |
| `NEWS_INTERNAL_API_KEY` | Cache invalidate webhook |
| `news.public-base-url` / `news.media-base-url` | SEO + media rewrite |
| `news.cache-ttl-seconds` | Cache TTL |

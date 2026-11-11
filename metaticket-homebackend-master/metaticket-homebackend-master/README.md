# MetaTicket Home Backend (Search Service)

MetaTicket Home Backend (còn gọi là **metasearch**) là **service tìm kiếm và hiển thị thông tin sự kiện cho trang chủ** của hệ thống MetaTicket (metaticket.vn). Service dùng Elasticsearch làm search engine để phục vụ truy vấn nhanh từ giao diện storefront.

## Dự án này phục vụ phần nào của hệ thống?

Đây là **backend chuyên biệt cho trang chủ**, tách riêng khỏi backend nghiệp vụ chính (`metaticket.core`) để tối ưu cho việc tìm kiếm và hiển thị danh sách show. Chức năng chính:

- **Tìm kiếm sự kiện**: theo từ khóa, danh mục, địa điểm, thời gian (Elasticsearch).
- **Hiển thị show nổi bật & banner**: cung cấp danh sách show highlight và banner cho trang chủ.
- **Đọc dữ liệu show cho trang chủ**: chi tiết show, danh mục, danh sách thành phố...

> ⚠️ Service này **không phải là nguồn quản lý dữ liệu show**. Mọi dữ liệu show (bao gồm thông tin ghim/xếp hạng, số vé đã bán...) đều do **`service.show` của `metaticket.core`** quản lý và đẩy sang đây. Service này chỉ **phản ánh (read model)** dữ liệu đã được core system cập nhật để phục vụ tìm kiếm/hiển thị.

### Liên hệ với các dự án khác

| Dự án | Vai trò trong hệ thống |
| --- | --- |
| `metaticket-homebackend` (dự án này) | Service tìm kiếm/hiển thị sự kiện (Elasticsearch) cấp dữ liệu cho trang chủ |
| `metaticket-home` | Giao diện storefront Angular, gọi service này để tìm kiếm/hiển thị sự kiện |
| `metaticket.core` | Backend nghiệp vụ (vé, đơn, thanh toán...); `service.show` là nguồn dữ liệu show, đẩy/cập nhật sang service này |

### API chính

- `/public/metasearch/shows` — endpoint công khai cho frontend (tìm kiếm, highlight, chi tiết).
- `/internal/metasearch/shows` — endpoint nội bộ nhận cập nhật dữ liệu show từ `service.show` (core system), xác thực bằng API key.

### Công nghệ

- **Framework**: Spring Boot 3.2.2 (Kotlin)
- **Search Engine**: Elasticsearch
- **Build**: Gradle, chạy trên Amazon Corretto JDK 17
- **Port**: `8080` (production), `48080` (development/staging)

> 📚 Chi tiết kiến trúc, models và API xem [`docs/METATICKET_HOMEBACKEND_TECHNICAL_DOCUMENTATION.md`](./docs/METATICKET_HOMEBACKEND_TECHNICAL_DOCUMENTATION.md). Hướng dẫn triển khai xem [`docs/DEPLOYMENT.md`](./docs/DEPLOYMENT.md).

---

## Phát triển local

```bash
# Build
./gradlew build

# Chạy service (profile dev, port 48080)
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Cần một Elasticsearch instance đang chạy (xem `docker-compose.elastic.yml`).

```bash
docker compose -f docker-compose.elastic.yml up -d
```

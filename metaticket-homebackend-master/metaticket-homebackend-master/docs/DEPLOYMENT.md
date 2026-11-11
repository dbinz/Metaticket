# Deployment Guide - MetaTicket Home Backend (Search Service)

## Tổng quan

MetaTicket Home Backend là ứng dụng Spring Boot (Kotlin/Java), được build bằng Gradle và chạy trên Amazon Corretto JDK 17. Deployment sử dụng Docker với docker compose thông qua Docker Context để deploy từ xa lên server.

**Lưu ý:** Cả môi trường staging và production hiện tại đều được deploy trên cùng một server (staging server) để giảm tải cho server production.

---

## Kiến trúc Deployment

| Môi trường  | Compose File                 | Container Name             | Port  | Spring Profile | Network            |
|-------------|------------------------------|----------------------------|-------|----------------|--------------------|
| Development | `docker-compose.dev.yml`     | dev_metaticket_search      | 48080 | dev            | dev_metaticket     |
| Staging     | `docker-compose.staging.yml` | staging_metaticket_search  | 48080 | staging        | staging_metaticket |
| Production  | `docker-compose.prod.yml`    | prod_metaticket_search     | 8080  | prod           | prod_metaticket    |

---

## Yêu cầu

- Docker Engine đã cài đặt trên máy local
- SSH access đến server đích (staging server)
- Docker Engine đã cài đặt trên server đích
- Server cần có đủ RAM (khuyến nghị tối thiểu 2GB free) cho JVM

---

## Thiết lập Docker Context

Docker Context cho phép bạn chạy lệnh docker từ máy local nhưng thực thi trên server từ xa thông qua SSH.

### 1. Tạo Docker Context cho Staging Server

```bash
docker context create metaticket-staging \
  --docker "host=ssh://user@<STAGING_SERVER_IP>"
```

Thay `user` bằng username SSH và `<STAGING_SERVER_IP>` bằng IP hoặc hostname của staging server.

> **Tip:** Nếu đã tạo context cho project `metaticket-home`, có thể dùng chung context vì cùng deploy trên một server.

### 2. Xác nhận context đã tạo

```bash
docker context ls
```

Output mẫu:

```
NAME                    DESCRIPTION     DOCKER ENDPOINT
default *               Current...      unix:///var/run/docker.sock
metaticket-staging                      ssh://user@192.168.1.100
```

### 3. Chuyển sang context cần sử dụng

```bash
docker context use metaticket-staging
```

Sau khi chạy lệnh này, tất cả lệnh docker (bao gồm docker compose) sẽ được thực thi trên staging server.

### 4. Quay về context local (khi cần)

```bash
docker context use default
```

---

## Quy trình Deploy

### Deploy Staging

```bash
# Chuyển sang context staging server
docker context use metaticket-staging

# Tạo network (chỉ cần chạy lần đầu, có thể đã tạo từ frontend)
docker network create staging_metaticket

# Build và deploy
docker compose -f docker-compose.staging.yml up -d --build
```

Service sẽ khởi động với `SPRING_PROFILES_ACTIVE=staging`, load cấu hình từ `application-staging.yml`.

### Deploy Production

Vì production cũng deploy trên staging server, ta sử dụng cùng Docker Context:

```bash
# Chuyển sang context staging server
docker context use metaticket-staging

# Tạo network (chỉ cần chạy lần đầu, có thể đã tạo từ frontend)
docker network create prod_metaticket

# Build và deploy
docker compose -f docker-compose.prod.yml up -d --build
```

Service sẽ khởi động với `SPRING_PROFILES_ACTIVE=prod`, load cấu hình từ `application-prod.yml`.

### Deploy Development (local)

```bash
# Đảm bảo đang dùng context local
docker context use default

# Tạo network (chỉ cần chạy lần đầu)
docker network create dev_metaticket

# Build và deploy
docker compose -f docker-compose.dev.yml up -d --build
```

Service sẽ khởi động với `SPRING_PROFILES_ACTIVE=dev`.

---

## Các lệnh quản lý

### Xem logs

```bash
docker compose -f docker-compose.<env>.yml logs -f
```

### Dừng service

```bash
docker compose -f docker-compose.<env>.yml down
```

### Rebuild và restart

```bash
docker compose -f docker-compose.<env>.yml up -d --build --force-recreate
```

### Kiểm tra trạng thái

```bash
docker compose -f docker-compose.<env>.yml ps
```

### Kiểm tra health (sau khi deploy)

```bash
# Thay port tương ứng với môi trường
curl http://localhost:<PORT>/actuator/health
```

---

## Cấu trúc Volumes

| Môi trường  | Log Path trên Server                                   |
|-------------|--------------------------------------------------------|
| Development | `./logs` (local)                                       |
| Staging     | `/staging/metaticket/service/homepage/logs`            |
| Production  | `/opt/metaticket/service/homepage/logs`                |

---

## Cấu hình theo môi trường

Ứng dụng sử dụng Spring Profiles để phân biệt cấu hình giữa các môi trường:

- `application-dev.yml` — cấu hình development
- `application-staging.yml` — cấu hình staging
- `application-prod.yml` — cấu hình production

Timezone mặc định: `Asia/Ho_Chi_Minh`

### Giới hạn tài nguyên & JVM (Production)

Container production (`docker-compose.prod.yml`) được cấu hình giới hạn bộ nhớ và tinh chỉnh JVM:

| Cấu hình | Giá trị | Ý nghĩa |
|----------|---------|---------|
| `mem_limit` | `1g` | Giới hạn RAM tối đa của container ở mức 1GB |
| `JAVA_TOOL_OPTIONS` → `-XX:MaxRAMPercentage=75.0` | 75% | JVM heap tối đa ~75% RAM khả dụng của container (≈768MB với `mem_limit=1g`) |
| `JAVA_TOOL_OPTIONS` → `-Duser.timezone=Asia/Ho_Chi_Minh` | `Asia/Ho_Chi_Minh` | Ép timezone ở cấp JVM cho container production |

> **Lưu ý:** Do container production bị giới hạn `mem_limit: 1g`, cần đảm bảo server còn đủ RAM trống trước khi deploy. `MaxRAMPercentage` giúp JVM tự điều chỉnh heap theo giới hạn của container thay vì theo RAM tổng của host.

---

## Dọn dẹp Docker Cache & Images

> **Quan trọng:** Sau vài lần deploy, Docker images và build cache cũ sẽ tích tụ trên server, chiếm hết dung lượng disk. Cần định kỳ dọn dẹp.

### Xóa images cũ (dangling images)

```bash
docker image prune -f
```

### Xóa toàn bộ images không sử dụng

```bash
docker image prune -a -f
```

### Xóa build cache

```bash
docker builder prune -f
```

### Dọn dẹp toàn diện (images + cache + volumes không dùng)

```bash
docker system prune -a -f
```

> ⚠️ **CẢNH BÁO:** Tuyệt đối KHÔNG dùng `docker system prune --volumes` hoặc `docker volume prune` nếu chưa chắc chắn. Lệnh này sẽ xóa volumes chứa data của các container khác (database, Elasticsearch,...) và **KHÔNG THỂ khôi phục**. Chỉ nên dùng `docker image prune` và `docker builder prune` để an toàn. Nếu cần prune toàn bộ, kiểm tra kỹ danh sách container đang chạy (`docker ps -a`) trước khi thực hiện.

### Khi nào cần dọn dẹp?

- Khi disk trên server gần đầy
- Sau 5-10 lần deploy liên tiếp
- Khi thấy lệnh build bị lỗi do thiếu dung lượng
- Kiểm tra dung lượng Docker đang chiếm: `docker system df`

---

## Lưu ý quan trọng

1. **Network external:** Các compose file sử dụng external network. Cần tạo network trước khi chạy compose lần đầu.
2. **Shared network:** Frontend và Backend cùng môi trường sử dụng chung network (ví dụ: `staging_metaticket`), cho phép giao tiếp nội bộ giữa các container.
3. **Port conflict:** Staging và Dev đều dùng port 48080. Không nên chạy cả hai trên cùng một server.
4. **SSH key:** Đảm bảo SSH key đã được cấu hình để Docker Context có thể kết nối không cần nhập password.
5. **Build time:** Gradle build trong Docker có thể mất vài phút lần đầu do tải dependencies. Các lần build sau sẽ nhanh hơn nhờ Docker layer cache.
6. **Cùng server:** Do staging và production cùng nằm trên một server, cần chú ý:
   - Port mapping không bị trùng (staging: 48080, production: 8080)
   - Tài nguyên server (RAM, CPU) đủ cho cả hai môi trường
7. **Elasticsearch:** Nếu backend cần kết nối Elasticsearch, tham khảo thêm `docker-compose.elastic.yml` để setup Elasticsearch cluster.
8. **Disk đầy:** Docker images và Gradle build cache tích tụ sau nhiều lần deploy. Thường xuyên chạy `docker system prune -a -f` để giải phóng dung lượng, đặc biệt trước khi build lại.

---

## Quy trình deploy đầy đủ (Frontend + Backend)

Khi cần deploy cả frontend và backend cho cùng một môi trường:

```bash
# 1. Chuyển context
docker context use metaticket-staging

# 2. Tạo shared network (lần đầu)
docker network create staging_metaticket

# 3. Deploy backend trước
cd /path/to/metaticket-homebackend
docker compose -f docker-compose.staging.yml up -d --build

# 4. Deploy frontend
cd /path/to/metaticket-home
docker compose -f docker-compose.staging.yml up -d --build

# 5. Verify
docker ps --filter "network=staging_metaticket"
```

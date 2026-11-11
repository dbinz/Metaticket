# Deployment Guide - MetaTicket Home (Frontend)

## Tổng quan

MetaTicket Home là ứng dụng Angular frontend, được build thành static files và serve bởi Nginx. Deployment sử dụng Docker với docker compose thông qua Docker Context để deploy từ xa lên server.

**Lưu ý:** Cả môi trường staging và production hiện tại đều được deploy trên cùng một server (staging server) để giảm tải cho server production.

---

## Kiến trúc Deployment

| Môi trường | Compose File                   | Container Name     | Port  | Network            |
| ------------- | ------------------------------ | ------------------ | ----- | ------------------ |
| Development   | `docker-compose.dev.yml`     | dev_metaticket     | 44200 | dev_metaticket     |
| Staging       | `docker-compose.staging.yml` | staging_metaticket | 44200 | staging_metaticket |
| Production    | `docker-compose.prod.yml`    | prod_metaticket    | 4200  | prod_metaticket    |

---

## Yêu cầu

- Docker Engine đã cài đặt trên máy local
- SSH access đến server đích (staging server)
- Docker Engine đã cài đặt trên server đích

---

## Thiết lập Docker Context

Docker Context cho phép bạn chạy lệnh docker từ máy local nhưng thực thi trên server từ xa thông qua SSH.

### 1. Tạo Docker Context cho Staging Server

```bash
docker context create metaticket-staging \
  --docker "host=ssh://user@<STAGING_SERVER_IP>"
```

Thay `user` bằng username SSH và `<STAGING_SERVER_IP>` bằng IP hoặc hostname của staging server.

### 2. Xác nhận context đã tạo

```bash
docker context ls
```

Output sẽ hiển thị danh sách context bao gồm `metaticket-staging`.

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

# Tạo network (chỉ cần chạy lần đầu)
docker network create staging_metaticket

# Build và deploy
docker compose -f docker-compose.staging.yml up -d --build

# Chuyển sang context default
docker context use default
```

Build argument `TARGET_ENV=build:staging` sẽ được tự động truyền vào Dockerfile để build ứng dụng với cấu hình staging.

### Deploy Production

Vì production cũng deploy trên staging server, ta sử dụng cùng Docker Context:

```bash
# Chuyển sang context staging server
docker context use metaticket-staging

# Tạo network (chỉ cần chạy lần đầu)
docker network create prod_metaticket

# Build và deploy
docker compose -f docker-compose.prod.yml up -d --build
```

Build argument `TARGET_ENV=build:production` sẽ được tự động truyền vào Dockerfile.

### Deploy Development (local)

```bash
# Đảm bảo đang dùng context local
docker context use default

# Tạo network (chỉ cần chạy lần đầu)
docker network create dev_metaticket

# Build image trước (dev compose không có build config)
docker build -t dev_metaticket --build-arg TARGET_ENV=build .

# Chạy container
docker compose -f docker-compose.dev.yml up -d
```

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

---

## Cấu trúc Volumes

| Môi trường | Log Path trên Server                         |
| ------------- | --------------------------------------------- |
| Development   | `./logs` (local)                            |
| Staging       | `/staging/metaticket/service/homepage/logs` |
| Production    | `/otp/metaticket/service/homepage/logs`     |

Logs Nginx được mount ra ngoài container để dễ dàng theo dõi và debug.

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
2. **Port conflict:** Staging và Dev đều dùng port 44200. Không nên chạy cả hai trên cùng một server.
3. **SSH key:** Đảm bảo SSH key đã được cấu hình để Docker Context có thể kết nối không cần nhập password.
4. **Cùng server:** Do staging và production cùng nằm trên một server, cần chú ý port mapping không bị trùng (staging: 44200, production: 4200).
5. **Disk đầy:** Docker images và cache tích tụ sau nhiều lần deploy. Thường xuyên chạy `docker system prune -a -f` để giải phóng dung lượng, đặc biệt trước khi build lại.

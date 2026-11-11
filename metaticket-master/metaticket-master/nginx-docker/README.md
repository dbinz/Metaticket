# Nginx Docker - Local Development Proxy

## Tổng quan

`nginx-docker` là một Docker container chạy Nginx, chịu trách nhiệm proxy domain `https://devlocal.metaticket.vn` về localhost nhằm phục vụ mục đích phát triển (development).

Mỗi developer có thể tự cấu hình cho mình từng service sẽ được proxy về **localhost (local)** hay về **dev server (remote)**, tuỳ thuộc vào service nào đang được develop trên máy cá nhân.

## Kiến trúc

```
Browser (https://devlocal.metaticket.vn)
    │
    ▼
┌─────────────────────────────────────────┐
│  Docker: Nginx (port 443)               │
│  SSL termination với cert metaticket.vn │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ Frontend configs (HTTP reverse  │    │
│  │ proxy cho /api/*, modules, etc) │    │
│  └─────────────────────────────────┘    │
│  ┌─────────────────────────────────┐    │
│  │ Backend configs (TCP stream     │    │
│  │ proxy cho Mongo, Redis, Kafka,  │    │
│  │ và các backend services)        │    │
│  └─────────────────────────────────┘    │
└──────────────┬──────────────┬───────────┘
               │              │
       ┌───────▼───┐   ┌─────▼─────┐
       │ localhost  │   │ Dev Server│
       │ (local)   │   │ (remote)  │
       │ services  │   │ 10.0.1.12 │
       └───────────┘   └───────────┘
```

## Cấu trúc thư mục

```
nginx-docker/
├── compose.yml              # Docker Compose config
├── run_local.bat            # Script khởi chạy nhanh
├── sample.dev.env           # File env mẫu (copy để tạo file cá nhân)
├── luan.dev.env             # Ví dụ env của developer "luan"
├── ryan.dev.env             # Ví dụ env của developer "ryan"
├── DOCKERFILE.nettools      # Dockerfile mở rộng với network debug tools
├── ssl/                     # SSL certificates cho domain metaticket.vn
│   ├── fullchain1.pem
│   ├── privkey1.pem
│   ├── cert1.pem
│   └── chain1.pem
└── dev.conf/
    ├── nginx.conf           # Config chính của Nginx
    └── templates/
        ├── frontend.conf.tpl        # Server block cho devlocal.metaticket.vn
        ├── portal.admin.conf.tpl    # Server block cho admin.devlocal.metaticket.vn
        ├── backend.conf.tpl         # Stream block cho backend TCP proxies
        ├── frontend/                # Các template local/remote cho frontend routes
        │   ├── show.local.conf.tpl
        │   ├── show.remote.conf.tpl
        │   ├── ...
        └── backend/                 # Các template local/remote cho backend streams
            ├── show.backend.local.conf.tpl
            ├── show.backend.remote.conf.tpl
            ├── ...
```

## Cách hoạt động

### Template System

Nginx Docker sử dụng cơ chế **envsubst** tích hợp sẵn trong image `nginx:latest`. Các file `.tpl` trong thư mục `templates/` sẽ được Nginx xử lý biến môi trường trước khi load.

Mỗi service có 2 template:
- `<service>.local.conf.tpl` — proxy request về **localhost** (qua `host.docker.internal`)
- `<service>.remote.conf.tpl` — proxy request về **dev server** (IP remote)

### Biến môi trường CONF_*

Trong file `.dev.env`, các biến `CONF_*` quyết định service nào dùng config local hay remote:

```env
# Service show sẽ proxy về dev server (remote)
CONF_SHOW=show.remote

# Service show backend cũng proxy về remote
CONF_SHOW_BACKEND=show.backend.remote

# Portal admin được serve từ local (build files trên máy dev)
CONF_PORTAL_ADMIN=portal.admin.local
```

Giá trị của biến `CONF_*` chính là tên file template (không bao gồm `.conf.tpl`).

### Frontend vs Backend

| Layer | Protocol | Mục đích |
|-------|----------|----------|
| **Frontend** | HTTP/HTTPS (reverse proxy) | Proxy các API endpoints (`/api/*`), serve static files, federated modules |
| **Backend** | TCP (stream proxy) | Proxy TCP connections cho database (Mongo, Redis, Kafka) và inter-service communication |
| **gRPC** | TCP/HTTP2 (stream proxy) | Proxy các gRPC server port giữa các service (inter-service RPC) |

### gRPC Proxy

gRPC chạy trên HTTP/2, được proxy ở tầng L4 (TCP `stream`) giống như các backend TCP proxy khác — nginx forward nguyên byte nên HTTP/2 hoạt động trong suốt (không cần cấu hình `grpc_pass`).

Mỗi service có một gRPC server riêng (khai báo trong `grpc.server.port` của `conf-sample/application.conf`). Với mỗi service có gRPC server, có 2 template trong `dev.conf/templates/backend/`:

- `<service>.grpc.local.conf.tpl` — proxy về **localhost** (`host.docker.internal`)
- `<service>.grpc.remote.conf.tpl` — proxy về **dev server** (IP remote)

Biến `CONF_*_GRPC` trong file `.dev.env` chọn local hay remote:

```env
# Show gRPC route về máy local
CONF_SHOW_GRPC=show.grpc.local

# Show gRPC route về dev server
CONF_SHOW_GRPC=show.grpc.remote
```

Mỗi gRPC service dùng 2 port giống backend HTTP:
- **`*_GRPC_LISTEN_PORT`**: port gRPC server thực tế (lấy từ `grpc.server.port` trong `application.conf`)
- **`*_GRPC_CONNECT_PORT`**: port docker expose để proxy tới (quy ước: `CONNECT = LISTEN + 10000`)

> Lưu ý: để đi qua proxy này, `grpc.channel.*.port` trong `application.conf` của service đang chạy local phải trỏ tới `*_GRPC_CONNECT_PORT` (ví dụ `14104`) thay vì port gRPC gốc (`4104`). Nếu gọi trực tiếp port gốc thì request không đi qua nginx-docker.

#### Danh sách gRPC ports

| Service | CONF (gRPC) | gRPC LISTEN | gRPC CONNECT |
|---------|-------------|-------------|--------------|
| Show | `CONF_SHOW_GRPC` | 4103 | 14103 |
| Ticket | `CONF_TICKET_GRPC` | 4104 | 14104 |
| Order | `CONF_ORDER_GRPC` | 4105 | 14105 |
| Payment | `CONF_PAYMENT_GRPC` | 4106 | 14106 |
| JobRunr | `CONF_JOBRUNR_GRPC` | 4107 | 14107 |
| Notification | `CONF_NOTIFICATION_GRPC` | 4108 | 14108 |
| TicketSlot | `CONF_TICKETSLOT_GRPC` | 4109 | 14109 |
| QRCode | `CONF_QRCODE_GRPC` | 4110 | 14110 |
| Admin API | `CONF_ADMIN_API_GRPC` | 4140 | 14140 |
| Customer User | `CONF_CUSTOMER_USER_GRPC` | 4150 | 14150 |
| Accessory | `CONF_ACCESSORY_GRPC` | 4120 | 14120 |

### Domains

- `devlocal.metaticket.vn` — Main frontend (homepage, APIs, modules)
- `admin.devlocal.metaticket.vn` — Portal Admin

## Hướng dẫn sử dụng

### Yêu cầu

- Docker Desktop đã cài đặt
- Đã cấu hình `hosts` file trỏ `devlocal.metaticket.vn` và `admin.devlocal.metaticket.vn` về `127.0.0.1`
- Kết nối VPN tới dev server (nếu dùng remote services)

### Bước 1: Tạo file env cá nhân

Copy `sample.dev.env` thành `<tên-bạn>.dev.env`:

```bash
cp sample.dev.env myname.dev.env
```

### Bước 2: Cấu hình local/remote cho từng service

Mở file `.dev.env` của bạn và chỉnh các biến `CONF_*`:

**Muốn develop service Show trên máy local:**
```env
CONF_SHOW=show.local
CONF_SHOW_BACKEND=show.backend.local
```

**Muốn dùng service Show từ dev server:**
```env
CONF_SHOW=show.remote
CONF_SHOW_BACKEND=show.backend.remote
```

### Bước 3: Khởi chạy

```bash
docker compose --env-file myname.dev.env up
```

### Bước 4: Truy cập

Mở browser và truy cập `https://devlocal.metaticket.vn`.

## Cấu hình chi tiết

### Biến kết nối chính

| Biến | Mô tả | Ví dụ |
|------|--------|-------|
| `METATICKET_REMOTE` | IP/hostname dev server (qua VPN) | `<dev ip>` |
| `METATICKET_LOCAL` | Hostname để docker connect về host machine | `host.docker.internal` |
| `NGINX_CONNECT_PORT` | Port browser connect tới | `443` |
| `NGINX_LISTEN_PORT` | Port nginx listen bên trong container | `443` |

### Port mapping

Mỗi backend service có 2 port:
- **LISTEN_PORT**: Port mà service thực tế listen trên máy local (hoặc remote)
- **CONNECT_PORT**: Port mà Docker expose ra ngoài để các service khác connect tới

Ví dụ:
```env
SHOW_BACKEND_LISTEN_PORT=4003     # Service Show listen port 4003
SHOW_BACKEND_CONNECT_PORT=14003   # Docker expose port 14003 -> proxy tới 4003
```

### Danh sách Services

| Service | Frontend CONF | Backend CONF | Listen Port |
|---------|---------------|--------------|-------------|
| Editor PolicyDesc | `CONF_EDITOR_POLICYDESC` | `CONF_EDITOR_POLICYDESC_BACKEND` | 4011 |
| Editor SeatPlanner | `CONF_EDITOR_SEATPLANNER` | `CONF_EDITOR_SEATPLANNER_BACKEND` | 4012 |
| Show | `CONF_SHOW` | `CONF_SHOW_BACKEND` | 4003 |
| Ticket | `CONF_TICKET` | `CONF_TICKET_BACKEND` | 4004 |
| Order | `CONF_ORDER` | `CONF_ORDER_BACKEND` | 4005 |
| Payment | `CONF_PAYMENT` | `CONF_PAYMENT_BACKEND` | 4006 |
| JobRunr | `CONF_JOBRUNR` | `CONF_JOBRUNR_BACKEND` | 4007 |
| Notification | `CONF_NOTIFICATION` | `CONF_NOTIFICATION_BACKEND` | 4008 |
| QRCode | `CONF_QRCODE` | `CONF_QRCODE_BACKEND` | 4010 |
| TicketSlot | `CONF_TICKETSLOT` | `CONF_TICKETSLOT_BACKEND` | 4013 |
| Admin API | `CONF_ADMIN_API` | `CONF_ADMIN_API_BACKEND` | 4040 |
| Auth | `CONF_AUTH` | `CONF_AUTH_BACKEND` | 4030 |
| Customer User | `CONF_CUSTOMER_USER` | `CONF_CUSTOMER_USER_BACKEND` | 4050 |
| Customer Auth | `CONF_CUSTOMER_AUTH` | `CONF_CUSTOMER_AUTH_BACKEND` | 4051 |

### Infrastructure Services

| Service | CONF | Listen Port | Connect Port |
|---------|------|-------------|--------------|
| MongoDB | `CONF_MONGO` | 47017 | 47018 |
| Kafka | `CONF_KAFKA` | 4092 | 14092 |
| Redis | `CONF_REDIS` | 4379 | 14379 |

### Frontend-only configs (không có backend stream)

| Config | Mô tả |
|--------|--------|
| `CONF_ROOT` | Homepage frontend (root path `/`) |
| `CONF_ASSETS` | Static assets |
| `CONF_PORTAL_ADMIN` | Portal Admin app |
| `CONF_PORTAL_SEATPLANNER` | Portal SeatPlanner app |
| `CONF_MODULE_EDITOR_POLICYDESC` | Federated module: Editor PolicyDesc |
| `CONF_MODULE_EDITOR_SEATPLANNER` | Federated module: Editor SeatPlanner |
| `CONF_MODULE_EDITOR_SEATPLANNER_UI` | Federated module: Editor SeatPlanner UI |
| `CONF_MODULE_WW` | Federated module: Web Wrapper |
| `CONF_MODULE_VICLASS` | Federated module: ViClass |
| `CONF_HOMEPAGE_API` | Homepage API |

## Ví dụ thực tế

### Scenario: Develop Portal Admin trên local, mọi thứ khác dùng remote

```env
# Tất cả dùng remote
CONF_SHOW=show.remote
CONF_SHOW_BACKEND=show.backend.remote
CONF_TICKET=ticket.remote
CONF_TICKET_BACKEND=ticket.backend.remote
# ... (các service khác = remote)

# Chỉ Portal Admin dùng local
CONF_PORTAL_ADMIN=portal.admin.local
```

### Scenario: Develop backend Show service trên local

```env
CONF_SHOW=show.local
CONF_SHOW_BACKEND=show.backend.local
# Các service khác giữ remote
```

## Debug & Troubleshooting

### Dùng image với network tools

Build image từ `DOCKERFILE.nettools` để có thêm `ping`, `curl`, `vim`, `net-tools`:

```bash
docker build -f DOCKERFILE.nettools -t nginx-nettools .
```

Sau đó sửa `compose.yml` dùng image `nginx-nettools` thay vì `nginx:latest`.

### Kiểm tra nginx config đang active

```bash
docker exec metaticket cat /etc/nginx/conf.d/frontend.conf
docker exec metaticket cat /etc/nginx/conf.d/backend.conf
```

### Kiểm tra logs

```bash
docker logs metaticket
docker exec metaticket tail -f /var/log/nginx/error.log
```

### Reload nginx sau khi thay đổi template

```bash
docker compose --env-file myname.dev.env down
docker compose --env-file myname.dev.env up
```

## Lưu ý

- File `.dev.env` cá nhân **không nên commit** vào git (chỉ commit `sample.dev.env` làm mẫu)
- SSL certificates trong `ssl/` được dùng chung cho toàn team
- `host.docker.internal` là hostname đặc biệt của Docker cho phép container connect ngược về host machine
- Khi chuyển service từ remote sang local, đảm bảo service đó đang chạy trên máy local với đúng port tương ứng

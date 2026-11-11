# MetaTicket – Hệ thống đặt vé & quản lý sự kiện

MetaTicket (metaticket.vn) là nền tảng đặt vé và quản lý sự kiện. Repository này là **monorepo lõi của hệ thống**, chứa toàn bộ backend nghiệp vụ cùng cổng quản trị nội bộ.

## Dự án này phục vụ phần nào của hệ thống?

Đây là phần "xương sống" xử lý toàn bộ vòng đời đặt vé: từ thiết kế sơ đồ chỗ ngồi, giữ chỗ, tạo đơn, thanh toán đa cổng (9Pay, VNPay) cho tới gửi thông báo. Repo gồm các lớp:

| Thư mục | Vai trò | Công nghệ |
| --- | --- | --- |
| `kotlin/` | Các microservice backend xử lý nghiệp vụ (vé, show, đơn hàng, thanh toán, thông báo, xác thực, phân quyền, QR, job nền...) | Kotlin + Ktor, MongoDB, Redis Cluster, Kafka, gRPC, JobRunr |
| `ts/` | **Cổng quản trị nội bộ** và các editor (quản lý show, đơn, vé, sơ đồ chỗ, chính sách, QR, dữ liệu khách hàng) | Angular 16 + Module Federation |
| `frontend/` | Giao diện đặt vé cho khách hàng cuối (Next.js). **Hiện tạm thời không còn được hỗ trợ** – xem mục [Trạng thái các thành phần](#trạng-thái-các-thành-phần). | Next.js (React) + TypeScript |

Một số khái niệm chính: **Show** (sự kiện), **Reservation** (giữ chỗ có thời hạn), **Ticket Slots** (khung thời gian mở bán), **Policy Descriptor** (chính sách hoàn/đổi vé).

### Các microservice backend (`kotlin/`)

| Service | Chức năng |
| --- | --- |
| `service.ticket` | Giữ chỗ & quản lý vé, xử lý xung đột giữ chỗ đồng thời (Redis Cluster) |
| `service.show` | Cấu hình show/sự kiện, session, sơ đồ venue, bảng giá |
| `service.order` | Xử lý đơn hàng, tính giá tạm tính |
| `service.payment` | Tích hợp cổng thanh toán 9Pay, VNPay (verify checksum) |
| `service.notification` | Gửi email & ZNS (Zalo) theo template (plugin-based) |
| `service.jobrunr` | Chạy các tác vụ nền theo lịch (vd: nhả chỗ khi hết hạn) |
| `service.authentication` / `service.authorization` | Xác thực JWT & phân quyền Casbin |
| `service.customer.auth` / `service.customer.user` | Xác thực & quản lý người dùng khách hàng |
| `service.admin` | API cho cổng quản trị |
| `service.editor.seatplanner` / `service.editor.policydesc` | Backend cho editor sơ đồ chỗ & chính sách |
| `service.order.datacollector` | Thu thập & báo cáo dữ liệu đơn hàng |
| `service.qrcode` | Tạo mã QR cho vé, đính kèm trong email thông báo đặt vé thành công |
| `service.ticketslots` | Tạo & quản lý vé điện tử (e-ticket); người dùng xem vé và từng slot của vé trên trang chủ |

> 📚 Tài liệu kỹ thuật chi tiết của từng service nằm trong thư mục [`docs/`](./docs) (mỗi service có một file `SERVICE_*_TECHNICAL_DOCUMENTATION.md`). Xem thêm [`docs/Ticket_Booking_Flow.md`](./docs/Ticket_Booking_Flow.md) để hiểu luồng đặt vé tổng thể.

## Trạng thái các thành phần

- ✅ **Backend (`kotlin/`)** và **Cổng quản trị (`ts/`)**: đang được hỗ trợ và vận hành.
- ⚠️ **Frontend đặt vé (`frontend/`, Next.js)**: **tạm thời không còn được hỗ trợ**. Giao diện đặt vé cho khách hàng hiện do dự án `metaticket-home` (Angular) đảm nhận. Nếu trong tương lai thành phần này được hỗ trợ trở lại, **cần cập nhật lại tài liệu và phần triển khai bên dưới cho phù hợp**.

---

# Hạ tầng & Triển khai

## MongoDB

MongoDB chạy bằng Docker.

``` bash
docker pull mongodb/mongodb-community-server:6.0.4-ubuntu2204
```

Tạo user để chạy MongoDB (trên môi trường dev dùng user `metaticket`).

``` bash
docker run --name mongodb -d -p 27017:27017 --user <mongodb user id>:<mongodb user group id> -v /opt/metaticket-mongo/data:/data/db mongodb/mongodb-community-server:6.0.4-ubuntu2204
```

## Redis Cluster

Hệ thống sử dụng **Redis Cluster** (không còn dùng Redis standalone). Các service kết nối tới cluster thông qua thư viện Lettuce (`RedisClusterManager` trong module `common`), với topology refresh định kỳ và tự động reconnect.

### Cấu hình trong service (`application.conf`)

Mỗi service có nhu cầu dùng Redis (vd: `service.ticket`, `service.ticketslots`, `service.customer.user`, `service.customer.auth`) khai báo danh sách node của cluster trong file HOCON `application.conf`:

``` hocon
# redis configuration
redis {
    cluster {
        nodes = ["10.0.1.12:43790", "10.0.1.12:43791", "10.0.1.12:43792"]
    }
}
```

- `nodes`: danh sách các node của cluster theo định dạng `host:port`. Chỉ cần khai báo một vài seed node, Lettuce sẽ tự khám phá toàn bộ topology còn lại.
- Client được cấu hình sẵn (trong `common`): refresh topology mỗi 30 giây, bật adaptive refresh triggers, `autoReconnect = true`, `maxRedirects = 16`.

### Triển khai Redis Cluster bằng Docker

Một cụm Redis Cluster tối thiểu cần ít nhất **3 master node** (khuyến nghị 6 node = 3 master + 3 replica cho production). Mỗi node là một tiến trình Redis bật chế độ cluster.

``` bash
docker pull redis:7.0.12-alpine
```

Với mỗi node, tạo một file `redis.conf` bật cluster mode, ví dụ:

``` conf
port 43790
cluster-enabled yes
cluster-config-file nodes.conf
cluster-node-timeout 5000
appendonly yes
```

Khởi chạy từng node (lặp lại cho mỗi port `43790`, `43791`, `43792`...):

``` bash
docker run --name redis-43790 -d --net host \
  --user <redis user id>:<redis user group id> \
  -v /opt/metaticket-redis/43790/redis.conf:/usr/local/etc/redis/redis.conf \
  -v /opt/metaticket-redis/43790/data:/data \
  redis:7.0.12-alpine redis-server /usr/local/etc/redis/redis.conf
```

> **LƯU Ý:** Container nhận hai volume — một cho file cấu hình và một cho thư mục dữ liệu. Thư mục dữ liệu phải cho phép user chạy container ghi được. Redis Cluster yêu cầu các node giao tiếp được với nhau qua cả cổng client (vd `43790`) và cổng bus (client port + 10000), nên thường dùng `--net host` hoặc map đầy đủ các cổng.

Sau khi các node đã chạy, khởi tạo cluster:

``` bash
redis-cli --cluster create \
  10.0.1.12:43790 10.0.1.12:43791 10.0.1.12:43792 \
  --cluster-replicas 0
```

(Đặt `--cluster-replicas 1` nếu chạy 6 node để mỗi master có 1 replica.)

## JobRunr

JobRunr chạy bên trong một JVM service (`service.jobrunr`), không cài đặt như một dịch vụ riêng biệt.

## WireGuard (truy cập mạng dev)

* Cài đặt WireGuard.
* Sinh cặp public / private key.
* Yêu cầu cấp một IP.
* Thêm tunnel mới với cấu hình sau:

``` conf
[Interface]
PrivateKey = <!!!!!!!------ YOUR PRIVATE KEY HERE -------------- >
Address = <!!!!! -------- ASK FOR AN IP TO BE ALLOCATED -------- >
DNS = 8.8.8.8

[Peer]
PublicKey = GB5rPFC1NF4swDWxJvDsempGWMe5IMBnta5rwKe/W3g=
AllowedIPs = 10.10.0.0/16
Endpoint = 42.96.41.206:51820
PersistentKeepalive = 25
```

* Kiểm tra kết nối bằng cách bật tunnel WireGuard và kết nối tới `10.1.0.12:47017` (MongoDB).

## Build & chạy backend

``` bash
# Từ thư mục metaticket/kotlin/
./gradlew build                  # Build toàn bộ service
./gradlew :service.show:run      # Chạy một service cụ thể
./gradlew test                   # Chạy test
```

## Build & chạy cổng quản trị (Angular)

``` bash
# Từ thư mục metaticket/ts/
yarn portal.admin:build          # Build admin portal
yarn portal.admin:dist           # Build production
```

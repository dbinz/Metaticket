# Chạy MetaTicket trên máy này

## Chạy website và backend bằng Docker

Mở Docker Desktop, đợi Linux engine sẵn sàng rồi chạy:

```powershell
cd E:\META
powershell -ExecutionPolicy Bypass -File .\start-docker.ps1
```

Website Docker: http://localhost:4300. Backend và dữ liệu dùng chung với cấu hình
local ở các phần dưới. Lần đầu script build frontend trong container nên cần Internet
và có thể mất vài phút. Cấu hình Compose đã kiểm tra hợp lệ; image frontend mới chưa
được build/kiểm tra trình duyệt tại thời điểm thêm hướng dẫn này.

`start-docker.ps1` gọi script backend (tạo cấu hình và khởi tạo database),
build `web` bằng `Dockerfile.local`, rồi chạy Nginx phục vụ Angular.
Nginx chuyển `/api` tới gateway backend qua network Docker.

Dừng cả bộ container, giữ lại volume dữ liệu:

```powershell
powershell -ExecutionPolicy Bypass -File E:\META\stop-docker.ps1
```

## Website khách hàng

Từ PowerShell:

```powershell
cd E:\META
.\start-home.ps1
```

Mở http://localhost:4200. Giữ cửa sổ chạy lệnh mở; Ctrl+C để dừng.

Đã cài thư viện frontend, biên dịch thành công và kiểm tra HTTP 200 cho trang chủ,
main.js, module trang chủ và module hCaptcha. `ng-hcaptcha` được cố định ở 2.7.0
theo yarn.lock vì bản 2.8.0 gây lỗi biên dịch dependency với Angular 17.

Cài lại thư viện khi cần:

```powershell
cd E:\META\metaticket-home-master\metaticket-home-master
npm.cmd install --ignore-scripts --package-lock=false
npm.cmd run start:local
```

Cấu hình `local` dùng `environment.local.ts` và `proxy.local.json`.
Frontend chuyển `/api` và `/maintenance/status` tới gateway local cổng 8081.
Gateway chuyển tìm kiếm tới cổng 48080 và các API nghiệp vụ tới các service Kotlin.

## Backend tìm kiếm

Đã cài WSL 3.0.3, bật Windows Subsystem for Linux và Virtual Machine Platform,
đặt phiên bản mặc định WSL 2 và khởi động lại Windows. Docker Linux engine đã chạy.
Backend đã build thành công, Elasticsearch healthy; API highlight trực tiếp ở cổng
48080 và thông qua proxy frontend ở cổng 4200 đều trả HTTP 200.
Đã thêm 48 sự kiện mẫu có tiền tố `[Mẫu]` bằng `seed-local.mjs`.
Có 8 banner ghim và 48 sự kiện trong danh sách highlight; tìm `am nhac` trả về mẫu âm nhạc.
Menu Sự kiện lấy 8 danh mục từ service.show: Âm nhạc, Workshop, Điện ảnh,
Sân khấu, Hội thảo, Thể thao, Thiếu nhi và Du lịch. Mỗi nhóm hiện có 6 mẫu.
Trong mỗi nhóm, các sự kiện mẫu cách nhau 30 ngày. Mỗi sự kiện chỉ có 1 suất
lúc 19:00 (giờ Việt Nam) tại một thành phố. Mỗi nhóm có 3 sự kiện ở Hà Nội
và 3 sự kiện ở Hồ Chí Minh; tổng cộng 48 suất.
API thành phố cung cấp hai địa điểm này; chọn địa điểm sẽ lọc đúng 3 sự kiện
trong từng nhóm (hoặc 24 sự kiện của tất cả các nhóm).
`bootstrap-core-mongo.js` bổ sung danh mục còn thiếu khi khởi động, giữ nguyên
những danh mục đã tồn tại. Bấm danh mục chuyển sang trang sự kiện và lọc theo mã nhóm.
Xem `install-wsl.log`, `install-wsl-status.json` và `backend-build.log` để kiểm tra kết quả.
Mỗi lần mở lại máy, mở Docker Desktop và đợi Linux engine sẵn sàng trước khi chạy.

Khởi chạy nhanh backend tìm kiếm:

```powershell
cd E:\META
.\start-backend.ps1
```

Hoặc chạy Docker Compose trực tiếp:

```powershell
cd E:\META
docker --context desktop-linux compose -f compose.local.yml up -d --build
docker --context desktop-linux compose -f compose.local.yml logs -f search
```

Compose này chạy Elasticsearch ở http://localhost:9200 và backend tìm kiếm
ở http://localhost:48080, lưu dữ liệu vào volume riêng. Cơ sở dữ liệu mới
chưa có sự kiện nếu chưa chạy script seed. API key `local-development-only` chỉ dành cho local.

Tạo hoặc cập nhật lại 48 mẫu (ID cố định, không nhân đôi dữ liệu):

```powershell
cd E:\META
node .\seed-local.mjs
```

Mỗi mẫu dùng một ảnh riêng trong `src/assets/demo-events` (48 ảnh Pexels lưu local).
`manifest.json` ánh xạ ảnh theo nhóm/thứ tự; `CREDITS.md` lưu nguồn và giấy phép.
Chạy `node .\download-event-images.mjs` nếu cần tải lại ảnh bị thiếu trước khi build web.
Ảnh là ảnh minh họa, không phải ảnh chụp các sự kiện mẫu. Ngày diễn tính tương đối từ ngày chạy script.
Chỉ dùng thử banner, highlight và tìm kiếm. Chi tiết sự kiện, ghế và thanh toán
cần dữ liệu và backend nghiệp vụ riêng.

Dừng backend:

```powershell
docker --context desktop-linux compose -f compose.local.yml down
```

## Backend nghiệp vụ MetaTicket

Đã build và chạy 17 service Kotlin: show, ticket, order, payment, jobrunr,
notification, authentication, authorization, customer.auth, customer.user,
admin, accessory, ticketslots, qrcode, order.datacollector và hai editor.
Chưa chạy portal admin Angular hoặc service.news/WordPress.

Hạ tầng local gồm MongoDB 7 replica set `localrs`, Redis Cluster 3 node,
Kafka một broker, Elasticsearch, backend search và gateway Nginx.
Chỉ publish các cổng backend ở địa chỉ loopback của máy.

Khởi động các backend (sau khi mở Docker Desktop):

```powershell
cd E:\META
.\start-core-backend.ps1
```

Build lại khi thay đổi mã nguồn Kotlin:

```powershell
.\start-core-backend.ps1 -Build
```

Build local dùng Gradle 8.1.1 theo repository, build JDK 17 và runtime JRE 21
để chạy bytecode Kotlin target JVM 18. Image dùng chung cho 17 service.

Cấu hình được tạo từ `conf-sample` bằng `prepare-core-local.mjs`, lưu ở `.local/core`.
Khóa JWT/RSA/AES local được tạo riêng và giữ ổn định trong `.local/core/secrets.json`;
không thay bằng khóa staging/production. `compose.core.local.json` được script tạo lại.

Kiểm tra trạng thái hoặc log:

```powershell
docker --context desktop-linux compose -f compose.local.yml -f compose.core.local.json ps
docker --context desktop-linux compose -f compose.local.yml -f compose.core.local.json logs --tail 100 service.show
```

Gateway: http://localhost:8081. API đi qua frontend ở http://localhost:4200/api/.
Đã kiểm tra JSON HTTP 200 cho captcha-setting, email-verification-setting,
city/fetch/all, event-category/fetch, accessory/show/fetch/all và search highlight.
API hồ sơ chưa đăng nhập trả 401; đăng nhập tài khoản không tồn tại trả 400.

Ô tìm kiếm sự kiện (gợi ý và trang kết quả) gửi `nameOnly=true` tới
`/public/metasearch/shows/suggestion`. Chế độ này yêu cầu các từ tìm kiếm khớp
trong tên sự kiện, hỗ trợ có/không dấu, hoa/thường và từ đang gõ dở.
Không lấy mô tả, mã nội bộ, danh mục hoặc địa điểm làm kết quả tìm theo tên.
API vẫn hỗ trợ tìm đa trường khi không bật cờ này.
Đã chạy 23 kiểm thử tích hợp trên index riêng và xác nhận 48 tên đầy đủ trả đúng
sự kiện tương ứng qua website Docker; `dem nhac` chỉ trả Đêm nhạc mùa thu.

Database nghiệp vụ mới chưa có tài khoản, sự kiện, ghế hoặc đơn hàng thật.
8 sự kiện `[Mẫu]` chỉ nằm trong search index, chưa có bản ghi tương ứng
trong service.show/ticket nên chưa thể dùng chúng để thử toàn bộ luồng đặt vé.
Chưa kiểm tra giữ chỗ, tạo đơn hoặc thanh toán thành công.

Service payment cần một cấu hình mặc định để khởi động: `bootstrap-core-mongo.js`
tạo cấu hình `LOCAL_UNCONFIGURED` với danh sách cổng trống nếu database chưa có cấu hình.
Không có tài khoản thanh toán thật. SMTP trỏ tới local cổng 1025, chưa có mail server;
ZNS và Cloudflare trỏ tới endpoint local không hoạt động. Email/OTP, ZNS và thanh toán
cần tích hợp, template và thông tin cấu hình riêng trước khi sử dụng.

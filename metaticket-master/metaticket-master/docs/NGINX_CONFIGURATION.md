# Cấu hình Nginx - Production & Staging

## 1. Tổng quan

Nginx là **cổng vào duy nhất** của MetaTicket trên mỗi server: terminate TLS, phục vụ file tĩnh (storefront proxy, portal admin, bundle MFE/`ww`, ảnh), định tuyến `{domain}/api/...` và `{admin-domain}/private/...` tới đúng service Kotlin, và **xác thực tại gateway** bằng `auth_request` rồi tiêm header định danh (`X-Customer-User-Id`, `X-OP-User-Id`) cho service phía sau. Các service không tự kiểm token cho phần lớn API — chúng tin header do nginx đặt.

Repo giữ hai cây cấu hình song song, được đồng bộ **thủ công** lên `/etc/nginx/` của từng server:

| Cây trong repo | Server | Domain |
|----------------|--------|--------|
| `nginx/prod/` | Production | `metaticket.com.vn`, `www.metaticket.com.vn`, `admin.metaticket.com.vn`, `news.metaticket.com.vn` |
| `nginx/staging/` | Staging | `staging.metaticket.vn`, `www.staging.metaticket.vn`, `admin.staging.metaticket.vn`, `news.staging.metaticket.vn` |

Không nằm trong repo: `nginx.conf` (block `http {}`), symlink `sites-enabled/`, chứng chỉ trong `/etc/letsencrypt/`.

Tài liệu liên quan (không lặp lại ở đây):

- TLS theo từng domain, cấp/gia hạn cert: [SSL_LETSENCRYPT_PER_DOMAIN.md](./SSL_LETSENCRYPT_PER_DOMAIN.md)
- Port/systemd/đường dẫn cài service: [SERVICE_DEPLOYMENT.md](./SERVICE_DEPLOYMENT.md)
- Proxy dev local `https://devlocal.metaticket.vn`: [nginx-docker/README.md](../nginx-docker/README.md)
- Quy ước route `/public`, `/private`, `/internal` phía service: [COMMON_LIBRARY_TECHNICAL_DOCUMENTATION.md](./COMMON_LIBRARY_TECHNICAL_DOCUMENTATION.md) mục 6.6–6.7
- Module `ww` được phục vụ qua `/modules`: [MODULE_WW_TECHNICAL_DOCUMENTATION.md](./MODULE_WW_TECHNICAL_DOCUMENTATION.md)

> Tài liệu này dùng placeholder cho mọi địa chỉ IP/host nội bộ (`<server-nội-bộ>`, `<IP-cổng-thanh-toán>`). Không ghi IP thật, credential hay host nội bộ vào tài liệu/commit.

---

## 2. Cấu trúc thư mục

```
nginx/<env>/                                   → /etc/nginx/
├── sites-available/                           → /etc/nginx/sites-available/  (bật bằng symlink trong sites-enabled/)
│   ├── <storefront-domain>                    # metaticket.com.vn | staging.metaticket.vn (+ www redirect)
│   ├── admin.<…>                              # portal admin + /private API
│   └── news.<…>                               # WordPress CMS
└── metaticket-frags/                          → /etc/nginx/metaticket-frags/
    ├── metaticket-upstream-server             # upstream {} cho mọi service  ⚠ dùng chung
    ├── metaticket-public-api                  # location /api/public/...      ⚠ dùng chung
    ├── metaticket-api                         # location /api/... (JWT khách hàng)
    ├── metaticket-private-api                 # location /private/... (JWT operator)  ⚠ dùng chung
    ├── metaticket-payment-ipn                 # IPN/return của cổng thanh toán
    ├── metaticket-modules                     # /modules/... (MFE, ww, viclass)
    ├── metaticket-assets                      # /assets, /show-image, /seatplanner-image, /.well-known
    ├── metaticket-portal                      # /admin, /odcapp, /planner (portal tĩnh)
    ├── metaticket-root                        # / → storefront hoặc Next.js cũ; /news/*.html → service.news
    ├── metaticket-wordpress                   # toàn bộ site news.*
    ├── metaticket-limit-req-zones             # limit_req_zone (include trong http {})
    ├── ssl-params                             # protocol + cipher chung
    ├── ssl/<FQDN>                             # cert riêng từng domain (quy ước hiện tại)
    └── metaticket-ssl                         # cert dùng chung kiểu cũ (chỉ site news còn dùng)
```

Ba frag đánh dấu ⚠ được include bởi **nhiều site** — sai ở đó có thể làm hỏng toàn bộ storefront lẫn portal cùng lúc.

---

## 3. Các site

### 3.1 Site storefront — `metaticket.com.vn` / `staging.metaticket.vn`

**File:** `nginx/prod/sites-available/metaticket.com.vn`, `nginx/staging/sites-available/staging.metaticket.vn`

1. `include metaticket-upstream-server` ở **đầu file, ngoài block `server`** — upstream được định nghĩa một lần trong context `http` và dùng chung cho mọi site (site admin không include lại).
2. `map $args $homepage_or_frontend_location`: query có `showId=…&sessionId=…` → `frontend` (Next.js cũ, đã ngừng hỗ trợ), còn lại → `homepage` (storefront `metaticket-home`). Regex chỉ kiểm có mặt hai tham số, không kiểm "chỉ có hai tham số".
3. Block `server` cho `www.<domain>` → `301` về domain chính.
4. Block `server` chính (`default_server` cho 443):
   - `include ssl/<domain>`; bật gzip cho text/JS/CSS/JSON/SVG.
   - `location = /internal/customer-authenticate { internal; … }` — subrequest xác thực khách hàng (mục 5.1).
   - Include theo thứ tự: `metaticket-public-api`, `metaticket-api`, `metaticket-payment-ipn`, `metaticket-modules`, `metaticket-assets`, `metaticket-portal`, `metaticket-root`.

### 3.2 Site admin — `admin.metaticket.com.vn` / `admin.staging.metaticket.vn`

**File:** `nginx/{prod,staging}/sites-available/admin.*`

- `include ssl/admin.<…>`; gzip.
- `location = /internal/authenticate { internal; proxy_pass http://authentication_service/internal/authentication/check-login; … }` — subrequest xác thực operator (mục 5.2).
- Include: `metaticket-public-api`, `metaticket-private-api`, `metaticket-portal`, `metaticket-modules`.
- `location /` phục vụ SPA `portal.admin` (`root <static>/portal/portal.admin`, `try_files $uri $uri/ /index.html`).

Portal admin gọi API theo hai dạng (`ts/packages/portal/metaticket/portal.admin/src/environments/environment*.ts`): `publicApi(path)` → `/api/public/<path>` và `privateApi(path)` → **`/private/<path>`** (không có tiền tố `/api`). Site admin **không** include `metaticket-api`, nên `/api/<service>` (API của khách hàng) không dùng được trên domain admin.

### 3.3 Site news — `news.metaticket.com.vn` / `news.staging.metaticket.vn`

**File:** `nginx/{prod,staging}/sites-available/news.*`

Chỉ `include metaticket-ssl` + gzip + `include metaticket-wordpress`. Toàn bộ site là reverse proxy tới container WordPress (`<server-nội-bộ>:8081`). Cách bật site được ghi ngay trong comment đầu file. Chi tiết WordPress: `metaticket-wordpress/docs/STAGING_DEPLOY.md`.

---

## 4. Frag dùng chung

### 4.1 `metaticket-upstream-server`

Một `upstream` cho mỗi backend. Service Kotlin chạy cùng server với nginx (`127.0.0.1`); port theo quy ước `30xx` (prod) / `40xx` (staging):

| Upstream | Service | Prod | Staging |
|----------|---------|------|---------|
| `show_service` | service.show | 3003 | 4003 |
| `ticket_service` | service.ticket | 3004 | 4004 |
| `order_service` | service.order | 3005 | 4005 |
| `payment_service` | service.payment | 3006 | 4006 |
| `jobrunr_service` | service.jobrunr | 3007 | 4007 |
| `notification_service` | service.notification | 3008 | 4008 |
| `ticket_slots_service` | service.ticketslots | 3009 | 4009 |
| `qrcode_service` | service.qrcode | 3010 | 4010 |
| `editor_policydesc_service` | service.editor.policydesc | 3011 | 4011 |
| `editor_seatplanner_service` | service.editor.seatplanner | 3012 | 4012 |
| `accessory_service` | service.accessory | 3020 | 4020 |
| `authentication_service` | service.authentication | 3030 | 4030 |
| `authorization_service` | service.authorization | 3031 | 4031 |
| `odc_service` | service.order.datacollector | 3032 | 4032 |
| `admin_service` | service.admin | 3040 | 4040 |
| `news_service` | service.news (Spring Boot) | 3041 | 4041 |
| `customer_user_service` | service.customer.user | 3050 | 4050 |
| `customer_auth_service` | service.customer.auth | 3051 | 4051 |
| `frontend_service` | Next.js `frontend/` (đã ngừng hỗ trợ) | 3000 | 4000 |
| `metasearch_service` | metaticket-homebackend | `<server-nội-bộ>:8080` | `127.0.0.1:48080` |
| `homepage_service` | metaticket-home (container) | `<server-nội-bộ>:4200` | `127.0.0.1:44200` |

Port runtime của service (`ktor.deployment.port` trong `application.conf` trên server) **phải khớp** cột tương ứng. Upstream chỉ có một `server` (không load-balance, không health check).

### 4.2 `metaticket-public-api` — API công khai

Một `location /api/public` cha đặt `Host`, `X-Real-IP`, `X-Forwarded-For`; các location con proxy tới service, đổi tiền tố `/api/public/<svc>` → `/public/<svc>/`:

| Location | Đích | Ghi chú |
|----------|------|---------|
| `/api/public/show` | `show_service/public/show/` | GET: `Cache-Control: public, max-age=10` |
| `/api/public/ticket` | `ticket_service/public/ticket/` | GET: `max-age=10` |
| `/api/public/order`, `payment`, `jobrunr`, `notification`, `ticket-slots`, `qrcode`, `authorization`, `odc`, `admin`, `customer-user`, `customer-auth` | `<svc>_service/public/<svc>/` | |
| `/api/public/accessory` | `accessory_service/public/accessory/` | Kế thừa `X-Real-IP`/`X-Forwarded-For` để rate limiter trong service thấy IP thật |
| `/api/public/authentication/` | `authentication_service/public/authentication/` | Có `/` cuối có chủ đích (xem 6.2) |
| `= /api/public/authentication/register` | `return 403` | Đăng ký operator đã chuyển sang `/private/authentication/register` |
| `/api/public/metasearch/` | `metasearch_service/public/metasearch/` | GET: `max-age=300` |
| `/api/public/editor/policydesc` | `editor_policydesc_service/editor/policydesc/` | Đọc tài liệu chính sách (không có `/public` phía service) |
| `/api/public/editor/seatplanner` | `editor_seatplanner_service/editor/seatplanner/` | Đọc sơ đồ ghế — module `ww` dùng endpoint này |
| `/api/public/news/` | `rewrite → /public/news/$1` rồi `proxy_pass http://news_service` | Spring không chấp nhận `//` nên dùng `rewrite` thay vì thay URI trong `proxy_pass` |

Frag này được include ở **cả site storefront và site admin**.

### 4.3 `metaticket-api` — API của khách hàng đã đăng nhập (chỉ site storefront)

```nginx
location /api {
    auth_request /internal/customer-authenticate;
    auth_request_set $phone  $upstream_http_x_customer_phone;
    auth_request_set $userId $upstream_http_x_customer_user_id;
    proxy_set_header X-Customer-Phone   $phone;
    proxy_set_header X-Customer-User-Id $userId;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;

    location /api/show     { proxy_pass http://show_service/show/; … }
    location /api/order    { proxy_pass http://order_service/order/; }
    location /api/payment  { proxy_pass http://payment_service/payment/; }
    …  # ticket, accessory, notification, ticket-slots, qrcode, authentication, authorization,
       # odc, admin, customer-user, customer-auth, editor/policydesc, editor/seatplanner
}
```

Mọi request `/api/<svc>/...` (không phải `/api/public/...`) đều phải có JWT khách hàng hợp lệ; thiếu/sai → `401` từ nginx, request không tới service. Ánh xạ: `/api/<svc>/x` → `<svc>_service/<svc>/x`. Ví dụ `/api/admin/...` → `service.admin` `/admin/...` (dashboard đối tác trên storefront).

### 4.4 `metaticket-private-api` — API của operator (chỉ site admin)

```nginx
location /private/ {
    auth_request /internal/authenticate;
    auth_request_set $userId $upstream_http_x_op_user_id;
    proxy_set_header X-OP-User-Id $userId;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;

    location /private/show { proxy_pass http://show_service; }
    location /private/show/upload/image { client_max_body_size 3M; proxy_pass http://show_service; }
    …
}
```

- `proxy_pass` **không có URI** → giữ nguyên đường dẫn gốc: `/private/show/x` → `show_service` `/private/show/x`. Route phía Ktor phải bắt đầu bằng `/private/<svc>`.
- Có location cho: accessory, show, ticket, order, payment, jobrunr, notification, ticket-slots, qrcode, authentication, authorization, odc, admin, customer-user, customer-auth, editor/policydesc, editor/seatplanner, news.
- Giới hạn body upload: ảnh show 3M; `uploadImage` của hai editor 5M. Mặc định nginx là 1M.

### 4.5 `metaticket-payment-ipn` — callback cổng thanh toán (chỉ site storefront)

| Location | Đích | Kiểm soát |
|----------|------|-----------|
| `= /api/payment/ipn/result/vnpay` | `payment_service/payment/ipn/result/vnpay` | `allow <IP-cổng-thanh-toán>; deny all;` |
| `/api/payment/ipn/result` | `payment_service/payment/ipn/result` | Không auth |
| `/api/payment/return/result` | `payment_service/payment/return/result` | Không auth |

Các location này ở **cấp `server`**, không lồng trong `location /api`, và là prefix dài hơn `/api` nên nginx chọn chúng trước → **không** đi qua `auth_request` khách hàng (cổng thanh toán và trình duyệt quay về sau thanh toán không có JWT). Xem thêm mục 8.

### 4.6 `metaticket-modules`, `metaticket-assets`, `metaticket-portal`, `metaticket-root`

| Frag | Location | Phục vụ |
|------|----------|---------|
| modules | `/modules/metaticket` | `<static>/modules/metaticket` (gồm `ww/planner.wrapper.js`) |
| | `/modules/metaticket/editor.seatplanner`, `/modules/metaticket/editor.seatplanner.ui`, `/modules/metaticket/editor.policydesc/` | alias về `<static>/modules/metaticket/mfe` |
| | `/modules/metaticket/themes/editor.seatplanner.ui` | `<static>/modules/metaticket/themes` |
| | `/modules/viclass` | `<static>/modules/viclass` (coordinator, `ww/mfe.webrt.js`) |
| assets | `/assets` | `<static>/assets` |
| | `/assets/frontend` | proxy `frontend_service` |
| | `/show-image`, `/show-image-temp` | `<service-root>/show/show-image[-temp]` (ảnh do service.show ghi), cache 3600s |
| | `/seatplanner-image` | `<service-root>/editor.seatplanner/uploads`, cache 3600s |
| | `/.well-known` | `<static>/.well-known` |
| portal | `/admin`, `/odcapp`, `/planner` | SPA `portal.admin`, `portal.order.datacollector`, `portal.seatplanner` (cũ) |
| root | `/` | `error_page 460 = @$homepage_or_frontend_location; return 460;` → `@homepage` hoặc `@frontend` |
| | `/assets/homepage`, `/payment-result` | luôn `@homepage` |
| | `/_next` | luôn `@frontend` |
| | `~ ^/news/(?!category/)([^/]+)\.html/?$` | rewrite → `/public/news/render/$1`, proxy `news_service` (SSR bài viết), header `X-Render-By: service.news` |
| | `~ ^/news/(?!category/)([^/.]+)/?$` | `301` → `/news/<slug>.html` |

`<static>` = `/opt/metaticket/static` (prod) hoặc `/staging/metaticket/static` (staging); `<service-root>` = `/opt/metaticket/service` hoặc `/staging/metaticket/service`. Mẹo `return 460` + `error_page` dùng để chuyển sang named location theo biến `map`. Mọi location tĩnh đặt `Access-Control-Allow-Origin *`.

Lưu ý: site storefront cũng include `metaticket-portal`, nên `https://<storefront-domain>/admin` vẫn trả SPA portal admin; nhưng API `/private/...` chỉ có trên domain admin.

### 4.7 `metaticket-wordpress` và `metaticket-limit-req-zones`

- `metaticket-limit-req-zones`: `limit_req_zone $binary_remote_addr zone=wp_login:10m rate=5r/m;` — **phải include trong block `http {}` của `nginx.conf`** (không include trong `server`), trước khi bật site news; thiếu thì `nginx -t` báo zone không tồn tại.
- `metaticket-wordpress` (chỉ site news):
  - Chặn: file `.php` trong `wp-content`/`wp-includes`, file ẩn (`.git`, `.env`, `.ht`, `.svn`), `xmlrpc.php`, và `/api/public/wordpress/` (REST WordPress chỉ được gọi qua `service.news`).
  - `= /wp-login.php`: `limit_req zone=wp_login burst=5 nodelay; limit_req_status 429;`.
  - `/`: proxy tới `<server-nội-bộ>:8081`, `client_max_body_size 64m`, `proxy_read_timeout 120s`, bỏ `X-Frame-Options` của WordPress và đặt CSP `frame-ancestors 'self' https://admin.<…>`.
  - Prod có thêm `proxy_redirect` để ép URL `wp-admin`/`wp-login.php`/`wp-json`/... về đúng host `news.*`.

### 4.8 Frag SSL

| Frag | Nội dung | Dùng bởi |
|------|----------|---------|
| `ssl-params` | `ssl_protocols TLSv1.2 TLSv1.3`, cipher `EECDH+AESGCM:EDH+AESGCM`, `ssl_prefer_server_ciphers off` | Mọi frag `ssl/<FQDN>` |
| `ssl/<FQDN>` | `include ssl-params` + `fullchain.pem`/`privkey.pem` của `/etc/letsencrypt/live/<FQDN>/` | Site storefront, www, admin |
| `metaticket-ssl` | Cert dùng chung kiểu cũ | Chỉ site news (cả hai môi trường) |

Quy trình cấp cert và tạo frag `ssl/<FQDN>` cho domain mới: [SSL_LETSENCRYPT_PER_DOMAIN.md](./SSL_LETSENCRYPT_PER_DOMAIN.md). `metaticket-ssl` không nên được giả định là phủ một subdomain mới (xem mục 12).

---

## 5. Xác thực tại gateway (`auth_request`)

### 5.1 Khách hàng (site storefront)

```
Browser ──GET /api/order/...  (Authorization: Bearer <JWT khách>)──▶ nginx
   nginx ──subrequest GET /internal/customer-authenticate──▶ customer_auth_service
            /internal/customer-auth/authenticate   (không body, chuyển tiếp Authorization, X-Original-URI)
         ◀── 200 + X-Customer-User-Id, X-Customer-Phone   |  401 / 500
   nginx ──proxy /order/... + X-Customer-User-Id, X-Customer-Phone──▶ order_service
```

`service.customer.auth` (`controller/AuthCtrl.kt` → `authenticate`) kiểm chữ ký JWT, so token với token đang lưu trong Redis cho user đó, chuẩn hóa SĐT (`normalizePhoneNumber`), rồi trả header. Bất kỳ kết quả nào khác 2xx → nginx trả lỗi cho client (401/403 giữ nguyên, lỗi khác thành 500).

### 5.2 Operator (site admin)

```
Portal ──/private/show/...  (Authorization: Bearer <JWT operator>)──▶ nginx
   nginx ──subrequest /internal/authenticate──▶ authentication_service
            /internal/authentication/check-login
         ◀── 200 + X-OP-User-Id   |  401
   nginx ──proxy /private/show/... + X-OP-User-Id──▶ show_service
```

`service.authentication` (`Route.kt` → `checkLogin`) xác minh token và phiên rồi trả `X-OP-User-Id`.

### 5.3 Vì sao header không giả mạo được — và khi nào bị phá

- `proxy_set_header X-OP-User-Id $userId` **ghi đè** header cùng tên mà client tự gửi, nên client không thể tự xưng danh tính.
- `location = /internal/...` có directive `internal;` → không truy cập trực tiếp từ ngoài được. Các route `/internal/*` của service không có location nào map tới, nên không lộ qua nginx (request kiểu `https://admin.<…>/internal/...` rơi vào `location /` và nhận `index.html` của portal).

> ⚠️ **Bẫy kế thừa directive của nginx:** `proxy_set_header` của location cha chỉ được kế thừa xuống location con **khi location con không khai báo bất kỳ `proxy_set_header` nào**. Nếu thêm một location con trong `/private/` hoặc `/api` mà có `proxy_set_header` riêng (ví dụ để đặt `Upgrade` cho websocket), toàn bộ header của cha — **kể cả `X-OP-User-Id`/`X-Customer-User-Id`** — sẽ mất, và header do client gửi sẽ lọt thẳng tới service. Khi buộc phải thêm `proxy_set_header` ở location con, **khai báo lại đủ** `X-OP-User-Id`/`X-Customer-User-Id` (+ `X-Customer-Phone`), `Host`, `X-Real-IP`, `X-Forwarded-For`. `auth_request`/`auth_request_set` vẫn kế thừa bình thường.

- Toàn bộ mô hình dựa trên việc **cổng HTTP của service không truy cập được từ ngoài nginx** (service bind `0.0.0.0` và tin header vô điều kiện). Đây là trách nhiệm của firewall/security group của server, không nằm trong cấu hình nginx.

---

## 6. Ánh xạ URL → service

### 6.1 Tóm tắt bốn lối vào

| Lối vào (từ trình duyệt) | Site | Auth tại nginx | Path tới service | Prefix route phía Ktor |
|--------------------------|------|----------------|------------------|------------------------|
| `/api/public/<svc>/x` | storefront + admin | Không | `/public/<svc>/x` | `/public/<svc>` |
| `/api/<svc>/x` | storefront | JWT khách → `X-Customer-User-Id`, `X-Customer-Phone` | `/<svc>/x` | `/<svc>` |
| `/private/<svc>/x` | admin | JWT operator → `X-OP-User-Id` | `/private/<svc>/x` (giữ nguyên) | `/private/<svc>` |
| `/api/payment/{ipn,return}/result/...` | storefront | Không (IPN VNPay đúng path exact có allowlist) | `/payment/{ipn,return}/result/...` | `/payment/...` |

Ngoại lệ: editor (`/api/public/editor/<x>` và `/api/editor/<x>` → `/editor/<x>/`, không có `/public`), news (`rewrite`), metasearch (service ngoài).

### 6.2 Quy tắc `proxy_pass` cần nhớ khi sửa

- **`proxy_pass` có URI** (`http://show_service/public/show/`): phần khớp với prefix của `location` được thay bằng URI. Ví dụ `location /api/public/show` + `/public/show/` biến `/api/public/show/list` thành `/public/show//list` (dư `/`). Ktor bỏ qua segment rỗng nên chạy được; Spring Boot (`service.news`) thì không, nên news dùng `rewrite … break` + `proxy_pass` không URI.
- **`proxy_pass` không URI** (`http://show_service`): giữ nguyên URI gốc — dùng cho `/private/`.
- **Thiếu `/` cuối ở `location` nhưng có `/` cuối ở URI** (`location /api/public/xxx` + `…/public/xxx/`): request `/api/public/xxxABC` cũng khớp và bị viết lại thành `/public/xxx/ABC`. Chính lỗi này từng cho phép `POST /api/public/authenticationregister` lọt tới `/public/authentication/register` dù đã có `return 403` cho path chuẩn; đã sửa bằng `location /api/public/authentication/`. Các location khác vẫn theo mẫu cũ — **location mới nên có `/` cuối ở cả `location` và URI**, và mọi lệnh chặn (`return 403`) phải kiểm cả biến thể không có `/`.
- Thứ tự ưu tiên: `location =` (exact) > prefix dài nhất (nếu là `^~` thì dừng) > regex theo thứ tự xuất hiện > prefix dài nhất. Các regex `/news/...` trong `metaticket-root` thắng `location /` nhờ quy tắc này.

---

## 7. Rate limiting và cache

### 7.1 Rate limiting

Nginx hiện **chỉ** giới hạn tốc độ cho `wp-login.php` (zone `wp_login`, 5 request/phút/IP, burst 5, trả `429`). Không có `limit_req` cho API MetaTicket (đăng nhập, OTP, checkout...). Giới hạn ở tầng ứng dụng nếu có nằm trong từng service (ví dụ `service.accessory` dựa vào `X-Real-IP`/`X-Forwarded-For` do frag public đặt; customer-auth/customer-user dùng captcha).

Thêm zone mới: khai báo `limit_req_zone` trong `metaticket-limit-req-zones` (frag nằm trong `http {}`), dùng `limit_req zone=<tên> …;` trong location cần giới hạn, kiểm `nginx -t`.

### 7.2 Cache

Nginx **không** dùng `proxy_cache`; chỉ gắn header để trình duyệt/CDN cache:

| Location | Header |
|----------|--------|
| `/api/public/show`, `/api/public/ticket`, `/api/show`, `/api/ticket` (GET) | `Cache-Control: public, max-age=10` |
| `/api/public/metasearch/` (GET) | `Cache-Control: public, max-age=300` |
| `/show-image`, `/seatplanner-image` | `Cache-Control: public, max-age=3600` + `Last-Modified` |

Lưu ý: `/api/show` và `/api/ticket` đi qua xác thực khách hàng nhưng vẫn gắn `public` — CDN/proxy dùng chung có thể cache phản hồi theo URL. Khi thêm endpoint trả dữ liệu riêng của user dưới `/api/show` hoặc `/api/ticket`, phải để service trả `Cache-Control: private, no-store` hoặc tách location. Dữ liệu metasearch có thể cũ tới 5 phút sau khi show được cập nhật.

---

## 8. Allowlist IP cho IPN cổng thanh toán

Cơ chế: một `location =` (exact match, ưu tiên cao nhất) cho endpoint IPN cần bảo vệ, bên trong `allow <IP-cổng-thanh-toán>; deny all;` rồi mới `proxy_pass`. Request từ IP khác nhận `403` từ nginx, không tới `service.payment`.

Thực tế cần biết:

- Hiện chỉ có **một** location exact `= /api/payment/ipn/result/vnpay`. Trong khi đó route IPN của `service.payment` là `/payment/ipn/result/<gateway>/{pgaCode}` (`service.payment/.../Route.kt`) và URL IPN đăng ký với cổng được sinh dạng `<baseIpnUrl>/<gateway>/<pgaCode>` (`PGAccountController.kt`). Request thật có thêm `/{pgaCode}` nên **không khớp** location exact và rơi vào `location /api/payment/ipn/result` — không có allowlist. Tính xác thực của IPN thực tế dựa vào kiểm chữ ký/hash trong `service.payment` (xem [SERVICE_PAYMENT_TECHNICAL_DOCUMENTATION.md](./SERVICE_PAYMENT_TECHNICAL_DOCUMENTATION.md) mục 5.3).
- Muốn allowlist thật sự có hiệu lực: dùng prefix có `/` cuối, ví dụ `location /api/payment/ipn/result/vnpay/ { allow …; deny all; proxy_pass http://payment_service/payment/ipn/result/vnpay/; }`, lấy danh sách IP chính thức từ cổng thanh toán, và test bằng IPN sandbox trên staging trước.
- Danh sách IP cổng thanh toán **không ghi vào tài liệu**; chỉ nằm trong frag trên server/repo.
- Nếu đặt nginx sau CDN/proxy, `allow` kiểm `$remote_addr` là IP của CDN — khi đó cần cấu hình `real_ip` (`set_real_ip_from` + `real_ip_header`) trước khi allowlist có ý nghĩa.

---

## 9. Khác biệt Production và Staging

| Mục | Production | Staging |
|-----|-----------|---------|
| Domain | `metaticket.com.vn`, `admin.`, `news.`, `www.` | `staging.metaticket.vn`, `admin.staging.`, `news.staging.`, `www.staging.` |
| Port service | `30xx` | `40xx` |
| Metasearch / storefront container | Trên `<server-nội-bộ>` (8080 / 4200) | Cùng máy (48080 / 44200) |
| Static root | `/opt/metaticket/static`, `/opt/metaticket/service` | `/staging/metaticket/static`, `/staging/metaticket/service` |
| Listen | Chỉ `443 ssl` (block port 80 đang comment) | Thêm `listen 80` trên mọi site **không redirect** — phục vụ cả HTTP thường |
| `server_name` | Chỉ domain | Site storefront và admin có thêm IP của server |
| `metaticket-ssl` (site news) | Cert dùng chung `metaticket.com.vn` (+ `ssl_protocols` còn TLSv1/1.1) | Cert dùng chung khác tên (`metaticket.vn-*`) |
| `metaticket-wordpress` | `proxy_pass …:8081/wp-login.php` và `…:8081/`; có `proxy_redirect` về `news.metaticket.com.vn` | `proxy_pass …:8081` (không URI); không có `proxy_redirect`; CSP trỏ `admin.staging.metaticket.vn` |
| `metaticket-root` | `/assets/homepage` đặt sau block news | Đặt trước — không ảnh hưởng vì là prefix khác nhau |

Các frag còn lại (`metaticket-api`, `metaticket-public-api`, `metaticket-private-api`, `metaticket-payment-ipn`, `ssl-params`) giống nhau ngoài comment. Khi sửa một môi trường, luôn sửa môi trường kia trong cùng commit (trừ khi khác biệt là có chủ đích và được ghi lại ở bảng này).

---

## 10. Quy trình thay đổi an toàn

Nguyên tắc (áp dụng cho mọi thay đổi, cả staging lẫn prod):

1. Sửa trong repo trước (`nginx/staging/...` và `nginx/prod/...`), review diff.
2. Thử trên **staging** trước; chỉ lên prod sau khi staging chạy đúng.
3. Trên server: **backup bằng `cp -a`** file/thư mục sắp sửa (đặc biệt `metaticket-upstream-server`, `metaticket-public-api`, `metaticket-private-api`) → copy file mới → `sudo nginx -t` → nếu OK mới `sudo systemctl reload nginx` (hoặc `sudo nginx -s reload`). **Không `restart`**: reload giữ worker cũ phục vụ tới khi xong, còn restart cắt mọi kết nối và nếu config lỗi thì nginx không lên lại — sập mọi domain trên server.
4. Kiểm tra bằng `curl` cả đường dẫn mới lẫn vài đường dẫn cũ quan trọng (trang chủ, `/api/public/show/...`, đăng nhập portal).
5. Rollback = copy lại bản backup → `nginx -t` → reload.

Mọi lệnh trên server do người vận hành tự chạy; máy dev Windows không có `rsync`, dùng `scp`.

### 10.1 Mẫu lệnh đồng bộ một frag

```bash
# [LAPTOP] — upload file đã sửa lên thư mục tạm trên server (staging trước)
scp nginx/staging/metaticket-frags/metaticket-public-api <user>@<staging-server>:/tmp/

# [STAGING] — backup, thay, kiểm tra, reload
TS=$(date +%Y%m%d-%H%M%S)
sudo cp -a /etc/nginx/metaticket-frags/metaticket-public-api /etc/nginx/metaticket-frags/metaticket-public-api.bak.$TS
sudo cp /tmp/metaticket-public-api /etc/nginx/metaticket-frags/metaticket-public-api
sudo nginx -t && sudo systemctl reload nginx
# lỗi → sudo cp -a /etc/nginx/metaticket-frags/metaticket-public-api.bak.$TS /etc/nginx/metaticket-frags/metaticket-public-api && sudo nginx -t && sudo systemctl reload nginx
```

> Đặt file backup ngoài các thư mục được `include` bằng wildcard (nếu `nginx.conf` có `include /etc/nginx/sites-enabled/*`, đừng để `.bak` trong `sites-enabled/`, vì nginx sẽ nạp cả bản backup và báo trùng `server_name`/`location`).

### 10.2 Thêm một service backend mới

1. Chọn port theo quy ước (`40NN` staging / `30NN` prod) và cấu hình đúng trong `application.conf` của service trên server.
2. `metaticket-upstream-server` (cả hai môi trường): `upstream <tên>_service { server 127.0.0.1:<port>; }`.
3. Thêm location theo loại API (nhớ quy tắc mục 6.2 và bẫy mục 5.3):
   - Công khai → trong `metaticket-public-api`: `location /api/public/<svc>/ { proxy_pass http://<tên>_service/public/<svc>/; }`
   - Khách hàng đăng nhập → trong `metaticket-api` (lồng trong `location /api`): `location /api/<svc>/ { proxy_pass http://<tên>_service/<svc>/; }`
   - Operator → trong `metaticket-private-api` (lồng trong `location /private/`): `location /private/<svc> { proxy_pass http://<tên>_service; }`
   - Service-to-service (`/internal/...`) → **không** thêm location nào.
4. Upload body lớn hơn 1M → thêm `client_max_body_size` ở location riêng cho endpoint upload.
5. Đồng bộ, `nginx -t`, reload theo mục 10.1; kiểm tra `curl -i https://<domain>/api/public/<svc>/...` và với API có auth: không token → `401`.

### 10.3 Thêm route cho service đã có

Phần lớn trường hợp **không cần sửa nginx**: location `/api/public/<svc>`, `/api/<svc>`, `/private/<svc>` đã bắt mọi đường dẫn con. Chỉ sửa nginx khi route mới cần chính sách khác (body lớn hơn, cache khác, chặn riêng, allowlist IP). Khi đó thêm location con dài hơn **bên trong** location cha tương ứng để vẫn kế thừa `auth_request` và header.

### 10.4 Thêm domain/subdomain mới

1. DNS trỏ về server.
2. Cấp cert riêng cho FQDN và tạo `metaticket-frags/ssl/<FQDN>` theo [SSL_LETSENCRYPT_PER_DOMAIN.md](./SSL_LETSENCRYPT_PER_DOMAIN.md) — không dùng lại `metaticket-ssl`.
3. Tạo `sites-available/<FQDN>` mới (một file cho mỗi domain, phân biệt bằng `server_name`); không include lại `metaticket-upstream-server` (đã được nạp từ site storefront). Nếu site cần API có auth, phải tự khai báo `location = /internal/authenticate` hoặc `/internal/customer-authenticate` tương ứng trong `server` của nó, vì frag `metaticket-private-api`/`metaticket-api` tham chiếu tới các location này.
4. `sudo ln -s /etc/nginx/sites-available/<FQDN> /etc/nginx/sites-enabled/<FQDN>` → `nginx -t` → reload.
5. Commit file site + frag ssl vào cả `nginx/staging` hoặc `nginx/prod` tương ứng.

Hướng dẫn thao tác `vi` trên server: `docs/guide/nginx-them-domain-path.md` ở thư mục workspace (lưu ý mục 12 về ví dụ trong tài liệu đó).

---

## 11. Xử lý sự cố

| Triệu chứng | Nguyên nhân thường gặp | Kiểm tra |
|-------------|------------------------|---------|
| `502 Bad Gateway` trên một nhóm API | Service tương ứng không chạy hoặc port lệch với upstream | `systemctl status metaticket-<svc>`, `ss -ltnp \| grep <port>`, so với `metaticket-upstream-server` |
| `401` mọi API `/api/<svc>` | `service.customer.auth` chết/Redis lỗi/token hết hạn | Log customer-auth; gọi thử `/api/public/customer-auth/...` |
| `500` thay vì `401` ở API có auth | Subrequest auth trả mã lạ (lỗi nội bộ auth service) | `error.log` của nginx có dòng `auth request unexpected status` |
| Portal mở được nhưng mọi thao tác `401` | `service.authentication` lỗi hoặc token operator hết hạn | Log authentication, `/internal/authentication/check-login` |
| `404` từ Ktor với đường dẫn có `//` hoặc sai prefix | Sai quy tắc `proxy_pass` (mục 6.2) hoặc route Ktor thiếu prefix | So `location` + URI `proxy_pass` với `Route.kt` |
| `413 Request Entity Too Large` | Upload vượt `client_max_body_size` | Thêm/điều chỉnh ở location upload |
| `nginx -t` báo `zone "wp_login" … unknown` | Chưa include `metaticket-limit-req-zones` trong `http {}` | `nginx.conf` |
| `nginx -t` báo `duplicate location` | Thêm location trùng prefix trong cùng `server` (kể cả qua frag) | `sudo nginx -T \| grep -n "location <path>"` |
| Thay đổi tĩnh (`ww`, storefront) không thấy | Cache trình duyệt/CDN, file không hash | Purge CDN, kiểm tra file trên server |

Lệnh hữu ích: `sudo nginx -T` (in toàn bộ config sau khi include), `sudo tail -f /var/log/nginx/error.log`.

---

## 12. Lưu ý và điểm lệch đã biết

- **IPN allowlist không áp cho path có `pgaCode`** (mục 8).
- **Staging phục vụ HTTP thường trên port 80** không redirect → token `Authorization` có thể đi qua kênh không mã hóa nếu client gọi `http://`. Prod chỉ mở 443.
- **Site news dùng `metaticket-ssl`** (cert chung kiểu cũ) thay vì `ssl/<FQDN>`; ở prod frag này còn bật TLSv1/TLSv1.1, và ở staging nó trỏ `ssl_certificate` tới file `chain.crt` — cần đối chiếu với cert thực tế trên server khi chuyển news sang frag riêng.
- **Mẫu `location` không có `/` cuối** vẫn còn ở hầu hết location public/api (mục 6.2). Hiện chỉ đường register từng khai thác được (đã sửa); Ktor không giải `..` nên các biến thể khác chỉ ra 404, nhưng location mới không nên theo mẫu này.
- **`frontend_service` (Next.js) và `/planner` (`portal.seatplanner`)** vẫn còn trong config dù đã ngừng hỗ trợ; query `?showId=&sessionId=` trên `/` vẫn được đẩy sang Next.js.
- **`/api/show`, `/api/ticket` gắn `Cache-Control: public`** dù đi qua auth khách hàng (mục 7.2).
- Không có rate limit ở nginx cho API MetaTicket (mục 7.1).

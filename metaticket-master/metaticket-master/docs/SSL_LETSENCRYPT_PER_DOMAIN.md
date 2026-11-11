# SSL Let’s Encrypt per-domain + auto-renew

Hướng dẫn vận hành trên server (SSH + sudo): cấp chứng chỉ **một FQDN** (không wildcard), gắn nginx, bật tự gia hạn. Có thể lặp lại cho mọi domain (staging / admin / www / …).

Đã áp dụng thành công cho `staging.metaticket.vn` (2026-07).

---

## 1. Khái niệm

| Thành phần | Vai trò |
|------------|---------|
| **Nginx** | Terminate TLS. Backend chỉ nhận HTTP nội bộ. |
| **Certbot + Let’s Encrypt** | Cấp / gia hạn file tại `/etc/letsencrypt/live/...`. |
| **HTTP-01** | Xác thực domain qua port **80**. Không cần DNS API. |
| **Wildcard** (`*.example.com`) | Cần DNS-01 — **không** thuộc phạm vi tài liệu này. |

```
Browser → DNS → :443 → Nginx (ssl_certificate) → proxy_pass → backend
```

Certbot không thay nginx phục vụ web; chỉ cập nhật file cert. Sau renew thành công cần **reload nginx** (deploy-hook).

---

## 2. Điều kiện trước khi làm

1. DNS `<FQDN>` trỏ đúng IP server nginx.
2. Port **80** và **443** mở từ internet.
3. Đã có (hoặc sẽ có) `server { server_name <FQDN>; listen 80/443 ... }` trong `sites-enabled`.
4. Quyền `sudo`.
5. Email team cho Let’s Encrypt (cảnh báo renew / sự cố), ví dụ `contact@metaticket.com.vn`.

**Downtime:** đúng thứ tự + `nginx -t` rồi `reload` (không `restart`) → gần như không downtime.

Khi `apt` hiện dialog *Daemons using outdated libraries*: **bỏ tick** các `staging.metaticket.*` (và app service không cần), hoặc Cancel — đừng restart hàng loạt vì chỉ cài certbot.

---

## 3. Cấu trúc file khuyến nghị

### Trên server

```
/etc/nginx/metaticket-frags/
  ssl-params                 # protocol/cipher chung
  ssl/<FQDN>                 # path cert RIÊNG từng hostname
  metaticket-ssl             # (cũ) giữ tạm cho host chưa migrate

/etc/nginx/sites-enabled/
  <site>                     # include ssl/<FQDN> trong đúng server block
```

### Frag chung — `ssl-params`

```nginx
ssl_protocols       TLSv1.2 TLSv1.3;
ssl_ciphers         EECDH+AESGCM:EDH+AESGCM;
ssl_prefer_server_ciphers off;
```

### Frag theo domain — `ssl/<FQDN>`

```nginx
include /etc/nginx/metaticket-frags/ssl-params;
ssl_certificate     /etc/letsencrypt/live/<LIVE_DIR>/fullchain.pem;
ssl_certificate_key /etc/letsencrypt/live/<LIVE_DIR>/privkey.pem;
```

### Trong site config

```nginx
server {
    listen 443 ssl;
    listen [::]:443 ssl;
    server_name <FQDN>;

    include /etc/nginx/metaticket-frags/ssl/<FQDN>;
    # ... locations ...
}
```

**Một hostname = một live dir = một frag.** Dùng `fullchain.pem` + `privkey.pem` (không dùng leaf-only / `chain.crt` làm certificate chính).

### Trong repo (đồng bộ sau khi ổn định trên server)

| Path | Ý nghĩa |
|------|---------|
| `nginx/staging/metaticket-frags/ssl-params` | TLS params |
| `nginx/staging/metaticket-frags/ssl/` | Frag theo FQDN |
| `nginx/staging/sites-available/` | Site `include` đúng frag |

Nếu certbot tạo `FQDN-0002`, path trong frag/repo phải khớp đúng suffix trên server.

---

## 4. Quy trình tuần tự (một domain)

Thay `<FQDN>`, `<EMAIL>`, `<LIVE_DIR>`, `<site-file>`.

Lần đầu thường `LIVE_DIR=<FQDN>`. Nếu output certbot ghi `.../FQDN-0002/...` thì dùng **đúng** `-0002`.

### Bước 0 — Backup & hiện trạng

```bash
sudo cp -a /etc/nginx/sites-enabled/<site-file> \
  /tmp/<site-file>.bak.$(date +%Y%m%d%H%M%S)

echo | openssl s_client -connect <FQDN>:443 -servername <FQDN> 2>/dev/null \
  | openssl x509 -noout -dates -subject -issuer

sudo nginx -t
sudo ls -la /etc/letsencrypt/live/
```

### Bước 1 — Cài Certbot

```bash
sudo apt update
sudo apt install -y certbot python3-certbot-nginx
```

Dialog restart services: bỏ tick MetaTicket apps → Ok (hoặc Cancel).

### Bước 2 — Cấp chứng chỉ (trước khi đổi nginx SSL path)

```bash
sudo certbot certonly --nginx \
  -d <FQDN> \
  --email <EMAIL> \
  --agree-tos \
  --non-interactive
```

Không gắn email (không khuyến nghị lâu dài):

```bash
sudo certbot certonly --nginx -d <FQDN> \
  --register-unsafely-without-email --agree-tos --non-interactive
```

Đọc output — ví dụ:

```text
Certificate is saved at: /etc/letsencrypt/live/staging.metaticket.vn-0002/fullchain.pem
Key is saved at:         /etc/letsencrypt/live/staging.metaticket.vn-0002/privkey.pem
This certificate expires on 2026-10-13.
```

→ `LIVE_DIR=staging.metaticket.vn-0002`.

```bash
sudo ls -la /etc/letsencrypt/live/<LIVE_DIR>/
sudo openssl x509 -in /etc/letsencrypt/live/<LIVE_DIR>/fullchain.pem \
  -noout -dates -subject -issuer
```

Kỳ vọng: Issuer **Let's Encrypt**, tên khớp `<FQDN>`, còn ~90 ngày.

### Bước 3 — Tạo frag (chưa reload)

```bash
sudo mkdir -p /etc/nginx/metaticket-frags/ssl

sudo tee /etc/nginx/metaticket-frags/ssl-params >/dev/null <<'EOF'
ssl_protocols       TLSv1.2 TLSv1.3;
ssl_ciphers         EECDH+AESGCM:EDH+AESGCM;
ssl_prefer_server_ciphers off;
EOF

# Thay LIVE_DIR cho đúng (có thể có -0001 / -0002)
sudo tee /etc/nginx/metaticket-frags/ssl/<FQDN> >/dev/null <<EOF
include /etc/nginx/metaticket-frags/ssl-params;
ssl_certificate     /etc/letsencrypt/live/<LIVE_DIR>/fullchain.pem;
ssl_certificate_key /etc/letsencrypt/live/<LIVE_DIR>/privkey.pem;
EOF

cat /etc/nginx/metaticket-frags/ssl-params
cat /etc/nginx/metaticket-frags/ssl/<FQDN>
sudo test -f /etc/letsencrypt/live/<LIVE_DIR>/fullchain.pem \
  && sudo test -f /etc/letsencrypt/live/<LIVE_DIR>/privkey.pem \
  && echo 'CERT FILES OK'
```

### Bước 4 — Sửa site: chỉ include của đúng `server_name`

```bash
sudo grep -n "server_name\|metaticket-ssl\|ssl/" \
  /etc/nginx/sites-enabled/<site-file>
```

Trong block `server_name <FQDN>;`, đổi:

```nginx
include /etc/nginx/metaticket-frags/metaticket-ssl;
```

→

```nginx
include /etc/nginx/metaticket-frags/ssl/<FQDN>;
```

Khi migrate từng domain: host khác **giữ** include cũ. Đổi frag khi chưa có `fullchain.pem` → `nginx -t` fail.

### Bước 5 — Test + reload

```bash
sudo nginx -t && sudo systemctl reload nginx
```

Không `restart nginx` trừ khi bắt buộc.

### Bước 6 — Verify HTTPS

```bash
echo | openssl s_client -connect <FQDN>:443 -servername <FQDN> 2>/dev/null \
  | openssl x509 -noout -dates -subject -issuer

echo | openssl s_client -connect <FQDN>:443 -servername <FQDN> -verify_return_error 2>&1 \
  | grep -E 'Verify return code|s:|i:'
```

Kỳ vọng: Let's Encrypt, đúng hostname, `Verify return code: 0 (ok)`.

Trình duyệt: dùng **cửa sổ ẩn danh** (tránh cache “Not secure” từ cert hết hạn cũ). Issuer `YR1` là intermediate Let’s Encrypt — bình thường.

### Bước 7 — Auto-renew + reload sau renew

```bash
sudo systemctl enable --now certbot.timer
systemctl list-timers | grep certbot

sudo mkdir -p /etc/letsencrypt/renewal-hooks/deploy
echo -e '#!/bin/sh\nsystemctl reload nginx' \
  | sudo tee /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
sudo chmod +x /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh

sudo certbot renew --dry-run
sudo certbot certificates
```

Timer chạy định kỳ; certbot chỉ renew thật khi còn khoảng **≤ 30 ngày**.

---

## 5. Checklist domain mới

```text
[ ] DNS <FQDN> → IP server
[ ] Port 80/443 mở
[ ] apt: certbot + python3-certbot-nginx (không restart app hàng loạt)
[ ] certonly --nginx -d <FQDN> → ghi lại LIVE_DIR (có thể *-0002)
[ ] ssl-params (nếu chưa) + ssl/<FQDN> trỏ đúng LIVE_DIR
[ ] sites-enabled: chỉ block đó include ssl/<FQDN>
[ ] nginx -t && systemctl reload nginx
[ ] openssl → Let's Encrypt, đúng tên, còn hạn
[ ] certbot.timer enabled
[ ] renewal-hooks/deploy/reload-nginx.sh +x
[ ] certbot renew --dry-run OK
[ ] Đồng bộ LIVE_DIR vào repo nếu commit nginx config
```

---

## 6. Ví dụ: `staging.metaticket.vn`

| Mục | Giá trị |
|-----|---------|
| FQDN | `staging.metaticket.vn` |
| LIVE_DIR trên server | `staging.metaticket.vn-0002` |
| Email | `contact@metaticket.com.vn` |
| Frag | `/etc/nginx/metaticket-frags/ssl/staging.metaticket.vn` |
| Site | `sites-enabled/staging.metaticket.vn` — chỉ đổi block `staging.metaticket.vn` |
| www / admin lúc test | vẫn `include .../metaticket-ssl` |

```bash
sudo certbot certonly --nginx -d staging.metaticket.vn \
  --email contact@metaticket.com.vn --agree-tos --non-interactive
```

Frag thực tế (có `-0002`):

```nginx
include /etc/nginx/metaticket-frags/ssl-params;
ssl_certificate     /etc/letsencrypt/live/staging.metaticket.vn-0002/fullchain.pem;
ssl_certificate_key /etc/letsencrypt/live/staging.metaticket.vn-0002/privkey.pem;
```

### Domain staging tiếp theo

1. `staging.metaticket.vn` — đã làm  
2. `www.staging.metaticket.vn` — lặp mục 4 với FQDN mới  
3. `admin.staging.metaticket.vn` — tương tự  
4. `news.staging...` (nếu có) — sau  

---

## 7. Email certbot

- Dùng để nhận cảnh báo hết hạn / renew fail / thông báo ACME.
- Không phải đăng nhập portal, không thay DNS ownership.
- Nên dùng mailbox team đọc được.

---

## 8. Troubleshooting

| Hiện tượng | Nguyên nhân thường gặp | Cách xử lý |
|------------|------------------------|------------|
| Not secure, cert GlobalSign hết hạn | Vẫn `include metaticket-ssl` cũ | Đổi sang `ssl/<FQDN>`, reload |
| `nginx -t`: cannot load certificate | Path sai / thiếu `-0002` / chưa cấp | `ls live/`, sửa frag |
| Certbot tạo `FQDN-0002` | Line cũ cùng tên còn trên disk | Dùng đúng suffix trong frag |
| Certbot fail challenge | DNS / firewall 80 / không listen 80 | Fix DNS, firewall, nginx :80 |
| openssl OK, Chrome Not secure | Cache SSL lỗi cũ | Incognito / Clear site data |
| apt hỏi restart services | needrestart | Đừng tick toàn bộ MetaTicket |
| Warn `conflicting server name "10.0.1.12"` | IP trùng nhiều block | Không chặn SSL; dọn sau |
| Renew xong web vẫn cert cũ | Thiếu deploy-hook reload | Thêm hook + reload |

```bash
echo | openssl s_client -connect <FQDN>:443 -servername <FQDN> 2>/dev/null \
  | openssl x509 -noout -dates -subject -issuer -fingerprint -sha256
```

---

## 9. Ngoài phạm vi

- Wildcard + DNS-01.
- Quyết định giữ / bỏ GlobalSign prod mua ngoài.
- Dùng `cert.pem` thay `fullchain.pem` làm `ssl_certificate`.

---

## 10. Lệnh nhanh

```bash
# Cấp mới
sudo certbot certonly --nginx -d <FQDN> --email <EMAIL> --agree-tos --non-interactive

# Xem / gia hạn
sudo certbot certificates
sudo certbot renew --dry-run
sudo certbot renew

# Nginx
sudo nginx -t && sudo systemctl reload nginx
```

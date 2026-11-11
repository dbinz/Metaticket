# Deployment Guide - MetaTicket TypeScript (Modules & Portals)

## Tổng quan

Project TypeScript của MetaTicket bao gồm hai loại ứng dụng frontend:

- **Module** — Micro-frontend modules (MFE), được load động vào hệ thống chính
- **Portal** — Ứng dụng web standalone (Angular SPA)

Sau khi build, output là static files (HTML, JS, CSS) được copy trực tiếp lên server.

**Môi trường prod và staging được deploy trên hai server khác nhau.**

---

## Danh sách ứng dụng

### Modules

| Module     | Mô tả                                            | Repo                                            |
| ---------- | -------------------------------------------------- | ----------------------------------------------- |
| metaticket | Module chính của MetaTicket (MFE + editors + WW) | Repo hiện tại                                 |
| viclass    | Module riêng của ViClass                         | Repo riêng (không nằm trong repo metaticket) |

### Portals

| Portal                     | Mô tả                                |
| -------------------------- | -------------------------------------- |
| portal.admin               | Portal quản trị admin                |
| portal.order.datacollector | Portal thu thập dữ liệu đơn hàng |

---

## Cấu trúc trên Server

### Đường dẫn theo môi trường

| Môi trường | Module Path                           | Portal Path                           | Server            |
| ------------- | ------------------------------------- | ------------------------------------- | ----------------- |
| Staging       | `/staging/metaticket/static/module` | `/staging/metaticket/static/portal` | Staging server    |
| Production    | `/opt/metaticket/static/module`     | `/opt/metaticket/static/portal`     | Production server |

### Cấu trúc folder trên server

```
/opt/metaticket/static/
├── module/
│   ├── metaticket/           # Module metaticket
│   │   ├── mfe/              # Micro-frontend container
│   │   ├── ww/               # Web Worker module
│   │   ├── editor.seatplanner.ui/
│   │   └── themes/
│   └── viclass/              # Module viclass (từ repo riêng)
│       ├── mfe/
│       ├── ww/
│       └── ...
└── portal/
    ├── portal.admin/         # Admin portal
    └── portal.order.datacollector/  # Order datacollector portal
```

---

## Yêu cầu

- Node.js 18+
- Yarn package manager
- SSH access đến server đích

---

## Quy trình Build

### Build scripts có sẵn trong package.json

#### Build cho Development (không optimize)

```bash
# Build tất cả modules (editors + mfe + ww)
yarn build

# Build tất cả portals
yarn portal:build
```

#### Build cho Production (optimize, minify, hashing)

```bash
# Build tất cả modules cho production
yarn dist

# Build từng portal cho production
yarn portal.admin:dist
yarn portal.order.datacollector:dist
```

### Chi tiết từng lệnh build

| Lệnh                                    | Output Path                                                   | Mô tả                    |
| ---------------------------------------- | ------------------------------------------------------------- | -------------------------- |
| `yarn dist`                            | `dist/metaticket/mfe/`, `dist/metaticket/ww/`, editors... | Tất cả modules           |
| `yarn mfe:dist`                        | `dist/metaticket/mfe/`                                      | MFE container              |
| `yarn ww:dist`                         | `dist/metaticket/ww/`                                       | Web Worker                 |
| `yarn editor:dist`                     | `dist/metaticket/editor.*`                                  | Editor libraries           |
| `yarn editor.ui:dist`                  | `dist/metaticket/editor.seatplanner.ui/`                    | Editor UI                  |
| `yarn portal.admin:dist`               | `dist/metaticket/portal.admin/`                             | Admin portal               |
| `yarn portal.order.datacollector:dist` | `dist/metaticket/portal.order.datacollector/`               | Order datacollector portal |

---

## Quy trình Deploy

### Deploy Module MetaTicket

#### 1. Build production

```bash
cd d:\metaticket\source\metaticket\ts
yarn dist
```

#### 2. Upload lên server

```bash
# Staging
scp -r dist/metaticket/mfe/* user@<STAGING_SERVER>:/staging/metaticket/static/module/metaticket/mfe/
scp -r dist/metaticket/ww/* user@<STAGING_SERVER>:/staging/metaticket/static/module/metaticket/ww/
scp -r dist/metaticket/themes/* user@<STAGING_SERVER>:/staging/metaticket/static/module/metaticket/themes/

# Production
scp -r dist/metaticket/mfe/* user@<PROD_SERVER>:/opt/metaticket/static/module/metaticket/mfe/
scp -r dist/metaticket/ww/* user@<PROD_SERVER>:/opt/metaticket/static/module/metaticket/ww/
scp -r dist/metaticket/themes/* user@<PROD_SERVER>:/opt/metaticket/static/module/metaticket/themes/
```

#### 3. Chuyển ownership

```bash
ssh user@<SERVER>
sudo chown -R metaticket:metaticket /staging/metaticket/static/module/metaticket/
# hoặc cho production:
sudo chown -R metaticket:metaticket /opt/metaticket/static/module/metaticket/
```

---

### Deploy Portal Admin

#### 1. Build production

```bash
yarn portal.admin:dist
```

#### 2. Upload lên server

```bash
# Staging
scp -r dist/metaticket/portal.admin/* user@<STAGING_SERVER>:/staging/metaticket/static/portal/portal.admin/

# Production
scp -r dist/metaticket/portal.admin/* user@<PROD_SERVER>:/opt/metaticket/static/portal/portal.admin/
```

#### 3. Chuyển ownership

```bash
sudo chown -R metaticket:metaticket /staging/metaticket/static/portal/portal.admin/
# hoặc:
sudo chown -R metaticket:metaticket /opt/metaticket/static/portal/portal.admin/
```

---

### Deploy Portal Order Datacollector

#### 1. Build production

```bash
yarn portal.order.datacollector:dist
```

#### 2. Upload lên server

```bash
# Staging
scp -r dist/metaticket/portal.order.datacollector/* user@<STAGING_SERVER>:/staging/metaticket/static/portal/portal.order.datacollector/

# Production
scp -r dist/metaticket/portal.order.datacollector/* user@<PROD_SERVER>:/opt/metaticket/static/portal/portal.order.datacollector/
```

#### 3. Chuyển ownership

```bash
sudo chown -R metaticket:metaticket /staging/metaticket/static/portal/portal.order.datacollector/
# hoặc:
sudo chown -R metaticket:metaticket /opt/metaticket/static/portal/portal.order.datacollector/
```

---

## Module ViClass

> ⚠️ **Lưu ý:** Module viclass là module riêng, KHÔNG nằm trong repo metaticket. Nó được maintain ở repo riêng của ViClass.

Trong repo metaticket, folder `local_modules/viclass/` chỉ chứa reference/symlink cho mục đích development local. Khi deploy viclass module, cần build từ repo viclass riêng và copy output lên server tại:

```
# Staging
/staging/metaticket/static/module/viclass/

# Production
/opt/metaticket/static/module/viclass/
```

---

## Lưu ý quan trọng

1. **Ownership:** Tất cả file PHẢI thuộc `metaticket:metaticket`. Service chạy dưới user metaticket cần quyền đọc các static files này.
2. **Không cần restart service:** Vì đây là static files, sau khi copy xong là có hiệu lực ngay (trừ khi có CDN/cache proxy phía trước). Browser có thể cần hard refresh để lấy file mới.
3. **Output hashing:** Build production (`dist`) sử dụng output hashing (tên file có hash). Điều này giúp tránh cache cũ trên browser, nhưng cũng có nghĩa là file cũ sẽ tích tụ trên server. Nên xóa folder cũ trước khi copy file mới:

   ```bash
   # Xóa file cũ trước khi deploy
   ssh user@<SERVER> "sudo rm -rf /opt/metaticket/static/portal/portal.admin/*"
   ```
4. **Thứ tự build module:** Khi build toàn bộ (`yarn dist`), editors cần build trước vì MFE và WW có thể depend vào editor libraries.
5. **Hai server khác nhau:** Staging và Production nằm trên hai server khác nhau, cần scp đến đúng server.
6. **Portal Seatplanner:** Ngoài portal.admin và portal.order.datacollector, còn có portal.seatplanner. Build bằng `yarn portal.seatplanner:dist`, deploy tương tự các portal khác.

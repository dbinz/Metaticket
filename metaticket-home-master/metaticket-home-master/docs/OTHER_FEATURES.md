# CÁC TÍNH NĂNG KHÁC — TÀI LIỆU TỔNG HỢP

> **Phiên bản:** 1.0  
> **Dự án:** MetaTicket-Home  
> **Cập nhật:** 2026-10-09  
> **Phạm vi:** các tính năng có trên `master` nhưng chưa được mô tả trong [METATICKET_HOME_TECHNICAL_DOCUMENTATION.md](./METATICKET_HOME_TECHNICAL_DOCUMENTATION.md), [NEWS.md](./NEWS.md), [SEARCH.md](./SEARCH.md).  
> **Liên quan:** [DEPLOYMENT.md](./DEPLOYMENT.md)

---

## MỤC LỤC

1. [Trang bình chọn nghệ sĩ khách mời (`/vote/nghe-si-khach-moi`)](#1-trang-bình-chọn-nghệ-sĩ-khách-mời)
2. [Phiếu hoàn tiền chuyển nhầm (`/refund-ticket`)](#2-phiếu-hoàn-tiền-chuyển-nhầm)
3. [Mở cổng thanh toán an toàn: cảnh báo chặn quảng cáo / in-app browser, popup bị chặn, trang kết quả](#3-mở-cổng-thanh-toán-an-toàn)
4. [Widget liên hệ nổi](#4-widget-liên-hệ-nổi)
5. [Tặng vé (chuyển vé điện tử)](#5-tặng-vé-chuyển-vé-điện-tử)
6. [Quản lý đơn hàng phụ kiện (`/quan-ly-don-hang`)](#6-quản-lý-đơn-hàng-phụ-kiện)

---

## 1. TRANG BÌNH CHỌN NGHỆ SĨ KHÁCH MỜI

| Hạng mục | Giá trị |
|----------|---------|
| Mục đích | Chiến dịch **tạm thời**: khách hàng đã đăng nhập bình chọn nghệ sĩ khách mời cho một đêm nhạc (nội dung landing đang viết cho NEU Concert 2026) |
| Route | `/vote/nghe-si-khach-moi` (`routeName.vote`), lazy, **không** gắn `authGuard` (landing công khai, đăng nhập được kiểm khi bấm "Bình chọn ngay") |
| Lối vào | Chỉ qua link trực tiếp — không có menu trên navbar/footer |
| Backend | `service.customer.auth` (`metaticket/kotlin`) — `VoteCtrl`, `VoteRepository`, `VoteOptions` |

### 1.1. Files

```
src/app/
├── pages/vote/vote.component.{ts,html}                 # landing + bảng bình chọn
├── lib/services/vote-service/vote.service.ts           # 3 API
├── lib/entities/request/vote/vote.request.ts           # VoteRequest
├── lib/entities/response/vote/vote.response.ts         # VoteOption, VoteOptionsResponse, VoteMeResponse, VoteSubmitResponse
├── lib/constants/route-name.ts                         # vote: 'vote/nghe-si-khach-moi'
├── lib/constants/api-url.ts                            # getVoteOptions, getMyVote, submitVote
└── app.routes.ts                                       # route vote
```

### 1.2. API

| apiUrl key | HTTP | Path | Auth | Mô tả |
|------------|------|------|------|-------|
| `getVoteOptions` | GET | `public/customer-auth/vote/options` | Không | Danh sách lựa chọn + cấu hình + tổng số người đã vote |
| `getMyVote` | GET | `customer-auth/vote/me` | Có (gateway chèn `X-Customer-User-Id`) | Người dùng hiện tại đã vote chưa, chọn gì |
| `submitVote` | POST | `customer-auth/vote` | Có | Gửi bình chọn |

```typescript
// GET public/customer-auth/vote/options
interface VoteOptionsResponse {
  multi: boolean;            // true = được chọn nhiều
  maxChoicePerVote: number;  // số lựa chọn tối đa mỗi lượt
  totalVotes: number;        // số NGƯỜI đã vote (không phải tổng lượt chọn)
  options: VoteOption[];     // đã sắp theo số vote giảm dần
}
interface VoteOption { id: string; label: string; thumbnailUrl?: string | null; description?: string | null; }

// GET customer-auth/vote/me
interface VoteMeResponse { hasVoted: boolean; optionIds?: string[]; }

// POST customer-auth/vote
interface VoteRequest { optionIds: string[]; }
interface VoteSubmitResponse { message: string; optionIds: string[]; }
```

| HTTP (submit) | Ý nghĩa |
|---------------|---------|
| `200` | "Vote thành công" |
| `400` | Lựa chọn rỗng/không hợp lệ, hoặc vượt `maxChoicePerVote` |
| `401` | Thiếu `X-Customer-User-Id` (chưa đăng nhập) |
| `409` | Đã vote rồi — body trả lại lựa chọn cũ |

### 1.3. Nguồn dữ liệu & cấu hình (backend)

- **Danh sách nghệ sĩ hardcode** trong `service.customer.auth/.../VoteOptions.kt` (`ALL`, `MULTI = true`, `MAX_CHOICE_PER_VOTE = 7`). Đây là nguồn sự thật duy nhất — FE không hardcode danh sách. Đổi nghệ sĩ = sửa file Kotlin + deploy lại `service.customer.auth`.
- **Lưu vote trong Redis**, một hash `customer_vote:responses` (`userId → "optionId1,optionId2"`), ghi bằng `HSETNX` để đảm bảo mỗi tài khoản chỉ vote 1 lần. Dùng một node Redis **riêng**, cấu hình `vote.redisNode` trong `application.conf` (không dùng chung cluster token đăng nhập).
- Thống kê (`getOptions`) cache trong RAM 5 giây.
- Kết quả có phần trăm: `GET public/customer-auth/vote-results`, bảo vệ bằng header `X-Admin-Secret` (giá trị lấy từ `vote.adminSecret` trong `application.conf` của server — **không** ghi vào tài liệu/repo). Comment trong `Application.kt` nhắc tới trang `vote-results.html` dùng endpoint này, nhưng trang đó không có trong repo storefront.
- Ảnh banner landing và ảnh nghệ sĩ là URL tuyệt đối tới asset tĩnh `/assets/neu/concert-2026/...` (banner hardcode trong `vote.component.html`, ảnh nghệ sĩ trong `VoteOptions.kt`).

### 1.4. Luồng FE

1. Vào trang → landing (`showLanding = true`): banner + nội dung giới thiệu + nút **BÌNH CHỌN NGAY**.
2. Bấm nút → chưa đăng nhập: điều hướng `/dang-nhap?returnUrl=/vote/nghe-si-khach-moi`; đã đăng nhập: `loadAll()` = `getMyVote()` rồi `getOptions()`.
3. `multi = false`: mỗi lựa chọn có nút **Vote** gửi ngay. `multi = true` (hiện tại): tick tối đa `maxChoicePerVote`, vượt quá → popup "Bạn chỉ được chọn tối đa N lựa chọn"; nút **Vote (x/N)** sticky cuối màn hình.
4. Sau submit (thành công **hoặc** lỗi) → `loadAll()` lại. Đã vote → chỉ đọc, hiện "XẾP HẠNG NGHỆ SĨ ĐƯỢC YÊU THÍCH NHẤT" (thứ tự theo số vote, badge vàng/bạc/đồng cho top 3, không hiện số vote/phần trăm).

### 1.5. Tắt / gỡ bỏ

**Tắt nhanh (chỉ FE):** xóa entry `routeName.vote` khỏi `app.routes.ts` → URL rơi vào `**` → `/not-found`. Build + deploy storefront.

**Gỡ hẳn FE:** xóa `pages/vote/`, `lib/services/vote-service/` (và export trong `lib/services/index.ts`), `lib/entities/{request,response}/vote/` (và export trong 2 file `index.ts`), key `vote` trong `route-name.ts`, 3 key vote trong `api-url.ts`.

**Gỡ backend (repo `metaticket`, deploy riêng):** bỏ `voteRoutes(...)` trong `Application.kt`, `VoteCtrl`/`VoteRepository` trong `ApplicationDI.kt`, file `VoteOptions.kt`, `route/vote_routing.kt`, DTO vote trong `pojo/request.kt` + `response/response.kt`, `allowHeader("X-Admin-Secret")` (đã đánh dấu TODO) và block `vote { ... }` trong `application.conf`. **Gỡ backend trước FE sẽ làm trang vote lỗi** — gỡ FE trước hoặc cùng lúc.

Dữ liệu vote (hash Redis) không tự xóa — xin ý kiến trước khi xóa/export, đó là dữ liệu thật của chiến dịch.

### 1.6. Lưu ý

- `loadAll()` lồng 2 subscribe **không có `error` handler**: nếu `getMyVote` hoặc `getOptions` lỗi (mạng, 401 do token hết hạn...) trang kẹt ở skeleton loading, không có thông báo.
- Submit lỗi bị nuốt im lặng (chỉ reload). Kết hợp với backend: `VoteRepository.trySaveVote` trả `false` cả khi Redis lỗi → `VoteCtrl` trả `409 "Bạn đã vote rồi"` → người dùng không biết vote chưa được ghi.
- Endpoint `options` công khai và trả thứ hạng → ai cũng xem được bảng xếp hạng qua API, kể cả chưa đăng nhập.

---

## 2. PHIẾU HOÀN TIỀN CHUYỂN NHẦM

| Hạng mục | Giá trị |
|----------|---------|
| Mục đích | Trang tĩnh giúp người **chuyển khoản nhầm** vào tài khoản của đơn vị tự khai thông tin, đọc điều khoản, ký điện tử và tạo **phiếu xác nhận dạng văn bản** để sao chép gửi cho đơn vị |
| Route | `/refund-ticket` — **không** phải route Angular; nginx phục vụ file HTML tĩnh |
| File | `src/app/pages/refund-ticket/index.html` (một file duy nhất: HTML + CSS + JS thuần, không framework) |
| Backend | **Không có.** Trang không gọi API, không lưu, không gửi dữ liệu đi |

> Không liên quan tới hoàn vé/hủy đơn của luồng đặt vé — đây là quy trình thủ công cho giao dịch chuyển nhầm.

### 2.1. Cách được build & phục vụ

- `angular.json` → `architect.build.options.assets` có entry copy `src/app/pages/refund-ticket/index.html` sang `dist/mt-ticket/browser/refund-ticket/index.html`.
- `nginx.conf` (được `Dockerfile` copy vào `/etc/nginx/conf.d/default.conf`):

```nginx
location = /refund-ticket {
    root /usr/share/nginx/html/mt-ticket/browser;
    try_files /refund-ticket/index.html =404;
}
```

Khớp chính xác (`=`) để nginx **không** tự 301 sang `/refund-ticket/` — redirect đó dựng URL tuyệt đối theo scheme/host của container (`http://...`), sai khi đứng sau proxy TLS.

- `nginx.dev.conf` **không** có location này (nhưng file đó cũng không được `Dockerfile` dùng).
- Local `ng serve`: `/refund-ticket` rơi vào router Angular → `/not-found`. Mở trực tiếp `http://localhost:4200/refund-ticket/index.html` để xem.

### 2.2. Nội dung & luồng

1. Form 5 mục: (1) giao dịch chuyển nhầm — số tiền (tự đọc thành chữ), thời điểm, mã giao dịch, ngân hàng/ví, số TK, tên chủ TK, lý do; (2) người yêu cầu — họ tên, **chỉ 4 ký tự cuối** CCCD/hộ chiếu, SĐT, email, tư cách (chủ TK / người được ủy quyền); (3) TK nhận hoàn — chỉ chính TK đã chuyển hoặc TK khác **cùng tên chủ**; (4) điều khoản (12 điều) + 9 ô cam kết/đồng ý bắt buộc; (5) ký bằng cách gõ lại họ tên.
2. Validate phía client (thời điểm không ở tương lai, tên ký khớp, mô tả bắt buộc khi chọn "Lý do khác"...).
3. "Tạo phiếu xác nhận" → sinh mã `HT-YYYYMMDD-XXXX` (4 ký tự ngẫu nhiên từ `crypto.getRandomValues`), thời điểm theo giờ VN, và **"Mã đối chiếu (SHA-256)"** của toàn bộ nội dung (`crypto.subtle`, bỏ qua nếu trình duyệt không hỗ trợ).
4. "Sao chép nội dung phiếu" → `navigator.clipboard.writeText`; có bản văn bản thuần để copy tay. Người dùng tự dán gửi kèm ảnh biên lai qua kênh hỗ trợ.
5. Sửa form sau khi tạo phiếu → phiếu cũ bị ẩn, phải tạo lại.

### 2.3. Cấu hình (`const CONFIG` trong `<script>` của `index.html`)

| Key | Giá trị hiện tại | Dùng ở |
|-----|------------------|--------|
| `company` | `META Ticket` | Điều khoản, cam kết, phiếu ("Đơn vị tiếp nhận") |
| `days` | `07` | Thời hạn hoàn tiền (ngày làm việc) trong điều khoản |
| `channel` | `META Ticket` | Câu hướng dẫn "Dán và gửi qua {channel}" |
| `contact` | email hỗ trợ chung | Điều khoản dữ liệu cá nhân |
| `version` | `v2026` | Phiên bản điều khoản, in trên phiếu |

- `channel` hiện chỉ là **placeholder** (trùng tên đơn vị) — câu hướng dẫn thành "gửi qua META Ticket", không chỉ ra kênh cụ thể (Zalo OA, email, fanpage...). Cần điền kênh thật trước khi phát link cho khách.
- Quy ước của file: giá trị bắt đầu bằng `[` (ví dụ `"[Zalo OA ...]"`) được render với class `ph` (đánh dấu placeholder) để dễ nhận ra chỗ chưa điền.
- Đổi điều khoản: sửa mảng `TERMS`, `COMMITS_A`, `COMMITS_B` — nhớ tăng `version` để phiếu cũ/mới phân biệt được.

### 2.4. Tắt / gỡ bỏ

Xóa entry asset `refund-ticket` trong `angular.json` + block `location = /refund-ticket` trong `nginx.conf` (+ thư mục `src/app/pages/refund-ticket/`). Chỉ xóa block nginx mà giữ asset thì `/refund-ticket` rơi vào `location /` → `try_files $uri $uri/` có thể 301 sang `/refund-ticket/` (lỗi scheme nêu ở 2.1).

### 2.5. Lưu ý

- Tải font từ Google Fonts (request ra ngoài) — chỉ là CSS, không gửi dữ liệu form.
- Dữ liệu chỉ nằm trong trình duyệt; không có lưu nháp, tải lại trang là mất.
- Trang dùng `innerHTML` để dựng điều khoản/ô cam kết nhưng chỉ với nội dung tĩnh trong `CONFIG`/`TERMS` đã qua hàm `esc()`; phiếu kết quả dựng bằng `textContent` — dữ liệu người dùng không đi vào `innerHTML`. Giữ nguyên nguyên tắc này khi sửa.

---

## 3. MỞ CỔNG THANH TOÁN AN TOÀN

Áp dụng cho **cả hai** luồng: đặt vé (`BookingPaymentComponent`) và mua phụ kiện (`AccessoryPaymentComponent` / `AccessoryCompletedComponent`). Mục tiêu: không để `payment/start` chạy khi tab cổng thanh toán không mở được — vì `payment/start` đẩy đơn sang `PaymentProcessing` và giữ ghế/tồn kho, mà luồng vé **chưa có API hủy** cho khách.

### 3.1. Files

| File | Vai trò |
|------|---------|
| `lib/services/blocker-detection/blocker-detection.service.ts` | `BlockerDetectionService.check()` → `{ adBlocker, inAppWebView }` |
| `lib/components/blocker-warning-banner/` | Banner vàng cảnh báo, đặt trên breadcrumb `booking.component.html` và `accessory-checkout.component.html` |
| `lib/services/payment/payment-window.service.ts` | `PaymentWindowService` — mở tab chờ trong user gesture, điều hướng sau |
| `pages/booking/components/booking-payment/` | Popup bị chặn, màn chờ, polling trạng thái (luồng vé) |
| `pages/accessory-checkout/components/accessory-payment/`, `accessory-completed/` | Tương tự cho phụ kiện |
| `pages/payment-result/` | Trang return URL của cổng thanh toán |
| `lib/services/notification/notification.service.ts` | `showBlockingWarning()` — notification không tự tắt |

### 3.2. Phát hiện chặn quảng cáo / in-app browser

- **Ad blocker:** chèn một `div` mồi mang class nằm trong EasyList (`adsbox ad-banner ...`) ra ngoài màn hình, chờ **120ms**, coi là bị chặn nếu `offsetHeight === 0` / `display:none` / `visibility:hidden`. Không gọi mạng. Lỗi khi dò → coi như không chặn (không được làm hỏng luồng thanh toán).
- **In-app WebView:** regex User-Agent `FBAN|FBAV|FB_IAB|Instagram|Zalo|Line/|MicroMessenger|TikTok`.
- **Cố ý không dò popup blocker trước:** gọi `window.open` ngoài user gesture luôn bị chặn → luôn dương tính giả. Popup blocker được phát hiện đúng lúc bấm thanh toán (3.3).

**Banner** (`<blocker-warning-banner>`): chạy `check()` ở `ngOnInit`; hiện khi có ad blocker hoặc in-app WebView; nội dung khác nhau cho hai trường hợp; nút "Tôi đã tắt — Kiểm tra lại" chạy lại `check()`; nút × ẩn đến hết vòng đời component.

### 3.3. Luồng bấm "Thanh toán"

```
startPayment()  (click handler, đồng bộ)
 ├─ đã dò thấy blocker và chưa cảnh báo lần nào?
 │     → showBlockingWarning(...) + dò lại ngầm, RETURN   (bấm lần 2 sẽ đi tiếp)
 ├─ PaymentWindowService.openPlaceholder()   ← window.open('', '_blank') NGAY trong click
 │     null (popup bị chặn) → mở popup "Trình duyệt của bạn đang khóa pop-ups", RETURN (KHÔNG gọi API)
 └─ POST payment/start
       ok  → navigateTo(tab, redirectUrl) → vào màn chờ / bước Hoàn tất (kể cả khi khách đã đóng tab chờ)
       lỗi → đóng tab chờ + notification lỗi
```

- Cờ blocker được **dò sẵn** ở `ngOnInit` (`_refreshBlockerState`) và chỉ đọc lại trong click: mọi `await` trước `window.open` làm mất user activation → bị chặn.
- Tab chờ hiển thị trang HTML "Đang chuyển tới cổng thanh toán, vui lòng chờ..." (`document.write`, bỏ qua nếu WebView không cho ghi).
- Popup bị chặn có 2 nút: **Tiếp tục thanh toán** (`continuePaymentSameTab()` → gọi `payment/start` rồi chuyển **chính tab hiện tại** sang cổng — không thể bị chặn) và **Quay lại**.
- **Mở lại trang thanh toán:** URL cổng được lưu (`BookingService.startPayment(redirectUrl)` / `AccessoryCheckoutService.goToPaymentTracking(redirectUrl)`) để nút "Mở lại trang thanh toán" dùng lại — không gọi lại `payment/start` (đơn đã `PaymentProcessing`, gọi lại sẽ 404).

### 3.4. Màn chờ & polling (luồng vé — `BookingPaymentComponent`)

| Tham số | Giá trị |
|---------|---------|
| Poll `GET payment/fetch-status?id=` | mỗi **3s** cho tới khi `Succeed` / `Failed` / `Cancelled` |
| Lỗi khi poll | thử lại mỗi 3s, tối đa **5** lần (`attempt`) → popup lỗi + `resetPaymentState()` |
| Nút "Tôi chưa thanh toán" | hiện sau **15s** (`_scheduleCancelOption`) |

- `Succeed` → `bookingCompleted()`; `Failed`/`Cancelled` → popup thất bại + `resetPaymentState()`.
- "Tôi chưa thanh toán" → popup xác nhận → `cancelPendingPayment()`: dừng poll, **đóng tab cổng thanh toán** (nếu còn tham chiếu), reset state client và `expireBookingSession()`. **Chỉ reset phía client** — ghế vẫn do backend giữ cho tới khi job phía server xử lý; đóng tab để khách không lỡ trả tiền đơn cũ sau khi đã đặt đơn mới.
- Reload trang khi `isStartedPayment = true` → vào thẳng màn chờ và poll tiếp. Tham chiếu tab cổng thanh toán mất khi reload (không đóng được nữa).
- Luồng phụ kiện: polling nằm ở bước Hoàn tất (3s, không giới hạn số lần) — đã mô tả ở mục `AccessoryCompletedComponent` của tài liệu chính.

### 3.5. Trang kết quả `/payment-result/:gateway/:code/:pD/:pC`

- Route Angular công khai, là return URL cổng thanh toán gọi về (thường mở trong tab cổng thanh toán).
- Component **không đọc từng param**: lấy nguyên phần path sau `/payment-result/` và gọi `GET /api/payment/return/result/{gateway}/{code}/{pD}/{pC}` (`apiUrl.updatePaymentResult`, `PaymentService.updatePaymentResultFromReturnUrl`).
- Response `PaymentRedirectResponse.code` (đọc cả từ body lỗi HTTP): `0` → thành công, `1` → thất bại, `3`/khác (body thành công) → đang xử lý; lỗi không parse được → báo lỗi, đề nghị liên hệ hỗ trợ.
- Sau khi gọi xong luôn đếm ngược **5s** rồi `leaveResultPage()`: tab có `window.opener` (do storefront mở) → `window.close()`; tab không có opener (khách thanh toán ngay trên tab hiện tại khi popup bị chặn) → điều hướng về `/`.
- Tab gốc của storefront không đọc kết quả từ trang này — nó tự biết qua polling (3.4).

### 3.6. Tắt / gỡ bỏ

- Bỏ banner: xóa `<blocker-warning-banner>` ở 2 template. Bỏ cảnh báo trong click: xóa nhánh `_blockerDetected && !_blockerWarningAcknowledged` trong 2 component payment.
- **Không** nên bỏ `PaymentWindowService.openPlaceholder()` trước API — quay lại kiểu `window.open` sau request sẽ bị popup blocker chặn sau khi đơn đã chuyển `PaymentProcessing`.

### 3.7. Lưu ý

- `attempt` của luồng vé không reset khi poll thành công một lần → 5 là tổng số lỗi trong cả phiên chờ, không phải 5 lỗi liên tiếp.
- Ở trang kết quả, nhánh `code === 0` nằm trong **body lỗi HTTP** gọi `expireBookingSession()`, còn nhánh `code === 0` trong body thành công gọi `bookingCompleted()` — hai đường xử lý "thành công" khác nhau.
- Component còn `console.log` debug (`PaymentResultComponent CONSTRUCTOR LOADED!`, đếm ngược).
- Dò ad blocker dựa trên EasyList nên có thể âm tính giả với blocker cấu hình riêng, và dương tính giả nếu CSS khác ẩn các class đó.

---

## 4. WIDGET LIÊN HỆ NỔI

| Hạng mục | Giá trị |
|----------|---------|
| Mục đích | Nút nổi góc màn hình mở danh sách kênh liên hệ: Facebook, Zalo OA, Hotline, Email |
| Vị trí | Gắn **toàn cục** trong `src/app/app.component.html` (`<app-contact-widget>`) — có mặt trên mọi route Angular |
| Files | `lib/components/contact-widget/contact-widget.component.{ts,html,scss}`, `lib/constants/social-contacts.ts` |
| API | Không có |

### 4.1. Hành vi

- Bấm nút → mở/đóng panel; hiệu ứng pulse tắt sau lần mở đầu. Đóng khi click ngoài (`document:mousedown`) hoặc `Escape`.
- Facebook / Zalo → `window.open(url, '_blank')`; Hotline → `tel:`; Email → `mailto:`.
- Bị ẩn bằng CSS toàn cục khi `body` có class `booking-immersive` (bước chọn ghế toàn màn hình — `BookingComponent` bật/tắt class này), xem `src/styles.css`.

### 4.2. Cấu hình

- URL/số/email dùng để **điều hướng** lấy từ `socialContacts` (`lib/constants/social-contacts.ts`).
- Nhãn phụ hiển thị (`sublabel` số hotline, địa chỉ email) lại **hardcode riêng** trong mảng `contacts` của component → đổi số hotline/email phải sửa **cả hai** chỗ.

### 4.3. Tắt / gỡ bỏ

Xóa `<app-contact-widget>` trong `app.component.html` (và import trong `app.component.ts`). Muốn ẩn ở một trang cụ thể: thêm class lên `body` và rule CSS tương tự `booking-immersive`.

---

## 5. TẶNG VÉ (CHUYỂN VÉ ĐIỆN TỬ)

| Hạng mục | Giá trị |
|----------|---------|
| Mục đích | Chủ vé chuyển một **ticket slot** (vé điện tử/QR) cho người khác theo số điện thoại |
| Route | Nằm trong `/quan-ly-ve` (`authGuard`) → chọn đơn (`?orderId=`) → nút **Tặng vé** trên từng vé |
| Backend | `service.ticketslots` (`metaticket/kotlin`) |

### 5.1. Files

```
src/app/pages/ticket/ticket-manager/
├── ticket-manager.component.{ts,html}               # danh sách đơn (gom ticket slot theo orderId)
├── ticket-slot-manager/ticket-slot-manager.component.*   # danh sách vé của 1 đơn: Xem QR, Tặng vé
├── transfer-ticket-slot/transfer-ticket-slot.component.* # popup tặng vé nhiều bước
└── model.ts                                         # TicketSlotByOrderModel
src/app/lib/services/ticket-slots-service/ticket-slots.service.ts
src/app/lib/entities/request/ticket-slot/ticket-slot-request.ts   # TransferTicketSlotRequest
src/app/lib/entities/response/ticket-slot/ticket-slot.response.ts # TicketSlotResponse, TicketSlotStateResponse
src/app/lib/enums/ticket-slot-status.ts              # TicketOptStatus, TicketSlotStatus, StateReason
```

### 5.2. API (tất cả cần đăng nhập)

| # | apiUrl key | HTTP | Path | Body / Query |
|---|------------|------|------|--------------|
| 1 | `getTicketSlots` | GET | `ticket-slots/fetch-by-owner` | `page`, `size` (FE gọi `page=1&size=1000`) |
| 2 | `getTicketSlotQrCode` | GET | `ticket-slots/get-qrcode` | `slotId`, `stateIdx` → base64 PNG (text) |
| 3 | `getTicketSlotsCaptchaSetting` | GET | `ticket-slots/captcha-setting` | → `{ enable, captchaGateway: 'hcaptcha'\|'recaptcha', siteKey }` |
| 4 | `initSessionTicketSlots` | POST | `ticket-slots/transfer/init-session` | `{ captchaToken? }` → set cookie phiên (HttpOnly) |
| 5 | `sendOtpTicketSlots` | POST | `ticket-slots/transfer/send-otp` | `{ slotId }` |
| 6 | `resendOtpTicketSlots` | POST | `ticket-slots/transfer/resend-otp` | `{}` |
| 7 | `verifyOtpTicketSlots` | POST | `ticket-slots/transfer/verify-otp` | `{ slotId, otp }` (response text; JSON `{code, message}` = lỗi) |
| 8 | `transferTicketSlot` | POST | `ticket-slots/transfer` | `TransferTicketSlotRequest` |

```typescript
interface TransferTicketSlotRequest {
  slotId: string;          // id đã Checksum-encode
  currentStateIdx: number; // state hiện tại của slot (chống chuyển trùng)
  version: number;         // optimistic lock
  newOwnerPhone: string;
  newOwnerName: string;
}
```

Hành vi BE chính (đọc từ `TicketSlotsCtrl` local): phiên tặng vé gắn cookie `..._SessionId`; captcha kiểm ở `init-session` khi bật; OTP gửi qua **Zalo ZNS** tới **SĐT của người đang giữ vé** (header `X-Customer-Phone` do gateway chèn), chỉ khi slot thuộc đúng người gọi; không cho tặng cho chính SĐT của mình. Lỗi thường trả **text thuần** (FE hiện thẳng `error.error`).

### 5.3. Luồng FE

1. `/quan-ly-ve`: tải toàn bộ slot (`size=1000`), gom theo `orderId`, gọi `getShowDetail` cho từng show (lỗi → giá trị fallback), sắp theo `lastModified` mới nhất.
2. Chọn đơn → `?orderId=` (giữ được nút Back). Mỗi **state** của slot là một dòng; slot đã chuyển đi có nhãn "Sent".
3. Nút **Tặng vé** bị vô hiệu khi `state.status === 'CHECKED_IN'`, `optStatus === 'CLOSED'` hoặc `state.reason === 'TRANSFERRED_AWAY'`.
4. Popup `TransferTicketSlotComponent` — `step`: `confirm` → `form` (tải captcha setting; tên + SĐT người nhận + nhập lại SĐT + captcha nếu bật) → submit = `init-session` rồi `send-otp` → `otp` (nhập OTP, gửi lại OTP) → `verify-otp` rồi `transfer` → `success` (emit `transferSuccess` → `reloadTicketSlots()`) hoặc `error` (tự đóng sau 30s).
5. **Xem QR** chỉ cho phép khi `optStatus === 'OPENED'`, state không `TRANSFERRED_AWAY`, `status === 'QR_GENERATED'`; `NONE`/`CLOSED` → "QR hiện chưa được kích hoạt...".

### 5.4. Tắt / gỡ bỏ

- Ẩn tính năng trên FE: xóa nút "Tặng vé" và `<app-transfer-ticket-slot>` trong `ticket-slot-manager.component.html` (+ import `TransferTicketSlotComponent`). API phía server vẫn còn — muốn chặn hẳn phải tắt ở `service.ticketslots`.
- Captcha bật/tắt và site key do **backend** cấp qua `captcha-setting` — không cấu hình ở FE.

### 5.5. Lưu ý

- Route `/quan-ly-slot-ve` (`routeName.ticketSlotManager`) trỏ thẳng `TicketSlotManagerComponent`, nhưng component này nhận dữ liệu qua `@Input` từ `TicketManagerComponent` → mở trực tiếp route đó chỉ thấy loading mãi.
- Danh sách vé chỉ tải một lần `size=1000`; khách có > 1000 slot sẽ thiếu vé.
- Popup chỉ kiểm tra "SĐT nhập lại khớp" ở FE; việc người nhận phải có tài khoản hay không do backend quyết định.
- Trong popup còn `console.log` token hCaptcha (`onHcaptchaVerify`) — không gửi đi đâu nhưng in ra console.

---

## 6. QUẢN LÝ ĐƠN HÀNG PHỤ KIỆN

| Hạng mục | Giá trị |
|----------|---------|
| Mục đích | Khách xem lại các đơn **phụ kiện đã thanh toán** của mình (khác "Quản lý vé" là của vé sự kiện) |
| Route | `/quan-ly-don-hang` (`routeName.orderManager`), `authGuard` |
| Lối vào | Menu tài khoản trên navbar, trang Thông tin tài khoản, và bước Hoàn tất của checkout phụ kiện |
| Files | `pages/order-manager/order-manager.component.{ts,html}`, `AccessoryService.getMyAccessoryOrders()`, `CustomerAccessoryOrderItem` (`lib/entities/response/accessory/order.response.ts`) |

### 6.1. API

| apiUrl key | HTTP | Path | Auth | Response |
|------------|------|------|------|----------|
| `getMyAccessoryOrders` | GET | `accessory/order/fetch/mine` | Có (không tiền tố `public/`) | `CustomerAccessoryOrderItem[]` — chỉ đơn `PaymentSucceed` của user đang đăng nhập |

`CustomerAccessoryOrderItem` = `AccessoryOrderResponse` + `id`, `showName`, `bannerImageUrl?`, `createdAt`, `customerUserId?`.

### 6.2. Hành vi

- Danh sách → chọn đơn bằng `?orderId=` (Back được); `orderId` không có trong danh sách → tự xóa query param.
- Nhãn hình thức nhận hàng: `PickupAtShow` → "Nhận tại show", `MetaTicketPickup` → "Nhận tại META Ticket", `Delivery` → "Giao hàng"; giá trị lạ hiện nguyên.
- Lỗi tải → coi như danh sách rỗng (chỉ `console.error`, không báo lỗi trên UI).

### 6.3. Tắt / gỡ bỏ

Xóa route `orderManager` trong `app.routes.ts`, mục "Quản lý đơn hàng" trong `profileMenuItem` của `navbar.component.ts` và link ở `pages/account/account.component.html`; sửa điều hướng `_leaveSuccess(['/', routeName.orderManager])` trong `accessory-completed.component.ts` (nếu không sẽ về `/not-found`).

---

*Tài liệu phản ánh code `master` tại 2026-10-09. Hành vi backend được đọc từ code local của repo `metaticket` — đối chiếu nhánh đang deploy trước khi dựa vào.*

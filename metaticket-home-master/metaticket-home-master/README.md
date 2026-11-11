# MetaTicket Home

MetaTicket Home là **website storefront (trang chủ) hướng tới khách hàng cuối** của hệ thống MetaTicket (metaticket.vn). Đây là điểm chạm đầu tiên của người mua vé: nơi khám phá, tìm kiếm sự kiện và thực hiện toàn bộ quy trình đặt vé.

## Dự án này phục vụ phần nào của hệ thống?

Trong khi repo `metaticket` chứa backend nghiệp vụ và cổng quản trị, dự án này là **lớp giao diện công khai** mà khách hàng truy cập trực tiếp. Chức năng chính:

- **Trang chủ & khám phá sự kiện**: banner nổi bật, danh sách show, lọc theo danh mục/địa điểm/thời gian.
- **Tìm kiếm sự kiện**: tìm theo từ khóa (dữ liệu lấy từ service tìm kiếm `metaticket-homebackend`).
- **Trang chi tiết sự kiện**: thông tin show, session, bảng giá.
- **Quy trình đặt vé nhiều bước**: chọn ghế → xem chính sách → nhập thông tin → thanh toán (VNPay, MoMo...) → xác nhận hoàn tất.
- **Tài khoản khách hàng**: đăng nhập/đăng ký, xác thực OTP, quản lý vé đã mua.

### Liên hệ với các dự án khác

| Dự án | Vai trò trong hệ thống |
| --- | --- |
| `metaticket-home` (dự án này) | Giao diện storefront Angular cho khách hàng |
| `metaticket-homebackend` | Service tìm kiếm sự kiện (Elasticsearch) cấp dữ liệu cho trang chủ |
| `metaticket` | Backend nghiệp vụ (vé, đơn, thanh toán...) + cổng quản trị |

### Công nghệ

- **Framework**: Angular 17+ (Standalone Components)
- **UI**: Ng-Zorro (Ant Design) + TailwindCSS + SCSS
- **State**: Custom Store Pattern với RxJS
- **Package Manager**: Yarn

> 📚 Chi tiết kiến trúc, components, services và luồng đặt vé xem trong [`docs/METATICKET_HOME_TECHNICAL_DOCUMENTATION.md`](./docs/METATICKET_HOME_TECHNICAL_DOCUMENTATION.md).

---

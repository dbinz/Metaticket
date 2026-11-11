# Quy trình Xác thực Người dùng (Authenticate)

## Mục tiêu

Tài liệu này mô tả quy trình xác thực người dùng đã đăng nhập, đảm bảo rằng token truy cập (JWT) của họ là hợp lệ và khớp với token được lưu trữ trong hệ thống.

## Kiến trúc

*   **Client (Mobile/Web Application):** Gửi yêu cầu xác thực kèm theo JWT trong header `Authorization`.
*   **Backend (Kotlin Ktor - service.customer.auth):** Xử lý việc xác minh JWT, kiểm tra với token lưu trữ trong Redis.
*   **Cache (Redis Cluster):** Lưu trữ token truy cập của người dùng.

## Quy trình

1.  **Client gửi yêu cầu xác thực:**
    *   Sau khi người dùng đã đăng nhập, mỗi yêu cầu cần xác thực sẽ được client gửi lên kèm theo `Authorization` header với giá trị là `Bearer <JWT_ACCESS_TOKEN>`.

2.  **Backend xử lý yêu cầu:**
    *   **Trích xuất Token:** Backend nhận yêu cầu và trích xuất JWT access token từ `Authorization` header. Nếu không có token, trả về lỗi `401 Unauthorized`.
    *   **Trích xuất Principal từ JWT:** Backend xác minh và giải mã JWT để lấy thông tin `principal` (thông tin định danh người dùng). Nếu JWT không hợp lệ, trả về lỗi `401 Unauthorized`.
    *   **Lấy thông tin User ID và Phone:** Từ `principal`, backend trích xuất `userId` và `phone`. Nếu thiếu thông tin này, trả về lỗi `401 Unauthorized`.
    *   **Kiểm tra Token trong Redis:** Backend sử dụng `userId` để truy vấn token tương ứng đang được lưu trữ trong Redis. Nếu không tìm thấy token trong Redis, trả về lỗi `401 Unauthorized`.
    *   **So sánh Token:** Backend so sánh token trích xuất từ header của client với token lấy từ Redis. Nếu hai token không khớp, trả về lỗi `401 Unauthorized`.
    *   **Chuẩn hóa Số điện thoại:** Số điện thoại được chuẩn hóa. Nếu không thành công, trả về lỗi `401 Unauthorized` với thông báo "Số điện thoại không đúng, vui lòng kiểm tra lại".
    *   **Thiết lập Response Headers:** Nếu tất cả các bước trên thành công, backend sẽ thêm các header vào response:
        *   `X-Customer-Phone`: Số điện thoại đã được chuẩn hóa của người dùng.
        *   `X-Customer-User-Id`: ID của người dùng.
    *   **Trả về thành công:** Backend trả về mã trạng thái `200 OK` cho client, xác nhận người dùng đã được xác thực thành công.

3.  **Xử lý lỗi:**
    *   **JWTVerificationException:** Nếu có lỗi trong quá trình xác minh JWT (ví dụ: token hết hạn, chữ ký không hợp lệ), backend trả về `401 Unauthorized` với thông báo "Invalid JWT".
    *   **Lỗi hệ thống khác:** Đối với các lỗi không mong muốn khác trong quá trình xử lý, backend trả về `500 InternalServerError` với thông báo "Lỗi hệ thống, vui lòng thử lại sau".

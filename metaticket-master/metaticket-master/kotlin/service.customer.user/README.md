# Quy trình đăng ký người dùng với reCAPTCHA, OTP và Redis Cluster bất đồng bộ

## Mục tiêu

Tài liệu này mô tả quy trình đăng ký người dùng an toàn, sử dụng reCAPTCHA v3, OTP và Redis Cluster bất đồng bộ để tăng cường hiệu suất và khả năng mở rộng.

## Kiến trúc

* **Frontend (Angular/React/Vue/HTML/JavaScript thuần):** Giao diện người dùng để đăng ký, hiển thị reCAPTCHA, nhập OTP.
* **Backend (Kotlin Ktor):** Xử lý xác minh reCAPTCHA, tạo và xác minh OTP, xử lý đăng ký người dùng.
* **Cache (Redis Cluster):** Lưu trữ session ID, trạng thái xác minh reCAPTCHA, mã OTP, sử dụng bất đồng bộ.

## Quy trình

1.  **Tạo Session:**
    * Khi người dùng truy cập trang đăng ký, frontend gửi yêu cầu đến backend để tạo session.
    * Backend tạo một session ID duy nhất (UUID) và lưu vào Redis Cluster với trạng thái "chưa xác minh reCAPTCHA".
    * Backend trả về session ID cho frontend.
2.  **Xác minh reCAPTCHA:**
    * Frontend sử dụng reCAPTCHA v3 để lấy token reCAPTCHA.
    * Frontend gửi token reCAPTCHA và session ID đến backend.
    * Backend xác minh token reCAPTCHA với Google reCAPTCHA API.
    * Nếu xác minh thành công, backend cập nhật trạng thái session trong Redis Cluster thành "đã xác minh reCAPTCHA".
    * Backend trả về kết quả xác minh cho frontend.
3.  **Tạo OTP:**
    * Frontend gửi yêu cầu đến backend để tạo OTP, gửi kèm session ID.
    * Backend kiểm tra trạng thái session trong Redis Cluster. Nếu trạng thái là "đã xác minh reCAPTCHA", backend tạo mã OTP ngẫu nhiên.
    * Backend lưu mã OTP và cập nhật trạng thái session trong Redis Cluster thành "OTP đã gửi".
    * Backend gửi thông báo thành công cho frontend.
4.  **Xác minh OTP:**
    * Frontend hiển thị form để người dùng nhập mã OTP.
    * Frontend gửi mã OTP và session ID đến backend.
    * Backend kiểm tra mã OTP trong Redis Cluster và so sánh với mã người dùng nhập.
    * Nếu mã OTP hợp lệ, backend cập nhật trạng thái session trong Redis Cluster thành "OTP đã xác minh".
    * Backend trả về kết quả xác minh cho frontend.
5.  **Đăng ký người dùng:**
    * Frontend gửi thông tin đăng ký và session ID đến backend.
    * Backend kiểm tra trạng thái session trong Redis Cluster. Nếu trạng thái là "OTP đã xác minh", backend xử lý đăng ký người dùng.
    * Backend xóa session ID khỏi Redis Cluster.
    * Backend trả về kết quả đăng ký cho frontend.

## Cài đặt

### Backend (Kotlin Ktor)

1.  Cài đặt Redis Cluster.
2.  Thêm các dependencies sau vào `build.gradle.kts`:

    ```kotlin
    dependencies {
        implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
        implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
        implementation("io.ktor:ktor-client-core:$ktor_version")
        implementation("io.ktor:ktor-client-cio:$ktor_version")
        implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
        implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
        implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
        implementation("ch.qos.logback:logback-classic:1.2.3")
        implementation("io.ktor:ktor-server-call-logging:$ktor_version")
        implementation("io.lettuce:lettuce-core:6.1.5.RELEASE")
    }
    ```

3.  Thay thế thông tin kết nối Redis Cluster trong mã nguồn.
4.  Thay thế `YOUR_SITE_KEY` và `YOUR_SECRET_KEY` bằng khóa reCAPTCHA của bạn.
5.  Chạy ứng dụng backend.

### Frontend (Angular)

1.  Cài đặt `ng-recaptcha`:

    ```bash
    npm install ng-recaptcha
    ```

2.  Cấu hình `RecaptchaModule` trong `app.module.ts`.
3.  Triển khai logic đăng ký, xác minh reCAPTCHA và OTP trong component.

## Lưu ý

* Điều chỉnh thời gian hết hạn của session ID và OTP cho phù hợp.
* Xử lý lỗi và các trường hợp ngoại lệ.
* Bảo mật Secret Key reCAPTCHA và thông tin nhạy cảm khác.
* Cần kết hợp OTP với các phương pháp xác thực khác để tăng cường bảo mật.
* Đảm bảo Redis Cluster đã được cấu hình đúng cách.
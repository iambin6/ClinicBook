# Khởi động backend trên Render

Render phải triển khai commit chứa các sửa đổi mới. Commit `3981101` vẫn có thao tác
`getCollectionInfoAsync(...).get()` không timeout trong `@PostConstruct` của chatbot.
Nếu Qdrant không phản hồi, khởi tạo ứng dụng có thể bị treo trước khi cổng HTTP sẵn sàng.
Bản sửa hiện tại chỉ gọi Qdrant trong lúc xử lý câu hỏi, có timeout và fallback.

## Cấu hình của image

- Profile mặc định trong Docker là `render`; bind IPv4 `0.0.0.0` và `${PORT:10000}`.
- Bộ nhớ JVM được tính theo giới hạn RAM của container, không đặt heap ban đầu cố định 200 MB.
- MySQL có timeout kết nối/socket hữu hạn và pool tối đa 5 kết nối theo mặc định.
- `GET /health` công khai trả 200 khi ứng dụng sẵn sàng nhận yêu cầu.
- File secret của máy cá nhân bị loại khỏi Docker build context.

Trên dịch vụ Render hiện có, dùng thư mục Dockerfile của backend làm root/context như trước.
Đặt Health Check Path là `/health`. Nếu đã cấu hình `SPRING_PROFILES_ACTIVE` ở dashboard,
đặt thành `render` để dùng profile này. Không bật `qdrant-local` trên Render.

Thiết lập các biến môi trường của dịch vụ:

```properties
PORT=10000
SPRING_PROFILES_ACTIVE=render
SPRING_DATASOURCE_URL=jdbc:mysql://<database-host>:<port>/<database-name>
SPRING_DATASOURCE_USERNAME=<database-user>
SPRING_DATASOURCE_PASSWORD=<database-password>
GOOGLE_CLIENT_ID=<google-client-id>
GOOGLE_CLIENT_SECRET=<google-client-secret>
FACEBOOK_CLIENT_ID=<facebook-client-id>
FACEBOOK_CLIENT_SECRET=<facebook-client-secret>
MAIL_USERNAME=<smtp-user>
MAIL_PASSWORD=<smtp-password>
JWT_SECRET=<jwt-secret>
GEMINI_API_KEY=<gemini-key>
APP_FRONTEND_URL=https://<frontend-domain>
```

`SPRING_DATASOURCE_URL` phải trỏ tới MySQL mà Render có thể truy cập, không dùng `localhost`
của máy phát triển. Giữ các tham số TLS theo yêu cầu của nhà cung cấp database.
Qdrant dùng endpoint Cloud/self-host truy cập được từ Render; xem `CHATBOT_SETUP.md`.

Các thay đổi key trong file local không cập nhật biến môi trường trên Render.
`EXPOSE` và việc xóa build cache không sửa được lệnh gọi bên ngoài đang treo trong bước khởi tạo.

## Kiểm tra image trước khi triển khai

```powershell
docker build -t clinic-booking:render-smoke .
./scripts/Test-RenderStartup.ps1
```

Script tạo MySQL riêng với dữ liệu rỗng và thông tin đăng nhập giả, rồi chạy image backend
với 512 MB RAM, 1 CPU, cổng container 10000 và hostname Qdrant cố ý không tồn tại.
Test yêu cầu `/health` trả 200 rồi xóa các container/volume test. Database đang dùng của
dự án và container Qdrant cục bộ được giữ nguyên.

Sau khi triển khai, xác nhận log có `Tomcat started on port ...` và
`Started ClinicBookingApplication`, rồi kiểm tra `GET /health` trả 200.
Nếu vẫn dừng trước khi mở cổng, cần xem các dòng lỗi sau đó và trạng thái CPU/RAM của instance.

Tham khảo: [Render port binding](https://render.com/docs/web-services#port-binding),
[xử lý lỗi triển khai](https://render.com/docs/troubleshooting-deploys).

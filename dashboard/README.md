# RiceGuard AI Admin Dashboard

Dashboard React/TypeScript dùng API Spring Boot tại `../backend`. Mọi số liệu và thao tác quản trị trên các trang tổng quan, báo cáo, bài viết, phản hồi AI, thư viện bệnh, vùng nguy cơ và vận hành được lấy từ backend. Khi API rỗng hoặc lỗi, trang hiển thị trạng thái tương ứng.

## Chạy và build

1. Chạy backend theo `../backend/README.md`. Cần tài khoản Firebase có custom claim `admin=true` để dùng dashboard.
2. Đặt `VITE_API_BASE_URL` nếu API không ở `http://localhost:8080/api/v1`.
3. Chạy `npm.cmd install`, `npm.cmd run dev` để phát triển hoặc `npm.cmd run build` để tạo `dist/`.
4. Cho phép origin của dashboard trong `CORS_ALLOWED_ORIGINS` của backend.

Để tạo sẵn tài khoản quản trị, đặt `ADMIN_BOOTSTRAP_EMAIL` và `ADMIN_BOOTSTRAP_PASSWORD` (ít nhất 12 ký tự) trong `backend/.env`, rồi khởi động backend. Backend tạo tài khoản Firebase nếu chưa tồn tại và gán custom claim `admin=true`. Nếu email đã tồn tại, mật khẩu hiện có được giữ nguyên. Giữ `backend/.env` ngoài Git; sau khi tạo xong có thể bỏ hai biến này khỏi cấu hình chạy thường ngày.

## API quản trị

- `GET /admin/overview`, `/admin/stats`: thống kê toàn hệ thống, xu hướng bảy ngày và phân bố bệnh.
- `GET /admin/reports`: lọc và phân trang; `PUT /admin/reports/{id}` duyệt; `POST /admin/reports/{id}/remove-content` xóa nội dung và xử lý các báo cáo liên quan.
- `GET /admin/posts`, `/admin/posts/{id}`, `/admin/posts/{id}/comments`: danh sách và chi tiết quản trị; `PUT /admin/posts/{id}/visibility` ẩn hoặc bỏ ẩn.
- `GET /admin/feedbacks`: lọc và phân trang; `PUT /admin/feedbacks/{id}` lưu trạng thái và kết luận, gồm ảnh đính kèm trong DTO.
- `GET /admin/jobs/reminders/preview`: danh sách đến hạn; `POST /admin/jobs/reminders?test=true` giới hạn tối đa 10 thông báo ở backend.
- `GET /admin/operations`: trạng thái và nhật ký; `POST /admin/jobs/risk-refresh`, `/admin/jobs/disease-sync`: chạy tác vụ thật.

Các bản xuất CSV được tạo trong trình duyệt từ toàn bộ trang dữ liệu phù hợp với bộ lọc. Tác vụ làm mới vùng nguy cơ quét lại các lượt quét Firestore để xây dựng bộ đếm `areaStats`; tránh chạy đồng thời với việc ghi lượt quét mới. Thống kê toàn hệ thống và các bộ lọc văn bản hiện phải đọc toàn bộ tập dữ liệu ở backend, cần chuyển sang chỉ mục/truy vấn tổng hợp nếu dữ liệu tăng lớn.

## Kiểm tra

`npm.cmd run build` kiểm tra TypeScript và tạo bản web production. `cd ../backend && .\gradlew.bat test --offline` chạy bộ test backend. Kiểm thử đầu cuối cần Firebase Emulator hoặc môi trường Firebase thử nghiệm cùng tài khoản admin.

## Quản trị mở rộng (mục 27–33)

- `/users`: tìm người dùng theo UID, tên, email, vai trò, địa bàn, xác minh và trạng thái khóa. Chi tiết hiển thị lượt quét, ruộng, người và thời điểm thực hiện thao tác.
- Khóa tài khoản cập nhật Firebase Authentication, thu hồi refresh token và buộc kiểm tra token đã thu hồi trên các yêu cầu sau đó. API không cho admin tự khóa hoặc khóa tài khoản quản trị khác.
- `/diseases`: thêm, sửa, ẩn và bỏ ẩn bệnh. Bản ghi đã biên tập được đánh dấu `adminEdited`; lần đồng bộ seed kế tiếp bỏ qua bản ghi này. Ảnh do admin tải lên được giữ khi cập nhật nội dung.
- `/campaigns`: soạn thông báo, chọn toàn bộ người dùng, tỉnh/thành hoặc các UID cụ thể; xem trước, lưu nháp, gửi và xem lịch sử. Chiến dịch được gửi trong tác vụ nền và lịch sử tự cập nhật trạng thái. Mỗi người chỉ có một thông báo hộp thư theo ID chiến dịch; có thể thử lại chiến dịch lỗi từng phần hoặc tác vụ bị treo quá 15 phút. Số `delivered` là số thông báo đã tạo trong hộp thư; FCM được gửi bất đồng bộ.
- `/posts` và `/feedbacks`: lọc phía backend theo các điều kiện quản trị. Bộ lọc bài viết có tác giả và trạng thái bị báo cáo; phản hồi AI có thời gian và bệnh đề xuất.
- `/audit`: tra cứu lịch sử theo người thực hiện, thao tác, loại và ID đối tượng. Nhật ký lưu các thao tác kiểm duyệt, tài khoản, bệnh, chiến dịch và tác vụ thủ công.

Các tác vụ có tác động thật lên Firebase/Firestore cần được kiểm thử đầu cuối trên môi trường thử nghiệm trước khi dùng ở production. Việc phát thông báo không được chạy trong bộ test tự động để tránh gửi đến người dùng thật.


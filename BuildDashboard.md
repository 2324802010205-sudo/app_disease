# Yêu cầu xây dựng dashboard quản trị RiceGuard AI

## 1. Mục tiêu và phạm vi

Dashboard là ứng dụng web dành cho quản trị viên RiceGuard AI. Dashboard sử dụng REST API của backend Spring Boot hiện có; backend tiếp tục quản lý dữ liệu Firestore, xác thực Firebase và các dịch vụ liên quan. Dữ liệu của từng người dùng như ruộng, nhật ký và lịch sử quét không mặc nhiên trở thành dữ liệu quản trị có thể xem chi tiết.

Các nhóm công việc chính:

1. Theo dõi tình hình hoạt động của hệ thống.
2. Kiểm duyệt bài viết, bình luận và báo cáo vi phạm trong cộng đồng.
3. Xem và xử lý phản hồi về kết quả chẩn đoán AI.
4. Quản lý thư viện bệnh lúa và chuyên gia được xác minh.
5. Xem bản đồ nguy cơ và vận hành tác vụ nhắc lịch.

**Quy ước trạng thái trong tài liệu:**

- **Có API:** Backend đã hỗ trợ tác vụ; cần xây dựng giao diện và tích hợp.
- **Cần API:** Phải bổ sung endpoint hoặc dữ liệu trả về trước khi hoàn thiện chức năng.
- **Một phần:** Làm được một phần với API hiện tại, nhưng chưa đủ quy trình quản trị.

## 2. Cấu trúc menu đề xuất

| Menu | Mức ưu tiên | Trạng thái |
| --- | --- | --- |
| Tổng quan | P1 | Cần API thống kê toàn hệ thống |
| Báo cáo vi phạm | P1 | Có API |
| Bài viết cộng đồng | P1 | Có API cơ bản |
| Phản hồi chẩn đoán AI | P1 | Một phần |
| Thư viện bệnh | P1 | Một phần |
| Người dùng và chuyên gia | P2 | Một phần |
| Bản đồ nguy cơ | P2 | Có API |
| Vận hành | P3 | Có API chạy thử nhắc lịch |

## 3. Chức năng chi tiết

### 3.1. Đăng nhập và phân quyền quản trị

**Giao diện cần làm**

- Trang đăng nhập gồm email, mật khẩu, nút đăng nhập, thông báo lỗi và trạng thái đang xử lý. Có thể thêm liên kết quên mật khẩu dùng API hiện có.
- Bố cục dashboard chung gồm thanh điều hướng, tên tài khoản và nút đăng xuất. Người chưa đăng nhập được chuyển về trang đăng nhập; người đã đăng nhập nhưng không có quyền admin thấy trang “Không có quyền truy cập”.
- Khi tải lại trang, kiểm tra phiên hiện có trước khi hiển thị nội dung quản trị để tránh hiện dữ liệu trong lúc chưa xác thực.

**Luồng xử lý cần làm**

1. Gửi email/mật khẩu tới `POST /api/v1/auth/login`; nhận `idToken`, `refreshToken`, `expiresIn` và thông tin `user`.
2. Kiểm tra `user.admin`. Chỉ chuyển vào dashboard khi quyền admin hợp lệ; backend vẫn xác minh claim trong ID token ở mọi request admin.
3. Gắn `Authorization: Bearer <idToken>` vào các request cần xác thực. Khi token hết hạn, gọi `POST /api/v1/auth/refresh` và thực hiện lại request phù hợp; nếu làm mới thất bại thì đưa về trang đăng nhập.
4. Xử lý rõ lỗi sai thông tin đăng nhập, lỗi mạng, HTTP 401 (phiên không hợp lệ) và HTTP 403 (không có quyền).
5. Đăng xuất bằng `POST /api/v1/auth/logout` nếu còn phiên hợp lệ, sau đó xóa phiên trên trình duyệt. Endpoint này thu hồi các phiên của tài khoản, nên giao diện cần diễn đạt rõ nếu dùng chức năng đăng xuất này.

**Điều kiện hoàn thành:** Tài khoản admin đăng nhập và gọi được API admin; tài khoản thường không vào được trang quản trị; tải lại trang vẫn kiểm tra được phiên; token hết hạn được xử lý; đăng xuất không còn truy cập được các trang cần xác thực.

**Cấu hình:** Backend đã bảo vệ `/api/v1/admin/**` bằng quyền `ADMIN`. Email quản trị được khai báo trong `ADMIN_EMAILS`; sau khi được gán claim, người dùng cần lấy token mới. Khi web và API khác origin, đặt `CORS_ALLOWED_ORIGINS` phù hợp. Không đưa khóa Firebase Admin hoặc khóa dịch vụ vào frontend.

### 3.2. Tổng quan hệ thống — cần API

Hiển thị các thẻ số liệu và biểu đồ theo khoảng thời gian:

- Tổng số người dùng, số tài khoản mới.
- Tổng lượt quét, lượt quét trong kỳ, số lượt đang chờ chẩn đoán nếu có.
- Tổng bài viết, bài viết mới và số báo cáo chưa xử lý.
- Số phản hồi chẩn đoán mới.
- Xu hướng lượt quét theo ngày/tuần/tháng, phân bố bệnh và khu vực có nhiều ca.

**Cần bổ sung:** Endpoint thống kê dành riêng cho admin, ví dụ `GET /api/v1/admin/overview?range=...`. `GET /api/v1/stats` hiện chỉ tính cho người dùng đang đăng nhập, nên không dùng làm số liệu toàn hệ thống. Thống nhất rõ cách tính từng chỉ số và khoảng thời gian trước khi hiển thị.

### 3.3. Báo cáo vi phạm — có API

**Giao diện cần làm**

- Trang danh sách báo cáo gồm các cột: loại (`POST`/`COMMENT`), lý do, người báo cáo, thời gian, trạng thái và nút xem chi tiết. Có bộ lọc `NEW`, `RESOLVED`, `DISMISSED`, `ALL`; phân trang bằng `nextCursor`.
- Trang hoặc ngăn chi tiết cho thấy mô tả báo cáo, `postId`, `targetId`, bài viết gốc và bình luận bị báo cáo nếu có. Với báo cáo bình luận, làm nổi bật đúng bình luận trong danh sách bình luận của bài viết.
- Các nút thao tác: **Bác bỏ báo cáo**, **Đánh dấu đã xử lý**, **Xóa bài viết** hoặc **Xóa bình luận** tùy đối tượng. Nút xóa phải nêu rõ nội dung sắp bị xóa và yêu cầu xác nhận.

**Luồng xử lý cần làm**

1. Gọi `GET /api/v1/admin/reports?status=NEW&limit=...`; khi đổi bộ lọc thì xóa cursor cũ và tải từ đầu. Khi bấm trang tiếp theo thì dùng `nextCursor` trả về.
2. Khi chọn báo cáo, dùng `postId` để gọi `GET /api/v1/posts/{id}`; nếu `type=COMMENT` thì gọi thêm `GET /api/v1/posts/{id}/comments` và tìm `targetId`.
3. Nếu nội dung không vi phạm, gọi `PUT /api/v1/admin/reports/{id}` với `{ "status": "DISMISSED" }`.
4. Nếu nội dung vi phạm, gọi API xóa đúng đối tượng: `DELETE /api/v1/admin/posts/{id}` cho bài viết hoặc `DELETE /api/v1/posts/{id}/comments/{commentId}` cho bình luận. Khi xóa thành công, gọi riêng `PUT /api/v1/admin/reports/{id}` với `{ "status": "RESOLVED" }`.
5. Sau thao tác, cập nhật danh sách và thông báo thành công/thất bại. Nếu bài viết hoặc bình luận đã bị xóa trước đó, vẫn hiển thị báo cáo và cho phép admin kết luận.

**Lưu ý về API hiện tại:** Xóa nội dung **không tự đổi trạng thái báo cáo**. Có thể có nhiều báo cáo cho cùng một nội dung; việc xử lý một báo cáo chưa tự xử lý các báo cáo còn lại. Khi xóa một bình luận gốc, backend cũng xóa các câu trả lời trực tiếp của bình luận đó.

**Điều kiện hoàn thành:** Admin lọc và mở được báo cáo; xem được nội dung còn tồn tại; bác bỏ hoặc xử lý được báo cáo; xóa đúng bài viết/bình luận sau xác nhận; giao diện phản ánh trạng thái mới và xử lý được nội dung đã biến mất.

### 3.4. Bài viết cộng đồng — có API cơ bản

**Giao diện cần làm**

- Trang danh sách bài viết hiển thị tiêu đề, tác giả, chủ đề, thời gian đăng, số lượt thích, số bình luận và ảnh đại diện nếu có. Có ô tìm kiếm từ khóa, lọc chủ đề và nút tải trang tiếp theo.
- Trang chi tiết hiển thị đầy đủ nội dung, tất cả ảnh, thông tin tác giả và danh sách bình luận/câu trả lời. Admin có thể đi từ một báo cáo tới trang này và thấy nội dung đang được báo cáo.
- Nút **Xóa bài viết** và **Xóa bình luận** kèm hộp xác nhận. Sau khi xóa, cập nhật danh sách hoặc quay về trang danh sách; hiển thị lỗi nếu nội dung đã bị xóa.

**Luồng xử lý cần làm**

1. Gọi `GET /api/v1/posts?topic=&q=&cursor=&limit=` để lấy bảng tin; dùng `nextCursor` để tải thêm. Chủ đề hợp lệ hiện gồm `PEST`, `EXPERIENCE`, `VARIETY`, `WEATHER`, `WATER`.
2. Gọi `GET /api/v1/posts/{id}` và `GET /api/v1/posts/{id}/comments` để xem chi tiết. Bình luận đã có cấu trúc câu trả lời; cho phép mở đúng bình luận qua `commentId` khi điều hướng từ báo cáo.
3. Gọi `DELETE /api/v1/admin/posts/{id}` hoặc `DELETE /api/v1/posts/{id}/comments/{commentId}` sau khi admin xác nhận. Backend hiện cho phép admin xóa bình luận qua API cộng đồng.

**Giới hạn hiện tại:** API danh sách là bảng tin cộng đồng, không có bộ lọc riêng theo tác giả, trạng thái kiểm duyệt hoặc số lượt bị báo cáo. Tìm kiếm `q` được lọc trên tập bài viết mà API đang phân trang, nên không nên mô tả là tìm kiếm toàn bộ hệ thống. Admin có thể xóa nhưng không có quyền sửa bài viết của người khác qua API cập nhật hiện tại.

**Điều kiện hoàn thành:** Admin duyệt được bài viết theo chủ đề, mở chi tiết và bình luận, đi từ báo cáo tới đúng nội dung, xóa được nội dung vi phạm và nhận thông báo kết quả rõ ràng.

### 3.5. Phản hồi chẩn đoán AI — một phần

- Xem danh sách phản hồi theo trang và mở chi tiết.
- Hiển thị ảnh quét, mã bệnh AI dự đoán, độ tin cậy, engine, lý do phản hồi, bệnh người dùng đề xuất, mô tả và thời gian gửi.
- Theo dõi trạng thái xử lý; cho phép người phụ trách ghi kết luận và đánh dấu đã xử lý.
- Lọc theo trạng thái, lý do, khoảng thời gian và bệnh được đề xuất.

**API hiện có:** `GET /api/v1/admin/feedbacks?cursor=&limit=` chỉ hỗ trợ lấy danh sách. **Cần bổ sung:** API cập nhật trạng thái/kết luận và bộ lọc. Bản ghi Firestore có `attachments`, nhưng DTO `AdminFeedback` hiện chưa trả danh sách ảnh đính kèm; cần bổ sung nếu dashboard phải xem các ảnh này.

### 3.6. Thư viện bệnh lúa — một phần

- Xem danh sách và tìm kiếm bệnh; lọc theo bộ phận cây, nguyên nhân và mức độ phổ biến.
- Xem tên bệnh, tên khoa học, mô tả, triệu chứng, điều kiện phát sinh, cách phòng ngừa, điều trị và ảnh minh họa.
- Tải ảnh mới để thay ảnh minh họa của bệnh.
- Về sau: thêm bệnh, sửa nội dung và ngừng hiển thị bệnh khi cần.

**API hiện có:** `GET /api/v1/diseases`, `GET /api/v1/diseases/{id}`, `POST /api/v1/admin/diseases/{id}/image`. **Cần bổ sung:** API thêm/sửa/xóa hoặc ẩn bệnh nếu muốn biên tập nội dung trên web. Danh mục bệnh hiện được nạp từ dữ liệu seed, nên cần xác định quy tắc đồng bộ trước khi cho sửa trực tiếp.

### 3.7. Người dùng và chuyên gia — một phần

- Danh sách và tìm kiếm người dùng; xem thông tin cần thiết cho công việc quản trị.
- Lọc người dùng đã được xác minh là chuyên gia.
- Xác minh hoặc hủy xác minh chuyên gia, lưu lại người thao tác và thời gian thay đổi nếu xây dựng quy trình duyệt.
- Nếu cần quản trị tài khoản: khóa/mở khóa và xem trạng thái tài khoản.

**API hiện có:** `PUT /api/v1/admin/users/{uid}/verified-expert` chỉ cập nhật cờ xác minh khi đã biết UID. **Cần bổ sung:** API danh sách/tìm kiếm người dùng, chi tiết hồ sơ dành cho admin và API khóa/mở tài khoản nếu có yêu cầu. Giới hạn dữ liệu trả về ở mức cần thiết cho quản trị.

### 3.8. Bản đồ nguy cơ — có API

- Xem khu vực theo tỉnh/huyện, số ca và mức nguy cơ.
- Lọc theo tỉnh, mã bệnh và số ngày; sắp xếp theo nguy cơ hoặc số ca.
- Xem chi tiết khu vực: các bệnh nổi bật, thời gian ghi nhận gần nhất, giai đoạn lúa và lời khuyên.
- Có thể trình bày trước bằng bảng và biểu đồ; bản đồ địa lý là bước mở rộng giao diện.

**API hiện có:** `GET /api/v1/risk/areas` và `GET /api/v1/risk/areas/info`. Dữ liệu này là số ca từ lượt quét được tổng hợp ẩn danh, **không phải dự báo AI**.

### 3.9. Vận hành — có API giới hạn

- Hiển thị nút chạy thủ công tác vụ nhắc lịch cho quản trị viên phục vụ kiểm thử/vận hành.
- Hiển thị số thông báo đã gửi theo kết quả trả về và yêu cầu xác nhận trước khi chạy.

**API hiện có:** `POST /api/v1/admin/jobs/reminders`. Chức năng này không nên đặt ở trang tổng quan để tránh thao tác nhầm.

### 3.10. Thông báo từ quản trị — cần API, giai đoạn sau

- Soạn thông báo, chọn nhóm người nhận hoặc khu vực, xem trước và gửi.
- Theo dõi trạng thái gửi và lịch sử thông báo.

**Cần bổ sung:** API tạo chiến dịch thông báo, chọn người nhận và lưu lịch sử gửi. API thông báo hiện tại phục vụ hộp thư của từng người dùng; tác vụ nhắc lịch không thay thế chức năng gửi thông báo từ dashboard.

## 4. Các API backend cần ưu tiên bổ sung

| Ưu tiên | API/khả năng cần bổ sung | Phục vụ màn hình |
| --- | --- | --- |
| P1 | Thống kê tổng hợp toàn hệ thống theo thời gian | Tổng quan |
| P1 | Cập nhật trạng thái và kết luận phản hồi chẩn đoán; trả ảnh đính kèm | Phản hồi chẩn đoán AI |
| P2 | Danh sách, tìm kiếm và chi tiết người dùng cho admin | Người dùng và chuyên gia |
| P2 | Thêm/sửa/ẩn bệnh cùng quy tắc đồng bộ dữ liệu seed | Thư viện bệnh |
| P3 | Bộ lọc quản trị nâng cao cho bài viết và báo cáo; nhật ký thao tác | Kiểm duyệt |
| P3 | Tạo và theo dõi thông báo do admin gửi | Thông báo |

## 5. Lộ trình triển khai đề xuất

1. **Giai đoạn 1 — dashboard dùng API hiện có:** Đăng nhập admin; báo cáo vi phạm; bài viết/bình luận; xem phản hồi AI; xem và thay ảnh thư viện bệnh; bản đồ nguy cơ.
2. **Giai đoạn 2 — hoàn thiện quản trị:** API và màn hình tổng quan; xử lý phản hồi AI; danh sách người dùng và luồng xác minh chuyên gia.
3. **Giai đoạn 3 — mở rộng:** Biên tập thư viện bệnh, thông báo từ admin, bộ lọc nâng cao và nhật ký thao tác.

## 6. Nguyên tắc triển khai

- Mọi thao tác đọc/ghi dữ liệu quản trị đi qua Spring Boot API; không truy cập Firestore trực tiếp từ trình duyệt.
- Phân quyền được kiểm tra ở backend cho từng endpoint admin. Với API cộng đồng dùng chung, kiểm tra quyền xóa bài/bình luận hiện có phải tiếp tục được giữ.
- Các danh sách lớn dùng phân trang theo `cursor` và `limit` như API hiện tại. Hiển thị trạng thái tải, danh sách rỗng và lỗi API.
- Các thao tác xóa, thay ảnh và chạy tác vụ vận hành cần xác nhận rõ đối tượng và kết quả.
- Không đưa khóa Firebase Admin, Cloudinary hoặc khóa AI vào mã frontend hay biến môi trường được đóng gói lên web.

## 7. Tệp backend tham chiếu

- `backend/src/main/java/com/riceguard/api/admin/AdminController.java`
- `backend/src/main/java/com/riceguard/api/security/SecurityConfig.java`
- `backend/src/main/java/com/riceguard/api/stats/StatsController.java`
- `backend/src/main/java/com/riceguard/api/community/CommunityController.java`
- `backend/src/main/java/com/riceguard/api/disease/DiseaseController.java`
- `backend/src/main/java/com/riceguard/api/risk/RiskController.java`
- `backend/src/main/java/com/riceguard/api/scan/FeedbackService.java`

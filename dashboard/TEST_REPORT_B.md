# Kết quả kiểm tra Dashboard B (mục 27–33)

Ngày kiểm tra: 27/10/2026 (Asia/Saigon).

| Mục | Phần đã triển khai | Kiểm tra tự động | Kiểm tra với Firebase thật |
|---|---|---|---|
| 27 — Người dùng | API danh sách, lọc theo UID/tên/email/vai trò/địa bàn; phân trang; chi tiết và KPI; trang `/users` | Build web và backend đạt | Chưa chạy |
| 28 — Chuyên gia | Xác minh/hủy; kiểm tra vai trò EXPERT; lưu UID/thời gian người thực hiện; ghi audit | Build và test backend đạt | Chưa chạy |
| 29 — Khóa tài khoản | Firebase disabled, thu hồi refresh token, kiểm tra token đã thu hồi; không tự khóa/admin khác; giao diện khóa/mở | Test tự khóa bị từ chối; build đạt | Chưa chạy |
| 30 — Bệnh lúa | API và giao diện thêm/sửa/ẩn/bỏ ẩn; bệnh ẩn rời API công khai và chẩn đoán; adminEdited bảo vệ bản sửa khi seed đồng bộ | Test mã bệnh sai bị từ chối; build đạt | Chưa chạy |
| 31 — Chiến dịch thông báo | Soạn, chọn toàn bộ/tỉnh/UID, xem trước, lưu nháp, gửi nền, lịch sử, thử lại không tạo trùng hộp thư | Test audience sai; build đạt | Chưa gửi thông báo thử |
| 32 — Lọc quản trị | Bài viết: tác giả/trạng thái/có báo cáo. AI: trạng thái/lý do/thời gian/bệnh đề xuất | Build web và backend đạt | Chưa chạy |
| 33 — Nhật ký | API và trang tra cứu theo actor/action/target; ghi thay đổi tài khoản, kiểm duyệt, bệnh, chiến dịch, vận hành | Build web và backend đạt | Chưa chạy |

| Lệnh kiểm tra | Kết quả |
|---|---|
| `npm.cmd run build` trong `dashboard/` | Đạt, tạo `dist/` |
| `.\gradlew.bat test --offline` trong `backend/` | Đạt, 54 test, 0 failures, 0 errors |
| `git diff --check` | Đạt |

Giới hạn: Chưa chạy kiểm thử đầu cuối vì các thao tác khóa tài khoản, gửi thông báo và ghi dữ liệu sẽ ảnh hưởng Firebase/FCM đang được cấu hình cho ứng dụng. Cần một Firebase Emulator hoặc dự án thử nghiệm và tài khoản admin thử nghiệm để xác nhận luồng thực tế trước khi dùng ở production. FCM chạy bất đồng bộ; trạng thái `delivered` xác nhận thông báo đã tạo trong hộp thư, không xác nhận điện thoại đã nhận push.

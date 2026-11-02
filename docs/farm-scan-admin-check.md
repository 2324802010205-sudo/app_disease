# Kiểm tra ruộng, lượt quét và admin — 02/11/2026

Đã chạy trên phiên đăng nhập đang mở ở Pixel 7. Ảnh chân dung được tạo bằng image_gen; [prompt và đường dẫn tài nguyên](community-avatar-photos.md).

## Dữ liệu mẫu được giữ lại

Các ruộng DEMO dùng để kiểm thử; địa chỉ và ảnh ruộng chỉ là minh họa, không phải vị trí thực của người dùng. Hồ sơ cá nhân không bị thay đổi địa bàn.

| Ruộng | Diện tích | Khu vực | Giống | Giai đoạn | Ngày gieo |
|---|---:|---|---|---|---|
| DEMO - OM5451 Châu Phú | 0.8 ha | Châu Phú, An Giang | OM5451 | Đẻ nhánh | 2026-10-05 |
| DEMO - ST25 Ninh Kiều | 1.2 ha | Ninh Kiều, Cần Thơ | ST25 | Làm đòng | 2026-09-15 |
| DEMO - Đài Thơm 8 Cao Lãnh | 0.6 ha | Cao Lãnh, Đồng Tháp | Đài Thơm 8 | Trổ bông | 2026-08-28 |

Mỗi ruộng có ảnh minh họa trên Cloudinary, thửa mẫu, ghi chú và lịch kiểm tra. Lịch có thể được cập nhật theo kết quả AI khi quét.

## Kết quả quét thực tế

| Nhãn ảnh nguồn | Kết quả AI/backend | Độ tin cậy | Thư mục Cloudinary |
|---|---|---:|---|
| than-la | Cần chụp lại | 0.00% | `riceguard/dev/dataset/khong-xac-dinh` |
| dom-nau | Cần chụp lại | 0.00% | `riceguard/dev/dataset/khong-xac-dinh` |
| bac-la | bac-la | 74.36% | `riceguard/dev/dataset/bac-la` |
| dao-on-la | Cần chụp lại | 0.00% | `riceguard/dev/dataset/khong-xac-dinh` |
| chay-la | Cần chụp lại | 0.00% | `riceguard/dev/dataset/khong-xac-dinh` |
| dom-nau-hep | Cần chụp lại | 0.00% | `riceguard/dev/dataset/khong-xac-dinh` |
| healthy | Cần chụp lại | 0.00% | `riceguard/dev/dataset/khong-xac-dinh` |

Kết quả trên phản ánh lần chạy thử, không phải số đo độ chính xác toàn bộ mô hình. Tất cả 7 ảnh đã lưu thật và còn ở trạng thái **Chưa duyệt** trong kho AI.

## Đối chiếu phía admin

- Tổng lượt quét hệ thống: **6 → 13**.
- Tổng ảnh kho AI: **6 → 13**.
- Ca bệnh trong ngày: **2 → 3**; ảnh cần chụp lại không được tính thành ca bệnh.
- Hồ sơ tài khoản đang mở: **4 ruộng**, **12 lượt quét**.

Đã đọc lại từng bản ghi từ API admin và tra cứu tài nguyên Cloudinary thật: khớp chủ tài khoản, trạng thái, khu vực, nhãn, thư mục và dung lượng ảnh. Dashboard tải đủ ảnh, không ghi nhận lỗi JavaScript.

Admin hiện hiển thị số ruộng trong hồ sơ người dùng; chưa có màn danh sách chi tiết ruộng trên dashboard. Chi tiết các ruộng xem trong app.

## Kiểm thử và minh chứng

- Build Android thành công.
- Luồng tạo 3 ruộng và 7 lượt quét qua giao diện: `LiveFarmScanDemoTest#createDetailedFieldsAndScanRealSamplePhotos` đạt (135 giây).
- Luồng xem lại dữ liệu không tạo thêm bản ghi: `liveFarmPreview=true` đạt (23 giây); số ruộng và lượt quét giữ nguyên.
- Đã sửa nhãn trạng thái chưa có chẩn đoán từ “Chưa quét” thành “Chưa có kết quả”, vì người dùng có thể đã thử quét nhưng ảnh cần chụp lại.

Ảnh chụp cục bộ:

- [Cộng đồng với chân dung mới](../build/community-photo-avatars.png)
- [Danh sách ruộng mẫu](../build/demo-fields-list.png)
- [Chi tiết ruộng mẫu](../build/demo-field-detail.png)
- [Kho ảnh AI trên admin](../build/admin-dataset.png)
- [Chi tiết lượt quét bạc lá](../build/admin-scan-detail.png)
- [Hồ sơ người dùng trên admin](../build/admin-current-user.png)

Mở dashboard đang chạy: [Kho ảnh AI](http://localhost:5173/dataset), [Người dùng](http://localhost:5173/users).


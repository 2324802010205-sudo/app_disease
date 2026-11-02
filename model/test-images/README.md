# Ảnh mẫu để thử quét

Có **28 ảnh JPG đã tải**, bốn ảnh trong mỗi thư mục: sáu bệnh mà `best.pt` hỗ trợ và lá khỏe. Chép một ảnh có tên `mendeley-...` hoặc `uci-...` từ thư mục cần thử vào emulator, rồi chọn ảnh đó khi quét.

| Thư mục | Nhãn |
|---|---|
| `bac-la` | Bạc lá |
| `chay-la` | Cháy lá |
| `dao-on-la` | Đạo ôn lá |
| `dom-nau` | Đốm nâu |
| `dom-nau-hep` | Đốm nâu hẹp |
| `than-la` | Than lá |
| `la-khoe` | Lá khỏe |

Mỗi thư mục con có README ghi lớp model, nguồn ảnh và số lượng ảnh đã tải. Ảnh có nhãn gốc; model có thể dự đoán khác nhãn đó. Nguồn và ghi công nằm trong [ATTRIBUTION.md](ATTRIBUTION.md). Thư mục `_unverified` giữ riêng một ảnh có sẵn nhưng chưa xác nhận được bệnh/nguồn; bỏ qua ảnh đó khi test.

## Chép ảnh lên emulator

Trong Android Studio mở **Device Explorer**, vào `/sdcard/Pictures/`, tạo thư mục theo mã bệnh nếu muốn giữ ảnh gọn, rồi kéo ảnh vào. Mở app và chọn ảnh từ thư viện khi quét. Nếu màn quét chỉ mở camera, có thể dùng **Extended Controls → Camera → Virtual Scene/Webcam** hoặc kéo ảnh vào cửa sổ emulator để thêm ảnh vào thư viện.


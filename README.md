# RiceGuard AI

Ứng dụng Android phát hiện bệnh lá lúa bằng ảnh, kèm backend Spring Boot. App **không dùng dữ liệu mẫu**: mọi màn hình lấy dữ liệu từ backend qua REST API.

```
project_final/
├─ app/        Ứng dụng Android (Java, XML, API 24+; build bằng Android Studio)
└─ backend/    REST API Spring Boot 3.5 / Java 17  →  Firestore, Firebase Auth, Cloudinary, FCM, Open-Meteo, Claude (AI)
```

## Chạy thử nhanh (không cần khóa thật)

1. **Backend + dữ liệu giả lập** (xem chi tiết ở `backend/README.md`):
   ```bash
   firebase emulators:start --only auth,firestore --project demo-riceguard   # cửa sổ 1
   cd backend && ./gradlew bootRunLocal                                      # cửa sổ 2  (Windows: gradlew.bat bootRunLocal)
   ```
2. **App**: mở thư mục gốc bằng Android Studio, chạy trên máy ảo. Mặc định app gọi `http://10.0.2.2:8080` (máy tính chủ nhìn từ máy ảo).
   Điện thoại thật: đặt IP máy tính vào `gradle.properties` (`riceguard.apiBaseUrl=http://192.168.1.10:8080`), máy tính và điện thoại cùng Wi-Fi.
3. Đăng ký tài khoản trong app rồi dùng bình thường. Chưa có AI thì ảnh vẫn được lưu và kết quả hiện “Đang chờ phân tích” (xem `DIAGNOSIS_PROVIDER` trong `backend/README.md`).

> **Android 17**: kết nối tới địa chỉ mạng nội bộ (10.0.2.2, IP LAN) cần quyền `ACCESS_LOCAL_NETWORK`. Bản debug tự xin quyền khi mở app;
> nếu lỡ từ chối, cấp lại bằng `adb shell pm grant com.example.project_final android.permission.ACCESS_LOCAL_NETWORK`.

## Đăng nhập Google và chế độ khách
* **Google**: bật Google trong Firebase Authentication, lấy *Web client ID* rồi đặt `riceguard.googleWebClientId=...` vào `gradle.properties` (và thêm SHA-1 của khóa ký app vào Firebase Console).
  Chưa đặt thì bấm nút Google sẽ báo chưa cấu hình. Đăng nhập bằng Google mà tài khoản chưa có → app chuyển sang màn **Hoàn tất đăng ký** (điền sẵn tên, email).
* **Khách**: “Tiếp tục với khách” chỉ cho xem (thời tiết, thư viện bệnh, bản đồ nguy cơ, đọc cộng đồng). Quét, bình luận, thích, lưu, ruộng, thông báo, hồ sơ
  đều hiện lời mời đăng nhập/đăng ký (`BaseActivity.requireAccount`). Màn hình mới cho khách vào thì khai báo `allowsGuest()`.

## Khi có khóa Firebase / Cloudinary
Chỉ cần điền biến môi trường cho backend (không sửa code, không commit khóa): `FIREBASE_CREDENTIALS_FILE`, `FIREBASE_WEB_API_KEY`, `CLOUDINARY_URL`, `ANTHROPIC_API_KEY`
(xem `backend/.env.example`). Sau đó chạy `./gradlew bootRun` và trỏ `riceguard.apiBaseUrl` của app tới địa chỉ HTTPS của backend.
Còn thiếu cho đến khi có `google-services.json`: thông báo đẩy FCM trên máy (backend đã hỗ trợ `POST /users/me/devices`).

## Cấu trúc app (`app/src/main/java/com/example/project_final`)
| Thư mục | Vai trò |
|---|---|
| `api/` | Retrofit + OkHttp: `RiceApi` (toàn bộ endpoint), `ApiClient`, tự làm mới token (`TokenAuthenticator`), lỗi chuẩn hóa (`ApiError`), phiên đăng nhập (`Session`) |
| `model/` | Lớp dữ liệu khớp DTO của backend |
| `util/` | Định dạng (`Fmt`), nhãn/màu trạng thái (`Look`), tải ảnh (`Img`, `Uploads`, `ImageUploader`, `ImagePicker`), danh mục lựa chọn (`Meta`) |
| `view/` | `NavBar` (thanh điều hướng nổi), `StateLayout`, `ZoomImageView` |
| `*Activity` | Mỗi màn hình một Activity, kế thừa `BaseActivity` (gọi API gắn vòng đời, lớp chờ, xử lý hết phiên) |

### Gỡ lỗi từ app
* Logcat, tag **`RiceApi`**: mọi yêu cầu/phản hồi (header `Authorization` bị ẩn), lỗi ghi kèm mã `X-Request-Id`; dùng mã đó tìm log tương ứng ở backend.
* Lỗi từ backend luôn có thông điệp tiếng Việt và hiển thị bằng toast hoặc lỗi dưới ô nhập (`Forms.showFieldErrors`).

## Giao diện
* Cỡ chữ dùng thang `@dimen/text_N` (`res/values*/dimens.xml`), tự nhỏ đi 1 sp trên màn hình hẹp (< 360 dp) và lớn lên trên máy tính bảng. Không ghi cỡ chữ trực tiếp trong layout.
* Cỡ chữ hệ thống được tôn trọng tới tối đa 1,3 lần (`BaseActivity.MAX_FONT_SCALE`) để bố cục không vỡ.
* Ô chữ/nút dùng `minHeight` + `wrap_content` thay vì chiều cao cố định; thanh điều hướng nổi chừa chỗ bằng `@dimen/nav_clearance`.
* Giao diện tối/ngôn ngữ tiếng Anh chưa hỗ trợ nên đã ẩn khỏi màn Cài đặt.

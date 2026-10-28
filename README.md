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
* **Google**: dùng Web client ID trong `riceguard.googleWebClientId` và Android app cùng Firebase project với backend. SHA-1/SHA-256 của debug keystore hiện đã đăng ký. Với bản release, đăng ký thêm SHA của khóa ký phát hành trong Firebase trước khi phân phối.
  Đăng nhập bằng Google mà tài khoản chưa có → app chuyển sang màn **Hoàn tất đăng ký** (điền sẵn tên, email).
* **Khách**: “Tiếp tục với khách” chỉ cho xem (thời tiết, thư viện bệnh, bản đồ nguy cơ, đọc cộng đồng). Quét, bình luận, thích, lưu, ruộng, thông báo, hồ sơ
  đều hiện lời mời đăng nhập/đăng ký (`BaseActivity.requireAccount`). Màn hình mới cho khách vào thì khai báo `allowsGuest()`.

## Khi có khóa Firebase / Cloudinary
Chỉ cần điền biến môi trường cho backend (không sửa code, không commit khóa): `FIREBASE_CREDENTIALS_FILE`, `FIREBASE_WEB_API_KEY`, `CLOUDINARY_URL`, `ANTHROPIC_API_KEY`
(xem `backend/.env.example`). Sau đó chạy `./gradlew bootRun` và trỏ `riceguard.apiBaseUrl` của app tới địa chỉ HTTPS của backend.

## Firebase trên Android và thông báo đẩy

* `app/google-services.json` đã được tải từ Android app của Firebase project hiện tại và được `.gitignore` bỏ qua. Khi dựng workspace mới có cùng service account cục bộ, chạy `node fetch-firebase-config.cjs` để tải lại cấu hình và kiểm tra SHA debug. Không đưa service account hoặc `google-services.json` vào Git.
* Sau khi đăng nhập, app lấy FCM token và gọi `POST /users/me/devices`. Khi token đổi, `RiceMessagingService` đăng ký lại. Khi đăng xuất, app gọi `DELETE /users/me/devices`. Android 13+ xin quyền thông báo sau đăng nhập; thông báo ở foreground mở hộp thư khi chạm.
* Kiểm thử đầu cuối cần backend đang chạy, thiết bị có Google Play services, tài khoản Google, và thông báo thật được gửi từ backend. Bản release cần HTTPS và SHA của khóa ký phát hành.

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
* Màn Cài đặt cho chọn theo hệ thống/sáng/tối và tiếng Việt/English; lựa chọn được lưu trên máy và đồng bộ với hồ sơ khi API thành công. Bảng màu ban đêm nằm trong `res/values-night`.
* Bản dịch English hiện có cho màn đăng nhập, đăng ký và Cài đặt. Các màn hình khác vẫn còn chuỗi tiếng Việt trong layout và mã Java, cần chuyển tiếp sang `strings.xml` để hoàn tất bản địa hóa toàn ứng dụng.

# RiceGuard AI

Ứng dụng Android phát hiện bệnh lá lúa bằng ảnh, kèm backend Spring Boot. App **không dùng dữ liệu mẫu**: mọi màn hình lấy dữ liệu từ backend qua REST API.

```
project_final/
├─ app/         Ứng dụng Android (Java, XML, API 24+; build bằng Android Studio). Gradle gốc ở thư mục này (settings.gradle.kts)
├─ backend/     REST API Spring Boot 3.5 / Java 17  →  Firestore, Firebase Auth, Cloudinary, FCM, Open-Meteo, mô hình AI
├─ dashboard/   Dashboard quản trị React + TypeScript (Vite), gọi API của backend
├─ model/       Mô hình nhận dạng bệnh lá lúa: weights/best.pt (YOLOv9c) + dịch vụ suy luận FastAPI. Xem model/README.md
├─ scripts/     Công cụ chạy/cấu hình: RunDemo.cmd (chạy demo Windows), demo/ (dữ liệu giả lập), fetch-firebase-config.cjs
└─ docs/        Tài liệu: BuildDashboard.md (yêu cầu dashboard), AnhGiaoDien.docx (ảnh giao diện), reference/
```

**Tìm lỗi ở đâu:** đăng nhập/Google → `app/.../GoogleAuth.java`, `util/GoogleSignIn.java` và `backend/.../auth/`; chẩn đoán AI → `model/service/` (mô hình) và `backend/.../diagnosis/` (cổng gọi + quy đổi kết quả); số liệu dashboard → `backend/.../admin/` và `dashboard/src/pages/`; cấu hình Firebase → `app/google-services.json` (không commit).

## Các lệnh cần chạy (môi trường thật: Firebase + Cloudinary)

Cần: JDK 17, Node 20+, Android Studio (SDK + một emulator, ví dụ Pixel 7), Python 3.10+ (chỉ cho dịch vụ AI), `backend/.env` và `backend/firebase-service-account.local.json` (hai file này không commit; mẫu ở `backend/.env.example`), `app/google-services.json` (xem mục Firebase bên dưới).

| # | Việc | Lệnh | Ghi chú |
|---|---|---|---|
| 1 | Backend API (cổng 8080) | `cd backend && ./gradlew bootRun` (Windows: `gradlew.bat bootRun`) | Đọc `.env`; kiểm tra: `curl localhost:8080/actuator/health` |
| 2 | Dịch vụ AI `best.pt` (cổng 8000) | `cd model && python -m venv .venv && source .venv/bin/activate && pip install -r requirements.txt && MODEL_API_TOKEN=<cùng DIAGNOSIS_HTTP_TOKEN> uvicorn service.main:app --port 8000` | Cần `DIAGNOSIS_PROVIDER=http` trong `backend/.env`; chi tiết ở `model/README.md`. Mặc định suy luận ở 640 px (`MODEL_IMGSZ`) |
| 3 | Dashboard quản trị | `cd dashboard && npm install && npm run dev` | Mở `http://localhost:5173`, đăng nhập bằng `ADMIN_BOOTSTRAP_EMAIL`/`PASSWORD` trong `.env` |
| 4 | Dựng app Android | `./gradlew :app:assembleDebug` (Windows: `gradlew.bat :app:assembleDebug`) | APK ở `app/build/outputs/apk/debug/app-debug.apk` |
| 5 | Cài app lên emulator | `adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk` | Hoặc bấm Run trong Android Studio |
| 6 | Chép ảnh thử vào máy ảo | `adb -s emulator-5556 push model/test-images /sdcard/Pictures/test-images` | Rồi chạy quét media (dưới đây) để bộ chọn ảnh thấy ảnh |
| 7 | Chạy test backend | `cd backend && ./gradlew test` | Dùng Firestore emulator, xem `backend/README.md` |
| 8 | Chạy test AI | `cd model && pytest` | |

Quét media để bộ chọn ảnh của emulator thấy ảnh vừa chép:

```bash
for f in $(adb -s emulator-5556 shell ls /sdcard/Pictures/test-images | tr -d '\r'); do
  adb -s emulator-5556 shell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/test-images/$f"
done
```

**Ảnh thư viện bệnh (Cloudinary).** Ảnh được tải lên qua dashboard (Thư viện bệnh → chọn bệnh → đổi ảnh) hoặc bằng API, và lưu ở folder Cloudinary `riceguard/<APP_ENV>/library/diseases`:

```bash
TOKEN=$(curl -s -H 'Content-Type: application/json' -d '{"email":"<admin>","password":"<mật khẩu>"}' localhost:8080/api/v1/auth/login | python -c "import sys,json;print(json.load(sys.stdin)['idToken'])")
curl -H "Authorization: Bearer $TOKEN" -F "file=@anh.jpg" localhost:8080/api/v1/admin/diseases/<ma-benh>/image   # ma-benh: bac-la, dao-on-la, dom-nau, ...
```

Ảnh đang dùng cho thư viện bệnh lấy từ bộ ảnh thử trong `model/test-images` (CC BY 4.0) và một ảnh khô vằn (miền công cộng, Wikimedia Commons, tệp `RiceSheathArk.jpg`); nguồn nằm ở `model/test-images/ATTRIBUTION.md`. Rỉ sắt và lùn xoắn lá dùng ảnh minh họa có sẵn trong app (`drawable-nodpi/img_saved_5.png`, `img_saved_6.png`, đã cắt viền và phóng lớn; độ phân giải thấp, nên thay bằng ảnh rõ hơn khi có). Bệnh chưa có ảnh (đạo ôn cổ bông) hiện biểu tượng mầm lúa thay thế cho tới khi quản trị viên tải ảnh lên.

## Chạy thử nhanh (không cần khóa thật)

0. **Windows, một cú nhấp**: chạy `scripts\RunDemo.cmd` (dựng emulator, backend, dữ liệu mẫu và dashboard). Hoặc làm tay theo các bước dưới.
1. **Backend + dữ liệu giả lập** (xem chi tiết ở `backend/README.md`):
   ```bash
   firebase emulators:start --only auth,firestore --project demo-riceguard   # cửa sổ 1
   cd backend && ./gradlew bootRunLocal                                      # cửa sổ 2  (Windows: gradlew.bat bootRunLocal)
   ```
2. **App**: mở thư mục gốc bằng Android Studio, chạy trên máy ảo. Mặc định app gọi `http://10.0.2.2:8080` (máy tính chủ nhìn từ máy ảo).
   Điện thoại thật: đặt IP máy tính vào `gradle.properties` (`riceguard.apiBaseUrl=http://192.168.1.10:8080`), máy tính và điện thoại cùng Wi-Fi.
3. Đăng ký tài khoản trong app rồi dùng bình thường. Chưa có AI thì ảnh vẫn được lưu và kết quả hiện “Đang chờ phân tích” (xem `DIAGNOSIS_PROVIDER` trong `backend/README.md`).
4. **Bật chẩn đoán bằng `best.pt`**: chạy dịch vụ trong `model/` rồi đặt `DIAGNOSIS_PROVIDER=http` cho backend (các bước trong `model/README.md`).

> **Android 17**: kết nối tới địa chỉ mạng nội bộ (10.0.2.2, IP LAN) cần quyền `ACCESS_LOCAL_NETWORK`. Bản debug tự xin quyền khi mở app;
> nếu lỡ từ chối, cấp lại bằng `adb shell pm grant com.example.project_final android.permission.ACCESS_LOCAL_NETWORK`.

## Đăng nhập Google và chế độ khách
* **Google**: dùng Web client ID trong `riceguard.googleWebClientId` và Android app cùng Firebase project với backend. SHA-1 của keystore dùng để ký bản đang chạy phải có trong Firebase Console > Project settings > Your apps (lấy bằng `keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android`); sai SHA thì đăng nhập Google báo lỗi ngay trên máy. Với bản release, đăng ký thêm SHA của khóa ký phát hành trong Firebase trước khi phân phối.
  Đăng nhập bằng Google mà tài khoản chưa có → app chuyển sang màn **Hoàn tất đăng ký** (điền sẵn tên, email).
* **Khách**: “Tiếp tục với khách” chỉ cho xem (thời tiết, thư viện bệnh, bản đồ nguy cơ, đọc cộng đồng). Quét, bình luận, thích, lưu, ruộng, thông báo, hồ sơ
  đều hiện lời mời đăng nhập/đăng ký (`BaseActivity.requireAccount`). Màn hình mới cho khách vào thì khai báo `allowsGuest()`.

## Khi có khóa Firebase / Cloudinary
Chỉ cần điền biến môi trường cho backend (không sửa code, không commit khóa): `FIREBASE_CREDENTIALS_FILE`, `FIREBASE_WEB_API_KEY`, `CLOUDINARY_URL`, `ANTHROPIC_API_KEY`
(xem `backend/.env.example`). Sau đó chạy `./gradlew bootRun` và trỏ `riceguard.apiBaseUrl` của app tới địa chỉ HTTPS của backend.

## Firebase trên Android và thông báo đẩy

* `app/google-services.json` (đúng vị trí này, nếu để ở thư mục gốc thì plugin google-services không được áp dụng và FCM không khởi tạo) tải từ Android app của Firebase project hiện tại và được `.gitignore` bỏ qua. Khi dựng workspace mới có cùng service account cục bộ, chạy `node scripts/fetch-firebase-config.cjs` để tải lại cấu hình và kiểm tra SHA debug. Không đưa service account hoặc `google-services.json` vào Git.
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

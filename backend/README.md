# RiceGuard AI – Backend (Spring Boot)

REST API cho ứng dụng Android RiceGuard AI: tài khoản, ruộng lúa, quét bệnh bằng ảnh, nhật ký, cộng đồng, thông báo, thời tiết, bản đồ nguy cơ, thống kê.

| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ / framework | Java 17, Spring Boot 3.5 (Web, Validation, Security, Actuator, Cache) |
| Cơ sở dữ liệu | **Firebase Firestore** |
| Đăng nhập | **Firebase Authentication** (email/mật khẩu), backend xác thực Bearer ID token |
| Lưu ảnh | **Cloudinary** (kiểm tra định dạng, nén, giới hạn 1600 px) |
| Thông báo đẩy | Firebase Cloud Messaging |
| Thời tiết | Open-Meteo (miễn phí, không cần khóa) |
| Chẩn đoán bệnh từ ảnh | Claude (vision) **hoặc** mô hình riêng qua HTTP **hoặc** tắt (`none`) |
| Tài liệu API | Swagger UI tại `/swagger-ui.html` |

## 1. Kiến trúc

```
Android app ──HTTPS + Bearer ID token──▶ Spring Boot ──▶ Firestore (dữ liệu)
                                              ├────────▶ Firebase Auth (tài khoản)
                                              ├────────▶ Cloudinary (ảnh)
                                              ├────────▶ FCM (thông báo đẩy)
                                              ├────────▶ Open-Meteo (thời tiết)
                                              └────────▶ Claude / mô hình AI (chẩn đoán)
```

* App **không** truy cập Firestore trực tiếp (`firestore.rules` chặn tất cả); mọi thao tác qua backend nên kiểm soát được quyền và dữ liệu.
* Dữ liệu riêng của người dùng nằm dưới `users/{uid}/...` nên người này không thể đọc dữ liệu người khác.
* Bản đồ nguy cơ đọc các **bộ đếm ẩn danh** `areaStats/{tỉnh}__{huyện}__{ngày}`, không đọc dữ liệu cá nhân.

### Cấu trúc Firestore

```
users/{uid}                       hồ sơ, cài đặt, token FCM
  ├─ fields/{id}                  ruộng lúa
  ├─ scans/{id}                   lượt quét + kết quả chẩn đoán
  ├─ diaries/{id}                 nhật ký canh tác
  ├─ notifications/{id}
  ├─ savedPosts/{postId}  savedDiseases/{diseaseId}
  └─ drafts/{id}                  bản nháp bài viết
diseases/{code}                   danh mục bệnh (nạp từ src/main/resources/seed/diseases.json)
posts/{id}                        bài viết cộng đồng
  ├─ comments/{id}                bình luận + trả lời
  └─ likes/{uid}
areaStats/{tỉnh}__{huyện}__{yyyyMMdd}   bộ đếm ca bệnh theo khu vực
feedbacks/{id}  reports/{id}  locks/{key}
```

## 2. Thiết lập dịch vụ ngoài

### Firebase
1. [Firebase Console](https://console.firebase.google.com) → tạo project.
2. **Build → Authentication → Sign-in method** → bật **Email/Password**.
3. **Build → Firestore Database** → *Create database* (chế độ production, chọn vùng gần Việt Nam, ví dụ `asia-southeast1`).
4. **Project settings → Service accounts → Generate new private key** → tải file JSON (**giữ bí mật, không commit**).
   Chuyển thành biến môi trường: `FIREBASE_CREDENTIALS=$(base64 -w0 service-account.json)` hoặc đặt `FIREBASE_CREDENTIALS_FILE=/đường/dẫn/file.json`.
5. **Project settings → General → Web API Key** → `FIREBASE_WEB_API_KEY` (dùng cho đăng nhập bằng mật khẩu).
6. Triển khai rules và index (bắt buộc, nếu thiếu sẽ có lỗi `FAILED_PRECONDITION: The query requires an index`):
   ```bash
   npm i -g firebase-tools && firebase login
   firebase deploy --only firestore --project <project-id>
   ```
   File: `firestore.rules` (chặn truy cập trực tiếp), `firestore.indexes.json` (2 index cho bài viết + chỉ mục collection group cho job nhắc lịch).

### Đăng nhập Google
Firebase Console → Authentication → Sign-in method → bật **Google**. Backend đổi Google ID token lấy phiên Firebase qua `accounts:signInWithIdp`
(Firebase tự kiểm tra chữ ký với Google), nên **không cần thêm cấu hình nào ở backend**. Phần Android cần **Web client ID**: mở rộng mục Google vừa bật
→ *Web SDK configuration* → copy *Web client ID* và đặt `riceguard.googleWebClientId=...` trong `gradle.properties` của app (xem README gốc).

### Cloudinary
[Console](https://console.cloudinary.com) → Dashboard → copy **API Environment variable** vào `CLOUDINARY_URL=cloudinary://KEY:SECRET@CLOUD_NAME`.
Ảnh được lưu ở `riceguard/<APP_ENV>/users/<uid>/<loại>/...`; khi xóa dữ liệu hoặc tài khoản, ảnh tương ứng bị xóa theo.

### Chẩn đoán AI (`DIAGNOSIS_PROVIDER`)
| Giá trị | Hành vi |
|---|---|
| `none` (mặc định) | Lưu ảnh và trả `status=PENDING`; chạy `POST /scans/{id}/diagnose` khi đã có AI. |
| `claude` | Gửi ảnh (đã thu nhỏ) cho Claude, nhận JSON có cấu trúc (mã bệnh, độ tin cậy, triệu chứng, khuyến nghị). Cần `ANTHROPIC_API_KEY`; model mặc định `claude-opus-5-5` (đổi bằng `DIAGNOSIS_CLAUDE_MODEL`). Mỗi lượt quét tính phí theo bảng giá API. |
| `http` | `POST DIAGNOSIS_HTTP_URL` với `{imageUrl, stage, variety, weather}`, trả JSON dạng `DiagnosisResult` (xem `diagnosis/DiagnosisResult.java`). Dùng khi nhóm tự huấn luyện mô hình. |

Quy tắc kết quả: ảnh không phải lúa hoặc độ tin cậy < 0.5 → `NEEDS_RETAKE` (kèm hướng dẫn chụp lại), mã bệnh không có trong danh mục bị từ chối. Mức rủi ro: `NORMAL` (khỏe), `WATCH` (nhẹ), `HIGH` (vừa/nặng).

> Kết quả AI chỉ mang tính tham khảo; khuyến nghị thuốc luôn nhắc đọc nhãn và hỏi cán bộ bảo vệ thực vật.

## 3. Chạy

Yêu cầu: JDK 17.

```bash
cp .env.example .env      # điền giá trị, rồi nạp vào môi trường shell:
set -a; source .env; set +a
./gradlew bootRun          # http://localhost:8080  (Swagger: /swagger-ui.html)
```

### Chạy cục bộ không cần dịch vụ thật (Firebase Emulator)
Cần Node.js và JDK 21+ cho emulator (backend vẫn chạy bằng JDK 17).
```bash
npm i -g firebase-tools
firebase emulators:start --only auth,firestore --project demo-riceguard   # cửa sổ 1, giữ nguyên
./gradlew bootRunLocal                                                    # cửa sổ 2: http://localhost:8080
```
`bootRunLocal` tự đặt biến môi trường cho emulator, lưu ảnh vào `uploads/` (không cần Cloudinary) và đặt `PUBLIC_BASE_URL=http://10.0.2.2:8080`
để máy ảo Android mở được ảnh. Muốn thử chẩn đoán AI cục bộ, đặt thêm `DIAGNOSIS_PROVIDER=http` + `DIAGNOSIS_HTTP_URL` (hoặc `claude` + `ANTHROPIC_API_KEY`) trước khi chạy.
Dữ liệu emulator mất khi tắt emulator; đó là điều mong muốn khi phát triển.

### Gỡ lỗi
* **Mã yêu cầu**: mọi phản hồi có header `X-Request-Id`; app Android tự gửi mã này (tag logcat `RiceApi`). Mã hiện trong mọi dòng log máy chủ dạng `INFO [3f2a9c1d]`,
  nên chỉ cần tìm mã đó là thấy toàn bộ vòng đời một thao tác.
* **Nhật ký mỗi yêu cầu** (logger `riceguard.request`): `GET /api/v1/fields 200 35ms uid=abc`. Lỗi 4xx ghi WARN, 5xx ghi ERROR kèm stack trace ở `GlobalExceptionHandler`.
* Tăng chi tiết: `LOG_LEVEL=DEBUG ./gradlew bootRunLocal`.
* Lỗi trả về luôn cùng một dạng `{code, message, status, path, timestamp, fields?}` (xem `GlobalExceptionHandler`), thông điệp tiếng Việt hiển thị thẳng cho người dùng.
* Swagger UI tại `/swagger-ui.html` để thử từng endpoint (nút Authorize dán `idToken`).
* Sức khỏe: `/actuator/health`.

### Test
```bash
./gradlew test    # test đơn vị; test tích hợp tự bỏ qua nếu chưa đặt FIRESTORE_EMULATOR_HOST
firebase emulators:exec --only auth,firestore --project demo-riceguard "./gradlew test"   # chạy cả test tích hợp
```

### Triển khai
Docker: `docker build -t riceguard-api . && docker run -p 8080:8080 --env-file .env riceguard-api`
(phù hợp Cloud Run, Render, Railway, Fly.io). Probe sức khỏe: `/actuator/health/liveness` và `/readiness`.
Production nên: dùng HTTPS (nền tảng thường lo), đặt `APP_ENV=prod`, `PUBLIC_BASE_URL`, cân nhắc `OPENAPI_ENABLED=false`.

## 4. API chính (`/api/v1`)

Mọi endpoint (trừ ghi chú *công khai*) cần header `Authorization: Bearer <idToken>`. Lỗi trả JSON `{code, message, status, path, timestamp, fields?}` với thông điệp tiếng Việt.
Danh sách phân trang: `?limit=&cursor=` → `{items, nextCursor}` (`nextCursor` rỗng = hết). Thời gian là epoch mili-giây UTC.

| Nhóm | Endpoint |
|---|---|
| Xác thực (*công khai*) | `POST /auth/register`, `/auth/login`, `/auth/google` (`{idToken, mode: LOGIN\|REGISTER}`; LOGIN mà chưa có tài khoản → 404 `GOOGLE_NOT_REGISTERED`), `/auth/refresh`, `/auth/forgot-password`; `POST /auth/change-password`, `/auth/logout` |
| Hồ sơ | `GET/PUT/DELETE /users/me`, `POST /users/me/avatar`, `PUT /users/me/settings`, `POST/DELETE /users/me/devices` (token FCM) |
| Ruộng | `GET/POST /fields`, `GET/PUT/DELETE /fields/{id}`, `POST /fields/{id}/photo` |
| Quét bệnh | `POST /scans` (multipart `image`, `fieldId?`, `stage?`, `variety?`, `note?`, `province?`, `district?`, `lat?`, `lng?`), `GET /scans?range=7d\|30d\|all&risk=&fieldId=`, `GET/DELETE /scans/{id}`, `POST /scans/{id}/diagnose`, `PUT /scans/{id}/note`, `GET /scans/compare?first=&second=`, `POST /scans/{id}/feedback` |
| Thư viện bệnh (*công khai*) | `GET /diseases?part=&cause=&q=`, `GET /diseases/{id}`; lưu: `PUT/DELETE /diseases/{id}/save`, `GET /users/me/saved-diseases` |
| Nhật ký | `GET/POST /diary`, `GET/PUT/DELETE /diary/{id}` |
| Cộng đồng (khách xem được `GET /posts`, `/posts/{id}`, `/posts/{id}/comments`) | `GET/POST /posts?topic=&q=`, `GET/PUT/DELETE /posts/{id}`, `PUT/DELETE /posts/{id}/like`, `/save`, `POST /posts/{id}/report`, bình luận `GET/POST /posts/{id}/comments`, `DELETE …/{commentId}`, `PUT/DELETE …/{commentId}/like`; `GET /users/me/posts`, `/saved-posts`, `/drafts` (+ `POST /users/me/drafts/{id}/publish`) |
| Thông báo | `GET /notifications?filter=all\|unread\|alert\|reminder\|community\|tip`, `PUT /notifications/{id}/read`, `PUT /notifications/read-all`, `GET /notifications/unread-count`, `DELETE /notifications/{id}` |
| Thời tiết (*công khai*) | `GET /weather?province=Thái Bình` hoặc `?lat=&lng=` (hiện tại, 6 mốc giờ, 7 ngày, lời khuyên canh tác) |
| Bản đồ nguy cơ (*công khai*) | `GET /risk/areas?province=&disease=&days=7&sort=risk\|count`, `GET /risk/areas/info?province=&district=` |
| Thống kê | `GET /stats?range=week\|month` |
| Ảnh | `POST /media/upload?kind=avatar\|field\|scan\|diary\|post\|feedback`, `GET /media/upload-signature`, `DELETE /media/{publicId}` |
| Danh mục lựa chọn (*công khai*) | `GET /meta/options` (giống lúa, giai đoạn, chủ đề… cho dropdown) |
| Quản trị (`admin`) | `GET /admin/reports`, `PUT /admin/reports/{id}`, `DELETE /admin/posts/{id}`, `PUT /admin/users/{uid}/verified-expert`, `GET /admin/feedbacks`, `POST /admin/diseases/{id}/image`, `POST /admin/jobs/reminders` |

**Ảnh đính kèm bài viết/nhật ký/phản hồi:** app tải ảnh trước bằng `POST /media/upload` (nhận `{url, publicId}`), rồi truyền mảng đó vào `images`. Server chỉ chấp nhận ảnh nằm trong thư mục của chính người dùng, thuộc Cloudinary của hệ thống.

**Đăng nhập trên Android** có hai cách: (a) gọi `POST /auth/login` nhận `idToken`/`refreshToken`, hết hạn sau ~1 giờ thì gọi `/auth/refresh`; hoặc (b) dùng Firebase Auth SDK rồi gửi `user.getIdToken()` làm Bearer — backend chấp nhận cả hai.

**Quyền admin:** đặt `ADMIN_EMAILS=a@x.com,b@y.com`; tài khoản trùng email sẽ được gán custom claim `admin` khi đăng ký hoặc khi server khởi động (tài khoản phải đăng nhập lại để token mới có claim). Nhãn "Chuyên gia" chỉ hiện khi admin gọi `verified-expert`.

## 5. Vận hành & giới hạn cần biết

* **Nhắc lịch**: mỗi 07:00 (giờ Việt Nam) gửi thông báo cho ruộng có `nextCheckDate` = hôm nay; khóa trong Firestore nên chạy nhiều instance không bị gửi trùng. Sau mỗi lần quét, ngày kiểm tra tiếp theo tự đặt (5/7/14 ngày theo mức rủi ro).
* **Rate limit** theo phút lưu trong bộ nhớ từng instance (120 yêu cầu/người dùng, 20 yêu cầu/IP cho `/auth`); cần giới hạn chặt hơn thì đặt thêm ở API gateway.
* **Bản đồ nguy cơ** = số lượt quét phát hiện bệnh trong N ngày gần nhất ở từng huyện (ngưỡng ở `riceguard.risk.*`), chưa phải mô hình dự báo.
* **Tìm kiếm bài viết** lọc trong bộ nhớ trên các bài gần nhất; nếu cộng đồng lớn nên thêm Algolia/Typesense.
* **Tọa độ thời tiết** có sẵn cho ~29 tỉnh trồng lúa (`weather/Provinces.java`); ruộng có `lat/lng` sẽ dùng tọa độ riêng.
* Xóa tài khoản (`DELETE /users/me`) xóa dữ liệu Firestore, ảnh Cloudinary và tài khoản Firebase; bài viết cộng đồng đã đăng hiển thị "Người dùng đã xóa" nếu chưa xóa riêng.

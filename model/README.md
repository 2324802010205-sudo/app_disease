# Mô hình nhận dạng bệnh lá lúa (`best.pt`)

`weights/best.pt` là YOLOv9c (ultralytics, detect, huấn luyện ở ảnh 768 px, 11 lớp; dịch vụ suy luận chạy mỗi ảnh ở 640 px và 800 px (có TTA) rồi gộp kết quả, vì một lần chạy đơn lẻ rất dao động: trên 28 ảnh mẫu có nhãn, ảnh bị báo "không thấy gì" giảm từ 10 xuống 4; đổi bằng `MODEL_IMGSZ` và `MODEL_AUGMENT`). `service/` bọc nó thành một dịch vụ HTTP mà backend gọi qua `DIAGNOSIS_PROVIDER=http`.

```
model/
├─ weights/best.pt       trọng số đã huấn luyện (không commit, xem .gitignore)
├─ class_map.json        tên lớp của mô hình -> mã bệnh trong thư viện bệnh của backend
├─ service/
│  ├─ main.py            FastAPI: /diagnose, /predict, /health
│  └─ postprocess.py     gộp các phát hiện thành kết quả chẩn đoán (thuần Python, test không cần torch)
└─ tests/                pytest cho postprocess + class_map
```

## Chạy

```bash
cd model
python -m venv .venv && source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu   # có GPU thì cài bản CUDA
pip install -r requirements.txt
MODEL_API_TOKEN=doi-token-nay uvicorn service.main:app --port 8000
```

Nối với backend (`backend/.env`):

```
DIAGNOSIS_PROVIDER=http
DIAGNOSIS_HTTP_URL=http://localhost:8000/diagnose
DIAGNOSIS_HTTP_TOKEN=doi-token-nay        # cùng giá trị MODEL_API_TOKEN
```

Dịch vụ phải tải được `imageUrl` mà backend gửi (ảnh lưu local thì `PUBLIC_BASE_URL` phải truy cập được từ máy chạy dịch vụ).

Trên máy phát triển Windows: chạy `scripts\RunModel.cmd` (dùng WSL, Python ở `~/riceguard-venv`, token lấy từ `backend/.env`) rồi mới chạy backend. Dịch vụ tắt thì ảnh quét vào `dataset/cho-phan-tich` và được phân tích lại khi mở lượt quét đó.

### Chạy thẳng trong backend Java (`DIAGNOSIS_PROVIDER=onnx`, không cần Python lúc chạy)

Backend có sẵn engine ONNX Runtime (`backend/.../diagnosis/OnnxDiagnosisEngine.java`) chạy chính `best.pt` đã xuất sang ONNX, cùng quy tắc với dịch vụ Python (`YoloPostprocess.java` là bản chuyển của `postprocess.py`). Chỉ cần Python **một lần** để xuất file:

```bash
bash model/export-onnx.sh        # tạo model/weights/best.onnx (~100 MB, không commit; chạy lại khi đổi best.pt)
# backend/.env:  DIAGNOSIS_PROVIDER=onnx        (không cần DIAGNOSIS_HTTP_URL / token)
```

Cấu hình tùy chọn (mặc định trùng dịch vụ Python): `DIAGNOSIS_ONNX_MODEL` (`../model/weights/best.onnx`), `DIAGNOSIS_ONNX_CLASS_MAP`, `DIAGNOSIS_ONNX_IMGSZ` (`640,800`), `DIAGNOSIS_ONNX_AUGMENT` (`true`), `DIAGNOSIS_ONNX_RUN_CONF` (0.05), `DIAGNOSIS_ONNX_CONF` (0.15). Đường dẫn tương đối tính từ thư mục chạy backend (`backend/`).

Độ ổn định: Java tái hiện đúng tiền xử lý của ultralytics (letterbox hình chữ nhật nhỏ nhất `rect=True`, không phải hình vuông; TTA ba tỉ lệ có lật; NMS theo lớp). Test `YoloOnnxParityTest` so với kết quả Python lưu ở `backend/src/test/resources/yolo-parity.json` trên 28 ảnh mẫu: cùng kết luận bệnh 28/28, độ tin cậy lệch trung bình ~0.002 (tối đa ~0.012; do cv2 và Java làm tròn khác nhau khi thu phóng ảnh). Đổi `best.pt` hay thông số thì sinh lại file đó từ dịch vụ Python. Dịch vụ Python vẫn giữ nguyên làm đường lui (`DIAGNOSIS_PROVIDER=http`).

## Gỡ lỗi

* `GET /health`: trọng số có tồn tại không, mô hình đã nạp chưa (nạp ở yêu cầu đầu tiên, vài giây).
* `curl -F file=@anh.jpg localhost:8000/predict`: trả kết quả chẩn đoán **kèm toàn bộ phát hiện thô** (lớp, độ tin cậy, diện tích), để xem mô hình thấy gì.
* Backend: logcat tag `RiceApi` có `X-Request-Id`; lỗi gọi dịch vụ AI ghi ở log backend với dòng "Chẩn đoán thất bại".

## Cách quy đổi kết quả

| Tình huống | Kết quả gửi backend |
|---|---|
| Không phát hiện gì | `riceLeaf=false` → app báo chụp lại. Lưu ý: ảnh lá khỏe đôi khi cũng không ra phát hiện nào (lớp `healthy` ít khi kích hoạt), nên bị xếp vào nhóm này |
| Chỉ lớp `healthy` | `healthy`, `severity=NONE` |
| Có vùng bệnh | bệnh có độ tin cậy cao nhất; lá khỏe không thắng bệnh |
| Lớp bệnh chưa có trong thư viện (`null` trong `class_map.json`) | `unknown` → app báo chưa xác định |

* Mô hình không có lớp "không phải lá lúa": ảnh lạ thường không ra phát hiện nào, nhưng ảnh có hình lá/lúa (logo, tranh) vẫn có thể bị nhận nhầm.
* **Mức độ nặng** là heuristic theo diện tích khung bệnh / diện tích ảnh: < 5 % LOW, 5–20 % MEDIUM, ≥ 20 % HIGH (`postprocess.py`). Chưa được kiểm chứng với chuyên gia, cần hiệu chỉnh khi có dữ liệu thật.
* **Triệu chứng / khuyến nghị** không nằm trong mô hình: backend điền từ thư viện bệnh (`HttpDiagnosisEngine.enrich`), nên admin sửa thư viện là app thấy ngay.
* Backend còn có ngưỡng riêng `DIAGNOSIS_MIN_CONFIDENCE` (mặc định 0.35, `application.yml`); dưới ngưỡng này lượt quét thành "cần chụp lại". Trước đây là 0.5, mức mà `best.pt` hiếm khi đạt: trên 28 ảnh mẫu 0.5 chỉ cho 9 kết quả (sau khi gộp nhiều lần chạy; trước đó chỉ 3) và 15 ảnh bị báo chụp lại, còn 0.35 cho 12 đúng, 7 sai, 9 chụp lại. Kết quả dưới 0.6 kèm lời nhắc đối chiếu thêm. Ngưỡng giữ phát hiện của dịch vụ sau khi gộp là `MODEL_CONF=0.15`.

## Ánh xạ lớp → bệnh

Tên lớp đọc trực tiếp từ `best.pt` (11 lớp, thứ tự chữ cái): `Brown spot`, `Leaf Blight`, `Leaf Scald`, `Leaf blast`, `Leaf smut`, `Narrow brown spot`, `Rice Bacterial Blight`, `Rice Blast`, `Rice Brownspot`, `bacterial leaf blight`, `healthy`. Dữ liệu huấn luyện là `rice-leaf-2` (gộp nhiều bộ dữ liệu) nên có 3 cặp trùng nghĩa, được gộp về cùng một mã bệnh:

| Lớp trong `best.pt` | Mã bệnh trong thư viện |
|---|---|
| `Brown spot`, `Rice Brownspot` | `dom-nau` (Đốm nâu) |
| `Leaf blast`, `Rice Blast` | `dao-on-la` (Đạo ôn lá) |
| `Rice Bacterial Blight`, `bacterial leaf blight`, `Leaf Blight` | `bac-la` (Bạc lá) |
| `Leaf Scald` | `chay-la` (Cháy lá) |
| `Leaf smut` | `than-la` (Than lá) |
| `Narrow brown spot` | `dom-nau-hep` (Đốm nâu hẹp) |
| `healthy` | `healthy` |

`Leaf Blight` được ánh xạ sang **bạc lá** là một giả định (trong bộ dữ liệu gốc nó đi cùng các lớp bạc lá do vi khuẩn); cần chuyên gia xác nhận.
Các bệnh `aiDetectable: true` trong `backend/src/main/resources/seed/diseases.json` chính là sáu bệnh trên; app hiện "AI RiceGuard nhận diện được" ở chi tiết bệnh.
Khô vằn, rỉ sắt, lùn xoắn lá, đạo ôn cổ bông, lem lép hạt có trong thư viện để đọc nhưng **mô hình này không nhận ra** chúng.
Thêm lớp mới vào mô hình: thêm bệnh vào thư viện, thêm dòng vào `class_map.json` (test `test_every_mapped_code_exists_in_disease_library` bắt quên mã).

## Ảnh người dùng → cải tiến mô hình

Mỗi ảnh quét, sau khi AI chẩn đoán, backend **chuyển** (không nhân đôi) ảnh vào thư mục theo nhãn AI trên Cloudinary:

```
riceguard/{env}/dataset/dom-nau/   dao-on-la/   bac-la/   chay-la/   than-la/   dom-nau-hep/   la-khoe/
riceguard/{env}/dataset/khong-xac-dinh/   ảnh không xác định: không phải lá lúa, độ tin cậy thấp hơn ngưỡng, hoặc lớp chưa có trong thư viện
riceguard/{env}/dataset/cho-phan-tich/    dịch vụ AI chưa bật hoặc lỗi; chẩn đoán lại thì ảnh đổi sang thư mục đúng
```

Mỗi ảnh có tag `dataset`, `ai-{nhãn}` và context `scan_id`, `uid`, `ai_label`, `confidence`, `engine`, `status` (lọc trong Media Library hoặc Admin API). 
Quy trình cải tiến: mở trang **Kho ảnh AI** trên dashboard, duyệt từng thư mục (AI đúng, sửa nhãn hoặc loại ảnh), dùng thư mục Không xác định làm nguồn ảnh cần gán nhãn mới, xuất CSV ảnh đã duyệt (cột nhãn đúng) rồi huấn luyện lại. Ảnh đã duyệt hiện trong `riceguard/{env}/verified/{nhãn đúng}` trên Cloudinary. Phản hồi "kết quả sai" của người dùng nằm ở collection `feedbacks` (kèm `scanDiseaseCode` và `suggestedDiseaseCode`) để lọc ảnh nghi sai.
Xóa tài khoản xóa luôn ảnh của người đó trong các thư mục này. Cần có thông báo/đồng ý của người dùng về việc dùng ảnh để cải tiến mô hình trước khi phát hành.

## Biến môi trường

`MODEL_WEIGHTS`, `MODEL_CLASS_MAP`, `MODEL_IMGSZ` (`640,800`, danh sách kích thước, mỗi cỡ một lần chạy), `MODEL_AUGMENT` (1 = bật TTA), `MODEL_RUN_CONF` (0.05, ngưỡng thô mỗi lần chạy), `MODEL_CONF` (0.15, sau khi gộp), `MODEL_API_TOKEN`, `MODEL_ALLOWED_HOSTS` (danh sách host ảnh được phép, phân tách bằng dấu phẩy; nên đặt khi triển khai để dịch vụ không bị dùng làm cầu tải URL tùy ý), `MODEL_MAX_IMAGE_BYTES`, `MODEL_FETCH_TIMEOUT`.

## Kiểm thử

```bash
cd model && pytest        # 16 test logic, không cần torch/GPU
```

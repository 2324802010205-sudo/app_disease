# Mô hình nhận dạng bệnh lá lúa (`best.pt`)

`weights/best.pt` là YOLOv9c (ultralytics, detect, huấn luyện ở ảnh 768 px, 11 lớp). `service/` bọc nó thành một dịch vụ HTTP mà backend gọi qua `DIAGNOSIS_PROVIDER=http`.

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

* **Mức độ nặng** là heuristic theo diện tích khung bệnh / diện tích ảnh: < 5 % LOW, 5–20 % MEDIUM, ≥ 20 % HIGH (`postprocess.py`). Chưa được kiểm chứng với chuyên gia, cần hiệu chỉnh khi có dữ liệu thật.
* **Triệu chứng / khuyến nghị** không nằm trong mô hình: backend điền từ thư viện bệnh (`HttpDiagnosisEngine.enrich`), nên admin sửa thư viện là app thấy ngay.
* Backend còn có ngưỡng riêng `min-confidence: 0.5` (`application.yml`); dưới ngưỡng này lượt quét thành "cần chụp lại". Ngưỡng giữ phát hiện của dịch vụ là `MODEL_CONF=0.25`.

## Ánh xạ lớp → bệnh (cần chuyên gia xác nhận)

11 lớp của `best.pt` có 3 cặp trùng nghĩa (`Brown spot`/`Rice Brownspot`, `Leaf blast`/`Rice Blast`, `Rice Bacterial Blight`/`bacterial leaf blight`) do gộp nhiều bộ dữ liệu; chúng được gộp về cùng một mã bệnh. `Leaf Blight` được ánh xạ sang **bạc lá** (`bac-la`) — đây là giả định.
`Leaf Scald`, `Leaf smut`, `Narrow brown spot` chưa có trong thư viện bệnh nên trả `unknown`. Muốn app nhận ra chúng: thêm bệnh trong dashboard (trang Thư viện bệnh), rồi đổi `null` thành mã bệnh trong `class_map.json`.

## Biến môi trường

`MODEL_WEIGHTS`, `MODEL_CLASS_MAP`, `MODEL_IMGSZ` (768), `MODEL_CONF` (0.25), `MODEL_API_TOKEN`, `MODEL_ALLOWED_HOSTS` (danh sách host ảnh được phép, phân tách bằng dấu phẩy; nên đặt khi triển khai để dịch vụ không bị dùng làm cầu tải URL tùy ý), `MODEL_MAX_IMAGE_BYTES`, `MODEL_FETCH_TIMEOUT`.

## Kiểm thử

```bash
cd model && pytest        # 16 test logic, không cần torch/GPU
```

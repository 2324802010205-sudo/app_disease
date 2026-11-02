"""
Dịch vụ suy luận cho best.pt (YOLOv9c, 11 lớp bệnh lá lúa), gắn vào backend qua DIAGNOSIS_PROVIDER=http.

  POST /diagnose   JSON {imageUrl, stage, variety, weather} -> DiagnosisResult  (backend gọi)
  POST /predict    multipart file=<ảnh>  -> kết quả + toàn bộ phát hiện thô      (tự thử / gỡ lỗi)
  GET  /health     trạng thái mô hình
"""
from __future__ import annotations

import hmac
import io
import json
import logging
import os
import threading
from pathlib import Path
from typing import Optional
from urllib.parse import urlparse

import httpx
from fastapi import Depends, FastAPI, File, Header, HTTPException, UploadFile
from PIL import Image, UnidentifiedImageError
from pydantic import BaseModel

from .postprocess import Detection, to_diagnosis

log = logging.getLogger("riceguard.model")
ROOT = Path(__file__).resolve().parent.parent

WEIGHTS = Path(os.getenv("MODEL_WEIGHTS", ROOT / "weights" / "best.pt"))
CLASS_MAP = Path(os.getenv("MODEL_CLASS_MAP", ROOT / "class_map.json"))
IMGSZ = int(os.getenv("MODEL_IMGSZ", "640"))             # đo trên 28 ảnh mẫu: 640 đúng 11/28, 768 chỉ 6/28
CONF = float(os.getenv("MODEL_CONF", "0.25"))             # ngưỡng giữ phát hiện
API_TOKEN = os.getenv("MODEL_API_TOKEN", "")               # trùng DIAGNOSIS_HTTP_TOKEN của backend; rỗng = không kiểm tra
ALLOWED_HOSTS = {h.strip().lower() for h in os.getenv("MODEL_ALLOWED_HOSTS", "").split(",") if h.strip()}
MAX_BYTES = int(os.getenv("MODEL_MAX_IMAGE_BYTES", str(12 * 1024 * 1024)))
FETCH_TIMEOUT = float(os.getenv("MODEL_FETCH_TIMEOUT", "15"))

app = FastAPI(title="RiceGuard model service")
_model = None
_lock = threading.Lock()   # ultralytics không an toàn khi gọi song song trên cùng một model


def class_map() -> dict:
    with open(CLASS_MAP, encoding="utf-8") as f:
        return {k: v for k, v in json.load(f).items() if not k.startswith("_")}


def get_model():
    global _model
    if _model is None:
        if not WEIGHTS.is_file():
            raise HTTPException(503, f"Không thấy file trọng số: {WEIGHTS}")
        from ultralytics import YOLO  # nạp muộn để import module nhanh và test logic không cần torch
        with _lock:
            if _model is None:
                _model = YOLO(str(WEIGHTS))
                log.info("Đã nạp %s, lớp: %s", WEIGHTS, _model.names)
    return _model


def require_token(authorization: Optional[str] = Header(default=None)) -> None:
    if not API_TOKEN:
        return
    supplied = authorization[7:] if authorization and authorization.lower().startswith("bearer ") else ""
    if not hmac.compare_digest(supplied.encode(), API_TOKEN.encode()):
        raise HTTPException(401, "Sai hoặc thiếu token")


def fetch_image(url: str) -> Image.Image:
    parsed = urlparse(url)
    if parsed.scheme not in ("http", "https") or not parsed.hostname:
        raise HTTPException(400, "imageUrl phải là http(s)")
    if ALLOWED_HOSTS and parsed.hostname.lower() not in ALLOWED_HOSTS:
        raise HTTPException(400, "Máy chủ ảnh không nằm trong MODEL_ALLOWED_HOSTS")
    try:
        with httpx.stream("GET", url, timeout=FETCH_TIMEOUT, follow_redirects=False) as r:
            if r.status_code != 200:
                raise HTTPException(502, f"Tải ảnh thất bại (HTTP {r.status_code})")
            buf = io.BytesIO()
            for chunk in r.iter_bytes():
                buf.write(chunk)
                if buf.tell() > MAX_BYTES:
                    raise HTTPException(413, "Ảnh quá lớn")
    except httpx.HTTPError as e:
        raise HTTPException(502, f"Không tải được ảnh: {e}") from e
    return decode(buf.getvalue())


def decode(data: bytes) -> Image.Image:
    try:
        return Image.open(io.BytesIO(data)).convert("RGB")
    except (UnidentifiedImageError, OSError) as e:
        raise HTTPException(400, "Không đọc được ảnh") from e


def detect(image: Image.Image) -> list[Detection]:
    model = get_model()
    with _lock:
        result = model.predict(image, imgsz=IMGSZ, conf=CONF, verbose=False)[0]
    h, w = result.orig_shape
    out = []
    for box in result.boxes:
        x1, y1, x2, y2 = box.xyxy[0].tolist()
        out.append(Detection(model.names[int(box.cls)], float(box.conf), max(0.0, (x2 - x1) * (y2 - y1) / (w * h))))
    return out


class DiagnoseRequest(BaseModel):
    imageUrl: str
    stage: Optional[str] = None
    variety: Optional[str] = None
    weather: Optional[str] = None


@app.get("/health")
def health():
    ready = _model is not None
    return {"status": "ok", "weights": str(WEIGHTS), "weightsExist": WEIGHTS.is_file(), "modelLoaded": ready,
            "classes": list(_model.names.values()) if ready else None, "imgsz": IMGSZ, "conf": CONF}


@app.post("/diagnose", dependencies=[Depends(require_token)])
def diagnose(req: DiagnoseRequest):
    return to_diagnosis(detect(fetch_image(req.imageUrl)), class_map())


@app.post("/predict", dependencies=[Depends(require_token)])
def predict(file: UploadFile = File(...)):
    data = file.file.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise HTTPException(413, "Ảnh quá lớn")
    dets = detect(decode(data))
    return {"diagnosis": to_diagnosis(dets, class_map()),
            "detections": [{"class": d.name, "confidence": round(d.confidence, 4), "area": round(d.area, 4)} for d in dets]}

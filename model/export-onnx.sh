#!/usr/bin/env bash
# Xuất weights/best.pt sang weights/best.onnx (kích thước ảnh động) để backend Java chạy trực tiếp: DIAGNOSIS_PROVIDER=onnx.
# Cần môi trường Python có ultralytics, onnx, onnxslim (~/riceguard-venv). Chỉ cần chạy lại khi đổi best.pt.
set -e
cd "$(dirname "$0")/weights"
PY="${PYTHON:-$HOME/riceguard-venv/bin/python}"
"$PY" -c "
from ultralytics import YOLO
print(YOLO('best.pt').export(format='onnx', imgsz=640, dynamic=True, simplify=True, opset=17))
"
ls -la best.onnx

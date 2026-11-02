#!/usr/bin/env bash
# Chạy dịch vụ best.pt trong WSL. Môi trường Python ở ~/riceguard-venv; token lấy từ backend/.env để hai bên luôn khớp.
set -e
cd "$(dirname "$0")"
export MODEL_API_TOKEN="$(grep '^DIAGNOSIS_HTTP_TOKEN=' ../backend/.env | cut -d= -f2 | tr -d '\r')"
export PYTHONDONTWRITEBYTECODE=1
exec ~/riceguard-venv/bin/uvicorn service.main:app --host 0.0.0.0 --port 8000

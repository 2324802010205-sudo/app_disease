"""Chuyển kết quả phát hiện của YOLO thành JSON đúng dạng DiagnosisResult của backend (không phụ thuộc torch)."""
from __future__ import annotations

from dataclasses import dataclass
from typing import Optional

HEALTHY = "healthy"
UNKNOWN = "unknown"

# Tỉ lệ diện tích ảnh bị các vùng bệnh che phủ -> mức độ nặng (heuristic, xem model/README.md).
SEVERITY_MEDIUM_FROM = 0.05
SEVERITY_HIGH_FROM = 0.20


@dataclass(frozen=True)
class Detection:
    name: str           # tên lớp trong best.pt
    confidence: float   # 0..1
    area: float         # diện tích hộp / diện tích ảnh, 0..1


def severity_of(coverage: float) -> str:
    if coverage >= SEVERITY_HIGH_FROM:
        return "HIGH"
    if coverage >= SEVERITY_MEDIUM_FROM:
        return "MEDIUM"
    return "LOW"


def _result(rice_leaf: bool, code: str, confidence: float, severity: str, summary: str,
            retake: Optional[str] = None) -> dict:
    return {
        "riceLeaf": rice_leaf,
        "diseaseCode": code,
        "confidence": round(max(0.0, min(1.0, confidence)), 4),
        "severity": severity,
        "symptoms": [],          # backend điền từ thư viện bệnh (HttpDiagnosisEngine.enrich)
        "summary": summary,
        "recommendations": [],   # như trên
        "retakeAdvice": retake,
    }


def to_diagnosis(detections: list[Detection], class_map: dict[str, Optional[str]]) -> dict:
    """
    Quy tắc:
    - Không có phát hiện nào  -> riceLeaf=false (backend yêu cầu chụp lại).
    - Có vùng bệnh            -> chọn bệnh có độ tin cậy cao nhất; lá khỏe chỉ thắng khi không có vùng bệnh nào
                                 (ưu tiên báo bệnh khi nghi ngờ, vì bỏ sót bệnh thiệt hại hơn báo nhầm).
    - Lớp bệnh chưa ánh xạ    -> diseaseCode="unknown": backend xử lý như chưa xác định.
    """
    if not detections:
        return _result(False, UNKNOWN, 0.0, "NONE", "Không nhận ra lá lúa trong ảnh.",
                       "Hãy chụp cận một chiếc lá lúa, đủ sáng, lá nằm giữa khung hình và không rung tay.")

    def code_of(d: Detection) -> str:
        return class_map.get(d.name) or UNKNOWN

    diseased = [d for d in detections if code_of(d) != HEALTHY]
    if not diseased:
        best = max(detections, key=lambda d: d.confidence)
        return _result(True, HEALTHY, best.confidence, "NONE", "Lá lúa trông khỏe mạnh, chưa thấy dấu hiệu bệnh.")

    top = max(diseased, key=lambda d: d.confidence)
    code = code_of(top)
    same = [d for d in diseased if code_of(d) == code]
    coverage = min(1.0, sum(d.area for d in same))
    if code == UNKNOWN:
        return _result(True, UNKNOWN, top.confidence, "NONE",
                       f"Mô hình phát hiện dấu hiệu \"{top.name}\" nhưng thư viện bệnh chưa có bệnh này.",
                       "Hãy nhờ cán bộ bảo vệ thực vật xác nhận, hoặc chụp lại cận lá bệnh để thử lại.")
    return _result(True, code, top.confidence, severity_of(coverage),
                   f"Phát hiện {len(same)} vùng nghi bệnh, che khoảng {round(coverage * 100)}% khung ảnh.")

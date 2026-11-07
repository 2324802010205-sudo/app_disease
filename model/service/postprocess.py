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


# Dưới mức này kết quả vẫn được báo nhưng kèm lời nhắc đối chiếu thêm (mô hình thường cho độ tin cậy thấp).
CONFIDENT_FROM = 0.6


def merge_runs(runs: list[list[Detection]], class_map: dict[str, Optional[str]]) -> list[Detection]:
    """
    Gộp phát hiện của nhiều lần suy luận (kích thước ảnh khác nhau, có TTA) thành một danh sách.
    Mỗi mã bệnh có độ tin cậy = trung bình các lần chạy của (độ tin cậy cao nhất trong lần đó), lần không thấy tính 0;
    diện tích = trung bình tổng diện tích các hộp cùng mã. Một lần chạy riêng lẻ rất hay dao động (cùng một lá, đổi
    640 px sang 800 px là đổi bệnh hoặc rơi dưới ngưỡng), gộp lại làm kết quả ổn định hơn.
    """
    if not runs:
        return []
    n = len(runs)
    conf: dict[str, float] = {}
    area: dict[str, float] = {}
    names: dict[str, tuple[float, str]] = {}      # mã -> (conf cao nhất, tên lớp tương ứng)
    for dets in runs:
        best: dict[str, float] = {}
        cover: dict[str, float] = {}
        for d in dets:
            code = class_map.get(d.name) or UNKNOWN
            if d.confidence > best.get(code, 0.0):
                best[code] = d.confidence
            cover[code] = cover.get(code, 0.0) + d.area
            if d.confidence > names.get(code, (0.0, ""))[0]:
                names[code] = (d.confidence, d.name)
        for code, c in best.items():
            conf[code] = conf.get(code, 0.0) + c / n
            area[code] = area.get(code, 0.0) + min(1.0, cover[code]) / n
    return [Detection(names[code][1], conf[code], area[code]) for code in conf]


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
        return _result(False, UNKNOWN, 0.0, "NONE", "AI chưa nhận ra dấu hiệu bệnh nào đủ rõ trong ảnh này.",
                       "Hãy chụp cận vùng lá có vết bệnh, đủ sáng, vết bệnh nằm giữa khung hình và không rung tay; có thể chụp thêm một vài góc.")

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
    summary = f"Phát hiện vùng nghi bệnh, che khoảng {round(coverage * 100)}% khung ảnh."
    if top.confidence < CONFIDENT_FROM:
        summary += " Độ tin cậy chưa cao, nên đối chiếu với thư viện bệnh hoặc hỏi cán bộ bảo vệ thực vật."
    return _result(True, code, top.confidence, severity_of(coverage), summary)

import json
from pathlib import Path

import pytest

from service.postprocess import Detection, merge_runs, severity_of, to_diagnosis

CLASS_MAP = {k: v for k, v in json.loads((Path(__file__).parent.parent / "class_map.json").read_text(encoding="utf-8")).items()
             if not k.startswith("_")}


def diagnose(*dets):
    return to_diagnosis(list(dets), CLASS_MAP)


def test_no_detection_asks_for_retake():
    r = diagnose()
    assert r["riceLeaf"] is False and r["retakeAdvice"]


def test_healthy_only():
    r = diagnose(Detection("healthy", 0.9, 0.6))
    assert (r["riceLeaf"], r["diseaseCode"], r["severity"]) == (True, "healthy", "NONE")


def test_disease_wins_over_healthy():
    r = diagnose(Detection("healthy", 0.9, 0.6), Detection("Rice Blast", 0.4, 0.02))
    assert r["diseaseCode"] == "dao-on-la" and r["confidence"] == 0.4


@pytest.mark.parametrize("names", [("Brown spot", "Rice Brownspot"), ("Leaf blast", "Rice Blast"),
                                   ("Rice Bacterial Blight", "bacterial leaf blight")])
def test_duplicate_classes_share_a_code(names):
    assert diagnose(Detection(names[0], 0.8, 0.01))["diseaseCode"] == diagnose(Detection(names[1], 0.7, 0.01))["diseaseCode"]


def test_coverage_sums_same_disease_and_sets_severity():
    r = diagnose(Detection("Brown spot", 0.8, 0.12), Detection("Rice Brownspot", 0.6, 0.10))
    assert r["diseaseCode"] == "dom-nau" and r["severity"] == "HIGH"


def test_coverage_capped_at_one():
    assert diagnose(Detection("Brown spot", 0.8, 0.9), Detection("Brown spot", 0.7, 0.9))["severity"] == "HIGH"


def test_formerly_unmapped_classes_now_have_library_entries():
    for name, code in [("Leaf Scald", "chay-la"), ("Leaf smut", "than-la"), ("Narrow brown spot", "dom-nau-hep")]:
        assert diagnose(Detection(name, 0.9, 0.1))["diseaseCode"] == code


def test_class_without_library_entry_is_unknown_with_retake_advice():
    r = to_diagnosis([Detection("Leaf smut", 0.9, 0.1)], {"Leaf smut": None})
    assert r["riceLeaf"] is True and r["diseaseCode"] == "unknown" and r["retakeAdvice"]


def test_class_missing_from_map_is_unknown():
    assert diagnose(Detection("Totally new class", 0.9, 0.1))["diseaseCode"] == "unknown"


@pytest.mark.parametrize("coverage,expected", [(0, "LOW"), (0.049, "LOW"), (0.05, "MEDIUM"), (0.199, "MEDIUM"), (0.2, "HIGH")])
def test_severity_thresholds(coverage, expected):
    assert severity_of(coverage) == expected


def test_every_model_class_is_mapped():
    # 11 lớp của best.pt; thêm lớp mới vào model mà quên class_map.json sẽ bị bắt ở đây
    assert len(CLASS_MAP) == 11


def test_every_mapped_code_exists_in_disease_library():
    seed = json.loads((Path(__file__).parents[2] / "backend/src/main/resources/seed/diseases.json").read_text(encoding="utf-8"))
    known = {d["id"] for d in seed} | {"healthy"}
    assert {c for c in CLASS_MAP.values() if c} <= known


def test_merge_averages_each_code_over_runs_and_counts_misses_as_zero():
    # Leaf Scald chỉ thấy ở 1 trong 2 lần: trung bình (0.8 + 0) / 2; Bacterial Blight thấy cả hai lần: (0.3 + 0.5) / 2
    runs = [[Detection("Leaf Scald", 0.8, 0.1), Detection("Rice Bacterial Blight", 0.3, 0.2)],
            [Detection("Rice Bacterial Blight", 0.5, 0.2)]]
    merged = {CLASS_MAP[d.name]: d for d in merge_runs(runs, CLASS_MAP)}
    assert merged["chay-la"].confidence == pytest.approx(0.4)
    assert merged["bac-la"].confidence == pytest.approx(0.4)
    assert merged["bac-la"].area == pytest.approx(0.2)


def test_merge_joins_duplicate_classes_by_code_and_keeps_best_name():
    runs = [[Detection("Rice Blast", 0.3, 0.05), Detection("Leaf blast", 0.5, 0.05)]]
    (d,) = merge_runs(runs, CLASS_MAP)
    assert d.name == "Leaf blast" and d.confidence == 0.5 and d.area == pytest.approx(0.1)


def test_merge_with_no_runs_is_empty():
    assert merge_runs([], CLASS_MAP) == []


def test_merged_result_is_steadier_than_a_single_run():
    # Một lần chạy 640 px chỉ cho 0.24 (rơi dưới ngưỡng 0.25 cũ); gộp với lần 800 px cho kết quả vượt ngưỡng.
    merged = merge_runs([[Detection("Leaf Scald", 0.54, 0.2)], [Detection("Leaf Scald", 0.48, 0.2)]], CLASS_MAP)
    assert diagnose(*merged)["diseaseCode"] == "chay-la"


def test_low_confidence_result_carries_a_caution():
    assert "đối chiếu" in diagnose(Detection("Rice Blast", 0.4, 0.02))["summary"]
    assert "đối chiếu" not in diagnose(Detection("Rice Blast", 0.8, 0.02))["summary"]

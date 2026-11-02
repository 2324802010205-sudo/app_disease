import json
from pathlib import Path

import pytest

from service.postprocess import Detection, severity_of, to_diagnosis

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

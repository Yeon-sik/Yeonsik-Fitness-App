"""Compare the same synthetic workout read back from Android Room and the iOS store."""
import json
import math
import sys
from pathlib import Path

expected = {
    "title": "ParityBench", "exercise": "바벨 플랫 벤치 프레스", "status": "completed",
    "catalogId": "chest_barbell_flat_bench_press", "primaryPart": "중부 가슴", "equipment": "바벨",
    "completedSets": 1, "setIndex": 1, "inputWeight": 100, "unit": "lb",
    "weightKg": 45.359237, "reps": 10, "rir": 2, "volumeKg": 453.59237,
}
android, ios = (json.loads(Path(path).read_text(encoding="utf-8-sig")) for path in sys.argv[1:])
if android.keys() != expected.keys() or ios.keys() != expected.keys():
    sys.exit("Unexpected fields in completed workout")
for field, target in expected.items():
    values = android[field], ios[field]
    if isinstance(target, float):
        matches = (math.isclose(*values, rel_tol=0, abs_tol=1e-8) and
                   all(math.isclose(value, target, rel_tol=0, abs_tol=1e-8) for value in values))
    else:
        matches = all(value == target for value in values)
    if not matches:
        sys.exit(f"{field}: Android={values[0]!r}, iOS={values[1]!r}, expected={target!r}")
print(f"Room and iOS store match on {len(expected)} fields: {json.dumps(android, ensure_ascii=False)}")

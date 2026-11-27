"""Package ordinary exercise metadata and exact first frames used by the Android workout summary."""
import json
import shutil
import sys
from pathlib import Path


def package(assets, destination):
    source = json.loads((assets / "exercise_family_mapping_v1.json").read_text(encoding="utf-8"))
    pictures = assets / "exercise-images"
    images = json.loads((pictures / "manifest.json").read_text(encoding="utf-8"))["exercises"]
    destination.mkdir(parents=True, exist_ok=True)
    entries = []
    for row in source["legacyExercises"]:
        identifier = row["legacyExerciseId"]
        if (identifier != row.get("canonicalPresetId") or row.get("legacyRecordType") != "weight_reps"
                or row.get("defaultLoadState") != "external_load"):
            continue
        frames = images.get(identifier, {}).get("frames", [])
        if frames:
            shutil.copyfile(pictures / frames[0]["file"], destination / (identifier + ".png"))
        entries.append({
            "name": row["presetNameKo"], "englishName": row["presetNameEn"],
            "metadata": {"id": identifier, "bodyPart": row["defaultUiPart"],
                         "primaryPart": row.get("primarySubPartNameKo") or "",
                         "equipment": row.get("equipmentNameKo") or ""},
        })
    (destination / "catalog.json").write_text(json.dumps(entries, ensure_ascii=False), encoding="utf-8")


if __name__ == "__main__":
    package(Path(__file__).resolve().parents[2] / "app/src/main/assets-fallback", Path(sys.argv[1]))

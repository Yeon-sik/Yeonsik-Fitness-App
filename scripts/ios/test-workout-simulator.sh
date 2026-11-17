#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."
output="$PWD/build/ios-sideload"
derived_data="$output/SimulatorDerivedData"
bundle_id='com.yeonsik.fitness.iosproof'
app="$derived_data/Build/Products/Debug-iphonesimulator/YeonsikIosProof.app"
mkdir -p "$output"

simulator="$(xcrun simctl list -j devices available | python3 -c '
import json, sys

iphones = []
for runtime, devices in json.load(sys.stdin)["devices"].items():
    if ".iOS-" not in runtime:
        continue
    version = tuple(int(part) for part in runtime.split(".iOS-", 1)[1].split("-"))
    if version[0] < 16:
        continue
    for device in devices:
        if device["name"].startswith("iPhone"):
            iphones.append((device["state"] == "Booted", version, device["name"], device["udid"]))
if not iphones:
    sys.exit("No available iOS 16+ iPhone simulator")
print(max(iphones)[-1])
')"

xcodebuild \
    -project iosApp/iosApp.xcodeproj \
    -scheme YeonsikIosProof \
    -configuration Debug \
    -destination "platform=iOS Simulator,id=$simulator" \
    -derivedDataPath "$derived_data" \
    -resultBundlePath "$output/WorkoutSmoke-$(date +%s).xcresult" \
    -parallel-testing-enabled NO \
    CODE_SIGNING_ALLOWED=NO \
    test

records_path() {
    local container
    container="$(xcrun simctl get_app_container "$simulator" "$bundle_id" data)"
    printf '%s/Library/Application Support/FitnessWorkout/workouts.json\n' "$container"
}

records="$(records_path)"
python3 - "$records" <<'PY'
import json
import math
import sys
from pathlib import Path

data = json.loads(Path(sys.argv[1]).read_text())
workout = data["history"][0]
logged_set = workout["exercises"][0]["sets"][0]
assert data["schemaVersion"] == 1 and data.get("inProgress") is None
assert workout["routineID"] in {routine["id"] for routine in data["routines"]}
assert logged_set["unit"] == "lb" and logged_set["inputWeight"] == 100
assert logged_set["reps"] == 10 and logged_set["rir"] == 2
assert math.isclose(logged_set["weightKg"], 45.359237)
PY

before="$(shasum -a 256 "$records" | cut -d ' ' -f 1)"
xcrun simctl install "$simulator" "$app"
after="$(shasum -a 256 "$(records_path)" | cut -d ' ' -f 1)"
[[ "$before" == "$after" ]]
xcrun simctl launch "$simulator" "$bundle_id"
echo 'Workout store preserved byte-for-byte after simulator install over existing app.'

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

# Boot explicitly so XCTest can reuse an already running device.
xcrun simctl bootstatus "$simulator" -b
if ! xcodebuild \
    -project iosApp/iosApp.xcodeproj \
    -scheme YeonsikIosProof \
    -configuration Debug \
    -destination "platform=iOS Simulator,id=$simulator" \
    -derivedDataPath "$derived_data" \
    -resultBundlePath "$output/WorkoutSmoke-$(date +%s).xcresult" \
    -parallel-testing-enabled NO \
    ARCHS="$(uname -m)" \
    CODE_SIGNING_ALLOWED=NO \
    test; then
    xcrun simctl bootstatus "$simulator" -b || true
    xcrun simctl spawn "$simulator" log show --last 10m --style compact \
        --predicate 'process == "YeonsikIosProof" AND eventMessage CONTAINS "Uncaught Kotlin exception"' || true
    exit 1
fi

records_path() {
    local container
    container="$(xcrun simctl get_app_container "$simulator" "$bundle_id" data)" || return
    [[ -n "$container" ]] || return 1
    printf '%s/Library/Application Support/%s\n' "$container" "$1"
}

# Xcode can shut down the simulator after testing. Container lookup requires it to be booted.
xcrun simctl bootstatus "$simulator" -b
records="$(records_path FitnessWorkout/workouts.json)"
body_records="$(records_path FitnessBody/body-metrics.json)"
python3 - "$records" "$body_records" <<'PY'
import json
import math
import sys
from pathlib import Path

data = json.loads(Path(sys.argv[1]).read_text())
workout = data["history"][0]
logged_set = workout["exercises"][0]["sets"][0]
assert data["schemaVersion"] == 2 and data.get("inProgress") is None
exercise = workout["exercises"][0]
assert exercise["id"] != exercise["routineExerciseID"]
assert workout["routineID"] in {routine["id"] for routine in data["routines"]}
assert logged_set["unit"] == "lb" and logged_set["inputWeight"] == 100
assert logged_set["reps"] == 10 and logged_set["rir"] == 2
assert math.isclose(logged_set["weightKg"], 45.359237)
body = json.loads(Path(sys.argv[2]).read_text())
assert body["schemaVersion"] == 1
assert math.isclose(body["owners"]["ios-local"]["entries"][0]["weightKg"], 72.75)
PY

before="$(shasum -a 256 "$records" | cut -d ' ' -f 1)"
body_before="$(shasum -a 256 "$body_records" | cut -d ' ' -f 1)"
xcrun simctl install "$simulator" "$app"
reinstalled_records="$(records_path FitnessWorkout/workouts.json)"
reinstalled_body_records="$(records_path FitnessBody/body-metrics.json)"
after="$(shasum -a 256 "$reinstalled_records" | cut -d ' ' -f 1)"
body_after="$(shasum -a 256 "$reinstalled_body_records" | cut -d ' ' -f 1)"
[[ "$before" == "$after" ]]
[[ "$body_before" == "$body_after" ]]
xcrun simctl launch "$simulator" "$bundle_id"
python3 scripts/ios/test-workout-store-migration.py "$simulator" "$bundle_id" "$reinstalled_records"
echo 'Workout and body stores preserved byte-for-byte after simulator install over existing app.'
if [[ "${GITHUB_ACTIONS:-}" == 'true' ]]; then
    xcrun simctl shutdown "$simulator"
fi

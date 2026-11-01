#!/usr/bin/env bash
set -euo pipefail
# Runs the core instrumentation suite on the single attached emulator.
# connectedDebugAndroidTest installs and uninstalls the app, and these tests delete its
# database, so any physical device is refused: never point this at a phone with records.
# Each class runs as its own connected test task. The uninstall between runs gives every
# class fresh app data, so one class cannot leave a database that fails the next.
repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$repo_root"
if [[ -f .cloud-env.sh ]]; then
  # shellcheck disable=SC1091
  source .cloud-env.sh
fi
list=scripts/cloud/android-core-instrumented-tests.txt
classes=()
while IFS= read -r line; do
  line=${line%%#*}
  line=${line//[[:space:]]/}
  [[ -n $line ]] || continue
  package=${line%.*}
  found=false
  while IFS= read -r source; do
    if grep -Eq "^package[[:space:]]+${package//./\\.}[[:space:];]*$" "$source"; then
      found=true
      break
    fi
  done < <(find app/src/androidTest -name "${line##*.}.java" -o -name "${line##*.}.kt")
  if [[ $found != true ]]; then
    echo "Listed test class has no matching source (class name and package): $line" >&2
    exit 1
  fi
  classes+=("$line")
done < "$list"
(( ${#classes[@]} > 0 )) || { echo "No test classes listed in $list." >&2; exit 1; }

adb=adb
if [[ -n ${ANDROID_HOME:-} && -x $ANDROID_HOME/platform-tools/adb ]]; then
  adb=$ANDROID_HOME/platform-tools/adb
fi
mapfile -t devices < <("$adb" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if (( ${#devices[@]} != 1 )); then
  echo "Attach exactly one emulator; found ${#devices[@]} device(s)." >&2
  exit 1
fi
serial=${devices[0]}
prop() { "$adb" -s "$serial" shell getprop "$1" | tr -d '\r'; }
if [[ $(prop ro.kernel.qemu) != 1 && $(prop ro.boot.qemu) != 1 ]] &&
  [[ ! $(prop ro.hardware) =~ ^(ranchu|goldfish)$ ]]; then
  echo "Refusing $serial: not an emulator. The core suite deletes app data." >&2
  exit 1
fi
export ANDROID_SERIAL=$serial

echo "Running ${#classes[@]} core instrumentation classes on $serial (API $(prop ro.build.version.sdk))."
results=app/build/outputs/androidTest-results
core_results=$results/core
core_reports=app/build/reports/androidTests/core
mkdir -p "$core_results" "$core_reports"
failed=()
for class in "${classes[@]}"; do
  echo "::group::$class"
  "$adb" -s "$serial" logcat -c || true
  # Never let a class that produced no results inherit the previous class's reports.
  rm -rf "${results:?}/connected" app/build/reports/androidTests/connected
  status=0
  bash ./gradlew --max-workers=2 --console=plain :app:connectedDebugAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.class=$class" || status=$?
  rm -rf "${core_results:?}/$class" "${core_reports:?}/$class"
  mkdir -p "$core_results/$class"
  if [[ -d $results/connected ]]; then
    cp -R "$results/connected/." "$core_results/$class/"
  fi
  if [[ -d app/build/reports/androidTests/connected ]]; then
    cp -R app/build/reports/androidTests/connected "$core_reports/$class"
  fi
  # The emulator log holds synthetic test data only; keep it for crash diagnosis.
  "$adb" -s "$serial" logcat -d > "$core_results/$class/logcat.txt" 2>&1 || true
  echo "::endgroup::"
  if (( status != 0 )); then
    failed+=("$class")
  fi
done

if command -v python3 >/dev/null; then
  # Print full failure text: the Gradle console keeps only the first lines of each trace.
  python3 - "$core_results" <<'PY'
import pathlib
import re
import sys
import xml.etree.ElementTree as ElementTree


def room_columns(block):
    columns, current = {}, None
    for line in block.splitlines():
        line = line.strip()
        if line.startswith("Column {"):
            current = {}
        elif current is not None and line.startswith("}"):
            name = current.pop("name", None)
            if name:
                columns[name] = current
            current = None
        elif current is not None:
            match = re.match(r"(\w+)\s*=\s*'(.*)',?$", line)
            if match:
                current[match.group(1)] = match.group(2)
    return columns


def room_diff(text):
    """Reduces Room's Expected/Found TableInfo dump to the columns and indices that differ."""
    if "Expected:" not in text or "Found:" not in text:
        return None
    expected, found = text.split("Expected:", 1)[1].split("Found:", 1)
    want, have = room_columns(expected), room_columns(found)
    lines = []
    for name in sorted(set(want) | set(have)):
        if name not in have:
            lines.append(f"  missing column {name}: {want[name]}")
        elif name not in want:
            lines.append(f"  unexpected column {name}: {have[name]}")
        elif want[name] != have[name]:
            changes = [f"{key} expected {want[name].get(key)} found {have[name].get(key)}"
                       for key in sorted(set(want[name]) | set(have[name]))
                       if want[name].get(key) != have[name].get(key)]
            lines.append(f"  {name}: " + "; ".join(changes))
    index = r"Index\s*\{\s*name\s*=\s*'([^']+)'"
    want_index, have_index = set(re.findall(index, expected)), set(re.findall(index, found))
    lines += [f"  missing index {name}" for name in sorted(want_index - have_index)]
    lines += [f"  unexpected index {name}" for name in sorted(have_index - want_index)]
    return lines or ["  (no column or index difference; compare foreign keys in the XML report)"]


root = pathlib.Path(sys.argv[1])
rows = []
for class_dir in sorted(path for path in root.iterdir() if path.is_dir()):
    tests = failures = skipped = 0
    details = []
    for report in sorted(class_dir.rglob("TEST-*.xml")):
        suite = ElementTree.parse(report).getroot()
        for case in suite.iter("testcase"):
            tests += 1
            if case.find("skipped") is not None:
                skipped += 1
            for problem in list(case.findall("failure")) + list(case.findall("error")):
                failures += 1
                full = (problem.text or problem.get("message") or "").strip()
                text = full.splitlines()
                diff = room_diff(full)
                if diff is not None:
                    stack = [line for line in text if line.strip().startswith("at ")][:8]
                    text = text[:1] + ["Room schema difference:"] + diff + stack
                details.append((case.get("name"), text[:40]))
    rows.append((class_dir.name, tests, failures, skipped))
    for name, text in details:
        print(f"\n--- FAILED {class_dir.name}#{name}")
        print("\n".join(text))
print("\nCore instrumentation summary (tests / failed / skipped):")
for name, tests, failures, skipped in rows:
    marker = "FAIL" if failures else ("NONE" if tests == 0 else "ok")
    print(f"{marker:4} {tests:3} {failures:3} {skipped:3}  {name}")
PY
fi

if (( ${#failed[@]} > 0 )); then
  printf 'Core instrumentation failed in %d class(es):\n' "${#failed[@]}" >&2
  printf '  %s\n' "${failed[@]}" >&2
  exit 1
fi
echo "All ${#classes[@]} core instrumentation classes passed."

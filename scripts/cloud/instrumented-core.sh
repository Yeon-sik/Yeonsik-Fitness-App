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
command -v python3 >/dev/null || { echo "python3 is required to validate test results." >&2; exit 1; }
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
if [[ -n ${ANDROID_SERIAL:-} ]]; then
  serial=$ANDROID_SERIAL
  if ! printf '%s\n' "${devices[@]}" | grep -Fxq -- "$serial"; then
    echo "Requested ANDROID_SERIAL is not an online device: $serial" >&2
    exit 1
  fi
else
  if (( ${#devices[@]} != 1 )); then
    echo "Attach exactly one emulator or select it with ANDROID_SERIAL; found ${#devices[@]} device(s)." >&2
    exit 1
  fi
  serial=${devices[0]}
fi
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

# A successful Gradle task alone does not prove that its class actually ran.
python3 scripts/cloud/verify-instrumented-results.py "$core_results" "${classes[@]}"

if (( ${#failed[@]} > 0 )); then
  printf 'Core instrumentation failed in %d class(es):\n' "${#failed[@]}" >&2
  printf '  %s\n' "${failed[@]}" >&2
  exit 1
fi
echo "All ${#classes[@]} core instrumentation classes passed."

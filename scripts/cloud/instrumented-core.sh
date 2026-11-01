#!/usr/bin/env bash
set -euo pipefail
# Runs the core instrumentation suite on the single attached emulator.
# connectedDebugAndroidTest installs and may uninstall the app, and these tests delete its
# database, so any physical device is refused: never point this at a phone with records.
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
  path=${line//.//}
  if [[ ! -f app/src/androidTest/java/$path.java && ! -f app/src/androidTest/kotlin/$path.kt ]]; then
    echo "Listed test class has no source file: $line" >&2
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
mkdir -p "$results"
"$adb" -s "$serial" logcat -c || true
status=0
joined=$(IFS=,; echo "${classes[*]}")
bash ./gradlew --no-daemon --max-workers=2 --console=plain :app:connectedDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=$joined" || status=$?
# Keep the emulator log for crash diagnosis; it holds synthetic test data only.
"$adb" -s "$serial" logcat -d > "$results/core-logcat.txt" 2>&1 || true
exit "$status"

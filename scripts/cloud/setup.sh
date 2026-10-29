#!/usr/bin/env bash
set -euo pipefail

# Provision Linux x86_64 only. Builds run separately so Claude's setup can cache.
[[ $(uname -s) == Linux && $(uname -m) == x86_64 ]] || {
  echo 'Use a Linux x86_64 cloud environment for this setup.' >&2; exit 1;
}
repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$repo_root"
for tool in java node curl unzip sha256sum; do
  command -v "$tool" >/dev/null || { echo "Missing $tool; install JDK 17+ and Node.js 22+ first." >&2; exit 1; }
done
java_major=$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)
[[ $java_major =~ ^[0-9]+$ && $java_major -ge 17 ]] || { echo 'JDK 17+ required.' >&2; exit 1; }
node -e 'if (Number(process.versions.node.split(".")[0]) < 22) process.exit(1)' || {
  echo 'Node.js 22+ required; select it in the cloud environment.' >&2; exit 1;
}

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/.android-sdk}}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x $sdkmanager ]] && command -v sdkmanager >/dev/null; then
  sdkmanager=$(command -v sdkmanager)
fi
if [[ ! -x $sdkmanager ]]; then
  [[ ! -e $ANDROID_HOME/cmdline-tools/latest ]] || {
    echo "Incomplete tools at $ANDROID_HOME/cmdline-tools/latest; repair it or select another SDK root." >&2; exit 1;
  }
  scratch=$(mktemp -d)
  trap 'rm -rf -- "$scratch"' EXIT
  # Official Android command-line tools, pinned with the published SHA-256.
  curl --fail --location --retry 3 --connect-timeout 20 \
    https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip \
    --output "$scratch/tools.zip"
  printf '%s  %s\n' '4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583' "$scratch/tools.zip" | sha256sum --check
  unzip -q "$scratch/tools.zip" -d "$scratch/unpacked"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  # Only the missing latest tools directory is installed; existing SDKs are kept.
  mv "$scratch/unpacked/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
export PATH="$(dirname "$sdkmanager"):$ANDROID_HOME/platform-tools:$PATH"
# yes can receive SIGPIPE after sdkmanager exits; retain sdkmanager's real status.
set +o pipefail
yes | "$sdkmanager" --sdk_root="$ANDROID_HOME" --licenses >/dev/null
license_status=${PIPESTATUS[1]}
set -o pipefail
(( license_status == 0 )) || exit "$license_status"
"$sdkmanager" --sdk_root="$ANDROID_HOME" 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0'

# Exported setup variables need not survive into a cloud session. Persist paths
# locally and source them from verification; do not overwrite local.properties.
printf 'export ANDROID_HOME=%q\nexport ANDROID_SDK_ROOT=%q\nexport PATH=%q:"$PATH"\n' \
  "$ANDROID_HOME" "$ANDROID_HOME" "$(dirname "$sdkmanager"):$ANDROID_HOME/platform-tools" > .cloud-env.sh
echo 'Toolchain ready. Run bash scripts/cloud/verify.sh in the coding session.'

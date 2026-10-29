#!/usr/bin/env bash
set -euo pipefail
repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$repo_root"
if [[ -f .cloud-env.sh ]]; then
  # shellcheck disable=SC1091
  source .cloud-env.sh
fi
java -version
node --version
# Keep these tasks aligned with kmp-release-readiness.yml. No device or remote
# credentials are needed; unsigned debug artifacts use Android's debug signing.
bash ./gradlew --no-daemon --max-workers=2 --console=plain \
  :shared:testAndroid :app:testDebugUnitTest :app:assembleDebug \
  :app:assembleDebugAndroidTest :app:lintDebug

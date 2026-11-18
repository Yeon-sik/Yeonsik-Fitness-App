#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."
output="$PWD/build/ios-sideload"
derived_data="$output/DerivedData"
app="$derived_data/Build/Products/Release-iphoneos/YeonsikIosProof.app"
ipa='Yeonsik-Fitness-unsigned.ipa'

xcodebuild -version
xcodebuild \
    -project iosApp/iosApp.xcodeproj \
    -scheme YeonsikIosProof \
    -configuration Release \
    -sdk iphoneos \
    -destination 'generic/platform=iOS' \
    -derivedDataPath "$derived_data" \
    ARCHS=arm64 \
    CODE_SIGNING_ALLOWED=NO \
    CODE_SIGNING_REQUIRED=NO \
    CURRENT_PROJECT_VERSION="${GITHUB_RUN_NUMBER:-1}" \
    build

plist="$app/Info.plist"
executable="$(plutil -extract CFBundleExecutable raw -o - "$plist")"
[[ "$(plutil -extract CFBundleIdentifier raw -o - "$plist")" == 'com.yeonsik.fitness.iosproof' ]]
[[ "$(plutil -extract CFBundleSupportedPlatforms.0 raw -o - "$plist")" == 'iPhoneOS' ]]
[[ "$(xcrun lipo -archs "$app/$executable")" == 'arm64' ]]
build_version="$(xcrun vtool -show-build "$app/$executable")"
printf '%s\n' "$build_version"
grep -Eq '^[[:space:]]*platform[[:space:]]+IOS[[:space:]]*$' <<< "$build_version"
[[ -z "$(find "$app" \( -name '*.mobileprovision' -o -name '_CodeSignature' \) -print)" ]]

staging="$(mktemp -d "$output/package.XXXXXX")"
trap 'rm -rf "$staging"' EXIT
mkdir "$staging/Payload"
ditto "$app" "$staging/Payload/$(basename "$app")"
ditto -c -k --norsrc --keepParent "$staging/Payload" "$staging/$ipa"
unzip -tq "$staging/$ipa"
mv "$staging/$ipa" "$output/$ipa"
(cd "$output" && shasum -a 256 "$ipa" > "$ipa.sha256")

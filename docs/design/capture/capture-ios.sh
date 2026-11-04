#!/usr/bin/env bash
# Takes the iOS reference captures into docs/design/ios-captures/.
# Device: iPhone 18 Pro simulator (402 x 874 pt, @3x = 1206 x 2622 px), iOS 27.2,
# light appearance, status bar pinned to 9:41 / full battery / full signal.
# Runs only ReferenceCaptureUITests (skipped in normal test runs) on the Debug
# fixture data, so a re-run produces the same images.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
OUT="$ROOT/docs/design/ios-captures"
DEVICE="${IOS_CAPTURE_DEVICE:-iPhone 18 Pro}"

UDID=$(xcrun simctl list devices available -j | /usr/bin/python3 -c "
import json,sys
d=json.load(sys.stdin)['devices']
print(next(x['udid'] for r in d.values() for x in r if x['name']=='$DEVICE'))")
xcrun simctl boot "$UDID" 2>/dev/null || true
xcrun simctl bootstatus "$UDID" -b >/dev/null
xcrun simctl ui "$UDID" appearance light
# No "slide to type" tutorial card over the keyboard.
xcrun simctl spawn "$UDID" defaults write com.apple.keyboard.preferences DidShowContinuousPathIntroduction -bool true
xcrun simctl status_bar "$UDID" override --time "9:41" --dataNetwork wifi --wifiMode active \
  --wifiBars 3 --cellularMode active --cellularBars 4 --batteryState charged --batteryLevel 100

mkdir -p "$OUT"
cd "$ROOT/ios"
TEST_RUNNER_CAPTURE_DIR="$OUT" xcodebuild test \
  -project SplitParty.xcodeproj -scheme SplitParty \
  -destination "platform=iOS Simulator,id=$UDID" \
  -only-testing:SplitPartyUITests/ReferenceCaptureUITests \
  -quiet
echo "Captures written to $OUT"

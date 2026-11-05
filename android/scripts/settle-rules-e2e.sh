#!/usr/bin/env bash
# Runs SettleRulesE2ETest: the app's real Pay transaction against the Firestore
# emulator loaded with backend/firestore.rules.
#
# Needs: a booted Android *emulator* (10.0.2.2 is its alias for this machine),
# and Java 21 + `npm ci` in backend/. Starts the Firestore emulator itself
# (project demo-split-party-settle, so no other data is touched) and stops it after.
#
# Usage: ANDROID_SERIAL=emulator-5554 android/scripts/settle-rules-e2e.sh
#
# It installs the debug and test APKs on that emulator only and runs the one
# class with `am instrument`, so unlike connectedDebugAndroidTest it never
# clears or uninstalls the app, nor touches other connected devices.
set -euo pipefail

: "${ANDROID_SERIAL:?Set ANDROID_SERIAL to an emulator serial, e.g. emulator-5554 (see adb devices)}"
case "$ANDROID_SERIAL" in emulator-*) ;; *) echo "ANDROID_SERIAL must be an emulator (10.0.2.2 only exists there)" >&2; exit 1 ;; esac

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
PROJECT=demo-split-party-settle

if ! nc -z localhost 8080 2>/dev/null; then
  (cd "$ROOT/backend" && npx firebase emulators:start --only firestore --project "$PROJECT" >/dev/null 2>&1) &
  EMULATOR_PID=$!
  # npx leaves the Java emulator behind; stop only the 8080 *listener* (killing every
  # 8080 connection would also kill the AVD's port forward and drop it from adb).
  trap 'kill $EMULATOR_PID 2>/dev/null || true; lsof -ti tcp:8080 -sTCP:LISTEN | xargs kill 2>/dev/null || true' EXIT
  for _ in $(seq 1 60); do nc -z localhost 8080 2>/dev/null && break; sleep 1; done
fi

(cd "$ROOT/android" && ./gradlew -q :app:installDebug :app:installDebugAndroidTest)
OUT="$("$ADB" -s "$ANDROID_SERIAL" shell am instrument -w -r \
  -e class com.splitparty.android.e2e.SettleRulesE2ETest \
  -e settleEmulator 10.0.2.2:8080 -e settleProject "$PROJECT" \
  com.splitparty.android.test/androidx.test.runner.AndroidJUnitRunner)"
echo "$OUT" | grep -E "^Error in|AssertionError|OK \(|FAILURES|Tests run" || true
echo "$OUT" | grep -q "^OK (" 

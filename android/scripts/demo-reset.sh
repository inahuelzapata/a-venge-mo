#!/usr/bin/env bash
# Deploys the Demo reset endpoint, installs the current build on every connected
# phone/emulator, then resets the REAL demo by running LiveDemoResetTest
# (taps Me → Demo reset → Reset) on one of them.
#
# Needs: `npx vercel login` once, backend/.env + backend/service-account.json,
# and npm ci in backend/.
#
# Usage: android/scripts/demo-reset.sh [--skip-deploy] [--skip-install]
#   ANDROID_SERIAL=<serial>  device that runs the reset (default: first in adb devices)
#   RESET_USER=<id>          seeded user to sign in as if the app is signed out (default maxi)
#
# Installs with Gradle and runs the one class with `am instrument`, so unlike
# connectedDebugAndroidTest it never clears or uninstalls the app (sign-ins stay).
set -euo pipefail

DEPLOY=1 INSTALL=1
for arg in "$@"; do
  case "$arg" in
    --skip-deploy) DEPLOY=0 ;;
    --skip-install) INSTALL=0 ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
PROPS="$ROOT/android/firestore.properties"

if [ "$DEPLOY" = 1 ]; then
  echo "==> Deploying the reset endpoint (Vercel)"
  LOG="$(mktemp)"
  (cd "$ROOT/backend" && npm run -s deploy:reset-endpoint </dev/null) | tee "$LOG"
  URL="$(sed -n 's/^Deployed: \(https:[^ ]*\).*/\1/p' "$LOG" | tail -1)"
  rm -f "$LOG"
  [ -n "$URL" ] || { echo "Could not read the deployed URL" >&2; exit 1; }
  # `vercel link` appends a duplicate `.vercel` line to this tracked file.
  git -C "$ROOT" checkout -q -- backend/reset-endpoint/.gitignore 2>/dev/null || true
  if grep -q '^RESET_DEMO_URL=' "$PROPS"; then
    sed -i '' "s|^RESET_DEMO_URL=.*|RESET_DEMO_URL=$URL|" "$PROPS"
  else
    echo "RESET_DEMO_URL=$URL" >> "$PROPS"
  fi
  echo "RESET_DEMO_URL=$URL (android/firestore.properties)"
fi

DEVICES="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')"
[ -n "$DEVICES" ] || { echo "No device connected (adb devices)" >&2; exit 1; }
SERIAL="${ANDROID_SERIAL:-$(echo "$DEVICES" | head -1)}"

if [ "$INSTALL" = 1 ]; then
  echo "==> Installing the debug build on: $(echo $DEVICES)"
  # Without ANDROID_SERIAL, Gradle installs on every connected device.
  (cd "$ROOT/android" && env -u ANDROID_SERIAL ./gradlew -q :app:installDebug :app:installDebugAndroidTest)
fi

echo "==> Resetting the demo from $SERIAL"
OUT="$("$ADB" -s "$SERIAL" shell am instrument -w -r \
  -e class com.splitparty.android.e2e.LiveDemoResetTest \
  -e liveDemoReset true -e liveDemoResetUser "${RESET_USER:-maxi}" \
  com.splitparty.android.test/androidx.test.runner.AndroidJUnitRunner)"
echo "$OUT" | grep -E "^Error in|AssertionError|Demo reset failed|Sign-in as|OK \(|FAILURES|Tests run" || true
# A skipped test (assumption, code -4) also ends in "OK (1 test)": treat it as not reset.
if echo "$OUT" | grep -q "STATUS_CODE: -4"; then echo "The reset test was skipped: nothing was reset" >&2; exit 1; fi
echo "$OUT" | grep -q "^OK (1 test)" || { echo "Demo reset did NOT complete" >&2; exit 1; }
echo "Demo reset done: every phone is back to the seeded state."

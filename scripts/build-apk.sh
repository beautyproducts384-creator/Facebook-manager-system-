#!/usr/bin/env bash
# Build the debug APK and copy it to dist/.
#
# Usage:
#   ./scripts/build-apk.sh [FACEBOOK_APP_ID]
#
# The Facebook App ID can be passed as an argument, via the FACEBOOK_APP_ID
# env var, or via facebook_app_id=... in local.properties. If none is given,
# the app builds in "unconfigured" mode: Real Mode login will show a setup
# dialog instead of attempting a broken OAuth flow (Demo Mode is unaffected).
set -euo pipefail

cd "$(dirname "$0")/.."

APP_ID="${1:-${FACEBOOK_APP_ID:-}}"

if [ -n "$APP_ID" ]; then
  echo "Building with Facebook App ID: $APP_ID"
  ./gradlew assembleDebug -PFACEBOOK_APP_ID="$APP_ID"
else
  echo "Building WITHOUT a Facebook App ID (Demo Mode fully works; Real Mode login will show a setup dialog)."
  ./gradlew assembleDebug
fi

mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/FacebookPageManager-debug.apk
echo "APK -> dist/FacebookPageManager-debug.apk"

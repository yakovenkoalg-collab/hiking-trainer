#!/bin/zsh
set -euo pipefail

PROJECT_DIR="${0:A:h:h}"
export ANDROID_SDK_ROOT="$PROJECT_DIR/.tooling/android-sdk"
MOUNTAIN_FORM_DNS="${MOUNTAIN_FORM_DNS:-8.8.8.8,1.1.1.1}"

exec "$ANDROID_SDK_ROOT/emulator/emulator" \
    -avd MountainFormApi35 \
    -gpu auto \
    -dns-server "$MOUNTAIN_FORM_DNS" \
    "$@"

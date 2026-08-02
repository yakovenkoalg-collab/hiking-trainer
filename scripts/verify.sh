#!/bin/zsh
set -euo pipefail

PROJECT_DIR="${0:A:h:h}"
export JAVA_HOME="$PROJECT_DIR/.tooling/jdk/jdk-17.0.20+8/Contents/Home"
export ANDROID_HOME="$PROJECT_DIR/.tooling/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

cd "$PROJECT_DIR"
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest

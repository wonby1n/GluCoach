#!/bin/bash
set -e

IME_ID="com.ssafy.s309/.feature.glucofit.keyboard.GlucoseKeyboard"

./gradlew :app:installDebug
adb shell ime enable "$IME_ID"
adb shell ime set "$IME_ID"

echo ""
echo "Glucofit IME set as default. Current default IME:"
adb shell settings get secure default_input_method
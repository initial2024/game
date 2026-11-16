#!/usr/bin/env bash
set -euo pipefail
classes="$1"
report_dir="$2"
mkdir -p "$report_dir"
adb shell getprop ro.build.version.release > "$report_dir/device.txt"
adb shell wm size >> "$report_dir/device.txt"
adb shell wm density >> "$report_dir/device.txt"
adb shell cat /proc/meminfo >> "$report_dir/device.txt"
if [[ "${CI:-}" == "true" && "${CHRONICLE_ONLINE_TEST:-}" != "true" ]]; then
  adb shell svc wifi disable
  adb shell svc data disable
  echo "Guest network switches after disabling:" >> "$report_dir/device.txt"
  adb shell settings get global wifi_on >> "$report_dir/device.txt"
  adb shell settings get global mobile_data >> "$report_dir/device.txt"
fi
set +e
adb shell am instrument -w -e ci_emulator true -e class "$classes" cn.chronicle.shanhe.test/androidx.test.runner.AndroidJUnitRunner | tee "$report_dir/instrumentation.txt"
runner_status=${PIPESTATUS[0]}
set -e
if [[ "$classes" == *CharacterFeatureTest* || "$classes" == *ScreenTest* || "$classes" == *MapIntegrationTest* || "$classes" == *MapRestartTest* ]]; then
  adb pull /sdcard/Pictures/ChronicleCI "$report_dir/screenshots" || true
fi
adb logcat -d -s System.out:I ChronicleMap:V chromium:E > "$report_dir/model-output.log" || true
if [[ "$runner_status" -ne 0 ]] || ! grep -Eq '^OK \([1-9][0-9]* tests?\)' "$report_dir/instrumentation.txt"; then
  echo 'Instrumentation did not report a successful test run.'
  exit 1
fi

if [[ "$classes" == *RealModelTest* ]]; then
  grep -q 'REAL_MODEL ' "$report_dir/model-output.log"
  grep -q 'REAL_CHARACTER_CHAT' "$report_dir/model-output.log"
  grep -q 'REAL_INTENT_STRUCTURED_VALID=true' "$report_dir/model-output.log"
fi

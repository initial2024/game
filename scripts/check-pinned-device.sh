#!/usr/bin/env bash
set -euo pipefail
if [ "$OPTIMIZED_RELEASE" = "true" ]; then
  adb install app-x86_64-release.apk
  adb install app-release-androidTest.apk
  curl -fL --retry 2 https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/9217f5db79a29953eb74d5343926648285ec7e67/qwen2.5-0.5b-instruct-q4_k_m.gguf -o "$RUNNER_TEMP/release-model.gguf"
  echo "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db  $RUNNER_TEMP/release-model.gguf" | sha256sum -c -
  adb shell mkdir -p /sdcard/Android/data/cn.chronicle.shanhe/files
  adb push "$RUNNER_TEMP/release-model.gguf" /sdcard/Android/data/cn.chronicle.shanhe/files/qwen.gguf
  adb shell am start -W -n cn.chronicle.shanhe/cn.chronicle.game.MainActivity
  CHRONICLE_ONLINE_TEST=true bash scripts/run-device-tests.sh cn.chronicle.game.ReleaseSmokeTest "$RUNNER_TEMP/map-device-report/optimized"
  adb shell am force-stop cn.chronicle.shanhe
  CHRONICLE_ONLINE_TEST=true bash scripts/run-device-tests.sh cn.chronicle.game.ReleaseRestoreTest "$RUNNER_TEMP/map-device-report/optimized/restart"
else
adb install app.apk
adb install test.apk
CHRONICLE_ONLINE_TEST=true bash scripts/run-device-tests.sh cn.chronicle.game.HubLiveTest "$RUNNER_TEMP/map-device-report/huggingface"
bash scripts/run-device-tests.sh cn.chronicle.game.AutoLoadChatTest "$RUNNER_TEMP/map-device-report/auto-load"
bash scripts/run-device-tests.sh cn.chronicle.game.CharacterFeatureTest "$RUNNER_TEMP/map-device-report/characters"
bash scripts/run-device-tests.sh cn.chronicle.game.ScreenTest,cn.chronicle.game.StorageTest "$RUNNER_TEMP/map-device-report/existing"
adb shell am force-stop cn.chronicle.shanhe
bash scripts/run-device-tests.sh cn.chronicle.game.MapIntegrationTest "$RUNNER_TEMP/map-device-report"
adb shell am force-stop cn.chronicle.shanhe
bash scripts/run-device-tests.sh cn.chronicle.game.MapRestartTest "$RUNNER_TEMP/map-device-report/restart"
adb shell pm clear cn.chronicle.shanhe
bash scripts/run-device-tests.sh cn.chronicle.game.ChronicleFeatureTest "$RUNNER_TEMP/map-device-report/chronicle"
fi

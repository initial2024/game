# 0.7.1 build and verification

This is a verified incremental update, not completion of all historical requests. The [complete requirements](REQUIREMENTS.md) and [handoff](HANDOFF.md) preserve the remaining strategy, character, investigation, map and performance work.

## APK

[Download the actual ARM64 APK](https://raw.githubusercontent.com/initial2024/game/apk-preview/chronicle-ai-0.7.1-arm64.apk). Package `cn.chronicle.shanhe`, version 0.7.1 / code12, 3,518,016 bytes (about3.36MiB), model weights separate.

SHA256: `7808992b153d3d6f15566e06572b54924584de639391ad41cc798caf87a474ed`.

Persistent development certificate SHA256: `ebd2aa94bee6d781d020e45a203c9b8f2d416a4f4b654da7cfadc9a67e438c49`. Signature verified and zipalign passed; all three ARM64 libraries have 16KiB LOAD alignment and are compressed. Private signing material is not included. The public APK was re-downloaded in full and matched the hash above.

## Changes

- Character message send awaits verification/loading of the installed default GGUF before real inference. No repeated manual model-center loading prerequisite. No automatic weight download.
- One shared mutex serializes native loading and generation; repeated load requests reuse a loaded model. Header/hash/memory checks remain mandatory. Checksum cancellation and JNI cancellation monitoring retain the file and save.
- Character dialog shows automatic loading, progress/cancel, model settings and expandable feedback. A persistent setting can disable auto-loading.
- Missing/corrupt/incompatible/insufficient-memory models retain a clear reason when falling back. The final template-save path no longer overwrites the error. Each reply retains its real model/template provenance.
- A model being imported/replaced is not loaded concurrently with that replacement. All eligible characters use the same adapter; relationship/decree effects still come only from validated rules.
- English project introduction, Chinese entry, historical requirements and portable handoff are published on main and the development branch. Updating GitHub About metadata separately returned403 (integration lacks administrative access); that field is not claimed updated.

## Actual verification

Source: `2544aedc4cb9097edff3d92d7f28ad0240149440`. [Actions44089542122](https://github.com/initial2024/game/actions/runs/44089542122) completed successfully: build, browser-map, android-test and release-smoke passed. Optional signed-apk and standalone native-model-test jobs were intentionally skipped; the separate release-smoke job did run actual GGUF inference. The CI unsigned ARM64 artifact was signed afterwards using the persistent development identity.

- 92 baseline JVM tests passed in the full core suite; no rule changes in this increment. Lint, debug/release and instrumentation compilation passed. A final local compilation also checked the import guard.
- 26 native Android checks passed: four isolated auto-load tests (missing file, explicit opt-out, corrupt checksum, cancellation during checksum), nine character/government tests, seven layout/storage tests, three map tests, one map restart, one chapter/petition test, one online Hugging Face search. [Per-group results](upgrade-0.7.1-evidence/android/summary.json).
- Two optimized-build checks passed. The test deliberately never enters model management or taps manual load: city selection → native rename → rule save/map refresh → chat header confirms unloaded model → send → real auto-load and streamed GGUF reply → database `assistant.source=model` → full process stop → restart/map persistence. [Chat test](upgrade-0.7.1-evidence/optimized/instrumentation.txt), [first-send marker](upgrade-0.7.1-evidence/optimized/model-output.log), [restart](upgrade-0.7.1-evidence/optimized/restart/instrumentation.txt), [actual chat screenshot](upgrade-0.7.1-evidence/optimized/screenshots/release-real-county-chat.png).
- Official Qwen2.5-0.5B-Instruct Q4_K_M,491,400,032bytes, SHA256 `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`; two threads,512 context. Persisted reply shown in the screenshot: “我近来身体安康，没有不适。” This is observed test output, not inserted fixture text.
- Delivered ARM64 and tested optimized x86_64 APKs have identical game DEX:5,051,592bytes,SHA256 `1838adcd2b53dde2a267a0d1a57cacdc4abf7c64da24b6d57350578b5ae7ba47`. Their native architectures remain different.
- Device: GitHub Actions API35/Android15 x86_64 Pixel4 configuration,390×844,density160,about2473MiB RAM/two CPU threads. These are emulator results, not vivo Android16 or ARM64-phone benchmarks.

The older run44088955923 was intentionally canceled after the final import guard; it is not the passing evidence. Current verification used the newer exact source above.

## Limits

No physical vivo/ARM64-phone acceptance. Cancellation during file verification was newly tested; this update does not claim a new systematic phone/JNI-load cancellation or thermal benchmark. The optimized test log contains a WebView renderer-exit entry (code-1) around closing/reopening the map; reopening, native work and restart succeeded, but its cause and long-session renderer memory still need investigation. No claim of zero leaks or no renderer exits is made.

The remaining requirements are still open/partial as registered: deeper Age-of-History-style countries/provinces, map command/movement interactions, multi-front wars, autonomous nations, four-domain corruption, richer cases and genealogy, persistent AI histories, rewind, balance, cache/grammar/thermal/ARM optimization, large exports and phone testing. New development after this release must get its own source/build evidence.

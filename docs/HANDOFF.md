# Developer handoff

[Project](../README.md) · [All requirements](REQUIREMENTS.md) · [中文需求](REQUIREMENTS.zh-CN.md)

Updated 2026-11-17 (Asia/Singapore). Read this and the full requirements register first. Continue the same game; the user has authorized code, tests, CI and development APK delivery. Do not require them to reconstruct previous requests.

## Repository and branch

- Repository: https://github.com/initial2024/game .
- **Work on `feature/character-chat-hf-search`, Draft PR https://github.com/initial2024/game/pull/2 .**
- Default `main` has older gameplay plus updated documentation. This docs update does not merge the game. Never build old main and call it current.
- Baseline before this docs update: `f10fb00c3c1a9cf06a36791b80c0a06d270bf438`. Shipped 0.7 source: `0b5e24912eb96619b297a8f3b4c23bf6c0a71b7a`; later commits fixed tests/CI and recorded evidence.
- `apk-preview` contains actual APKs and pinned test pairs, not source. Check remote heads/local changes before starting.
- [Current APK](https://raw.githubusercontent.com/initial2024/game/apk-preview/chronicle-ai-0.7.0-arm64.apk): 0.7.0/code11, 3,518,016 bytes, SHA256 `e7ee26053f23ca8e653295dcac2eb080ccdbd7347efaaaeb37b124e836cae472`.
- Existing environment checkout: `/workspace/work/chronicle-ai`. Clone the development branch elsewhere; absolute workspace paths are not portable dependencies.

## Immediate next change: send automatically loads model

The latest user answer says they want to send a message and have the installed default model load automatically.

1. `ModelManager.kt` uses `filesDir/models/active.gguf`, with hash/settings in `inference` preferences. Current manual `load()` is asynchronous without an awaitable outcome. Add a shared, coalesced, cancellable load path that retains SHA/header/memory checks and the JNI mutex.
2. `GameViewModel.sendChat()` only calls real inference if `models.isLoaded` was already true. Installed-but-unloaded currently always uses templates. Await loading on send; no silent large download.
3. Keep missing-file/failure reason visible and offer model management. An explicit auto-load setting can preserve a user's preference for templates.
4. Current generation catch sets an error note, but final fallback save overwrites it with generic text. Retain the real reason. Cancel must not turn into a reply or execute CONVERSE twice.
5. Show verification/loading/generation in the compact dialog; preserve identity filtering and the common inference path for all eligible characters.
6. Modify optimized real-GGUF smoke to start unloaded and send directly, checking persisted `assistant.source=model`. Verify corrupt/no-model cases and cancellation during load. Do not claim this task complete just because code compiles.

Afterwards continue P1/P2 in REQUIREMENTS.md. The user asks for all historical requests, not only this convenience change.

## Architecture / invariants

- `core/src/main/kotlin/cn/chronicle/core/`: pure Kotlin World/Rules/RealtimeEngine and engines for economy, characters, events, education, family, sovereigns, cases, warfare, local troops, payroll, resources, terrain, canals, map and chronicles.
- `app/src/main/java/cn/chronicle/game/`: Compose, GameViewModel, SQLite Store, ModelManager, ModelDownloadService, HuggingFaceClient, WorldMapScreen and panels.
- `app/src/main/cpp/bridge.cpp`: llama.cpp JNI loader/streaming/templates/cancel. `vendor/llama.cpp`: b5046 source and notices; model weights remain separate.
- Foreground clock advances every two wall-clock seconds by selected game-day multiplier. Quarterly accounting is internal. No next-turn button, fee or wait for ordinary dialogue.
- Revision/CAS and SQLite transactions protect state. Rules settle before AI; strict command/entity/legal checks gate every effect.
- Dialogues keyed by save/person; relationship anti-farming, visible-identity filtering and no AI-created evidence/guilt.
- Current hierarchy: 8 provinces/14 prefectures/171 counties, 197 local units plus central armies and hundreds of officers. Keep real city buildings/resources/officers/troops.
- Eight save slots; whole-save export currently 8MiB/10k decisions/2k chats; separate streaming full-chat export exists. Never delete records to force export under the limit.

## Map provenance

Original latest map: `integration/map-upgrade/upstream/chronicle_world_map_v3_2.html`, SHA256 `2553b2c5af23f80895a315154ece758d728813c39095f9649d9aad230a82fd56`.

Active: `app/src/main/assets/maps/chronicle_world_map.html`. Tool/patch: `scripts/upgrade-map.py`, `integration/map-upgrade`. Run `python scripts/upgrade-map.py --check`; regenerate reproducible patch when output changes. Preserve original visuals and controls.

WebViewAssetLoader HTTPS, CSP, stable-ID read-only messages, snapshot/delta ordering and save isolation. Sandbox editing cannot alter trusted simulation. Terrain extracted from the fictional supplied map is reference data, not real DEM/mineral surveys.

## Build / device tests

JDK17, Gradle8.13, SDK36, BuildTools35.0.0, NDK27.2.12479018, CMake3.22.1; min26/target36, ARM64+x86_64, 16KiB native alignment.

```sh
git switch feature/character-chat-hf-search
./gradlew :core:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
python scripts/upgrade-map.py --check
```

Existing environment: source `/workspace/work/java-env.sh`; Gradle `/workspace/work/tools/gradle-8.13/bin/gradle`, cache `/workspace/work/gradle-cache`. Use portable setup elsewhere. Run Gradle invocations sequentially on a checkout; parallel calls previously corrupted intermediates.

- `.github/workflows/android.yml`: core/lint/debug/release/browser/device/optimized inference jobs; dispatch on development branch. Report each actual outcome.
- `map-device-check.yml`: fast device workflow, `optimized_release` input, exact pairs pinned by `.github/device-apk-revision` and `.github/release-apk-revision` from `apk-preview`. Never validate changed code with an old pair.
- `scripts/check-pinned-device.sh` / `run-device-tests.sh`: execute/check instrumentation, collect screenshots/crashes. Online only with deliberate `CHRONICLE_ONLINE_TEST=true`.
- `app/src/releaseTest/.../ReleaseSmokeTest.kt` / `ReleaseRestoreTest`: optimized black-box native/map/SQLite/real-model/restart. Build with `-Pchronicle.testRelease=true :app:assembleRelease :app:assembleReleaseAndroidTest`; this pair's test-only debug signature is not the shipped stable identity.
- No usable local hardware emulator at baseline. Actions uses Android15/API35 x86_64 Pixel4, 390×844, density160, about2473MiB/2CPU. No vivo ARM64 acceptance exists.

### Existing pitfalls

- R8 must retain NativeBridge, NativeTokenSink.onProgress(byte[]) and shared instrumentation Kotlin/tracing ABI. Current keep rules retain Kotlin runtime and androidx.tracing.Trace; removing blindly crashed runner before tests.
- ErrorProne annotation dontwarn entries are test-only. Do not hide missing runtime classes globally.
- Wait for actual rendered world header/navigation, not only state.
- Compose performScrollTo can leave controls behind bottom nav. Existing viewport-aware scroll helper prevents missed war/confirm clicks.
- Two “设置” labels: model center uses RadioButton; avoid matching archive's settings label or duplicating navigation.
- Android15 background-launch restrictions require optimized UI tests to start via UIAutomator shell am start.
- emulator-runner runs each script line separately; put branches in a bash file.
- Keep JVM/test/CI invocations sequential locally; avoid duplicate hundreds-of-MB model downloads on limited disk.

## Models and signing

Verified emulator model: official Qwen2.5-0.5B-Instruct Q4_K_M, Apache2.0, 491,400,032 bytes, fixed revision `9217f5db79a29953eb74d5343926648285ec7e67`; hash `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`.

Existing `/workspace/work/qwen.gguf` can be verified/staged. Source:
https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/9217f5db79a29953eb74d5343926648285ec7e67/qwen2.5-0.5b-instruct-q4_k_m.gguf

Official ModelScope full-file hash passed; HF Mirror only Range/length/header tested. Other search results/recommendations are not proof of compatibility. Actual UI tests must infer real text and persist model provenance, not inject a fixed reply.

Stable-sign environment variable names: CHRONICLE_KEYSTORE, CHRONICLE_STORE_PASSWORD, CHRONICLE_KEY_PASSWORD, alias chronicle. Actions additionally needs CHRONICLE_KEYSTORE_BASE64. Never reveal values, private paths/passwords or keys in logs/source/artifacts. Public development cert SHA256: `ebd2aa94bee6d781d020e45a203c9b8f2d416a4f4b654da7cfadc9a67e438c49`.

If private key is unavailable, preserve unsigned/test builds and report stable signing blocked. Do not generate a replacement identity and call it an update. Increment versionCode on changed shipping code; no unconfirmed commercial release.

## Evidence / handoff discipline

[0.7 report](https://github.com/initial2024/game/blob/f10fb00c3c1a9cf06a36791b80c0a06d270bf438/docs/UPGRADE_0.7_TESTING.md); raw `docs/upgrade-0.7-evidence` on development branch.

- 92 JVM tests; one parameter sweep covers64×6 combinations, not384 authored stories. Existing1,000-quarter replay regression included.
- [44063583058](https://github.com/initial2024/game/actions/runs/44063583058): build/browser passed; two old device jobs failed. Do not call the entire run green.
- [44065322668](https://github.com/initial2024/game/actions/runs/44065322668): optimized actual GGUF/restart2/2.
- [44067225670](https://github.com/initial2024/game/actions/runs/44067225670): full native22/22.
- Baseline delivered ARM64 and tested optimized x86_64 share DEX SHA256 `3c702273f18c705d69e2541619dc0bc226a55b61583f74f835a7de90f17af886`; native architectures remain different.

Each new delivery needs exact source/CI outcomes, APK size/hash/cert, model/device, actual tests, limitations and public APK re-download verification. Update requirements/handoff. If interrupted, record unfinished code, branch/commit and next reproducible command; do not leave only a conversational promise.

## References

Tianming inspected as README-only design, not a playable engine. The Orient Doomsday is Age of History3 mod data/assets, not a reusable standalone engine. No recognized repo license was exposed. Continue original implementation; do not import unlicensed content.

Next after auto-load: strategic choices/province jurisdiction/country AI; life-cycle/succession cleanup; case chains; persistent bounded AI history; once-only rewind; map overlays/movement; balance and presentation/device profiling. Full criteria remain in REQUIREMENTS.md.

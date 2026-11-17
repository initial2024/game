# Developer handoff

[Project](../README.md) · [All requirements](REQUIREMENTS.md) · [中文需求](REQUIREMENTS.zh-CN.md)

Updated 2026-11-17 (Asia/Singapore). Read this and the full requirements register first. Continue the same game; the user has authorized code, tests, CI and development APK delivery. Do not require them to reconstruct previous requests.

## Repository and branch

- Repository: https://github.com/initial2024/game .
- **Work on `feature/character-chat-hf-search`, Draft PR https://github.com/initial2024/game/pull/2 .**
- Default `main` has older gameplay plus updated documentation. This docs update does not merge the game. Never build old main and call it current.
- Baseline before this docs update: `f10fb00c3c1a9cf06a36791b80c0a06d270bf438`. Shipped 0.7 source: `0b5e24912eb96619b297a8f3b4c23bf6c0a71b7a`; later commits fixed tests/CI and recorded evidence.
- `apk-preview` contains actual APKs and pinned test pairs, not source. Check remote heads/local changes before starting.
- [Current verified APK](https://raw.githubusercontent.com/initial2024/game/apk-preview/chronicle-ai-0.7.1-arm64.apk): 0.7.1/code12, 3,518,016 bytes, SHA256 `7808992b153d3d6f15566e06572b54924584de639391ad41cc798caf87a474ed`.
- Existing environment checkout: `/workspace/work/chronicle-ai`. Clone the development branch elsewhere; absolute workspace paths are not portable dependencies.

## Verified 0.7.1 and active 0.8 work

0.7.1/code12 is delivered. Source `2544aedc4cb9097edff3d92d7f28ad0240149440`, artifact branch commit `697fc50`, [full green Actions44089542122](https://github.com/initial2024/game/actions/runs/44089542122). 92 JVM tests,26 native checks and2 optimized actual-GGUF/restart checks passed. See [0.7.1 report](https://github.com/initial2024/game/blob/feature/character-chat-hf-search/docs/UPGRADE_0.7.1_TESTING.md). Automatic loading on first send, retained fallback causes, settings/feedback and cancel are implemented; do not reimplement the old missing feature.

**Active development is 0.8.0/code13; do not label it delivered until its own checks pass.** The user clarified that the map must be an interaction hub, beyond strategy panels, and asked whether the references have maps. [REFERENCES.md](REFERENCES.md) records inspected map/province/scenario data and the original implementation approach.

New code:

- `MapOrders.kt`: resolve current country/city/territory ownership by stable IDs; domestic army deployment, foreign mobilization/current-front orders and targeted diplomacy. WebView still sends selections only.
- `WorldMapScreen.kt`: native map-army selection and point-to-order preview; foreign diplomacy/intelligence/ruler tabs. Use current callbacks/state, not a stale WebView-factory capture. County clicks retain real building/officer/troop pages. New controls do not replace the supplied HTML.
- `WarfareEngine.kt`: steady/rapid/siege differ in time, money, raw materials, supply, power, losses and progress; named envoy negotiation, real eligibility/cost/time/refusal/cooldown/memory; forced border peace requires actual leverage. Saved campaign IDs prevent stale orders applying to a later war, and ended fronts refund/cancel obsolete orders.
- `DialogueEngine.kt`: formally contacted foreign rulers are diplomatic equals, not ministers; role-visible identity/context and anti-submissive output filtering. Conversation cannot change borders or international relations.
- New core tests `CampaignChoicesTest` / `MapOrdersTest`; native tests extend map touch → targeted mission → map-selected army → actual queued move/arrival/SQLite and strategy previews. Old region-0 or `territory-r2` assumptions were incorrect fixtures: Chang'an county maps to region1 and its current prefecture territory ID is `prefecture-jingji`.

Next: run/inspect the 0.8 full CI build,106 core checks, native map/strategy regressions and optimized real-model/restart flow; fix failures, collect evidence and deliver a matching stable-signed ARM64 APK. Update the current APK/report/requirements here. Current0.8 source: `475aa5358442fa7b5acf47f54cbd424163a7c639`; [CI44097229976](https://github.com/initial2024/game/actions/runs/44097229976) is under verification. Check actual outcomes rather than assume success.

This is still a single-front prototype with regional deployment, not complete Age-of-History-level simulation. Moving army/trade markers, full new-territory jurisdiction/local finances, autonomous countries, multiple fronts and many historic requests remain partial/open. Continue the whole requirements register, not just this increment.

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

## Repository metadata limitation

English README/backlog/handoff were successfully published to main in docs-only commit `2f978881055c6af8b75b4e5cb52331c5271f063c`; gameplay was not merged. Updating the GitHub About description/homepage through the managed integration returned HTTP403, Resource not accessible by integration. The public README already contains the English introduction and all backlog links. About metadata remains pending administrative capability; do not report it updated.

## Resume checkpoint: execution environment unavailable

The execution environment disconnected on2026-11-17. All0.8 gameplay/code/tests are safely pushed at `475aa5358442fa7b5acf47f54cbd424163a7c639`; no gameplay merge to main has occurred. CI44097229976 continues independently. Check its actual job results and fix any failed native/map checks before calling0.8 verified. The current verified stable-signed download remains0.7.1. The0.8 APK cannot yet be signed with the established private development key because that key's environment is unavailable; do not generate a different identity or claim a test signature can overwrite it.

Resume in a restored or fresh environment: fetch the development branch, inspect/preserve local diffs, recover the existing private signing identity through its authorized private channel, obtain the exact CI ARM64 release artifact, verify source/DEX/hash/certificate/alignment, sign it, re-download the published APK and update evidence. A restored working tree may contain uncommitted documentation-only checkpoint edits; compare and preserve them before pulling. Use a separate checkout if needed. Never upload the key or player saves. GitHub source/docs/CI remain accessible even while shell access is unavailable.

## 0.8 device rerun checkpoint

Original application source remains `475aa5358442fa7b5acf47f54cbd424163a7c639`. CI44097229976 passed build/core/lint/browser-map/optimized real-GGUF and restart. Its native regression passed four auto-load fault tests, then stopped at live Hugging Face DNS resolution: an earlier offline group disabled the network, and the script did not restore it. This did not execute the later map/strategy assertions and is not a passing native acceptance.

Harness fixes restore guest networking explicitly for online groups and run live search before offline groups. `.github/device-ci-run.json` now pins run44097229976/source475aa53 and its exact `automated-tests-and-development-apk` pair. The fast map workflow verifies run/source provenance, downloads that existing pair and records checksums, then reruns all native groups; it does not substitute older pinned0.7 APKs. [Rerun44100027922](https://github.com/initial2024/game/actions/runs/44100027922), harness source `922df2c4f338e6b05e452bc4c06aebd8ee8cbc32`, is in progress at this checkpoint. Inspect its result and fix any real UI failures; do not mark the native loop complete until it passes. Optimized-release=true still uses the separately pinned old pair; that mode must not be claimed as new0.8 proof. The independent0.8 optimized CI job above is the correct current evidence.

0.8 stable signing remains blocked by the unavailable private-key environment. 0.7.1 is still the verified installable download. All historical requirements, including autonomous nations, moving map markers and full local jurisdiction, remain recorded; this increment is not the entire requested game.

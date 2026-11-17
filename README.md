# Chronicle AI

An original, offline, native Android historical strategy game: govern a country, develop provinces, investigate corruption, manage characters and armies, and optionally converse through an on-device language model. Continuous, pausable time supports 1×, 4×, 16× and 64× speeds.

[简体中文](README.zh-CN.md) · [Complete requirements / remaining work](docs/REQUIREMENTS.md) · [Developer handoff — start here](docs/HANDOFF.md) · [Development source / Draft PR](https://github.com/initial2024/game/pull/2)

> Development build, not a finished game. The default branch carries older gameplay plus current documentation. Continue code work on `feature/character-chat-hf-search`; this documentation update does not merge the gameplay branch.

## Download and verified baseline

[Download 0.7.0 ARM64 APK](https://raw.githubusercontent.com/initial2024/game/apk-preview/chronicle-ai-0.7.0-arm64.apk) — 3,518,016 bytes / about 3.36 MiB. Model weights are separate; the game is playable without them.

SHA-256: `e7ee26053f23ca8e653295dcac2eb080ccdbd7347efaaaeb37b124e836cae472`.

[Build, test evidence and limitations](https://github.com/initial2024/game/blob/f10fb00c3c1a9cf06a36791b80c0a06d270bf438/docs/UPGRADE_0.7_TESTING.md): 92 passing JVM tests, 22 passing native Android regression checks, and two optimized-build checks including real GGUF inference and process-restart persistence. These are Android 15 x86_64 emulator results, not vivo or ARM64 phone measurements. The original full build run had device-job failures; subsequent focused verification passed, as recorded in the report.

Package: `cn.chronicle.shanhe`. Published development APKs share a persistent development signing certificate; private keys are never committed. Changing that key later needs an explicit migration plan.

## Playable today

- Ancient dynasty and early-industrial settings, customizable country/ruler/era, difficulty, seed, national scale and international climate.
- Deterministic economy, budgets, population, research, projects, terrain/resources, diplomacy and initial strategic warfare; validated policy previews and confirmation.
- Four native Compose tabs, compact subpages, local SQLite saves, and the user's supplied map upgraded inside a restricted offline WebView.
- Eight provinces, 14 prefectures and 171 counties in the default hierarchy; city buildings, officers, resources, county/provincial troops, salaries and canals.
- Royal journeys, disguises, identity verification, evidence, petitions and 64 authored case premises with seeded factual variants and different consequences.
- Free character conversations and relationship changes, proposed decrees, recruitment, examinations, appointments, academies, overseas study, family naming, adult consorts, optional pregnancy and initial succession.
- Optional guidance, first-year protection, guards, fictional espionage organizations, chapter objectives, quarterly reports, standing grain orders and four initial development routes.
- Hugging Face GGUF search, device-memory recommendations, local import, ModelScope / HF Mirror / original-source choices, resumable foreground downloads, hash checks, llama.cpp inference and labeled template fallback.

## Full development backlog

All historical requests, statuses and acceptance criteria are in the [complete requirements register](docs/REQUIREMENTS.md). Still incomplete or requiring broader validation:

1. **Conversation convenience:** auto-load the installed default model on message send; show loading, retain failure reasons, allow cancellation and verify every eligible character uses real inference.
2. **Historical strategy:** meaningful province jurisdiction, autonomous countries, multiple fronts, supply, assault/siege/diplomacy, treaties and era-specific institutions, informed by the actual *Age of History* game.
3. **Investigation depth:** higher-quality case chains, motivated reversals, ambiguity, linked clues, evidence-board presentation, confrontation, false-evidence counterplay and varied petitions; complete four-domain corruption accounts.
4. **Characters and continuity:** autonomous lives, faction coalitions, genealogy, per-ruler relationships, recovery/lifespan, inheritance disputes, richer regency, teaching balance and lasting appointments.
5. **Local government and travel:** independent local finances, county/prefecture policies, settlement growth/damage/recovery, richer terrain production, delegated state affairs and revisit consequences.
6. **War, intelligence and safety:** deeper armies, commanders, spy networks, conspiracies, fictional missions/bounties, evidence and countermeasures; guard and assassination consequences across country sizes.
7. **Narrative and goals:** persisted bounded AI histories; deeper investigation → judgment → construction → pressure → inheritance loop, route endings and once-per-game quarter rewind.
8. **Economy and free actions:** complex validated financing and partial execution, transparent cash-flow forecasts, standing orders, rescue choices and many-seed ten-year balance statistics.
9. **Map and presentation:** tax/security/morale overlays, moving armies/trade, drawing caches, memory profiling, compact layouts, sound, haptics, readable fonts, ink transitions and accessible seal confirmation.
10. **On-device AI:** additional licensed lightweight-model tests, grammar-constrained JSON, KV-prefix reuse, thermal adaptation, ARM instruction dispatch, measured recommendations and mirror/background-download reliability.
11. **Saves and verification:** larger streaming exports, migration/isolation/interruption regressions, long-session memory checks, measured baseline profiles and actual vivo Android 16 / ARM64 acceptance.
12. **Delivery and handoff:** stable package/signature, real Actions builds/APK links, complete source, honest evidence/limitations and continuously updated bilingual requirements.

## Build and contribute

Read [HANDOFF.md](docs/HANDOFF.md) before changing code. Use JDK 17, Gradle 8.13, Android SDK 36, Build Tools 35.0.0, NDK 27.2.12479018 and CMake 3.22.1. The development branch uses Kotlin/Compose, pure Kotlin `core`, SQLite and vendored llama.cpp b5046; minSdk 26 / targetSdk 36.

```sh
git clone https://github.com/initial2024/game.git
cd game
git switch feature/character-chat-hf-search
./gradlew :core:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
```

Run device tests separately on a configured emulator or phone. Actions records real test/build artifacts; optimized inference must be tested as well as debug builds. Models are optional; no paid API is required and player saves are not automatically uploaded.

## References and permissions

The user cited *Age of History*, [Tianming](https://github.com/Barbatos923/Tianming) and [The Orient Doomsday](https://github.com/ZANReping/The-Orient-Doomsday). At inspection, Tianming contained a design README; the latter was an Age of History 3 mod data/assets repository. Neither exposed a recognized repository license. No code or assets from them have been imported. This remains an original game; retain third-party/model licenses and do not perform an unconfirmed commercial launch.

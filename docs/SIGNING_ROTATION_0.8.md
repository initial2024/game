# Chronicle AI v0.8.0 — signing identity rotation (2026-11-18)

The owner explicitly authorized creating a NEW Android release signing identity after the former private key became unavailable. The old certificate cannot be used to update the new signed builds.

## Public, non-secret certificate fingerprint

- New certificate SHA-256: `fd1025cdc4d99c82720d6af49812d6b0491c5be03305a086608d97f0615fa768`
- Algorithm: RSA-3072, SHA256withRSA, PKCS#12
- Key alias: `chronicle`
- Verified PKCS#12 keystore SHA-256 (private backup file, do not publish its bytes): `5194836b7d2ea4bc347630e2181362cc13b22a1d3432a3f0ffe207d11b6915df`
- Certificate was regenerated and locally checked from the private PKCS#12 backup; previous placeholder certificate (`0ed01577...`) is not the signing identity.
- Old certificate SHA-256 (legacy APKs only): `ebd2aa94bee6d781d020e45a203c9b8f2d416a4f4b654da7cfadc9a67e438c49`
- Application ID remains `cn.chronicle.shanhe`
- v0.8.0: `versionCode=13`, `versionName=0.8.0`
- ARM64 APK file: `apk-preview/chronicle-ai-0.8.0-arm64.apk`

## User data migration constraint

Android refuses an in-place APK update when the old and new signing certificates differ. First export or back up private SQLite game saves, user preferences, and any local GGUF model(s) through an existing, proven app backup/export workflow **before uninstalling** the old APK. Do not assume that copying the public Documents directory includes the app-private database. Uninstalling may irreversibly delete app data.

Do not attempt to alter `applicationId` merely to bypass the signing mismatch. The user may uninstall only after data retention is verified.

## Private key safety

The new `private-signing.p12` and its passwords must **never** be committed or uploaded as a public artifact or included in a PR comment. Store private backup copies offline in an encrypted vault. A lost new signing key prevents future in-place updates of newly signed releases.

The repository owner must set three GitHub Actions repository secrets using the private handoff files (not committed):

- `CHRONICLE_KEYSTORE_BASE64`: single-line base64 encoding of the P12 file
- `CHRONICLE_STORE_PASSWORD`: P12 store password
- `CHRONICLE_KEY_PASSWORD`: P12 key password

GitHub Actions secrets: `https://github.com/initial2024/game/settings/secrets/actions`.

The release workflow still requires the four existing success gates `build`, `browser-map`, `android-test`, `release-smoke`, and a trusted `[release-0.8]` push on the original branch before automatic publication. No bypasses.

After publish, independently verify APK signer certificate, SHA-256 checksum, applicationId, version, ABI, source SHA, and `chronicle-ai-0.8.0-build.json` from the actual `apk-preview` branch.

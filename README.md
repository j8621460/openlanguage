# Palabras: function-word dictionary + translator (Android 8 → 16)

Offline dictionary of the A1–B1 function words from the eight guides
(German, French, Italian, Portuguese, Dutch, Russian, Arabic, Irish → Spanish):
**2,476 entries** with Spanish meaning, examples, "watch out" notes and chapter
filter. Each entry can show its **English** meaning, and the **Translator** tab
translates any text to English (or any of the 10 languages) with the
**Google Cloud Translation API**. Without an API key, it opens Google Translate
instead, so the app is fully usable with no setup.

- minSdk 26 (Android 8.0), targetSdk/compileSdk 36 (Android 16)
- Kotlin 2.1.21, AGP 8.10.1, Gradle 8.11.1, JDK 17, no Compose, 7 small AndroidX deps
- Text-to-speech for every language that has a voice installed
- Select text in any app → **Translate with Palabras**

## 1. Get the APK (no computer needed)

1. Create a new GitHub repo and upload **everything in this folder**, including
   the hidden `.github/` and `.semgrep/` folders and `gradle/wrapper/gradle-wrapper.jar`.
2. Pushing to `main` runs **📦 Build APK** automatically (or run it from the
   Actions tab → *Run workflow*).
3. Open the finished run → **Artifacts** → `palabras-apk-N` → unzip →
   install `Palabras-debug.apk` (allow "install unknown apps").

## 2. Signed release builds (optional, recommended)

1. Actions → **🔑 Generate release keystore** → Run workflow → type `GENERATE`.
2. Download its artifact, create the 4 repository secrets listed in
   `secrets-to-save.txt` (`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`,
   `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`), keep an offline backup, then
   **delete the artifact and the run**.
3. From now on **📦 Build APK** also outputs a signed, minified
   `Palabras-release.apk`, and pushing a tag `v1.0.0` runs **🚀 Signed release**,
   which publishes `Palabras-v1.0.0.apk` + checksums on a GitHub Release.

## 3. Google Translate API key

1. In Google Cloud console, enable **Cloud Translation API** (needs billing;
   the first 500,000 characters per month are free at the time of writing).
2. *APIs & Services → Credentials → Create API key.*
3. Restrict it: **API restrictions** → Cloud Translation API only;
   **Application restrictions** → Android apps → add the package name and
   SHA-1 shown in the app's Settings screen (debug builds are
   `com.palabrasfuncionales.app.debug` with a different SHA-1 than release).
4. Paste the key in **Settings → Save key → Test key**.

## Security measures

App: HTTPS only and system CAs only (`network_security_config.xml`), no
backups or device transfer, API key encrypted with AES-256-GCM in the Android
Keystore and sent in a header (never in the URL), `X-Android-Package` /
`X-Android-Cert` headers so the key can be locked to the app, only the INTERNET
permission, R8 minification and debug logs stripped in release, no analytics
or third-party SDKs.

CI (same approach as the reference repo): every action pinned to a commit SHA,
read-only default token with per-job permissions, `persist-credentials: false`,
Gradle wrapper validation, no `${{ }}` interpolation of user input inside
scripts, signing secrets only in env vars, keystore deleted after use, no
Gradle cache for release builds, APKs checked with `apksigner` + `aapt`
(package id, minSdk 26, targetSdk 36, not debuggable), SHA-256 checksums.
Scanners: CodeQL (Kotlin + workflows), detekt, Semgrep (+ project rules in
`.semgrep/`), mobsfscan, gitleaks (full history), OSV-Scanner, dependency
review on PRs, Dependabot with a 14-day cooldown.

Optional hardening: add `distributionSha256Sum=` (from
https://gradle.org/release-checksums/) to `gradle/wrapper/gradle-wrapper.properties`.

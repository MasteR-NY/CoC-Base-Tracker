# Build and Release Guide

## Requirements

- JDK 21 or later
- Gradle 9.7.1
- Android SDK Platform 37
- Android SDK Build Tools 36.0.0 or later
- Python 3 for source packaging and optional catalog maintenance

Set `JAVA_HOME` and `ANDROID_HOME`, or use `local.properties` for the SDK location. The build script also accepts `-JavaHome` and `-GradlePath`.

## Build the APK

From the repository root:

```powershell
pwsh -File .\build.ps1
```

The script runs unit and Android framework tests, renders the app and widget previews, runs Android lint, and assembles the release APK. It then aligns the APK, signs it, verifies the signature, and prints its SHA-256 hash.

The version comes from `app/build.gradle.kts`. Signed APKs are written to `outputs/CoC-Base-Tracker-<version>.apk`. Normal builds refresh the tracked preview images in `docs/images/`; `-SkipTests` leaves those images unchanged. See [Preview Maintenance](DESIGN-PREVIEWS.md).

## Signing and Updates

The build creates a local signing keystore and random password in `.private/` if no key exists. Keep that directory private and backed up. It is excluded from Git and the source archive.

Use the same signing key and a higher Android version code for future updates. The Android package remains `com.villageclock.app` to preserve compatibility with earlier releases, including those named Village Clock and CoC Village Tracker.

A source recipient creates their own signing identity. An APK signed with a different key cannot update an existing installation signed with the release key.

## Prepare a Release

1. Update `versionCode` and `versionName` in `app/build.gradle.kts` for application changes. Add a matching entry to `app/src/main/assets/release-notes.json` with highlights and short usage steps; keep earlier entries.
2. Run the normal build and inspect the refreshed preview images.
3. Review test and lint reports under `app/build/`. Record device testing separately from framework tests.
4. Scan the signed APK before distribution.
5. Commit and push the source, README, and updated preview images. Keep `.private/`, local SDK paths, tools, caches, and generated outputs out of Git.
6. Create a GitHub release with the matching version tag, copy that version’s notes from `CHANGELOG.md` into its description, attach the signed APK under Assets, and mark the release as latest.

The README download button opens `https://github.com/MasteR-NY/CoC-Base-Tracker/releases/latest`. It follows the latest published release without depending on a fixed APK filename.

For documentation-only changes, the APK does not need a new version or rebuild.

## What’s New Guide

`app/src/main/assets/release-notes.json` is the source for both the offline app guide and the public changelog. Run `python tools/update_release_notes.py` after editing it to regenerate `CHANGELOG.md`; source packaging also runs this step. Use concrete changes and instructions for each release. Keep earlier entries so users can expand their history in the app.

The home screen shows a summary when the installed version code is newer than the last acknowledged guide. Read Guide and Got It record that version locally. The bottom What’s New button always remains available. Opening the guide makes no network request and does not change saved accounts or widget selections; View GitHub Releases opens the browser only when tapped.

Framework tests require a bundled entry matching both the installed version name and code. They also check summary dismissal, reopening, earlier-release expansion and preservation of account data. Normal builds refresh the guide preview alongside the app, planner and widget images.

## In-App Update Checks

The app header reads `BuildConfig.VERSION_NAME`, so version labels follow the installed build automatically. The bottom Check for Updates button sends an explicit HTTPS GET to `https://api.github.com/repos/MasteR-NY/CoC-Base-Tracker/releases/latest`. If it fails, a second GET checks `https://github.com/MasteR-NY/CoC-Base-Tracker/releases/latest` with automatic redirects disabled. Only a stable numeric tag under the same HTTPS GitHub repository is accepted from its Location header; no redirected destination is fetched. Internet permission is required; cleartext traffic is disabled. No request runs on launch or in a background schedule.

Requests contain an application/version User-Agent and a JSON or HTML Accept header. They do not include credentials, account tags, names or village exports. The client uses twelve-second connection/read timeouts, a 512 KiB API response limit, and normal Android certificate/hostname validation. Version comparison is numeric, so 1.10 sorts after 1.9. A website fallback confirms the latest tag but leaves APK availability unknown; only the API verifies uploaded assets. If both paths fail, the dialog preserves separate API and Releases errors, distinguishing DNS, TLS, timeouts and HTTP status failures, with Retry and browser options. It never treats an error as up to date.

Newer releases trigger an in-app notice and open the fixed repository Releases URL in the browser. A release known from the API to lack an uploaded APK is identified as such. Responses completing while the activity is paused wait until it resumes before opening a browser. The updater never downloads, installs or executes an APK itself. Keep this repository public and attach the signed APK before marking a stable release as latest. Update UI and compact-home tests run on API 26 (Android 8), API 29 (Android 10) and API 35; these are framework checks, not physical-phone networking tests.

## Source and Verification Package

```powershell
python tools/package_release.py
```

This script packages an explicit list of public source files and documentation. It verifies the source archive, excludes signing material, and writes setup instructions, verification metadata, and SHA-256 checksums under `outputs/`.

Packaging requires the signed APK, passing test reports, lint output, matching README previews, and a `Defender-<version>.txt` scan record containing a successful exit code and the matching APK SHA-256. It does not perform a new antivirus scan itself. The original signing-certificate identity is recorded for the locally built release series; distributors using their own signing key must update that metadata to match their actual certificate.

## Data and Validation

Auto-sync registers a clipboard-change listener only while MainActivity is resumed, and reads text only when its window has focus and the preference is enabled. Focus return also queues a check so an export copied in Clash is discovered without background access. A shape filter precedes full validation on the import executor; non-text clipboard URIs are never coerced. A SHA-256 fingerprint suppresses repeated clipboard exports, and saved timestamps prevent older/equal snapshots from replacing or selecting an account. Manual widget imports retain their tag guard. Settings is available before the first account is saved, including the option to disable auto-sync.

Clipboard lifecycle, duplicate/stale handling, settings, account isolation and manual widget guards are tested on API 26, 29 and 35. The supplied personal export is inspected locally and excluded from the source package; tests use synthetic data. See [Village Export Data](EXPORT-DATA.md) for the sample field inventory, calculation example and feature recommendations.

The runtime catalog is an offline community data snapshot. Its source revision, reproduction commands, coverage, and special-rule limitations are documented in the [Upgrade Planner Reference](UPGRADE-PLANNER.md). Countdown calculations are documented in [Timer Rules](TIMING.md).

Village exports have an unofficial schema. Check imported timers against the game when validating a release, especially after new game updates. Framework tests and rendered previews do not replace physical-device checks for launcher behavior, battery restrictions, alarm delivery, or foreground-service lifecycle.

## Attribution

Original application code is MIT-licensed. Third-party presentation resources, the Lilita One font, game artwork, and factual catalog have separate attribution and terms. See [Credits and Third-Party Resources](DESIGN-CREDITS.md).

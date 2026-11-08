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

## In-App Update Checks and Downloads

The header reads `BuildConfig.VERSION_NAME`. Check for Updates performs anonymous HTTPS GETs to the fixed repository API and, if needed, its public latest-release redirect. App requests have 12-second connect/read timeouts, a 512 KiB JSON limit, `Connection: close` to avoid stale pooled sockets, and normal certificate/hostname validation. Only stable numeric tags in this exact repository are accepted. Socket failures retain up to 300 characters of actual system detail, including causes.

If both app paths fail, `UpdateDownloads.fetchMetadata` submits the same API URL to Android Download Manager. This uses the system service’s network path, waits at most 35 seconds, bounds the response, parses the same release schema, and removes its temporary file and download record. It is a fallback, not a way to bypass blocked GitHub access. Checks occur only after a user request. They contain the app version, never village exports, names, tags or credentials.

A newer release displays Download Update / Later without opening a browser. The asset must be an uploaded APK whose HTTPS URL exactly matches this repository, tag and safe filename. A redirect-only result tries `CoC-Base-Tracker-<version>.apk` in the verified tag; a missing asset is reported rather than claimed available. GitHub asset metadata supplies size and optional SHA-256 digest.

After explicit approval, Download Manager writes to this app’s external-files Downloads directory. No broad storage permission is used. The app retains only its own download ID, version, URL, size and digest in separate private preferences. Progress, cancellation, retry and reopening survive activity recreation. An exported completion receiver accepts only the system download-complete action for the owned ID, queries its actual state and verifies the file before posting a notification. It never launches an activity or installer from the background. Background completion notifications require Android notification permission; Update Download remains available without it.

Before offering installation, and again when Install Update is tapped, checks require a completed file no larger than 100 MB, matching expected size, matching GitHub digest when supplied, the installed package name, a newer version code, the offered version name, and the same current signing certificates as the installed app. A missing digest does not waive package/signing/version checks. Android’s installer performs its own final package/signature verification.

`REQUEST_INSTALL_PACKAGES` supports the explicit install flow on Android 8+. If `canRequestPackageInstalls()` is false, the app opens the per-app Install unknown apps setting only after the user chooses Open Settings. On return, the user taps Install Update again. A read-only Download Manager content URI grant opens the system installer; no file URI or silent-install API is used. After a successful app update, the next home opening removes the old owned download.

Keep this repository public and attach an APK signed with the original private key before marking a stable release as latest. API 26/29/35 framework checks cover prompting, safe asset URLs, system fallback selection, download ownership/storage, hash/package/version/key rejection, install-source settings and URI grants. They do not reproduce the user’s physical-phone SocketException or replace device installation tests. Android references: [DownloadManager](https://developer.android.com/reference/android/app/DownloadManager), [canRequestPackageInstalls](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()).

## Source and Verification Package

```powershell
python tools/package_release.py
```

This script packages an explicit list of public source files and documentation. It verifies the source archive, excludes signing material, and writes setup instructions, verification metadata, and SHA-256 checksums under `outputs/`.

Packaging requires the signed APK, passing test reports, lint output, matching README previews, and a `Defender-<version>.txt` scan record containing a successful exit code and the matching APK SHA-256. It does not perform a new antivirus scan itself. The original signing-certificate identity is recorded for the locally built release series; distributors using their own signing key must update that metadata to match their actual certificate.

## Data and Validation

Overview models are in `VillageOverview.java` and `Availability.java`. Hall caps follow the planner’s known facility gates; progress does not promote levels on timer expiry. Availability shares the existing alarm receiver but has per-account cursors and notification ID 8, separate from upgrade-finished ID 7. Import resets the availability cursor to the import time, so already-ready stale exports do not trigger a notification burst. Disabling alerts suppresses notifications and removes their scheduling contribution; upgrade alarms remain independent.

Official source notes are bundled in `app/src/main/assets/official-updates.json` and displayed from Overview → News. Updating these is a release-maintenance task, not a runtime background download. See [Official Game Data References](GAME-DATA.md) for the review procedure. Android-rendered Overview previews are included in normal builds and source packages.

Auto-sync registers a clipboard-change listener only while MainActivity is resumed, and reads text only when its window has focus and the preference is enabled. Focus return also queues a check so an export copied in Clash is discovered without background access. A shape filter precedes full validation on the import executor; non-text clipboard URIs are never coerced. A SHA-256 fingerprint suppresses repeated clipboard exports, and saved timestamps prevent older/equal snapshots from replacing or selecting an account. Manual widget imports retain their tag guard. Settings is available before the first account is saved, including the option to disable auto-sync.

Clipboard lifecycle, duplicate/stale handling, settings, account isolation and manual widget guards are tested on API 26, 29 and 35. The supplied personal export is inspected locally and excluded from the source package; tests use synthetic data. See [Village Export Data](EXPORT-DATA.md) for the sample field inventory, calculation example and feature recommendations.

The runtime catalog is an offline community data snapshot. Its source revision, reproduction commands, coverage, and special-rule limitations are documented in the [Upgrade Planner Reference](UPGRADE-PLANNER.md). Countdown calculations are documented in [Timer Rules](TIMING.md).

Village exports have an unofficial schema. Check imported timers against the game when validating a release, especially after new game updates. Framework tests and rendered previews do not replace physical-device checks for launcher behavior, battery restrictions, alarm delivery, or foreground-service lifecycle.

## Attribution

The optional identity pipeline uses Python with Pillow: `python tools/build_identity_catalog.py --fetch-art`. It vendors pinned metadata, verifies manifest image hashes, and generates compact offline thumbnails plus `item-identities.json`. Normal Android builds use the committed output and do not fetch artwork. Review [Item Names and Artwork](ITEM-IDENTITIES.md) before changing the pinned source revisions, especially unresolved IDs and shared-image variants.

Original application code is MIT-licensed. Third-party presentation resources, the Lilita One font, game artwork, and factual catalog have separate attribution and terms. See [Credits and Third-Party Resources](DESIGN-CREDITS.md).

## Level Artwork Maintenance

After fetching the planner tables and identity manifest, run `python tools/build_level_art.py` with Pillow installed. The optional tool downloads pinned images, validates available manifest hashes, rejects oversized/empty sources, and bundles 128-pixel thumbnails. Exact source and output hashes are written to `docs/level-art-sources.json`. The app’s `level-art.json` contains only resource mappings. Normal builds use committed resources and do not fetch artwork.

Planner model tests cover hall caps, reachable facility prerequisites, hero equipment gates, next-hall visibility, running-job retention and unknown IDs. API 26/29/35 UI checks cover button filters, per-base/category isolation, Cancel/Apply, explicit Catalog Max, empty results, recreation and target artwork. Native render checks include the main list, filter dialog, Army, Builder Base and equipment, plus compact and landscape QA images. The normal build refreshes the README previews.

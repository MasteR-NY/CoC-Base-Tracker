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

1. Update `versionCode` and `versionName` in `app/build.gradle.kts` for application changes.
2. Run the normal build and inspect the refreshed preview images.
3. Review test and lint reports under `app/build/`. Record device testing separately from framework tests.
4. Scan the signed APK before distribution.
5. Commit and push the source, README, and updated preview images. Keep `.private/`, local SDK paths, tools, caches, and generated outputs out of Git.
6. Create a GitHub release with the matching version tag, attach the signed APK under Assets, and mark the release as latest.

The README download button opens `https://github.com/MasteR-NY/CoC-Base-Tracker/releases/latest`. It follows the latest published release without depending on a fixed APK filename.

For documentation-only changes, the APK does not need a new version or rebuild.

## Source and Verification Package

```powershell
python tools/package_release.py
```

This script packages an explicit list of public source files and documentation. It verifies the source archive, excludes signing material, and writes setup instructions, verification metadata, and SHA-256 checksums under `outputs/`.

Packaging requires the signed APK, passing test reports, lint output, matching README previews, and a `Defender-<version>.txt` scan record containing a successful exit code and the matching APK SHA-256. It does not perform a new antivirus scan itself. The original signing-certificate identity is recorded for the locally built release series; distributors using their own signing key must update that metadata to match their actual certificate.

## Data and Validation

The runtime catalog is an offline community data snapshot. Its source revision, reproduction commands, coverage, and special-rule limitations are documented in the [Upgrade Planner Reference](UPGRADE-PLANNER.md). Countdown calculations are documented in [Timer Rules](TIMING.md).

Village exports have an unofficial schema. Check imported timers against the game when validating a release, especially after new game updates. Framework tests and rendered previews do not replace physical-device checks for launcher behavior, battery restrictions, alarm delivery, or foreground-service lifecycle.

## Attribution

Original application code is MIT-licensed. Third-party presentation resources, the Lilita One font, game artwork, and factual catalog have separate attribution and terms. See [Credits and Third-Party Resources](DESIGN-CREDITS.md).

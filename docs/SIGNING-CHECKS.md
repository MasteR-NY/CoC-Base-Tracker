# Signing Checks

An update must use the installed app's package name and signing certificates, with a newer version. A different signing key cannot be repaired by changing a filename, version number or README. Do not uninstall an existing app merely to get past a verification error: its saved accounts are local.

## What Was Verified

On November 8, 2026, the public v1.19 APK was downloaded from this repository and checked with the Android SDK's `apksigner verify --print-certs` command. It is byte-for-byte identical to the supplied v1.19 build and uses the same certificate as the earlier local release builds.

| Record | SHA-256 |
| --- | --- |
| Published v1.19 APK | `cc5dc60069b0781a9ec6f7a5a73a75c39cc9fa0e0c0face265523c64849bb467` |
| Release signing certificate | `607696bd8e6ae0130aa00429915fab8de31ce5383fcf8886a4306a1db8767a71` |

These are public fingerprints, not private signing material. The APK installed on the reporting phone was not retrieved, so its actual certificate cannot be established from a screenshot.

## Compatibility Change in Version 1.20

Earlier checks could report a mismatch when Android supplied a non-null modern signing record with no certificates, even when legacy certificate data was available. Version 1.20:

1. Requests both `GET_SIGNING_CERTIFICATES` and `GET_SIGNATURES` on Android 9 and later, and the legacy flag on Android 8.
2. Uses a nonempty modern signer set when available; otherwise reads legacy certificates.
3. Retries a legacy-only metadata query when no certificate was read.
4. Distinguishes an unreadable certificate from a genuine mismatch.
5. Requires the complete, nonempty signer sets to match before installation is offered.

A conflicting legacy record cannot override a nonempty modern signer set. Missing certificates remain blocked. Size, optional GitHub digest, package and version checks are retained, and Android independently checks signatures during installation. The change handles a tested metadata failure path; it does not claim the phone-specific cause was reproduced.

Android's [PackageManager reference](https://developer.android.com/reference/android/content/pm/PackageManager) documents the metadata flags; [SigningInfo](https://developer.android.com/reference/android/content/pm/SigningInfo) describes current signer records. The legacy path is retained for compatibility, not to accept an unverified APK.

## Updating From an Affected Version

Download the signed APK directly from the release page or supplied download link, then open it from the phone's file manager. Install it over the existing app. This lets Android's installer check the update without relying on the older app's checker. No uninstall or data reset is required for releases using the original signing key.

If Android itself rejects the update, keep the existing app and locate a build signed with its original key. A separately built or re-signed APK may have a different certificate. Restoring the matching private signing key is a release-maintenance action; never publish or send that key.

## Certificate Details

A certificate-related failure in version 1.20 offers **Certificate Details**, showing the Android API level and installed/downloaded SHA-256 fingerprints, or Unavailable. **Copy Details** writes those diagnostics to the clipboard only when tapped. It sends nothing automatically and includes no village/account data. The installer stays unavailable while verification fails.

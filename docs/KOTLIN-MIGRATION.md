# Kotlin Migration

Version 1.20 completes the migration begun in 1.19. All **37 maintained production app source files are Kotlin**, including MainActivity, PlannerActivity and OverviewActivity. No production Java source files remain. Existing Java regression tests are retained as independent callers of the Kotlin implementation.

## Migrated Components

| Area | Kotlin Implementation |
| --- | --- |
| Main screens | MainActivity, PlannerActivity, OverviewActivity |
| Village import | ExportParser, ClipboardSync, AccountNames |
| Timer calculations | UpgradeTiming, Village, ActiveEffects, FastEvents, DurationText, ClockText, CountdownText |
| Account storage | Store, VillageArchive |
| Upgrade planning and progress | UpgradePlanner, VillageOverview, Availability |
| Offline names and art | Catalog, ItemIdentities, ItemArt, LevelArt |
| Home screen widgets | VillageWidget, UpgradeListService, WidgetConfigActivity, HiredWorkers |
| Background work and alerts | LiveTimerService, Reminders, AlarmReceiver |
| Update checks and installation | UpdateChecker, UpdateDownloads, UpdateDownloadReceiver, UpdateActivity |
| Release guidance | ReleaseNotes, WhatsNewActivity, OfficialUpdates, TimerPanels |

The Android Gradle plugin's built-in Kotlin support compiles the app. No separate Kotlin Android plugin is applied. App code and existing test callers target JVM 17. The minimum Android version remains 8.0 (API 26).

## Upgrade Compatibility

The application ID, manifest component names, signing identity, archive paths, preference names and saved JSON format remain unchanged. Existing accounts and launcher widget selections are read directly. Notification IDs, pending-intent identities and account-specific reminder cursors are retained.

Kotlin models retain the fields and static methods used by regression tests through `@JvmField`, `@JvmStatic` and explicit checked-exception declarations. Nullable records remain nullable: a missing account, unknown artwork or unavailable item is not replaced with invented data.

## Behavior Checks

The pre-migration tests remain in place for timing, finite boost expiry, potion priority, recurring helper work, input validation, account migration/isolation, planner hall caps, TH12 fixes, notifications, widget rendering, update fallback parsing and APK signature checks. Keeping those callers in Java also exercises language interoperability. New Kotlin framework tests cover hired-worker icons, builder/research headings, exact timer matching and permanent-slot exclusion on API 26, 29 and 35.

Normal builds render the same Android preview screens with **Anonymous** as the timer/widget sample name. The real personal export and local migration backups are excluded from source archives. Test results and physical-device testing are recorded separately; framework tests do not establish phone-specific launcher or network behavior.

## Hired Worker Artwork

The word badge is replaced by a head cropped from Supercell's official Goblin Builder portrait. It appears on extra-work rows and beside headings while hired work is running, with an accessible description. The Build Menu matches markers to the imported timer; it does not infer a hire from the number of jobs alone. Original art, crop and hashes are documented in `hired-worker-art.json` and [Presentation Credits](DESIGN-CREDITS.md).

## Signing Compatibility

Version 1.20 requests modern and legacy certificate records, retries legacy queries when no signer was supplied, and uses legacy certificates if the modern record is empty. A nonempty modern signer set takes precedence; a conflicting legacy record cannot hide a real mismatch. Missing metadata remains blocked with a distinct explanation and copyable fingerprints. See [Signing Checks](SIGNING-CHECKS.md).

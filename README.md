# CoC Base Tracker

A private Android home-screen widget for Clash of Clans upgrade countdowns.

Repository name: `CoC-Base-Tracker`. Previously named Village Clock; version 1.5 renames the app and includes the offline Build Menu while preserving the Android package, signing identity, saved villages, and widget selections.

## Design previews

Actual Android layouts rendered with synthetic sample data. The Build Menu adapts the gray header, brown cards, level badges and bottom tabs in [Supercell's October 2026 interface redesign](https://supercell.com/en/games/clashofclans/blog/news/weve-revamped-the-clash-of-clans-user-interface/). The widget retains the reference project's green countdown bars and scrollable account-specific list.

| App / countdowns | Build Menu | Home-screen widget |
| --- | --- | --- |
| <img src="docs/images/app-design.png" width="270" alt="CoC Base Tracker app with grouped upgrade countdowns"> | <img src="docs/images/build-menu-design.png" width="270" alt="Build Menu with base switching, upgrade cards and category tabs"> | <img src="docs/images/widget-design.png" width="270" alt="Scrollable home-screen widget"> |

The normal `build.ps1` command renders these previews from the actual app/widget layouts and refreshes `docs/images/` automatically. Commit changed images with design changes so GitHub shows the current version. `-SkipTests` does not refresh previews. See [preview maintenance](docs/DESIGN-PREVIEWS.md).

## Use it

1. Install `CoC-Base-Tracker-1.5.apk` on an Android 8.0+ phone. It uses the original private signing key and installs as an update over versions 1.0–1.4; do not uninstall first.
2. In Clash of Clans, open **Settings → More Settings → Export village data**, then **Copy**. The exact wording can vary with the game's language/version.
3. Open CoC Base Tracker and tap **Sync from clipboard / add account**.
4. Tap **Add widget** and choose its village, or long-press the home screen and find CoC Base Tracker under Widgets.
5. Allow notifications for finish reminders. On Android 12+, **Allow precise timers** improves alarm timing and changes finished widget rows promptly.

The visible countdowns tick every second using Android's native Chronometer. They continue after Clash and the companion app close. Tap a widget's **Sync** button after copying a fresh export; only that user action reads clipboard text. Scroll vertically inside the widget to see all upgrades. Tap an upgrade to open Clash. The widget does not switch the actual in-game Supercell account for you.

## Multiple accounts and widgets

Import each account's village export once. **Villages** in the app switches the account you are viewing. This does not change the account selected on existing widgets. Add two widgets and choose a different village for each, or tap **▾** beside a widget's Sync button to reassign it later. Names, exports, and reminder cursors are stored separately per player tag. All saved villages can send finish reminders, with the village name shown in each notification.

Your original 1.0 village and name migrate automatically. Existing widgets keep that village. Removing one account does not remove another account or redirect widgets to it. If a widget is bound to a removed account, choose a saved account for that widget. A widget's Sync rejects clipboard data from the wrong account; import another account through the app instead.

Scrollable widgets use native collection items on Android 12+, with a permission-protected collection service for older Android versions and unusually large imports. Scrolling depends on the launcher's normal support for Android list widgets.

**A live countdown is not a live connection to the game.** Sync again after starting new upgrades, finishing early, using a potion, or changing helper assignments. The official player API does not currently provide active upgrade queues/timers, so this application does not pretend to discover those changes automatically.

## Features

The app keeps the Clash-style presentation introduced in 1.2: wooden panels, Lilita One font, gold buttons/headings, circular game artwork, green timer bars, and gold completed rows. Sections stay grouped in the scrollable widget. Green bars measure elapsed countdown time since the last export, rather than an unknown full-upgrade percentage. See [presentation credits](docs/DESIGN-CREDITS.md).

- Builders, research, pets, Builder Base, and Star Laboratory queues.
- Export timestamp-based countdowns, including known active boosts and helper speed-ups.
- Upgrade-finished notifications and alarm restoration after reboot, update, or clock changes.
- Multiple saved villages, names per account, and a separate village selection for every widget.
- Scrollable home-screen upgrade lists with no fixed visible-row cap.
- Build Menu with Village Base / Builder Base switching, four Home Village tabs and two Builder Base tabs.
- Remaining upgrades, grouped duplicate levels and walls, next-level requirements, baseline costs/times, equipment ore costs and catalog maximum levels.
- Resource/type/hero and status filters, Cost Min / Cost Max sorting, and active upgrades first by remaining timer.
- Potion duration stacks, independent boost expirations, integer finish-time arithmetic, and event-discount preservation. See [timer rules](docs/TIMING.md).
- Stale-export rejection and bounded JSON parsing.
- Clearly marked sample preview; sample data is never saved as your village or scheduled for notifications.
- No internet permission, account login, analytics, advertising, screen capture, accessibility access, or automatic clipboard monitoring.
- Private internal storage, system cloud backup disabled, and an explicit clear-data action.

Android can delay inexact alarms or pause an app after force-stop. If precise-timer access is denied, a widget's finished state may update late and its native countdown can briefly show elapsed time past zero. Reopen the companion app after force-stopping it to restore reminders. Standard Android reboot delivery occurs after the device is unlocked.

The village-export schema is not officially documented. Boost interpretation and unknown future game fields can require updates. Validate against your in-game timers after importing. Missing catalog IDs show a readable fallback rather than a fabricated item name. Android framework tests cover widget inflation, scrolling adapters, migration, and account isolation; a physical phone was not available.

## Build Menu and upgrade requirements

Tap **Build Menu · what’s left to upgrade** above your timers. Village Base offers **Buildings & Heroes**, **Army Upgrades**, **Hero Equipment** and **Hero Pets**. Builder Base offers the first two. Cards show exported current levels, next catalog levels, and prices/durations per item. Tap for prerequisite levels, acquisition notes where supplied, and levels remaining to the catalog maximum. Identical building/wall levels are grouped; different active timers remain separate.

The filter button beside the title opens **Resource**, **Type** (Army, Defense, Hero, Resource, New and Non-Seasonal where applicable), or **Hero** for equipment. **Cost Min / Cost Max** reproduce the game's cost directions. Default sorting puts active timers first, shortest first. Companion additions include time/name sorting and Remaining, All, Requirements met, In progress, Locked / unowned and Maxed views. Filters are independent per base/tab and reset when the menu closes. Empty restrictive filters clear to Remaining. Cost sorting compares amounts per item; choose one currency for a meaningful comparison.

**Planning values are baseline catalog values.** They do not apply future event, Gold Pass, potion or helper discounts. Running countdowns continue using exported timers and boost rules. Requirements met checks listed level gates against current exported levels; it does not promise sufficient resources or an available builder. Ended timers request a fresh sync rather than inventing a completed level.

The community catalog contains 223 items, checked on 8 November 2026, through Town Hall 18 / Builder Hall 10. This pinned snapshot cannot guarantee every current game rule. Standard levels are covered; supercharges, some crafted modules, merge recipes, hall prerequisite checklists, and items without verified IDs require in-game confirmation. Missing equipment is labeled **Not in export**, not assumed owned. Omitted bases do not become empty villages. Merge-consumed old defenses are not invented as missing new buildings. See [planner data and limits](docs/UPGRADE-PLANNER.md).

## Build and signing

Use JDK 21+, Gradle 9.7.1, Android SDK Platform 37, and SDK build-tools 36.0.0+. Set `JAVA_HOME` and `ANDROID_HOME` (or `local.properties`).

```powershell
pwsh -File .\build.ps1
```

The script runs parser/planner tests, Android framework tests and native layout previews, Android lint, and a release build, then aligns and signs the APK. It refreshes README previews, verifies the signature and prints the APK SHA-256. It supports `-JavaHome` and `-GradlePath` when those tools are not on PATH. The source ZIP excludes installed tools, local SDK paths, caches, output files, and signing secrets.

The signing keystore and its random password are generated locally in `.private/`. **Keep that directory private and backed up.** It is excluded from Git and the source ZIP. You need the same private key to install future updates over this APK. No signing private key is embedded in the APK or publicly committed. A source recipient generates their own key; their build will require uninstalling your build before installation.

## Credits

Original application logic and vector artwork: Village Clock contributors (now CoC Base Tracker), MIT license. The app adapts presentation resources from the reviewed Coc-Widget repository at the user's request; see [presentation credits](docs/DESIGN-CREDITS.md). Third-party presentation resources, font, and game artwork retain their separate attribution and terms.

The original names catalog came from the inspected Coc-Widget build at commit `68626b688116ff19a535ec5be630f8b9bd85d759`. The planner uses factual costs, requirements and standard levels from Christopher Pezza's MIT-licensed [clash-of-clans-data](https://github.com/chiefpansancolt/clash-of-clans-data/tree/7afaa0375e5865c04021836a3a4192ffcfea092d), pinned at `7afaa0375e5865c04021836a3a4192ffcfea092d`. Its license is retained at `app/src/main/assets/data-license.txt`. Catalog durations are for future planning only; they never invent start times or replace exported running timers.

This project is not affiliated with, endorsed, sponsored, or approved by Supercell. Clash of Clans and its related names belong to Supercell. See [Supercell's Fan Content Policy](https://supercell.com/en/fan-content-policy/).

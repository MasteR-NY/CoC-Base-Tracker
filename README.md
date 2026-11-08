# CoC Base Tracker

Upgrade timers, village planning, and account-specific home screen widgets for Clash of Clans.

[![Download Latest APK](docs/images/download-apk.svg)](https://github.com/MasteR-NY/CoC-Base-Tracker/releases/latest)

**Android 8.0+ · Offline · Multiple Accounts**

Download the `.apk` file under **Assets** on the latest release page. When updating, install over your existing version to keep your saved accounts and widgets.

## Design Previews

| Upgrade Timers | Build Menu | Home Screen Widget |
| --- | --- | --- |
| <img src="docs/images/app-design.png" width="270" alt="Upgrade timers with an account name, player tag and grouped countdowns"> | <img src="docs/images/build-menu-design.png" width="270" alt="Build Menu with base selection, upgrade costs and category tabs"> | <img src="docs/images/widget-design.png" width="270" alt="Scrollable home screen widget with account details and live countdowns"> |

*Previews use sample account data. Widget appearance may vary by launcher.*

## Features

- Scrollable home screen widgets with live countdowns in days, hours, minutes, and seconds.
- Upgrade tracking and finish reminders for builders, research, pets, and both bases.
- Upgrade planning with level requirements, baseline costs, and durations.
- Resource, type, hero, and status filters, with cost, time, and name sorting.
- Multiple accounts with in-game names, optional nicknames, and independent widgets.

## Getting Started

1. Download and install the APK from the [latest release](https://github.com/MasteR-NY/CoC-Base-Tracker/releases/latest).
2. In Clash of Clans, open **Settings → More Settings → Export village data** and copy the export. Menu wording may vary by game version or language.
3. Open CoC Base Tracker and tap **Sync from clipboard / add account**.
4. Enter the account's in-game name if prompted. You can edit it later under **Settings → In-game Name**.
5. Tap **Add widget** and choose an account, or add CoC Base Tracker from your launcher's widget picker.

Allow notifications for upgrade-finished alerts and the live widget controls. On Android 12 and later, **Allow precise timers** helps finish alerts arrive on time.

### Keeping Your Village Current

Timers run from your most recent village export and continue counting down when Clash is closed. Import a fresh export after starting an upgrade, finishing one early, using a potion, or changing helper assignments. New activity in Clash appears after you sync.

Active potions, upgrade-speed Magic Snacks, Clock Tower boosts, and assigned helpers are included in the timer calculations. Boosted upgrades show their work rate, accelerated **Work left** countdown, and estimated real **Finishes in** time. Open **Boosts & Helpers** to review the effects recorded in your export.

Additional doses extend boost duration. Known repeating helper assignments are forecast when their assignment and cooldown are included in the export. Books, Hammers and other instant finishes appear after a fresh sync; unused chest rewards and combat/resource boosts do not accelerate upgrade timers. See [Timer Rules](docs/TIMING.md) for supported effects and examples.

## Multiple Accounts and Widgets

Import an export from each account, then use **Accounts** to switch between them. Each home screen widget keeps its own account selection. Tap **▾** beside a widget's Sync button to change it.

In-game names and optional nicknames such as Main or Mini are saved per player tag. If an export omits the name, enter it once in the app. Names stay saved across later syncs. Editing an account label here does not change your name in Clash of Clans.

## Live Widget Timers

**Live readable widget timers** is enabled by default. An ongoing notification keeps the formatted countdowns updating while the app is closed. Display updates pause when the screen is off or locked and recalculate when you unlock your phone.

Choose **Pause live updates** in the notification to use Android's clock-style widget timer. Turn **Live readable widget timers** back on in Settings to restore the days, hours, minutes, and seconds format.

If Android stops background updates or you force-stop the app, reopen it to restore tracking. Your launcher's widget support and Android battery settings can affect updates and alert timing.

## Build Menu

| Base | Upgrade Categories |
| --- | --- |
| Village Base | Buildings & Heroes, Army Upgrades, Hero Equipment, Hero Pets |
| Builder Base | Buildings & Heroes, Army Upgrades |

Tap an upgrade card to view its next level, listed requirements, baseline cost, and duration. Buildings and walls at the same level are grouped together; prices and durations are shown per item. Resource, type, and hero filters help narrow the list.

Planning costs and durations are baseline values. Event discounts, Gold Pass benefits, potions, and helpers may change the in-game quote. Listed requirements do not account for your available resources or free builders.

The offline catalog covers standard levels through **Town Hall 18** and **Builder Hall 10**. Supercharges, some crafted modules, merge recipes, and other special conditions require confirmation in game. See the [Upgrade Planner Reference](docs/UPGRADE-PLANNER.md) for coverage and limitations.

## Privacy

CoC Base Tracker works offline. Village exports and account labels are stored on your device, with system cloud backup disabled. No Supercell login, password, or verification code is required.

The app has no internet permission, advertising, or analytics. It reads clipboard text only when you choose to sync. Saved accounts can be removed from Settings.

## Documentation

- [Timer Rules](docs/TIMING.md)
- [Upgrade Planner Reference](docs/UPGRADE-PLANNER.md)
- [Build and Release Guide](docs/DEVELOPMENT.md)
- [Preview Maintenance](docs/DESIGN-PREVIEWS.md)
- [Credits and Third-Party Resources](docs/DESIGN-CREDITS.md)

## Credits

Original application code is available under the [MIT License](LICENSE). The presentation draws on [Coc-Widget](https://github.com/Yusuf-Bedri-Bitiren/Coc-Widget); game artwork, fonts, and other third-party resources retain their respective terms. Full attribution is in [Credits and Third-Party Resources](docs/DESIGN-CREDITS.md).

CoC Base Tracker is an unofficial fan project and is not affiliated with or endorsed by Supercell. Clash of Clans and its related assets belong to Supercell. See [Supercell's Fan Content Policy](https://supercell.com/en/fan-content-policy/).

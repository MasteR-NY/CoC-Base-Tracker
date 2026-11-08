# Release Notes

Changes and usage notes for CoC Base Tracker. The installed app includes this guide under **What’s New**.

## v1.19 — Kotlin Core and Clearer Widget Markers

- **Built Mostly in Kotlin:** Tracking, import validation, upgrade planning, account storage, widget rendering, reminders, live updates and APK update handling now use Kotlin. The three main screen classes remain in Java, with their compact layouts retained. Saved accounts and widget selections use the same package and data format.
- **Hired Workers at a Glance:** An official Goblin Builder head icon replaces the GOBLIN text badge. It marks extra upgrades in the app, Build Menu and widget, with a matching queue-heading icon. Hired jobs remain separate from permanent builder slots.
- **Anonymous Sample and Expanded Guide:** Sample timer and widget previews use Anonymous. The README adds permissions, sync, battery settings, update troubleshooting and build guidance specific to this app.

### How to Use It

1. Install this release over your existing app to keep accounts and widgets. The Kotlin migration does not require a new import.
2. A Goblin head marks an extra hired upgrade from the export. Copy a new export after hiring or finishing one.
3. Read the README for first-time setup, privacy details and troubleshooting.

## v1.18 — A Tighter Layout and Correct TH12 Upgrades

- **More Fits on Screen:** Upgrade rows, Build Menu cards and Overview cards use smaller portraits, tighter spacing and smaller headings. Duplicate card padding is removed while keeping inner margins and normal Android text scaling.
- **Monolith Arrow and Stone Slammer Restored:** Monolith Arrow and Electro Fangs had swapped data IDs in the source catalog. Their names, hero requirements and artwork now match the game data. Stone Slammer’s first three levels are correctly available within TH12. A level-17 Monolith Arrow now shows its next level at Blacksmith 5.
- **More Resilient Release Checks:** Android Downloads tries the public latest-release page before the API fallback, avoiding that API’s anonymous request quota. Transient 500/502/503/504 responses receive one retry; 403/429 responses are not repeatedly retried. Failures show a short explanation with expandable technical details. APK availability remains explicit when a release is still publishing.

### How to Use It

1. Open Build Menu → Hero Equipment for Monolith Arrow, or Army Upgrades → Siege for Stone Slammer. Current Hall Only stays enabled by default.
2. Check for Updates uses the new public-page fallback automatically. Retry after a short wait if GitHub is still processing a newly published release.
3. If EPERM remains in Technical Details, check this app’s Android network access. Server fallback changes do not grant blocked socket permissions.

## v1.17 — Know When It Finishes

- **Finish Clocks Beside Upgrades:** Current upgrades show their estimated local finish date/time beside the countdown. Settings → Time Format offers Device Default, 12-Hour and 24-Hour. Dates identify tomorrow and later finishes.
- **One Shared Boost Panel:** Active potions, snacks, Clock Tower boosts and the bundled Hammer Jam event appear above the upgrades, with an icon, name, multiplier or discount, time left and local end time. Names are no longer repeated on every upgrade. Boost calculations are unchanged.
- **Next Slot Availability:** When every regular builder slot is busy, Next free in shows the estimated wait and local availability time. Goblin jobs are kept separate. Laboratory, Pet House and Builder Base queues have their own estimates. The widget scrolls through boosts and upgrades together.

### How to Use It

1. Open Settings → Time Format and select your preferred clock style. The choice applies to the app, Build Menu and widgets.
2. Check Active Boosts above the upgrade sections. Tap an app boost row for stacking or event details. Sync after using another item.
3. Finish clocks and Next free estimates use the last export and modeled boost expiry. Copy a new export after changing upgrades, potions or helpers.

## v1.16 — A Build Menu for Your Hall

- **Plan Your Current Hall:** The Build Menu defaults to upgrades reachable within your current Town Hall or Builder Hall, with active upgrades first and the next hall upgrade shown separately. Turn off Current Hall Only in the filter menu to see Catalog Max.
- **Level-Specific Artwork:** Upgrade rows, app timers and widgets preview the destination level using bundled artwork. The current level stays on the badge. Buildings, walls, traps and troops use exact level mappings where art is available; fixed portraits remain for items without level variants.
- **Game-Style Filters and Costs:** Resource icons and Type buttons match the supplied game examples, with Cost Max and Cost Min sorting. Costs and ore requirements sit beside each upgrade. Filters stay separate per base and category; Cancel leaves the current selection unchanged.

### How to Use It

1. Open Build Menu and select Village Base or Builder Base. The default scope follows the hall level in your last export.
2. Tap the sliders button to choose resources, types or heroes, then Cost Max or Cost Min and Apply. Reset returns to the current-hall default.
3. Current upgrades keep their exported countdowns in days, hours, minutes and seconds. Costs and future durations are baseline catalog values before event or Gold Pass discounts. Sync after an upgrade finishes to refresh levels.

## v1.15 — Updates Without the Browser

- **Download and Install in the App:** Download a published APK directly from the update prompt. Follow progress, leave the screen while Android downloads, and tap Install Update when ready. Android asks you to allow this install source and confirm installation.
- **Another Way to Check for Updates:** After the API and public Releases checks fail, Android Downloads tries the release metadata. Socket errors now include the actual system detail instead of only the exception name. This does not bypass blocked GitHub connections.
- **Two More Cosmetic Identities:** Official game-data IDs now identify Amber Hot Streak and Sour Elixir Fountain, with matching offline artwork. Two skin records still have no verified name or icon and remain labeled by ID.

### How to Use It

1. Tap Check for Updates, then Download Update if a newer APK is available.
2. After downloading, tap Install Update. If Android asks, allow installs from CoC Base Tracker, return, and tap Install Update again. Confirm Android’s installation prompt.
3. Use Update Download or the ready notification to return to a saved download. Open Overview → Collection → Decorations for the newly identified cosmetics.

## v1.14 — Names and Artwork

- **Named Crafting Modules:** Hot Candle, Hero Hunter and Cake-A-Pult modules now show their stat names, verified catalog caps and defense portraits with stat symbols.
- **Recognizable Helpers:** Alchemist and Prospector are identified correctly in helper cards and availability reminders. Helper portraits make each one easier to recognize.
- **Cosmetic Names and Icons:** The collection uses a bundled identity catalog and compact offline artwork for known skins, sceneries, decorations, obstacles and house parts. Entries missing from the source data keep their IDs.

### How to Use It

1. Open Overview → Crafting to see named modules and their current levels.
2. Open Helpers for corrected helper names and portraits, or Collection for cosmetic names and artwork.
3. Tap a crafting module to inspect its identity and cap. Unresolved entries remain visible for later data updates.

## v1.13 — Your Village at a Glance

- **Progress for Both Bases:** Overview shows building, wall, trap, army and hero level progress. Choose targets for your Current Hall or the full offline Catalog Max.
- **Crafting and Collections:** Track all exported crafting modules, equipment levels and cosmetic collections. Unverified names stay visible as IDs, and unknown module caps are clearly marked.
- **Helper and Clock Tower Alerts:** See exported cooldowns and receive a one-time availability reminder for each deadline. Sync after using a helper or Clock Tower to keep estimates current.
- **Official Game References:** The News button in Overview opens dated notes and links to Supercell’s official blog, support and community-channel directory. Notes are bundled with this release.

### How to Use It

1. Tap Overview beside Build Menu, then switch Village Base or Builder Base.
2. Use Progress, Crafting, Helpers or Collection to explore your export. Collection includes an equipment and cosmetic selector.
3. Enable notifications and Helper & Clock Tower alerts in Settings. Availability is estimated from the last export; sync after use in Clash.

## v1.12 — Auto-Sync on Return

- **Copy, Then Return:** Copy a village export in Clash and return to CoC Base Tracker. A fresh export imports automatically when the tracker has focus. Android does not allow background clipboard watching or an automatic redirect from Clash’s Copy button.
- **Your Sync Controls:** Auto-sync on return is enabled by default and can be turned off in Settings, even before importing an account. Duplicate and older exports are ignored. Each widget keeps its account selection.
- **Updated Helper Detection:** Repeat-helper settings from newer exports are recognized. Forecasts use the assigned helper’s cooldown, and a repeat preference on an idle item does not create an active upgrade.

### How to Use It

1. Open Clash and copy Settings → More Settings → Export village data.
2. Switch back to the tracker, or tap its live-timer notification. The app auto-syncs a new export without a separate Sync tap.
3. Turn off Settings → Auto-sync on return if you prefer manual Sync / Add Account.

## v1.11 — Smoother Timers and a Cleaner Home

- **More Reliable Update Checks:** If the GitHub API is unavailable, the app checks the public latest-release redirect. Errors now identify connection, DNS or secure-connection failures, with Retry and Open Releases options. Android 8.0 and later remain supported.
- **Smooth Boosted Countdowns:** In the app and Build Menu, boosted work counts down through intermediate seconds instead of jumping once per real second. The home widget shows steady real time remaining with its boost badge.
- **Less Clutter:** Settings starts collapsed, actions use shorter labels, and long explanations have moved to Help & About. Account names, sync status and important timer information stay visible.

### How to Use It

1. Tap Check for Updates; if both checks fail, the message shows the reason and offers Retry or Open Releases.
2. Sync a fresh export after using a potion to see the fast work countdown in the app. Finishes in remains the estimated real completion time.
3. Expand Settings for account and notification controls. Open Help & About for sync instructions, timer details and privacy information.

## v1.10 — What’s New Guide

- **Release Highlights on Launch:** A short summary introduces the installed release after an update. Read the guide or tap Got It to dismiss it for that version.
- **Read the Guide Anytime:** What’s New at the bottom of the app opens the full release guide, including changes from earlier versions.
- **Available Offline:** Release highlights are included with the app. You can read them without an internet connection.

### How to Use It

1. Open CoC Base Tracker to see the release summary.
2. Tap Read Release Guide for the full details, or Got It to continue.
3. Reopen What’s New at the bottom of the app whenever you need it.

## v1.9 — App Version and Update Checks

- **Installed Version in the Header:** The app title includes its installed version so you can tell which build you are using.
- **Check for Updates:** The button at the bottom checks the latest stable GitHub release. It confirms when you are current and opens Releases when a newer version is available.
- **Checks Stay Under Your Control:** GitHub is contacted only when you request a check. Village exports, player tags and account names are not sent. Failed checks offer a retry or manual release-page check.

### How to Use It

1. Scroll to the bottom and tap Check for Updates.
2. If a newer release opens, download its APK from Assets.
3. Install the APK over your existing app to keep saved accounts and widgets.

## v1.8 — Boost-Aware Upgrade Timers

- **Work Left and Finish Estimates:** Boosted upgrades show their effective speed, accelerated Work left countdown and estimated real Finishes in time.
- **Potions, Snacks and Helpers:** Known upgrade-speed effects apply for their remaining duration. Repeating helper assignments can be forecast when the export provides the assignment and cooldown.
- **Boosts & Helpers:** Review recorded effects and upcoming speed changes in one place. Use items in Clash first, then sync a fresh export.

### How to Use It

1. Use the potion, snack or helper in Clash of Clans.
2. Copy a fresh village export and sync it in the tracker.
3. Open Boosts & Helpers to review the imported effects.

## v1.7 — In-Game Account Names

- **Recognizable Accounts:** In-game names appear with player tags in the app, widgets, account selection and finish reminders.
- **Names That Stay Saved:** Enter a name once if an export omits it. Names persist across syncs, and optional nicknames such as Main or Mini remain separate.

### How to Use It

1. Use Set In-game Name or Settings → In-game Name to enter or edit a name.
2. Use Account Nickname for an optional label without changing your name in Clash.

## v1.6 — Readable Live Countdowns

- **Days, Hours, Minutes and Seconds:** Countdowns use a readable format such as 1d 12h 18m 56s in the app, Build Menu and live widgets.
- **Live Widget Controls:** An ongoing notification keeps readable widget timers updating. Pause live updates from the notification or Settings to use Android’s clock-style widget countdown.

### How to Use It

1. Keep Live readable widget timers enabled for the full duration format.
2. Allow notifications to see the live widget controls.

## v1.5 — CoC Base Tracker

- **A New App Name:** The app and project were renamed CoC Base Tracker. Installing over the earlier app preserves saved villages and widget selections.

## v1.4 — Village Upgrade Planning

- **Build Menu:** Switch between Village Base and Builder Base and browse their upgrade categories.
- **Plan the Next Upgrade:** Review remaining standard upgrades, listed requirements, baseline costs and durations. Use resource, type, hero and status filters to narrow the list.

### How to Use It

1. Tap Build Menu above your upgrade timers.
2. Choose a base and category, then tap a card for its details.

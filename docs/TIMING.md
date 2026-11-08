# Timer Rules

CoC Base Tracker uses the remaining upgrade work and active effects in a village export. Calculations begin at the export's timestamp, not the time you import it. Catalog durations never replace an active upgrade's exported timer.

## Work Left and Finish Time

A boosted upgrade shows three values:

- **Speed** identifies the active effect and effective work rate, such as Research Potion at 24×.
- **Work left** is the remaining normal-speed upgrade work. At 24×, this drops by 24 seconds for each real second.
- **Finishes in** is the estimated real time until completion, including boost expiration and any explicitly recurring helper sessions that can be forecast.

A Research Potion with one hour left completes 24 hours of laboratory work during that hour. An upgrade with 48 hours of work therefore finishes in 25 real hours: one boosted hour followed by 24 normal hours. Work that finishes within the boost window completes sooner; 12 hours of research finishes in 30 minutes.

Both values use days, hours, minutes and seconds. A work rate changes at its own expiry; it is not applied to the full remaining duration after the effect ends. **Boosts & Helpers** in the app lists the recorded sources, current rate, next speed change and projected finish for each affected upgrade.

## Supported Upgrade Effects

| Effect | Affected Queue | Work Rate While Active |
| --- | --- | --- |
| Builder Potion | Home Village builder upgrades, including Goblin Builder jobs | 10× |
| Builder Bite | Home Village builder upgrades | 2× |
| Research Potion | Laboratory troops, spells and siege research, including Goblin Researcher jobs | 24× |
| Pet Potion | Pet House | 24× |
| Study Soup | Laboratory and Pet House | 4× on its own |
| Clock Tower button or Clock Tower Potion | Builder Base construction and Star Laboratory | 10× |
| Builder's Apprentice | Its assigned Home Village upgrade | Adds work equal to the exported helper level |
| Lab Assistant | Its assigned Laboratory research | Adds work equal to the exported helper level |

The parser reads the active durations from `builder_boost`, `builder_consumable`, `lab_boost`, `pet_boost`, `lab_consumable`, and `clocktower_boost`. A Clock Tower activation uses its exported remaining duration; the app does not assume a fixed tower level or button duration.

### Stacking and Overlap

Additional doses of the same item extend its duration. Two Research Potions mean up to two hours at 24×, rather than one hour at 48×. Partly consumed stacks retain the full exported remaining duration.

Builder Potion and Builder Bite share a builder-speed group. The stronger active effect takes priority; a Bite that is still active after the potion ends continues at 2×. It is not added to the potion as an extra multiplier.

Study Soup contributes three additional units of work to the Laboratory or Pet House. With a Research or Pet Potion active, the supported combined rate is 27×. Potion speed never multiplies helper work. A level-8 Apprentice plus a Builder Potion contributes a total of 18 units of work per second; a level-12 Lab Assistant plus Research Potion and Study Soup contributes 39.

Each source expires independently. The implementation integrates work over those windows and rounds the final fractional real second upward. Duplicate records for one potion family are not counted as additional simultaneous speed.

## Helper Hut Assignments

A helper affects only the upgrade whose export entry records its assignment. An active `helper_timer` supplies the remaining current session. Merely owning or upgrading a helper does not accelerate every upgrade.

An explicit `recurrent_helper: true` flag, a known helper level, and a positive exported unified `helper_cooldown` enable a repeat-session forecast on that same upgrade. The shared cooldown can appear on a helper entry or in the boosts object. The current supported work day is **23 hours**, with **one-hour helper sessions**, following Supercell's Helper Hut guide.

Forecasts include the partial current session and later sessions, with no overlapping helper work. If the repeat flag or cooldown is absent, the app counts the current recorded session only. It does not invent a daily assignment or transfer a helper to another upgrade after a job ends. Sync after changing or cancelling an assignment.

Alchemist and Prospector resource conversions do not reduce construction or research time.

## Other Magic Items and Chest Rewards

A chest reward affects an upgrade according to the item used, not the chest's rarity or its place in storage.

- Unused potions, snacks, Books and Hammers in inventory do not change active timers.
- Books, Hammers and gem finishes are instant actions. A fresh export reflects the completed level or absence of the old running timer; the app does not apply an artificial timed multiplier to them.
- Resource boosts and resource conversions do not speed construction or research.
- Power Potion, Hero Potion, Mighty Morsel, Power Pancakes and other combat effects do not speed these upgrade queues or complete permanent upgrades.

Use an item in Clash of Clans first, then import a fresh export. This app's buttons open the game or explain the imported effects; they do not consume game items.

## Events and Gold Pass

Permanent or event time reductions already present in exported work are retained. The app does not apply a second blanket discount because Hammer Jam or a Gold Pass benefit is active. Builder Base, crafted modules and other queues therefore do not accidentally receive a Home Village-only discount.

The known Hammer Jam calendar note is informational. It never changes timer arithmetic, downloads an event calendar or supplies an unverified new speed multiplier. Future game effects need a supported export field and a verified rule.

## Display and Background Updates

Activity text updates while its window has focus and cancels callbacks when rows detach or the window loses focus. Readable widgets use a user-selected foreground service and ongoing notification. It refreshes the existing list adapter, preserving account selections and scroll position.

Boosted work in the app and Build Menu includes fractional real seconds. Updates follow each next displayed work-second boundary, capped at one update per 16 milliseconds; at 24× speed this is roughly every 42 milliseconds. Normal clocks retain one-second updates. Each displayed value is recalculated from the snapshot and boost/helper schedule, including expiry, rather than subtracting an assumed amount per callback. A delayed frame therefore catches up accurately.

Home screen widgets show real time remaining and a boost badge, using one-second foreground-service refreshes. They do not attempt the activity’s rapid text updates through the launcher’s RemoteViews collection. This avoids large work-time jumps in the widget and keeps the existing scrolling behavior. The app retains both accelerated Work left and real Finishes in for boosted jobs.

Screen-off and lock pause display updates without a wake lock. Unlocking recalculates work and estimated completion from the saved snapshot. Upgrade-finish alarms use the projected real finish time. Pausing live updates returns the widget to Android's native countdown of real time until completion.

Force-stop requires reopening the app. Android battery restrictions can delay updates or alarms. Framework tests and previews do not establish physical-device accuracy for every launcher.

## Validation and Limits

The village-export schema is unofficial. Tests cover finite potion windows, duration stacks, overlap priority, individual queue eligibility, partial and recurring helper sessions, event-discount preservation, and work-countdown boundaries. A separate second-by-second simulator checks the work integrator.

The item rates and helper schedule have official references below. Export fields and overlap behavior are also checked against current open-source export parsers and boost implementations. These are supported calculation rules, not proof of live-game behavior for every account or future update. Compare a freshly imported boosted timer with the game, especially after game updates.

## References

- [Supercell: Magic Items and The Trader](https://support.supercell.com/clash-of-clans/en/articles/magic-items-and-the-trader.html)
- [Supercell: Helpers and The Helper Hut](https://support.supercell.com/clash-of-clans/en/articles/helpers-2.html)
- [Supercell: Builder Potion does not affect the Apprentice](https://supercell.com/en/games/clashofclans/blog/release-notes/june-update-full-patch-notes/)
- [Supercell: Magic Snacks and chest rewards](https://supercell.com/en/games/clashofclans/blog/game-updates/september-update-full-patch-notes/)
- [Supercell: February 2026 combat-item changes](https://supercell.com/en/games/clashofclans/blog/release-notes/the-february-update-has-escaped/)
- [Supercell: November 2026 Hammer Jam](https://supercell.com/en/games/clashofclans/blog/news/hammer-jam-is-here-backup-has-arrived/)
- [ClashKing: export field model](https://github.com/ClashKingInc/clashy.py/blob/main/coc/game_data.py)
- [Coc-Widget: export parser](https://github.com/Yusuf-Bedri-Bitiren/Coc-Widget/blob/68626b688116ff19a535ec5be630f8b9bd85d759/app/src/main/java/com/cocwidget/app/model/ExportParser.kt)
- [Clash Ledger: current boost-group implementation](https://github.com/chitti-a7r4/coc-tracker/blob/main/app/src/main/java/org/clashledger/app/BoostState.java)

# Village Overview

Tap **Overview** beside Build Menu. It opens Progress, Crafting, Helpers and Collection for the selected account. Switch Village Base and Builder Base at the top. Viewing a different base does not change any widget’s selected account.

## Base Progress

Progress covers buildings, walls, traps, army upgrades, heroes and pets where the standard catalog provides data. Tap a category for its exported levels and grouped quantities.

- **Current Hall** targets standard levels and quantities for the exported hall, including known Laboratory, Hero Hall and other facility gates for that hall. Those facilities do not need to already be maxed.
- **Catalog Max** targets the offline catalog’s full standard levels and quantities, currently through TH18 / BH10. Targets can require a higher hall than the account currently has.
- Percentages measure **level steps**, weighted by quantity, rather than time, cost or account strength. For example, 300 level-2 walls toward level 3 represent 600 of 900 level steps.
- An upgrade in progress retains its exported level until a sync confirms the next level. Timer expiry does not manufacture completed levels.
- Unknown items and missing caps need catalog data. Seasonal defenses, supercharges and uncertain merge quantities do not receive invented targets.

## Crafting

Crafting reads all exported `buildings[].types[].modules[]` records. The supplied sample has three types and nine modules; other export sizes are supported. Cards show exported levels and any active module timer.

The nine Phase 4 modules have verified names, catalog caps, baseline prices, durations and hall requirements, with a defense portrait and stat symbol. Cards show the current-hall target; tap for the next-level quote and prerequisites. Other unmapped modules retain their IDs; missing caps read **Maximum level not verified**. The tracker does not guess a phase deadline/reset or requirements for a later phase. See [Crafting Requirements](CRAFTING.md) for the verified tables and scope. See [Item Names and Artwork](ITEM-IDENTITIES.md) for mappings and source revisions.

Supercell’s seasonal announcement opened the current defenses from TH11, whereas general support still describes a highest-hall condition. The tracker follows actual exported inventory, including the supplied TH12 sample. See [Official Game Data References](GAME-DATA.md).

## Helper and Clock Tower Availability

Helpers shows Home Village helper levels and exported cooldowns. Switch to Builder Base for Clock Tower. Missing cooldowns remain unknown; an exported zero means ready at the snapshot. Unknown helper IDs retain a stable label rather than an unverified name.

Availability alerts are enabled by default alongside the notification master setting. Disable them separately in Settings or Overview → Helpers. Each account has an independent cursor and notification. The existing alarm receiver schedules the next exported deadline and sends a one-time reminder. A later sync replaces the deadline; one snapshot does not create recurring daily uses or alerts.

Sync after using a helper or Clock Tower. Old data cannot know it has been used again, so Ready labels and reminders are estimates to confirm in game. Notifications must be enabled; precise-alarm access on Android 12+ improves timing. Battery restrictions can delay delivery. Removing an account removes its availability notification and cursor.

## Equipment and Collections

Collection includes Home Village equipment, skins, sceneries, decorations, obstacles and house parts. Builder Base has separate skins, scenery, decorations and obstacles. Grouped `cnt` values contribute to owned quantities.

Equipment shows exported levels, catalog caps, hero association and rarity where available. Tap for details; prices and requirements remain in Build Menu → Hero Equipment. Other collectibles show names, offline artwork, owned entries and quantities. Tap a cosmetic for its ID and any shared-artwork note. No percentage of all obtainable cosmetics is claimed because the export supplies owned IDs rather than a complete catalog. Unmapped names remain visible as IDs.

Resource balances, ore balances, unused Magic Items and chest inventory are not inferred from the sample.

## Official News

**News** opens dated, bundled notes. Official Source, Official Blog and Official Channels open Supercell’s pages in a browser. Reading included notes works offline; this is not a background feed or an automatic balance-data downloader.

## Validation

Model tests cover hall/facility caps, wall quantities, unchanged exported levels, nine module records, cosmetic quantities, unknown IDs and missing versus zero cooldowns. API 26, 29 and 35 framework tests cover navigation, account isolation, sample safety, notification cursors, one-time delivery, replacement deadlines and account removal. Previews render actual Android screens with synthetic data. Physical-device alarm and launcher behavior still need device testing.

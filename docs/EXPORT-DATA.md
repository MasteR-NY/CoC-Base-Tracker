# Village Export Data

This reference describes the supplied village export and the features its fields can support. Counts and durations describe the snapshot taken **8 November 2026 at 16:03:20, Philippine time**. They are not live account data. The player tag and original export are omitted from this reference and the distributable source ZIP.

## Snapshot Contents

| Data | Present in This Snapshot | Useful For |
| --- | --- | --- |
| Village identity | Player tag and export timestamp; no in-game name | Account matching and sync age; names still need a manual label when absent |
| Base levels | Town Hall 12 and Builder Hall 8 | Base-specific planning and upgrade requirements |
| Home buildings | 39 records, including grouped counts, 300 walls, gear-up flags, a weapon level and nested crafting data | Building and wall progress; gear-up, weapon and crafting views |
| Home traps | 7 grouped records representing 36 traps | Trap progress and planning |
| Home army | 23 troop records, 3 siege machines, 13 spells, 4 heroes, 38 equipment records; pets array empty | Army, hero and equipment progress; empty pets does not supply future pet requirements |
| Builder Base | 36 building records, 6 trap records representing 18 traps, 10 troops and 2 heroes | Builder Base planning and progress |
| Helpers | 3 helper records with levels; 2 include cooldowns | Helper level and availability dashboard, once item IDs are verified |
| Boosts | `lab_boost: 3585`, `clocktower_cooldown: 65897` | Laboratory acceleration and next Clock Tower availability |
| Crafting | 3 type records containing 9 modules, all listed at level 2 | Crafting collection and progress; Phase 4 baseline costs, durations and hall gates are verified in v1.21 |
| Home cosmetics | 10 skin IDs, 2 scenery IDs, 25 house-part IDs, 26 decorations and 21 obstacles by quantity | Collection inventory and snapshot comparisons |
| Builder Base cosmetics | 1 skin ID, no scenery or decoration entries, 89 obstacles by quantity | Builder Base collection inventory |

Grouped records use `cnt` to represent multiple items. Do not treat one array record as one physical building, wall, trap or decoration. The export also does not provide village coordinates for recreating a layout.

## Active Upgrade Timers

| Queue | Jobs in This Snapshot | Notes |
| --- | --- | --- |
| Home construction | 4 Air Defenses upgrading from level 9, 2 X-Bows from level 5 | One X-Bow has `extra: true`, which the tracker handles as the extra/Goblin builder |
| Laboratory | Lightning Spell upgrading from level 8 | Research Potion applies; the idle troop’s repeat preference does not assign a helper to this spell |
| Builder Base construction and heroes | Mega Tesla from level 3, Gold Mine from level 5, Battle Copter from level 15 | Three timer records; timer count alone should not be presented as a verified free-builder count |
| Star Laboratory | Power P.E.K.K.A from level 13 | A Laboratory potion for the Home Village does not accelerate Star Laboratory work |
| Pets | None | No pet timer is supplied |

There are **11 active timer records** in total. A timer attaches to its own item; the presence of an equipment level or cosmetic ID does not create a timed upgrade.

For Lightning Spell, the export contains **91,225 seconds of work** and **3,585 seconds of Research Potion time**. Under the tracker’s supported 24× rule, with no helper assigned to that spell:

1. The potion completes `3,585 × 24 = 86,040` work seconds.
2. The remaining `5,185` work seconds run at normal speed.
3. The estimated real time is `3,585 + 5,185 = 8,770` seconds, or **2h 26m 10s**, measured from the snapshot.

The Clock Tower value is a **cooldown**, not an active boost. It must not apply a 10× speed-up. The supplied cooldown is **18h 18m 17s** from the snapshot. Likewise, a helper cooldown indicates availability, not a current helper session.

## Helper Fields

The sample uses `helper_recurrent` on a troop that has no `timer`. This records a repeat preference; it does not establish an active upgrade or transfer the helper to the running spell.

Version 1.12 accepts `helper_recurrent` and the earlier `recurrent_helper` spelling. A repeating assignment is forecast only for an item with a running timer, a known relevant helper level, and an exported cooldown. Each helper uses its own cooldown. Unknown helper IDs are not guessed into construction or research speed multipliers.

There is no `helper_timer` in this sample. It therefore does not establish a currently active helper session. Existing fallback naming data also lacks a name for helper ID `93000003`; its identity needs verification before presenting a specific name or behavior.

## Recommended Features

| Priority | Feature | What Is Available | Additional Work |
| --- | --- | --- | --- |
| High | Progress dashboard for each base | Building, trap, wall, troop, hero and equipment levels/counts | Match verified maximum levels to the selected hall; show unknown items separately |
| High | Snapshot history and change summary | Tags, timestamps, levels/counts and active timers | Retain earlier snapshots locally; distinguish confirmed level changes from estimated timer completion |
| High | Helper and Clock Tower availability reminders | Helper levels/cooldowns and Clock Tower cooldown | Verify helper names/IDs; recalculate after a fresh sync because use in game can change availability |
| Medium | Wall and equipment completion views | 300 level-13 Home walls and 38 equipment records | Verified per-level catalog limits and equipment metadata; resource balances are absent |
| Medium | Gear-up and Town Hall weapon progress | `gear_up` and `weapon` flags | Verify field semantics and any special upgrade rules rather than treating flags as ordinary levels |
| Medium | Crafting and module tracker | Nested `types` and `modules` IDs/levels | Verify names, types, caps, unlock requirements, costs and times for IDs missing from the catalog |
| Optional | Cosmetics collection | Skins, sceneries, decorations, obstacles and house parts | Verified name/art mappings and an optional collection catalog for comparison |

Auto-sync on return is implemented in v1.12. Version 1.13 adds the base progress, crafting, helper/Clock Tower availability and equipment/collection views described in [Village Overview](OVERVIEW.md). Version 1.21 adds verified Phase 4 module planning, documented in [Crafting Requirements](CRAFTING.md). Snapshot history and comparisons remain future work. Unverified module/cosmetic mappings and missing resource balances remain limitations.

## Information Not Supplied

This sample does not include resource or ore balances, gems, item/chest inventory, potion quantities in storage, account email, Supercell login/session credentials, clan information, trophies, village layout coordinates, an event identifier, Gold Pass discount values, or upgrade prices and prerequisites. Names, costs, durations and requirements displayed by the planner come from its separate offline catalog where available.

Do not infer that every game version’s export has identical fields. In particular, the absence of inventory here means this snapshot cannot support an accurate Magic Item stock counter or chest inventory. An active boost can still be timed from the exported boost field.

## Copy and Auto-Sync

**Auto-sync on return** is enabled by default and can be turned off in Settings. Copy a fresh export in Clash, then return to the tracker. The app reads plain clipboard text only while it is resumed and has focus, validates the export, ignores duplicate/older snapshots, and imports a fresh one without an extra Sync tap. Manual widget sync retains its account guard, and existing widget selections remain unchanged.

The app does not open clipboard URIs, save unrelated clipboard text, poll the clipboard in its background widget service, or upload exports. A local export fingerprint prevents the same clipboard content from being repeatedly imported, including immediately after removing an account.

Android 10 and later restrict clipboard access to the focused app or default keyboard. Android also restricts background activity launches. Consequently, Copy in Clash cannot automatically notify this tracker and bring it forward. Switching back through Recent Apps, the launcher or the tracker’s live-timer notification gives the tracker focus and permits auto-sync. See [Android clipboard restrictions](https://developer.android.com/about/versions/10/privacy/changes#clipboard-data) and [background activity restrictions](https://developer.android.com/guide/components/activities/secure-bal).

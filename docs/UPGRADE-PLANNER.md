# Upgrade Planner

The planner reads the same private per-account village JSON used by the timers. It does not log in, fetch player data, change the game, or poll the clipboard. Selecting a base/tab does not affect widget account bindings. The synthetic sample menu is never saved.

## Data Provenance

`app/src/main/assets/planner-catalog.json` is a compact factual subset of Christopher Pezza's MIT-licensed clash-of-clans-data repository at commit `7afaa0375e5865c04021836a3a4192ffcfea092d`. Checked 2026-11-09. It contains 232 Home Village and Builder Base items: buildings, traps, walls, troops, spells, siege machines, heroes, guardians, equipment, pets, and nine Phase 4 crafting modules. Module hall gates are cross-checked against pinned official tables. Each item retains its source path. License: `app/src/main/assets/data-license.txt`.

Reproduce with Python 3:

```powershell
python tools/fetch_planner_data.py
python tools/compile_planner_catalog.py
```

The first command downloads JSON from the pinned public revision in `tools/planner-sources.json` into ignored `.tools/planner-data/`. Downloaded code is never executed. The second retains costs, durations, requirements, unlock notes and hall count limits, then applies the explicit reviewed entries in `tools/planner-corrections.json`. Each correction checks the expected upstream value and fails for review if that value changes. Corrected IDs must remain unique. See [Catalog Corrections](CATALOG-CORRECTIONS.md) for the equipment-ID swap and TH12 Stone Slammer gates. Runtime use is entirely offline. Updating the revision is a deliberate developer change: review the data diff, update coverage/date in README and this document, and run tests and previews.

## Interpretation

- Levels remain those in the last export. Upgrading prerequisites do not satisfy their target level yet. Expired timers request sync rather than promoting levels.
- Identical item/level/status groups aggregate counts; different timers remain separate. Prices/durations are per item. Current Hall Only is the default scope. Next levels are kept when their hall and recursively checked facility caps fit the exported hall. Active timers, unsupported owned IDs and the next Town Hall/Builder Hall upgrade remain visible. Turning off the scope removes that cap filter; it does not promote levels or invent quantities.
- Listed Town Hall/Builder Hall, Laboratory/Star Laboratory, Hero Hall, Blacksmith, Pet House, Barracks/Dark Barracks, Spell Factory/Dark Spell Factory and Workshop gates are checked against exported levels. Details list known unmet gates.
- Equipment uses Shiny, Glowing and Starry Ore, with hero filters. Absent equipment is labeled not in export. Acquisition notes are source history, not proof of ownership or a current shop offer.
- Missing building counts use source hall count tables. Old defenses with merge-count ambiguity are not presented as missing new construction. An omitted base produces no inferred inventory.
- Default order: active timers by finish, ended timers awaiting sync, upgrades with listed gates met, then other items. The companion does not reproduce Supercell's Suggested Upgrades ranking. Cost Min/Max compare numeric baseline amounts per item. Equipment uses selected ores or the sum of listed ores, without assuming an exchange rate.
- Filters combine resources, categories and heroes. New and Permanent further restrict the category selection. More Options contains status and the default Suggested Order. Empty matches remain empty until the user changes or resets a filter.

## Limits

The community snapshot covers standard levels through Town Hall 18 and Builder Hall 10. It cannot guarantee complete or current gameplay rules. Supercharges are excluded because their reused level numbers must not become normal upgrades. Items without verified IDs are excluded. Future crafting phases, merge ingredients, hall upgrade checklists, acquisition conditions, storage capacity, currencies, builder availability and other special conditions require in-game confirmation. Unknown exported IDs remain visible as needing a catalog update.

Baseline planning costs/durations do not include events, Gold Pass, potions or helpers. Running countdowns still use the export-based timing engine, preserving in-game reductions and known active boosts without applying them twice. A menu value is not an affordability claim or a promise that an upgrade can start.

## Build Menu Layout and Filters

Version 1.18 removes duplicate card padding and uses smaller portraits, text and section gaps, with inner margins retained. Overview cards and the timer list use the same compact spacing. Android font scaling remains enabled.

The gray category header, brown list cards, green progress rows, dark section bars, resource/type grid and Cost Max/Cost Min buttons follow the supplied November 8 game screenshots, adapted for a phone companion. There is no game-background capture or accessibility service. Timers retain the full d/h/m/s format.

Current upgrades stay above remaining rows for every sort order. In the default order, up to three owned AVAILABLE non-wall rows with the shortest baseline durations become Suggested Upgrades; other rows follow. Explicit cost sorting uses one remaining section, with the hall upgrade separately identified. Local suggestions do not claim to reproduce Supercell’s recommendation algorithm. A selected currency determines cost comparison; amounts are per item, without multiplying grouped quantities. Alternative wall currencies use a single cost with both resource icons; equipment shows each required ore independently.

Army types are Troops, Spells and Siege. Building types are Army, Defense, Hero and Resource; New and Permanent further restrict selected types. Equipment uses hero filters. Builder army filters show cost sorting because that category has one upgrade currency. A staged copy is committed on Apply; Cancel leaves existing choices intact. Filters are separate per base/tab and saved across activity recreation. They reset when the menu is newly opened, and never reset merely because no rows match.

Future hero equipment is gated by its owner’s availability at the current hall. The export does not supply equipment-slot assignments or resource balances, so rows do not claim Equipped/Unequipped or red/green affordability. Pet upgrades show a Town Hall 14 unlock panel below that hall. Unknown owned IDs retain a visible fallback and running timers; equal-level jobs with different finish times stay separate.

## Level Artwork

`level-art.json` maps 1,376 exact levels across 142 items to 1,143 deduplicated model thumbnails. Nine additional currency images support costs and filters. Reused appearances reference the same resource. Upgrade cards and app/widget timers request the destination level; badges retain the exported current level. Level-indexed collection records request their current level. Heroes, equipment and other items without level-specific models keep their fixed portrait. Missing or out-of-range levels fall back to the generic item image; an unrelated level is never substituted.

The artwork changes offline as imported levels and upgrade targets change. No runtime image downloads are added. Ended timers request a fresh sync rather than assuming the upgrade completed normally. See [Level Artwork](LEVEL-ART.md) for source pins and reproduction.

Army items absent from the export appear under Unlocks / Not in Export with Check Unlock, not a zero-cost research quote. Their source prerequisites remain available in details. Ownership, calendar availability and actual research eligibility are confirmed in game.

## Crafting Modules and Navigation

Version 1.21 places Close Build Menu at the left of the base selectors. Category controls use centered, aspect-preserving 24dp icon boxes and two-line label space; the Work for Hire marker is 14dp. Compact, standard and landscape previews are regenerated with the app.

Only exported crafting modules become upgrade rows. They appear under Buildings & Heroes → Defense; Permanent excludes them. Current Hall uses the phase-specific station gate and target-level hall requirements, retaining active or ended timers for sync. Missing modules and parent defenses do not become invented new construction. A station containing exported defense types is treated as placed at level 1 when its export omits `lvl`, without rewriting the saved export. See [Crafting Requirements](CRAFTING.md) for tables, source hashes and seasonal limits.

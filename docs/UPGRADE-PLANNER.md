# Offline upgrade planner

The planner reads the same private per-account village JSON used by the timers. It does not log in, fetch player data, change the game, or poll the clipboard. Selecting a base/tab does not affect widget account bindings. The synthetic sample menu is never saved.

## Data provenance

`app/src/main/assets/planner-catalog.json` is a compact factual subset of Christopher Pezza's MIT-licensed clash-of-clans-data repository at commit `7afaa0375e5865c04021836a3a4192ffcfea092d`. Checked 2026-11-08. It contains 223 Home Village and Builder Base items: buildings, traps, walls, troops, spells, siege machines, heroes, guardians, equipment, pets, and identifiable crafted defenses. Each item retains its source path. License: `app/src/main/assets/data-license.txt`.

Reproduce with Python 3:

```powershell
python tools/fetch_planner_data.py
python tools/compile_planner_catalog.py
```

The first command downloads JSON from the pinned public revision in `tools/planner-sources.json` into ignored `.tools/planner-data/`. Downloaded code is never executed. The second retains costs, durations, requirements, unlock notes and hall count limits. Runtime use is entirely offline. Updating the revision is a deliberate developer change: review the data diff, update coverage/date in README and this document, and run tests and previews.

## Interpretation

- Levels remain those in the last export. Upgrading prerequisites do not satisfy their target level yet. Expired timers request sync rather than promoting levels.
- Identical item/level/status groups aggregate counts; different timers remain separate. Prices/durations are per item. Remaining levels are to the catalog's global standard maximum, including a current upgrade; they are not a count of upgrades available at the current hall.
- Listed Town Hall/Builder Hall, Laboratory/Star Laboratory, Hero Hall, Blacksmith, Pet House, Barracks/Dark Barracks, Spell Factory/Dark Spell Factory and Workshop gates are checked against exported levels. Details list known unmet gates.
- Equipment uses Shiny, Glowing and Starry Ore, with hero filters. Absent equipment is labeled not in export. Acquisition notes are source history, not proof of ownership or a current shop offer.
- Missing building counts use source hall count tables. Old defenses with merge-count ambiguity are not presented as missing new construction. An omitted base produces no inferred inventory.
- Default order: active timers by finish, ended timers awaiting sync, upgrades with listed gates met, then other items. The companion does not reproduce Supercell's Suggested Upgrades ranking. Cost Min/Max compare numeric baseline amounts per item. Equipment uses selected ores or the sum of listed ores, without assuming an exchange rate.
- Filters combine across Resource/Type/Hero groups, with OR within a group. Status/time/name options are companion additions. Restrictive empty filters clear to Remaining; All and Maxed may intentionally stay empty.

## Limits

The community snapshot covers standard levels through Town Hall 18 and Builder Hall 10. It cannot guarantee complete or current gameplay rules. Supercharges are excluded because their reused level numbers must not become normal upgrades. Items without verified IDs are excluded. Crafted modules, merge ingredients, hall upgrade checklists, acquisition conditions, storage capacity, currencies, builder availability and other special conditions require in-game confirmation. Unknown exported IDs remain visible as needing a catalog update.

Baseline planning costs/durations do not include events, Gold Pass, potions or helpers. Running countdowns still use the export-based timing engine, preserving in-game reductions and known active boosts without applying them twice. A menu value is not an affordability claim or a promise that an upgrade can start.

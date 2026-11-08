# Reviewed Catalog Corrections

Equipment and siege corrections were reviewed November 8, 2026; Phase 4 crafting was reviewed November 9 for version 1.21. The planner keeps its pinned upstream snapshot and applies a small, explicit correction list at build time. It does not download game rules while running.

## Equipment IDs

| Equipment | Correct Export ID | Hero |
| --- | --- | --- |
| Monolith Arrow | `90000016` | Archer Queen |
| Electro Fangs | `90000059` | Dragon Duke |

The pinned planner tables had these two IDs reversed. The [Clashy static metadata](https://github.com/ClashKingInc/clashy.py/blob/33ca78371dda26e6ccda76c49fb7d07242eeb2f0/coc/static/static_data.json) explicitly maps the IDs and heroes above. The identity overlay now includes both matching portraits, with artwork URLs and hashes in `tools/identity-overrides.json`.

The supplied export contains Monolith Arrow ID `90000016` at level 17. Previously it was interpreted as Dragon Duke equipment and hidden by that hero's later hall requirement. It now offers Monolith Arrow level 18 at Blacksmith 5, with baseline costs of 2,700 Shiny, 600 Glowing and 50 Starry Ore. No exported level or ownership is changed.

## Stone Slammer at TH12

The upstream Stone Slammer table assigned TH13 to levels 1, 2 and 3. These three hall gates are corrected to TH12. Level 4 retains its TH13 / Laboratory 11 requirement. Existing costs, times, Laboratory gates and Workshop requirements are retained.

The [Stone Slammer reference](https://clashofclans.fandom.com/wiki/Stone_Slammer) identifies its Workshop 3 / TH12 unlock. The [official 18.647.6 character table](https://game-assets.clashofclans.com/443f20eb3aa749c454b1da26259eac44f145a40b/logic/characters.csv), asset fingerprint `443f20eb3aa749c454b1da26259eac44f145a40b`, independently lists Stone Slammer's Siege Workshop production and early Laboratory requirements compatible with TH12. This table is matched by its explicit name and `TID_SIEGE_MACHINE_BALLOON`, not by a guessed row number. Its upgrade-from rows are not used to overwrite the planner's target-level costs or durations.

The supplied export has TH12, Laboratory 10, Workshop 3 and a level-1 Stone Slammer in `siege_machines`. The default current-hall Army Upgrades menu now includes its level-2 upgrade, with a level-3 cap at TH12.

## Reproduction and Checks

`tools/planner-corrections.json` records the fields, original values, corrected values and evidence. `python tools/compile_planner_catalog.py` applies only those entries, checks the expected upstream values and rejects duplicate IDs. An upstream change stops compilation for review instead of silently applying an outdated correction. The runtime catalog also records the corrections.

Android framework tests on API 26, 29 and 35 use a synthetic snapshot with the relevant levels and verify both rows in the default Build Menu, equipment names/artwork, hero gates, hall caps and Monolith Arrow's ore costs. The original personal export is inspected locally and excluded from source packages. This review does not establish that every upstream item is current; unusual requirements still need confirmation in game.

## Phase 4 Crafting Station

Version 1.21 corrects the pinned station’s TH18 availability/count gate and level-1 hall requirement to TH11 for Phase 4. The dated seasonal announcement and numeric official module gates establish the exception; the generic highest-hall rule remains relevant to future phases. The original expected values are checked before correction. Module items are compiled separately from all 90 official-checked cost/time records, with ten target-level hall gates each. See [Crafting Requirements](CRAFTING.md).

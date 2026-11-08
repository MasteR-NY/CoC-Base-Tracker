# Crafting Requirements

Reviewed **9 November 2026** for **Phase 4**, game data **18.647.6**. These are bundled baseline tables; they do not update silently over the network.

## Current Defenses

| Defense | Modules | Export IDs |
| --- | --- | --- |
| Hot Candle | Hitpoints, damage per second, seconds active | 102000033–102000035 |
| Hero Hunter | Hitpoints, damage per second, poison level | 102000036–102000038 |
| Cake-A-Pult | Hitpoints, damage per hit, explosion damage | 102000039–102000041 |

Each module has ten levels. Upgrade modules individually; the parent defense is not a separate normal building-level upgrade. Only modules included in an account’s export are shown. The tracker does not infer the selected defense, a future phase, a reset date or unused modules.

| Target Module Level | Required Town Hall |
| --- | --- |
| 1 | 11 |
| 2 | 12 |
| 3 | 13 |
| 4 | 14 |
| 5 | 15 |
| 6 | 16 |
| 7 | 17 |
| 8, 9, 10 | 18 |

The Crafting Station is available at TH11 for this phase, following [Supercell’s seasonal announcement](https://supercell.com/en/games/clashofclans/blog/news/the-awesome-quest-is-here/). This exception does not establish eligibility for a later phase. The generic building table and [support page](https://sc-clashofclans.zendesk.com/hc/en-us/articles/49529139405595-Crafted-Defenses) describe a highest-hall rule; applying that generic rule to Phase 4 would incorrectly lock the sample’s TH12 inventory.

## Using the App

Open **Build Menu → Buildings & Heroes** for module upgrades. **Defense** includes crafted modules; **Permanent** excludes them. At TH12, level 2 modules have reached their current-hall target. Turn off **Current Hall Only** to see their next upgrades with the TH13 requirement. Running upgrades stay visible regardless of this filter and keep their exported boost-adjusted timers.

Open **Overview → Crafting** to see all exported modules, including ones capped for the current hall. Tap a card for the next level’s baseline resource cost, duration and requirements. Quotes are per module, before Gold Pass or event reductions. They do not promise resources, an available builder or eligibility under every special game rule. Fresh exports confirm completed levels.

## Sources and Reproduction

The linked Fandom [Crafting Station](https://clashofclans.fandom.com/wiki/Crafting_Station), [Hot Candle](https://clashofclans.fandom.com/wiki/Hot_Candle), [Hero Hunter](https://clashofclans.fandom.com/wiki/Hero_Hunter) and [Cake-A-Pult](https://clashofclans.fandom.com/wiki/Cake-A-Pult) pages were checked. Direct page access was blocked during this review; the indexed station page described an older phase. No inaccessible Fandom table was treated as verified current data.

The source compiler instead cross-checks all **90 module-level prices and durations** from the pinned community records against official game CSVs. It reads the nine modules’ numeric hall gates from those CSVs and verifies their Phase 4/archetype membership. Hours such as `43` are compared as elapsed seconds, rather than mistaken for a disagreement with `1d 19h`.

`tools/compile_crafting_catalog.py` pins SHA-256 checksums for four official files under the asset revision `443f20eb3aa749c454b1da26259eac44f145a40b`: `logic/seasonal_defense_modules.csv`, `logic/seasonal_defense_archetypes.csv`, `logic/seasonal_defense.csv` and `logic/buildings.csv`. The resulting asset records source URLs, hashes, game version, review date and seasonal scope in `craftingReview`. Module entries retain community source paths, parent IDs and official record names. The original community license remains bundled.

```powershell
python tools/fetch_planner_data.py
python tools/compile_planner_catalog.py
```

This explicit developer import downloads data only. Changed official bytes, unexpected module membership, conflicting baseline values or changed correction inputs stop compilation for review. Android builds and the installed app use the generated asset offline. The source package includes the compiler; it excludes local caches and private account exports.

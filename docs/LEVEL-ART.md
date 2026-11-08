# Level Artwork

Version 1.16 bundles **1,376 exact level mappings for 142 items**, represented by **1,143 deduplicated model thumbnails**. Nine resource icons support Gold, Elixir, Dark Elixir, Builder Gold, Builder Elixir, Shiny Ore, Glowing Ore, Starry Ore and Gems.

Build Menu cards and running app/widget timers request the target level. The small badge remains the exported current level, and the row states the current → next level. Level-indexed collections use the current level. Items with fixed portraits, such as heroes and equipment, retain those portraits. A missing level mapping uses the generic item image. The app does not invent a nearby level’s appearance.

The mapping is offline. It refreshes automatically when the app renders a different imported level or upgrade target; it does not contact a game server. A completed timer still requests sync to confirm the new saved level.

## Sources

- [ClashKingAssets](https://github.com/ClashKingInc/ClashKingAssets/tree/fdac291ae7fbc2901221ff945834be534d0344ae/assets), revision `fdac291ae7fbc2901221ff945834be534d0344ae`. Building level images and resource icons are checked against the pinned manifest’s SHA-256 hashes.
- [Christopher Pezza’s game-data repository](https://github.com/chiefpansancolt/clash-of-clans-data/tree/7afaa0375e5865c04021836a3a4192ffcfea092d), revision `7afaa0375e5865c04021836a3a4192ffcfea092d`. Remaining models use explicit per-level normal-image paths from the source tables.

[Source Records](level-art-sources.json) preserve original URLs, downloaded-source SHA-256 hashes and transformed resource hashes. Shared appearances reference one resource. Original game artwork remains © Supercell; see [Presentation Credits](DESIGN-CREDITS.md).

## Reproduction

Run these optional developer commands with Python and Pillow installed:

```powershell
python tools/fetch_planner_data.py
python tools/build_identity_catalog.py --fetch-art
python tools/build_level_art.py
```

The tools read pinned public data and image files without executing upstream scripts or game code. Images are bounded, decoded, trimmed and resized to at most 128 × 128 pixels. The current generation had no missing source images. Normal Android builds use the bundled resources and need no artwork downloads.

Tests on API 26, 29 and 35 check exact-level changes, fixed-portrait fallbacks, widget destination levels and resource icons. An API 35 resource check resolves and decodes every unique mapped image. New game models require a reviewed catalog/artwork update and another app release.

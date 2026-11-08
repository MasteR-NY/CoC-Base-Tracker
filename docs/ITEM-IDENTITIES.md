# Item Names and Artwork

Version 1.14 bundles **878 identity records** and **869 compact artwork files**. Nine crafting module records share their parent defense portraits and add a stat symbol. Names and pictures work offline; no image service is contacted by the app.

## Crafting Modules

| Defense | IDs | Displayed Module Names | Catalog Cap |
| --- | --- | --- | --- |
| Hot Candle | 102000033–102000035 | Hitpoints, Damage, Duration | 10 each |
| Hero Hunter | 102000036–102000038 | Hitpoints, Damage, Poison Effect | 10 each |
| Cake-A-Pult | 102000039–102000041 | Hitpoints, Damage, Splash Damage | 10 each |

Module identity and cap data agree between the two pinned game-data sources below. A defense portrait identifies the parent; the app’s heart, blade, hourglass, poison and burst symbols identify the stat. These are not claimed to be extracted in-game module-button icons. Names also apply to active upgrade rows and reminders, while the planner preserves unknown requirements when no full planning row is available.

## Helpers

| Export ID | Name |
| --- | --- |
| 93000000 | Builder’s Apprentice |
| 93000001 | Lab Assistant |
| 93000002 | Alchemist |
| 93000003 | Prospector |

The earlier general name table incorrectly associated 93000002 with Prospector. The identity overlay corrects it to Alchemist and uses 93000003 for Prospector in cards, catalog lookups and availability reminders. Actual helper portraits are bundled separately for each ID.

## Cosmetic Coverage

In the supplied sample, **91 of 95 distinct cosmetic IDs** have names and artwork. This includes both sceneries, all 25 house parts, all 34 obstacle records across the two bases, 9 of 11 skins and 21 of 23 decorations. All exported helpers and all nine modules are resolved.

These four sample IDs remain unresolved in the verified sources:

| Type | Export ID |
| --- | --- |
| Hero skin | 52000268 |
| Hero skin | 52000337 |
| Decoration | 18000244 |
| Decoration | 18000248 |

They remain visible by ID and receive no borrowed portrait. Older game tables contain unnamed placeholders or lack corresponding records; a nearby ID, release date or a similar picture is insufficient evidence of a match.

Some variants share a name and source image, particularly obstacles. Tap a cosmetic to inspect its data ID and whether the source uses shared artwork. Owned quantities and exported levels are preserved.

## Sources and Reproduction

- [Clashy static game metadata](https://github.com/ClashKingInc/clashy.py/blob/33ca78371dda26e6ccda76c49fb7d07242eeb2f0/coc/static/static_data.json), revision `33ca78371dda26e6ccda76c49fb7d07242eeb2f0`.
- [ClashKing artwork and manifest](https://github.com/ClashKingInc/ClashKingAssets/tree/fdac291ae7fbc2901221ff945834be534d0344ae/assets), revision `fdac291ae7fbc2901221ff945834be534d0344ae`.
- [Crafting module tables and defense portraits](https://github.com/chiefpansancolt/clash-of-clans-data/tree/7afaa0375e5865c04021836a3a4192ffcfea092d/data/home/crafted-defenses), revision `7afaa0375e5865c04021836a3a4192ffcfea092d`.

Run `python tools/build_identity_catalog.py --fetch-art` with Pillow installed. The tool downloads data without executing upstream scripts, checks each manifest-backed image’s SHA-256, trims transparent margins and writes static thumbnails no larger than 128 × 128 pixels. Animated source previews contribute their first frame. Game-data and artwork revisions and source hashes are recorded in `app/src/main/assets/item-identities.json`.

The helper source script and cached inputs are not needed to run the app or its Android build. Future identity updates should change pinned revisions, review mappings, rebuild artwork, update coverage notes and rerun tests. Keep an unresolved ID until a reliable mapping is available.

Framework tests on API 26, 29 and 35 verify module/helper mappings, cosmetic identities and fallback behavior. A resource test resolves and decodes every declared icon. README previews render the actual updated screens. Upstream license notices and game-art attribution are retained in [Presentation Credits](DESIGN-CREDITS.md).

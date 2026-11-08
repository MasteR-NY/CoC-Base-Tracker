# Item Names and Artwork

Version 1.18 bundles **882 identity records** and **873 compact artwork files**. Nine crafting module records share their parent defense portraits and add a stat symbol. Names and pictures work offline; no image service is contacted by the app.

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

The supplied export has 95 distinct cosmetic IDs. **93 now have verified names and bundled artwork**, including `18000244` **Amber Hot Streak** and `18000248` **Sour Elixir Fountain**.

The current public game package points to official asset fingerprint `443f20eb3aa749c454b1da26259eac44f145a40b`. Its CDN manifest reports **18.647.6**. The decoration index contains explicit GlobalIDs, and its localization table supplies these exact English names. The two matching portraits are published in ClashPerk’s decoration catalog and bundled with source hashes. This replaces the older CSV snapshot used for the earlier search.

| Export ID | Current Official Table Record | App Status |
| --- | --- | --- |
| 18000244 | `Community Flamer Orange` → `TID_DECO_COMMUNITY_FLAMER_ORANGE` → Amber Hot Streak | Named, with artwork |
| 18000248 | `cosmic_fountain` → `TID_DECO_COSMIC_FOUNTAIN` → Sour Elixir Fountain | Named, with artwork |
| 52000268 | `MinionHero018`, Minion Prince; blank TID and Icon | ID retained; no verified public name or icon |
| 52000337 | `GrandWarden059`, Grand Warden; blank TID and Icon | ID retained; no verified public name or icon |

The [November announcement](https://supercell.com/en/games/clashofclans/blog/news/clash-of-titans-is-hot-off-the-press/) names current skins but does not map them to export IDs. It cannot establish a name for either blank skin row. No seasonal-date guesses or borrowed skin portraits are applied.

Some other source records share a display name or picture across variants. Tapping a cosmetic shows its data ID and identifies shared artwork so those variants remain distinguishable. Coverage describes the supplied snapshot, not a complete list of every collectible in the game.

### Rechecking the Official Tables

Run `python tools/check_cosmetic_ids.py`. It downloads only three pinned data files, verifies their recorded SHA-256 hashes, decodes the tables without executing game code, and prints the four records above. No account data is needed. The current public package was inspected only through bounded ZIP-directory/fingerprint reads; it was never installed or executed.

`tools/identity-overrides.json` records the official fingerprint, English TIDs, source hashes and the two artwork URLs. Official files: [decoration index](https://game-assets.clashofclans.com/443f20eb3aa749c454b1da26259eac44f145a40b/logic/decos_logic.scindex), [English text table](https://game-assets.clashofclans.com/443f20eb3aa749c454b1da26259eac44f145a40b/localization/texts.csv), [skin table](https://game-assets.clashofclans.com/443f20eb3aa749c454b1da26259eac44f145a40b/logic/skins.csv). Artwork is linked from [ClashPerk’s decoration catalog](https://wiki.clashperk.com/decorations?group=Home+village).

## Equipment Identity Corrections

Monolith Arrow is `90000016` (Archer Queen); Electro Fangs is `90000059` (Dragon Duke). Version 1.18 corrects the reversed IDs in the planner snapshot and adds the matching pinned portraits to the identity overlay. Their hero gates now follow the correct equipment. See [Catalog Corrections](CATALOG-CORRECTIONS.md) for evidence and validation.

## Sources and Reproduction

- [Clashy static game metadata](https://github.com/ClashKingInc/clashy.py/blob/33ca78371dda26e6ccda76c49fb7d07242eeb2f0/coc/static/static_data.json), revision `33ca78371dda26e6ccda76c49fb7d07242eeb2f0`.
- [ClashKing artwork and manifest](https://github.com/ClashKingInc/ClashKingAssets/tree/fdac291ae7fbc2901221ff945834be534d0344ae/assets), revision `fdac291ae7fbc2901221ff945834be534d0344ae`.
- [Crafting module tables and defense portraits](https://github.com/chiefpansancolt/clash-of-clans-data/tree/7afaa0375e5865c04021836a3a4192ffcfea092d/data/home/crafted-defenses), revision `7afaa0375e5865c04021836a3a4192ffcfea092d`.

Run `python tools/build_identity_catalog.py --fetch-art` with Pillow installed. The tool downloads data without executing upstream scripts, checks each manifest-backed image’s SHA-256, trims transparent margins and writes static thumbnails no larger than 128 × 128 pixels. Animated source previews contribute their first frame. Game-data and artwork revisions and source hashes are recorded in `app/src/main/assets/item-identities.json`.

The helper source script and cached inputs are not needed to run the app or its Android build. Future identity updates should change pinned revisions, review mappings, rebuild artwork, update coverage notes and rerun tests. Keep an unresolved ID until a reliable mapping is available.

Framework tests on API 26, 29 and 35 verify module/helper mappings, cosmetic identities and fallback behavior. A resource test resolves and decodes every declared icon. README previews render the actual updated screens. Upstream license notices and game-art attribution are retained in [Presentation Credits](DESIGN-CREDITS.md).

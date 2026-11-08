# Presentation credits

Version 1.4 also adapts the gray headers, brown upgrade cards, black level badges and category-tab structure shown in [Supercell's October 2026 interface announcement](https://supercell.com/en/games/clashofclans/blog/news/weve-revamped-the-clash-of-clans-user-interface/). Its public Build Menu and filter screenshots were used as visual references, not included as app or README screenshots. README previews render this app's actual Android layouts with synthetic data.

The app and widget presentation in CoC Base Tracker (formerly Village Clock) is adapted at the user's request from [Yusuf-Bedri-Bitiren/Coc-Widget](https://github.com/Yusuf-Bedri-Bitiren/Coc-Widget), reference commit `68626b688116ff19a535ec5be630f8b9bd85d759`.

The reference's wooden panel, green/gold row drawables, gold buttons, outlines, item icons, and Lilita One font informed this adaptation. The panel and rows are adapted to CoC Base Tracker's scrollable lists and independent widget account selections. Application logic, account storage, and timer calculations remain this project's implementation.

The reference repository did not include a project-wide license at the reviewed commit. Its adapted presentation files retain this origin notice; the MIT license for original Village Clock code does not relicense third-party presentation files or game artwork. This source/APK is delivered for the user's requested local adaptation.

- Item artwork © Supercell, referenced under [Supercell's Fan Content Policy](https://supercell.com/en/fan-content-policy/). This is an unofficial, non-commercial fan companion.
- Lilita One by Juan Montoreano: SIL Open Font License 1.1, retained in `app/src/main/assets/LilitaOne-OFL.txt`.
- Factual item names/durations dataset: Christopher Pezza, MIT; retained in `app/src/main/assets/data-license.txt`.

Green bars represent countdown progress since the last export; they are not a guessed percentage of the entire upgrade's original duration. Completed countdowns use the reference's gold row treatment.

## Identity Artwork Added in Version 1.14

Additional helper portraits, skins, scenery thumbnails, decorations, obstacles and house-part images are vendored from [ClashKingAssets](https://github.com/ClashKingInc/ClashKingAssets) at revision `fdac291ae7fbc2901221ff945834be534d0344ae`. Credit to ClashKing for extracting, organizing and publishing these resources. Original game artwork remains © Supercell. Its upstream [GPL-3.0 notice](licenses/ClashKingAssets-GPL-3.0.txt) is retained; this project's MIT license does not relicense third-party artwork or upstream material.

Identity metadata is derived from the pinned [Clashy game-data file](https://github.com/ClashKingInc/clashy.py/blob/33ca78371dda26e6ccda76c49fb7d07242eeb2f0/coc/static/static_data.json). Its [MIT notice](licenses/Clashy-MIT.txt) is retained. Crafted defense portraits and cross-checked module metadata use the existing pinned chiefpansancolt source. Stat symbols are original Android vector resources; module cards combine these symbols with a defense portrait.

Artwork is resized for offline app use; animated previews use a static frame. No source publisher's extraction code is executed or included as app code. Source IDs, name coverage and reproducible import steps are documented in [Item Names and Artwork](ITEM-IDENTITIES.md).

## Decoration Artwork Added in Version 1.15

Amber Hot Streak and Sour Elixir Fountain names are verified against explicit IDs and English TIDs in Supercell’s public 18.647.6 asset tables. Their game-art portraits are provided by [ClashPerk’s decoration catalog](https://wiki.clashperk.com/decorations?group=Home+village), with exact URLs and hashes in `tools/identity-overrides.json`. Credit to ClashPerk for publishing these extracted previews; original artwork remains © Supercell under its Fan Content Policy. Only the two game-art images are reused; no site UI, extraction code or prose is bundled.

## Level Artwork Added in Version 1.16

Building models and nine resource icons use the same pinned ClashKingAssets manifest, with source SHA-256 verification. Additional level-specific models use images explicitly referenced by the pinned Christopher Pezza catalog. Original game artwork remains © Supercell; existing ClashKing and factual catalog notices continue to apply. Artwork is cropped, resized and deduplicated for offline use. Exact source URLs, original hashes and transformed-file hashes are retained in `docs/level-art-sources.json`. No upstream extraction code is executed or shipped. The sliders vector is original app artwork.

The revised Build Menu follows the user-supplied game screenshots dated November 8, 2026. The README previews render the actual app with synthetic village data; personal screenshots are not included in the release source archive.

## Shared Boost Artwork Added in Version 1.17

Builder Potion, Research Potion, Pet Potion, Builder Bite, Study Soup, Clock Tower and the hammer symbol use the existing pinned ClashKingAssets game-art source. Its retained license/attribution applies; original artwork remains © Supercell. Source hashes are recorded in `effect-art-sources.json`. The hammer is a representative game-item symbol for Hammer Jam, not a claimed copy of the event’s promotional logo. No new app-runtime artwork requests are made.

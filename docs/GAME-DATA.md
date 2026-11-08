# Official Game Data References

Reviewed **8 November 2026**. Official announcements explain game rules; exports provide saved account state. The planner’s separate community catalog supplies ID mappings and baseline tables. These sources serve different purposes.

## Sources Used in This Release

| Source | What Was Checked | App Behavior |
| --- | --- | --- |
| [October Balance Announcement](https://supercell.com/en/games/clashofclans/blog/news/balance-changes-incoming-october-2026/) · 17 Oct 2026 | Troop and equipment combat adjustments | A dated News reference; no unsupported rewrite of upgrade work or prices |
| [The Awesome Quest](https://supercell.com/en/games/clashofclans/blog/news/the-awesome-quest-is-here/) · 31 Jul 2026 | Current crafting trio and seasonal TH11 availability | Show modules actually exported by lower-hall accounts |
| [Support: Crafted Defenses](https://sc-clashofclans.zendesk.com/hc/en-us/articles/49529139405595-Crafted-Defenses) | Individual modules and phase changes | Keep unverified caps, stat mappings and phase dates unknown |
| [Support: Helpers and the Helper Hut](https://sc-clashofclans.zendesk.com/hc/en-us/articles/49529140074139-Helpers-The-Helper-Hut) | Shared work-day schedule and helper behavior | Use exported availability deadlines and supported assigned-helper rules |
| [November Season](https://supercell.com/en/games/clashofclans/blog/news/clash-of-titans-is-hot-off-the-press/) · 1 Nov 2026 | Next hall announced for mid-month | Retain explicit TH18 / BH10 catalog coverage instead of inventing future levels |
| [Hammer Jam Details](https://supercell.com/en/games/clashofclans/blog/news/hammer-jam-is-here-backup-has-arrived/) · 1 Nov 2026 | Eligible reductions and exclusions | Keep exported active work authoritative; avoid a duplicate discount |

The general crafting support page and seasonal announcement differ on the unlock hall. The dated seasonal exception and actual export explain the TH12 sample’s modules, without establishing unknown module caps or a permanent future unlock rule.

## Official Social and Community Channels

Use **Follow Clash of Clans On** on [Supercell’s Clash of Clans page](https://supercell.com/en/games/clashofclans/) for its official social and community destinations. Trace factual changes to an identifiable Supercell announcement or staff statement; a community discussion alone is not a verified rule.

The app’s Official Channels button opens that directory. It does not collect posts, require social login or send village exports to a community service.

## Updating Game Data

1. Review official blog posts, release notes, support and linked channels. Record publication and stated effective dates separately; an announcement is not proof of deployment.
2. Distinguish combat stats from level caps, prices, durations and export-field changes. A damage adjustment alone does not change upgrade work.
3. Reconcile seasonal or conflicting guidance and retain source links. Missing data is not a guessed name, cap or ID mapping.
4. Change baseline tables only with verifiable data and provenance. Use existing tools for reproducible community catalog imports; official prose rarely supplies a full machine-readable table.
5. Test affected timing, hall gates, grouped counts, equipment and modules. Validate a fresh game export when available.
6. Update `app/src/main/assets/official-updates.json`, references and release notes, then rebuild previews and distribute a signed app update.

Bundled News notes are reviewed snapshots. Update checks fetch app-release metadata only. Timer rules are never silently replaced by a downloaded data file or social post. See [Upgrade Planner Reference](UPGRADE-PLANNER.md) and [Timer Rules](TIMING.md).

Version 1.14 adds a separate pinned identity/artwork layer, documented in [Item Names and Artwork](ITEM-IDENTITIES.md). Official posts explain the module roles and helper behavior; community game-file metadata supplies exact numeric ID mappings and the corresponding art. Identity imports do not overwrite active work or apply new timer multipliers.

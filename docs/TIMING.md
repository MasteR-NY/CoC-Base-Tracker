# Timer rules in CoC Base Tracker

CoC Base Tracker predicts finish times from the village snapshot, not from the catalog's normal full upgrade duration. The snapshot's `timestamp` anchors all calculations. The `timer` is treated as remaining upgrade work after any permanent/event time reductions already applied by the game.

## Stacked copies of a potion

The game exports the total remaining duration of a boost. Two Builder Potions therefore mean up to two hours at the same 10x speed; they do not produce one hour at 20x. The same rule applies to Research, Pet, and Clock Tower potions. The app uses the entire exported duration, including partly consumed stacks.

## Different boosts and helpers

For each interval between boost expirations, the calculation adds their extra work rates to normal speed:

| Source | Normal speed plus extra work |
|---|---:|
| Builder Potion | 1 + 9 |
| Research Potion | 1 + 23 |
| Pet Potion | 1 + 23 |
| Clock Tower boost | 1 + 9 |
| Builder Bite | 1 + 1 |
| Study Soup | 1 + 3 |
| Assigned Apprentice / Assistant | 1 + helper level |

Independent active sources add their extra rates rather than multiplying the helper's work by a potion. For example, a Builder Potion, Builder Bite, and level-8 Apprentice together perform 1 + 9 + 1 + 8 = 19 seconds of base work per second on the assigned job. The helper applies to that job only. Each source stops contributing at its own exported expiration. Exact integer arithmetic rounds a fractional finish second upward.

Builder Potions/Bites affect Home Village builders, research boosts affect the Laboratory, Pet Potions affect the Pet House, and Clock Tower boosts affect Builder Base builders and the Star Laboratory. The current parser applies the exported Study Soup field to the Laboratory and Pet House.

Already stacked durations are never truncated to one hour, and duplicate entries for one boost family cannot add another independent speed multiplier.

## Fast-upgrade events and Gold Pass

Time discounts are retained from the exported remaining work. CoC Base Tracker does not halve the timer again merely because Hammer Jam is active, and does not reconstruct a duration from a normal catalog value. This also avoids applying a Home Village discount to Builder Base, or to excluded upgrades such as Crafted Defenses and Supercharges.

The app includes an informational note for the announced November 2026 Hammer Jam snapshot window. That calendar note never changes timer arithmetic. Other duration-reduction events work through their exported remaining time in the same way; the app does not download event calendars or invent unknown speed multipliers.

## What still requires a fresh export

New potions, extra stacked doses, snack activations, early finishes, newly assigned or recurring helpers, new upgrades, and changed event effects require a fresh village export. The app models the helper work active in the snapshot; it does not assume unobserved future daily helper assignments. Events that change a boost's actual speed beyond the supported rates require an updated rule rather than a guessed multiplier.

The export schema is unofficial. Tests cover the arithmetic, queues, expirations, duration stacks, event non-duplication, and account separation, but these tests do not establish live-game accuracy for every future combination. Compare the first import against the in-game timer.

## References

- [Supercell: using multiple potions](https://support.supercell.com/clash-of-clans/en/articles/magic-items-and-the-trader.html)
- [Supercell: Builder Potion does not affect the Apprentice](https://supercell.com/en/games/clashofclans/blog/release-notes/june-update-full-patch-notes/)
- [Supercell: November 2026 Hammer Jam, reductions and exclusions](https://supercell.com/en/games/clashofclans/blog/news/hammer-jam-is-here-backup-has-arrived/)
- [ClashKing's export field model](https://github.com/ClashKingInc/clashy.py/blob/main/coc/game_data.py)

The references document potion duration stacking, helper independence, event eligibility, and export fields. They do not provide a complete official village-export schema or certify this app.

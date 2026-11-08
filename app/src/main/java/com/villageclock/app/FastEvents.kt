package com.villageclock.app

import java.time.Instant

/** Exported event discounts must never be applied twice. */
object FastEvents {
    private val JAM_START = Instant.parse("2026-11-01T08:00:00Z").toEpochMilli()
    @JvmField val JAM_END: Long = Instant.parse("2026-11-16T08:00:00Z").toEpochMilli()
    @JvmStatic fun hammerJam(now: Long): Boolean = now >= JAM_START && now < JAM_END
    @JvmStatic fun noteAt(snapshotTime: Long): String = if (hammerJam(snapshotTime))
        "Hammer Jam snapshot: keep the game’s reduced upgrade times. No second 50% discount is applied."
    else "Event and Gold Pass reductions are kept as exported. No blanket event multiplier is applied."
}

package com.villageclock.app;

import java.time.Instant;

/** Event discounts are already in the remaining work exported by the game. Never discount twice. */
public final class FastEvents {
    private static final long JAM_START = Instant.parse("2026-11-01T08:00:00Z").toEpochMilli();
    private static final long JAM_END = Instant.parse("2026-11-16T08:00:00Z").toEpochMilli();
    public static String noteAt(long snapshotTime) {
        return snapshotTime >= JAM_START && snapshotTime < JAM_END
            ? "Hammer Jam snapshot: keep the game’s reduced upgrade times. No second 50% discount is applied."
            : "Event and Gold Pass reductions are kept as exported. No blanket event multiplier is applied.";
    }
    private FastEvents() { }
}

package com.villageclock.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Integrates remaining work across each boost's expiry without multiplying independent workers. */
public final class UpgradeTiming {
    public static final class Boost {
        public final String family;
        public final long extraSpeed, remainingSeconds;
        public Boost(String family, long extraSpeed, long remainingSeconds) {
            this.family = family; this.extraSpeed = extraSpeed; this.remainingSeconds = remainingSeconds;
        }
    }
    public static long secondsToFinish(long exportedWorkSeconds, List<Boost> incoming) {
        if (exportedWorkSeconds < 0) throw new IllegalArgumentException("Negative work");
        if (exportedWorkSeconds == 0) return 0;
        Map<String, Boost> families = new LinkedHashMap<>();
        for (Boost boost : incoming) {
            if (boost.extraSpeed <= 0 || boost.remainingSeconds <= 0) continue;
            Boost previous = families.get(boost.family);
            // The export already contains the total duration of stacked copies of a potion.
            // The same family must never be counted twice as another independent speed multiplier.
            if (previous == null || previous.remainingSeconds < boost.remainingSeconds) families.put(boost.family, boost);
        }
        TreeSet<Long> ends = new TreeSet<>();
        for (Boost boost : families.values()) ends.add(boost.remainingSeconds);
        long work = exportedWorkSeconds, elapsed = 0;
        for (long end : ends) {
            long rate = 1;
            for (Boost boost : families.values()) if (boost.remainingSeconds >= end) rate = Math.addExact(rate, boost.extraSpeed);
            long span = end - elapsed;
            long needed = work / rate + (work % rate == 0 ? 0 : 1);
            if (needed <= span) return Math.addExact(elapsed, needed);
            // Since needed > span, this product is strictly smaller than remaining work.
            work -= span * rate;
            elapsed = end;
        }
        return Math.addExact(elapsed, work);
    }
    private UpgradeTiming() { }
}

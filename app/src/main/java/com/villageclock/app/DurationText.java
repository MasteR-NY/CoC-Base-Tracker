package com.villageclock.app;

/** Shared, unambiguous duration text. Negative remaining time never appears. */
public final class DurationText {
    public static String seconds(long seconds) {
        long value = Math.max(0, seconds);
        return value / 86400 + "d " + value % 86400 / 3600 + "h "
            + value % 3600 / 60 + "m " + value % 60 + "s";
    }
    public static String remaining(long finishAt, long now) {
        long ms = Math.max(0, finishAt - now);
        return seconds(ms / 1000 + (ms % 1000 == 0 ? 0 : 1));
    }
    private DurationText() { }
}

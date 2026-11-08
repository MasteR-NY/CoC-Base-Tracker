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
    public static String compactRemaining(long end,long now){
        long seconds=Math.max(0,end-now)/1000+(Math.max(0,end-now)%1000==0?0:1);
        long days=seconds/86400,hours=seconds%86400/3600,minutes=seconds%3600/60;
        return (days>0?days+"d ":"")+(days>0||hours>0?hours+"h ":"")+(days>0||hours>0||minutes>0?minutes+"m ":"")+seconds%60+"s";
    }
    private DurationText() { }
}

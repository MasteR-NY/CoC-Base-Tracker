package com.villageclock.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Village {
    public enum Queue {
        BUILDERS("Builders", "B"), LAB("Laboratory", "L"), PETS("Pet House", "P"),
        BASE("Builder Base", "BB"), STAR_LAB("Star Laboratory", "SL");
        public final String label, glyph;
        Queue(String label, String glyph) { this.label = label; this.glyph = glyph; }
    }
    public static final class Upgrade {
        public final int dataId, level;
        public final String name, key;
        public final Queue queue;
        public final long finishAt;
        public final long snapshotAt;
        public final UpgradeTiming.Plan timing;
        public final boolean extraBuilder;
        Upgrade(int dataId, int level, String name, Queue queue, long finishAt, boolean extraBuilder, int ordinal) {
            this(dataId, level, name, queue, finishAt, extraBuilder, ordinal, 0, null);
        }
        Upgrade(int dataId, int level, String name, Queue queue, long finishAt, boolean extraBuilder, int ordinal, long snapshotAt, UpgradeTiming.Plan timing) {
            this.dataId = dataId; this.level = level; this.name = name; this.queue = queue;
            this.finishAt = finishAt; this.extraBuilder = extraBuilder;
            this.snapshotAt = snapshotAt; this.timing = timing;
            this.key = queue.name() + ":" + dataId + ":" + level + ":" + ordinal;
        }
        public boolean done(long now) { return finishAt <= now; }
        public String label() { return name + "  → " + level; }
        public long elapsed(long now) { return Math.max(0, (now - snapshotAt) / 1000); }
        public boolean hasBoosts() { return timing != null && timing.boosted(); }
        public String workText(long now) { return "Work left: " + DurationText.seconds(timing.remainingWork(elapsed(now))); }
        public String etaText(long now) { return "Finishes in: " + DurationText.remaining(finishAt, now); }
        public String boostText(long now) {
            if (!hasBoosts()) return "";
            long elapsed = elapsed(now);
            java.util.List<String> sources = timing.sources(elapsed);
            return timing.speed(elapsed) + "× · " + (sources.isEmpty() ? "Normal speed" : String.join(" + ", sources));
        }
    }
    public final String tag, playerName;
    public final long exportedAt;
    public final int townHall, builders, baseBuilders;
    public final List<Upgrade> upgrades;
    Village(String tag, long exportedAt, int townHall, int builders, int baseBuilders, List<Upgrade> upgrades) {
        this(tag, "", exportedAt, townHall, builders, baseBuilders, upgrades);
    }
    Village(String tag, String playerName, long exportedAt, int townHall, int builders, int baseBuilders, List<Upgrade> upgrades) {
        this.tag = tag; this.playerName = AccountNames.clean(playerName); this.exportedAt = exportedAt; this.townHall = townHall;
        this.builders = builders; this.baseBuilders = baseBuilders;
        ArrayList<Upgrade> ordered = new ArrayList<>(upgrades);
        ordered.sort(java.util.Comparator.comparingLong(u -> u.finishAt));
        this.upgrades = Collections.unmodifiableList(ordered);
    }
    public int running(Queue queue, long now) {
        int count = 0;
        for (Upgrade u : upgrades) if (u.queue == queue && !u.done(now)) count++;
        return count;
    }
    public int running(long now) {
        int count = 0;
        for (Upgrade u : upgrades) if (!u.done(now)) count++;
        return count;
    }
    public int freeBuilders(long now) {
        int occupied = 0;
        for (Upgrade u : upgrades) if (u.queue == Queue.BUILDERS && !u.extraBuilder && !u.done(now)) occupied++;
        return Math.max(0, builders - occupied);
    }
    public long nextFinish(long now) {
        for (Upgrade u : upgrades) if (u.finishAt > now) return u.finishAt;
        return 0;
    }
    public List<Upgrade> forWidget(long now) {
        ArrayList<Upgrade> result = new ArrayList<>();
        for (Upgrade u : upgrades) if (!u.done(now)) result.add(u);
        for (Upgrade u : upgrades) if (u.done(now)) result.add(u);
        return result;
    }
}

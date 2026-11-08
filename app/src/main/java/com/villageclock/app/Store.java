package com.villageclock.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class Store {
    private static volatile Catalog catalog;
    public static SharedPreferences prefs(Context context) { return context.getSharedPreferences("village-clock", Context.MODE_PRIVATE); }
    public static Catalog catalog(Context context) {
        if (catalog == null) synchronized (Store.class) { if (catalog == null) catalog = new Catalog(context.getApplicationContext()); }
        return catalog;
    }
    private static VillageArchive archive(Context c) { return new VillageArchive(new File(c.getFilesDir(), "villages")); }
    private static String key(String property, String tag) { return property + "." + VillageArchive.canonical(tag); }
    private static synchronized void migrate(Context c) {
        if (prefs(c).getBoolean("multi-village-migrated", false)) return;
        AtomicFile legacy = new AtomicFile(new File(c.getFilesDir(), "village.json"));
        if (legacy.getBaseFile().exists() || new File(legacy.getBaseFile().getPath() + ".bak").exists()) {
            try {
                String text = new String(legacy.readFully(), StandardCharsets.UTF_8);
                Village v = ExportParser.parse(text, catalog(c), System.currentTimeMillis());
                String tag = VillageArchive.canonical(v.tag);
                Village existing = read(c, tag);
                if (existing == null || existing.exportedAt <= v.exportedAt) archive(c).write(tag, text);
                if (!prefs(c).contains("active-tag")) {
                    prefs(c).edit().putString("active-tag", tag).putString(key("name", tag), prefs(c).getString("name", ""))
                        .putLong(key("notified-through", tag), prefs(c).getLong("notified-through", System.currentTimeMillis()))
                        .putLong(key("last-import", tag), prefs(c).getLong("last-import", System.currentTimeMillis())).commit();
                }
                if (prefs(c).edit().putBoolean("multi-village-migrated", true).commit()) legacy.delete();
                return;
            } catch (Exception failure) { return; } // Retry later; preserve the legacy file.
        }
        prefs(c).edit().putBoolean("multi-village-migrated", true).commit();
    }
    private static Village read(Context c, String tag) {
        try { return ExportParser.parse(archive(c).read(tag), catalog(c), System.currentTimeMillis()); }
        catch (Exception invalidOrMissing) { return null; }
    }
    public static synchronized List<Village> all(Context c) {
        migrate(c);
        ArrayList<Village> result = new ArrayList<>();
        for (String tag : archive(c).tags()) { Village v = read(c, tag); if (v != null) result.add(v); }
        result.sort((a, b) -> title(c, a).compareToIgnoreCase(title(c, b)));
        return result;
    }
    public static synchronized String activeTag(Context c) {
        migrate(c);
        String active = prefs(c).getString("active-tag", "");
        if (!active.isEmpty() && read(c, active) != null) return active;
        for (String tag : archive(c).tags()) if (read(c, tag) != null) { prefs(c).edit().putString("active-tag", tag).apply(); return tag; }
        return "";
    }
    public static void select(Context c, String tag) { prefs(c).edit().putString("active-tag", VillageArchive.canonical(tag)).apply(); }
    public static synchronized Village load(Context c) { String tag = activeTag(c); return tag.isEmpty() ? null : read(c, tag); }
    public static synchronized Village load(Context c, String tag) { migrate(c); return tag == null || tag.isEmpty() ? null : read(c, tag); }
    public static synchronized void save(Context c, String text, Village village) throws IOException {
        migrate(c);
        String tag = VillageArchive.canonical(village.tag);
        Village previous = read(c, tag);
        if (previous != null && previous.exportedAt > village.exportedAt) throw new IOException("An older export cannot replace this village.");
        archive(c).write(tag, text);
        prefs(c).edit().putString("active-tag", tag).putLong(key("notified-through", tag), System.currentTimeMillis())
            .putLong(key("last-import", tag), System.currentTimeMillis()).apply();
    }
    public static synchronized void clear(Context c) throws IOException {
        String tag = activeTag(c);
        if (tag.isEmpty()) return;
        archive(c).remove(tag);
        prefs(c).edit().remove(key("name", tag)).remove(key("notified-through", tag)).remove(key("last-import", tag)).remove("active-tag").apply();
        c.getSystemService(android.app.NotificationManager.class).cancel(tag, 7);
    }
    public static void rename(Context c, String tag, String name) { prefs(c).edit().putString(key("name", tag), name.trim()).apply(); }
    public static String title(Context c, Village v) {
        if (v == null) return "Choose village";
        String custom = prefs(c).getString(key("name", v.tag), "").trim();
        return custom.isEmpty() ? v.tag : custom;
    }
    public static long notifiedThrough(Context c, String tag) { return prefs(c).getLong(key("notified-through", tag), System.currentTimeMillis()); }
    public static void setNotifiedThrough(Context c, String tag, long time) { prefs(c).edit().putLong(key("notified-through", tag), time).apply(); }
    public static String widgetTag(Context c, int id) {
        String pref = "widget-tag." + id;
        if (prefs(c).contains(pref)) return prefs(c).getString(pref, "");
        String tag = activeTag(c);
        if (!tag.isEmpty()) bind(c, id, tag);
        return tag;
    }
    public static void bind(Context c, int id, String tag) { prefs(c).edit().putString("widget-tag." + id, VillageArchive.canonical(tag)).commit(); }
    public static void removeWidget(Context c, int id) { prefs(c).edit().remove("widget-tag." + id).apply(); }
    public static String age(long timestamp, long now) {
        long mins = Math.max(0, (now - timestamp) / 60000);
        if (mins == 0) return "just now";
        if (mins < 60) return mins + "m ago";
        if (mins < 1440) return (mins / 60) + "h ago";
        return (mins / 1440) + "d ago";
    }
}

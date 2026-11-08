package com.villageclock.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Parses a snapshot, never a Supercell login/session. All JSON is treated as untrusted. */
public final class ExportParser {
    public static final int MAX_CHARS = 1_000_000;
    private static final long MAX_SECONDS = 10L * 366 * 86400;
    public interface Names { String name(int id); }
    public static final class InvalidExport extends Exception {
        public InvalidExport(String message) { super(message); }
    }
    public static Village parse(String text, Names names, long now) throws InvalidExport {
        if (text == null || text.length() > MAX_CHARS) throw new InvalidExport("Export is empty or too large (maximum 1 MB)." );
        try {
            JSONObject root = new JSONObject(text.trim());
            long seconds = bounded(root, "timestamp", Long.MAX_VALUE);
            if (seconds < 1420070400L || seconds > now / 1000 + 300) throw new InvalidExport("The export timestamp is invalid. Copy a fresh village export from Clash." );
            if (!(root.opt("buildings") instanceof JSONArray) && !(root.opt("buildings2") instanceof JSONArray))
                throw new InvalidExport("This is not a village export. Use Settings → More Settings → Export village data." );
            String tag = root.optString("tag", "").trim();
            if (!tag.matches("#[A-Za-z0-9]{2,16}")) throw new InvalidExport("This export has no valid player tag." );
            long exportedAt = seconds * 1000;
            Map<Integer,Integer> helperLevels = new HashMap<>();
            Map<Integer,Long> helperCooldowns = new HashMap<>();
            long helperCooldown = 0;
            JSONArray helpers = root.optJSONArray("helpers");
            if (helpers != null) for (int i = 0; i < helpers.length(); i++) {
                JSONObject h = helpers.optJSONObject(i);
                if (h != null) helperLevels.put(h.optInt("data"), Math.max(0, Math.min(20, h.optInt("lvl"))));
                if (h != null && h.has("helper_cooldown")) helperCooldowns.put(h.optInt("data"), bounded(h, "helper_cooldown", MAX_SECONDS));
            }
            JSONObject boosts = root.optJSONObject("boosts");
            if (boosts != null) helperCooldown = Math.max(helperCooldown, bounded(boosts, "helper_cooldown", MAX_SECONDS));
            String[] fields = {"buildings", "traps", "heroes", "guardians", "units", "spells", "siege_machines", "pets", "buildings2", "traps2", "heroes2", "units2"};
            Village.Queue[] queues = {Village.Queue.BUILDERS, Village.Queue.BUILDERS, Village.Queue.BUILDERS, Village.Queue.BUILDERS,
                Village.Queue.LAB, Village.Queue.LAB, Village.Queue.LAB, Village.Queue.PETS,
                Village.Queue.BASE, Village.Queue.BASE, Village.Queue.BASE, Village.Queue.STAR_LAB};
            ArrayList<Village.Upgrade> jobs = new ArrayList<>();
            int townHall = 0, builders = 0, baseBuilders = 0, totalEntries = 0;
            for (int f = 0; f < fields.length; f++) {
                JSONArray entries = root.optJSONArray(fields[f]);
                if (entries == null) continue;
                totalEntries += entries.length();
                if (totalEntries > 10000) throw new InvalidExport("This export contains too many entries." );
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject entry = entries.optJSONObject(i);
                    if (entry == null) continue;
                    int id = entry.optInt("data", -1);
                    int count = Math.max(1, Math.min(50, entry.optInt("cnt", 1)));
                    if (fields[f].equals("buildings")) {
                        if (id == 1000001) townHall = Math.max(0, Math.min(100, entry.optInt("lvl")));
                        if (id == 1000015) builders += count;
                        if (id == 1000064) builders++;
                    }
                    if (fields[f].equals("buildings2")) {
                        if (id == 1000034) baseBuilders = Math.max(1, baseBuilders);
                        if (id == 1000078 || id == 1000047) baseBuilders = 2;
                    }
                    add(entry, count, queues[f], exportedAt, boosts, helperLevels, helperCooldowns, helperCooldown, names, jobs);
                    JSONArray types = entry.optJSONArray("types");
                    if (types != null) for (int t = 0; t < Math.min(types.length(), 100); t++) {
                        JSONObject type = types.optJSONObject(t);
                        JSONArray modules = type == null ? null : type.optJSONArray("modules");
                        if (modules != null) for (int m = 0; m < Math.min(modules.length(), 100); m++) {
                            JSONObject module = modules.optJSONObject(m);
                            if (module != null) add(module, 1, queues[f], exportedAt, boosts, helperLevels, helperCooldowns, helperCooldown, names, jobs);
                        }
                    }
                }
            }
            List<ActiveEffects.Effect> effects=new ArrayList<>();
            if(boosts!=null)for(String key:new String[]{"builder_boost","builder_consumable","lab_boost","pet_boost","lab_consumable","clocktower_boost"}){
                long duration=bounded(boosts,key,MAX_SECONDS);if(duration>0)effects.add(ActiveEffects.effect(key,exportedAt+duration*1000));
            }
            return new Village(tag, AccountNames.clean(root.opt("name")), exportedAt, townHall, Math.min(builders, 7), baseBuilders, jobs,effects);
        } catch (JSONException | StackOverflowError ex) {
            throw new InvalidExport("The copied text is not valid village JSON." );
        }
    }
    private static void add(JSONObject item, int count, Village.Queue queue, long at, JSONObject boosts,
                            Map<Integer,Integer> levels, Map<Integer,Long> cooldowns, long helperCooldown, Names names, List<Village.Upgrade> out) throws InvalidExport {
        long work = bounded(item, "timer", MAX_SECONDS);
        if (work == 0) return;
        int id = item.optInt("data", -1);
        if (id < 0) throw new InvalidExport("An upgrade has no valid item ID." );
        int level = item.optInt("lvl", 0);
        if (level < 0 || level > 1000) throw new InvalidExport("An upgrade level is out of range." );
        ArrayList<UpgradeTiming.Boost> windows = new ArrayList<>();
        if (boosts != null) {
            if (queue == Village.Queue.BUILDERS) { boost(windows, boosts, "builder_boost", 9, "builder-speed", "Builder Potion"); boost(windows, boosts, "builder_consumable", 1, "builder-speed", "Builder Bite"); }
            if (queue == Village.Queue.LAB) { boost(windows, boosts, "lab_boost", 23, "research", "Research Potion"); boost(windows, boosts, "lab_consumable", 3, "soup", "Study Soup"); }
            if (queue == Village.Queue.PETS) { boost(windows, boosts, "pet_boost", 23, "pet", "Pet Potion"); boost(windows, boosts, "lab_consumable", 3, "soup", "Study Soup"); }
            if (queue == Village.Queue.BASE || queue == Village.Queue.STAR_LAB) boost(windows, boosts, "clocktower_boost", 9, "clock-tower", "Clock Tower");
        }
        int helper = queue == Village.Queue.BUILDERS ? 93000000 : queue == Village.Queue.LAB ? 93000001 : 0;
        long helperDuration = bounded(item, "helper_timer", MAX_SECONDS);
        long cooldown=cooldowns.getOrDefault(helper,helperCooldown);
        boolean recurring=item.has("helper_recurrent")?item.optBoolean("helper_recurrent",false):item.optBoolean("recurrent_helper",false);
        UpgradeTiming.Helper assignment = helper == 0 || levels.getOrDefault(helper, 0) == 0 ? null : new UpgradeTiming.Helper(
            levels.get(helper), helperDuration, recurring && cooldown > 0 ? cooldown : -1,
            queue == Village.Queue.BUILDERS ? "Builder's Apprentice" : "Lab Assistant");
        UpgradeTiming.Plan plan = new UpgradeTiming.Plan(work, windows, assignment);
        long finish = at + plan.secondsToFinish() * 1000;
        String name = names.name(id);
        if (name == null || name.isEmpty()) name = queue.label + " item #" + id;
        if (out.size() + count > 500) throw new InvalidExport("Too many active upgrades in this export." );
        for (int i = 0; i < count; i++) out.add(new Village.Upgrade(id, level + 1, name, queue, finish, item.optBoolean("extra", false), out.size(), at, plan));
    }
    private static long bounded(JSONObject obj, String field, long max) throws InvalidExport {
        if (!obj.has(field)) return 0;
        Object v = obj.opt(field);
        if (!(v instanceof Number)) throw new InvalidExport("Invalid numeric field: " + field);
        double d = ((Number)v).doubleValue();
        if (!Double.isFinite(d) || d < 0 || d > max || d != Math.floor(d)) throw new InvalidExport("Out-of-range field: " + field);
        return ((Number)v).longValue();
    }
    private static void boost(List<UpgradeTiming.Boost> out, JSONObject obj, String key, long extra, String group, String label) throws InvalidExport {
        long duration = bounded(obj, key, MAX_SECONDS);
        if (duration > 0) out.add(new UpgradeTiming.Boost(key, extra, duration, group, label));
    }
}

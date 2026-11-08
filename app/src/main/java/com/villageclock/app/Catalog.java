package com.villageclock.app;

import android.content.Context;
import org.json.JSONObject;
import java.util.HashMap;
import java.nio.charset.StandardCharsets;

public final class Catalog implements ExportParser.Names {
    private final HashMap<Integer,String> names = new HashMap<>();
    public Catalog(Context context) {
        try (java.io.InputStream in = context.getAssets().open("gamedata.json")) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            JSONObject data = new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
            java.util.Iterator<String> keys = data.keys();
            while (keys.hasNext()) { String key = keys.next(); names.put(Integer.parseInt(key), data.getJSONObject(key).getString("n")); }
            for (UpgradePlanner.Item item : Store.planner(context).items()) names.put(item.id, item.name);
            for(ItemIdentities.Entry entry:ItemIdentities.load(context).items())names.put(entry.id,entry.name);
        } catch (Exception ignored) { /* Unknown IDs retain a visible, stable fallback. */ }
    }
    @Override public String name(int id) { return names.get(id); }
}

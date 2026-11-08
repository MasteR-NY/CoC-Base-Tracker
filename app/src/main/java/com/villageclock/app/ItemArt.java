package com.villageclock.app;

import android.annotation.SuppressLint;
import android.content.Context;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemArt {
    private static final ConcurrentHashMap<Integer,Integer> icons = new ConcurrentHashMap<>();
    @SuppressLint("DiscouragedApi")
    public static int icon(Context c, int dataId) {
        Integer known = icons.get(dataId);
        if (known != null) return known;
        int id = c.getResources().getIdentifier("item_" + dataId, "drawable", c.getPackageName());
        if (id == 0) id = R.drawable.ic_village;
        icons.put(dataId, id); return id;
    }
    private ItemArt() { }
}

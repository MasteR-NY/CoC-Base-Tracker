package com.villageclock.app;

import android.annotation.SuppressLint;
import android.content.Context;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemArt {
    public static int icon(Context c,int dataId,int level){int exact=LevelArt.icon(c,dataId,level);return exact!=0?exact:icon(c,dataId);}
    private static final ConcurrentHashMap<Integer,Integer> icons = new ConcurrentHashMap<>();
    @SuppressLint("DiscouragedApi")
    public static int icon(Context c, int dataId) {
        Integer known = icons.get(dataId);
        if (known != null) return known;
        int id = c.getResources().getIdentifier("item_" + dataId, "drawable", c.getPackageName());
        ItemIdentities.Entry identity=ItemIdentities.load(c).item(dataId);
        if(identity!=null&&!identity.icon.isEmpty()){int resolved=c.getResources().getIdentifier(identity.icon,"drawable",c.getPackageName());if(resolved!=0)id=resolved;}
        if (id == 0) id = R.drawable.ic_village;
        icons.put(dataId, id); return id;
    }
    private ItemArt() { }
}

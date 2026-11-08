package com.villageclock.app;

import android.content.Context;
import org.json.*;
import java.util.*;

/** One exported work-day/Clock Tower deadline; never invents recurring availability alarms. */
public final class Availability {
    public static final String PREF="notify-availability";
    public static final class Entry {
        public final String key,name;public final int id,level;public final long readyAt;
        Entry(String key,String name,int id,int level,long readyAt){this.key=key;this.name=name;this.id=id;this.level=level;this.readyAt=readyAt;}
        public String status(long now){return readyAt<0?"Cooldown not supplied":readyAt<=now?"Ready · sync to confirm":"Ready in: "+DurationText.remaining(readyAt,now);}
    }
    public static List<Entry> parse(String raw,long exportedAt)throws JSONException{
        JSONObject root=new JSONObject(raw);List<Entry> result=new ArrayList<>();JSONArray helpers=root.optJSONArray("helpers");
        Set<Integer> seen=new HashSet<>();if(helpers!=null)for(int i=0;i<helpers.length();i++){JSONObject h=helpers.optJSONObject(i);if(h==null||h.optInt("data",-1)<0)continue;int id=h.optInt("data");if(!seen.add(id))continue;
            result.add(new Entry("helper-"+id,ItemIdentities.helperName(id),id,h.optInt("lvl"),deadline(h,"helper_cooldown",exportedAt)));
        }
        JSONObject boosts=root.optJSONObject("boosts");boolean clock=boosts!=null&&boosts.has("clocktower_cooldown");JSONArray buildings=root.optJSONArray("buildings2");
        if(buildings!=null)for(int i=0;i<buildings.length();i++){JSONObject b=buildings.optJSONObject(i);if(b!=null&&b.optInt("data")==1000039)clock=true;}
        if(clock)result.add(new Entry("clock-tower","Clock Tower",1000039,-1,boosts==null?-1:deadline(boosts,"clocktower_cooldown",exportedAt)));
        return result;
    }
    private static long deadline(JSONObject object,String key,long at){Object n=object.opt(key);if(!(n instanceof Number))return -1;double d=((Number)n).doubleValue();return !Double.isFinite(d)||d<0||d>10L*366*86400||d!=Math.floor(d)?-1:at+((Number)n).longValue()*1000;}
    static List<Entry> load(Context c,Village village){try{return parse(Store.raw(c,village.tag),village.exportedAt);}catch(Exception missing){return Collections.emptyList();}}
    public static long cursor(Context c,String tag){String key="availability-through."+VillageArchive.canonical(tag);if(!Store.prefs(c).contains(key))Store.prefs(c).edit().putLong(key,System.currentTimeMillis()).commit();return Store.prefs(c).getLong(key,System.currentTimeMillis());}
    static void through(Context c,String tag,long at){Store.prefs(c).edit().putLong("availability-through."+VillageArchive.canonical(tag),at).apply();}
    private Availability(){ }
}

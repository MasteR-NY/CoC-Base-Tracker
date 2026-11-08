package com.villageclock.app;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Versioned offline identity overlay. Unmapped IDs never borrow a neighboring item's name. */
public final class ItemIdentities {
    private static volatile ItemIdentities instance;
    public static final class Entry {
        public final int id,parent,cap;public final String name,shortName,kind,icon;public final boolean sharedArtwork;
        Entry(int id,JSONObject data){this.id=id;name=data.optString("name");shortName=data.optString("short_name",name);kind=data.optString("kind");icon=data.optString("icon");parent=data.optInt("parent");cap=data.optInt("cap");sharedArtwork=data.optBoolean("shared_artwork");}
    }
    private final Map<Integer,Entry> entries=new HashMap<>();
    public ItemIdentities(String raw)throws JSONException{JSONObject data=new JSONObject(raw).getJSONObject("items");Iterator<String> keys=data.keys();while(keys.hasNext()){String key=keys.next();int id=Integer.parseInt(key);entries.put(id,new Entry(id,data.getJSONObject(key)));}}
    public Entry item(int id){return entries.get(id);}
    public Collection<Entry> items(){return Collections.unmodifiableCollection(entries.values());}
    public static ItemIdentities load(Context context){
        if(instance==null)synchronized(ItemIdentities.class){if(instance==null)try(InputStream input=context.getAssets().open("item-identities.json")){
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=input.read(buf))!=-1)out.write(buf,0,n);instance=new ItemIdentities(out.toString(StandardCharsets.UTF_8.name()));
        }catch(Exception missing){try{instance=new ItemIdentities("{\"items\":{}}");}catch(JSONException impossible){throw new IllegalStateException(impossible);}}}return instance;
    }
    public static String helperName(int id){switch(id){case 93000000:return "Builder’s Apprentice";case 93000001:return "Lab Assistant";case 93000002:return "Alchemist";case 93000003:return "Prospector";default:return "Helper #"+id;}}
    public static int moduleSymbol(String name){if(name.contains("Hitpoints"))return R.drawable.module_health;if(name.contains("Poison"))return R.drawable.module_poison;if(name.contains("Seconds")||name.contains("Duration"))return R.drawable.module_time;if(name.contains("Explosion")||name.contains("Splash"))return R.drawable.module_explosion;return R.drawable.module_damage;}
}

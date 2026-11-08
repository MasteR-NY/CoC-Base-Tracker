package com.villageclock.app;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exact level mappings only. Shared appearances reference the same bundled drawable. */
final class LevelArt {
    private static JSONObject items;
    private static final Map<String,Integer> cache=new HashMap<>();
    private static synchronized JSONObject items(Context context){
        if(items==null)try(InputStream input=context.getAssets().open("level-art.json")){
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
            while((count=input.read(buffer))!=-1){if(out.size()+count>2*1024*1024)throw new IOException("Artwork index too large");out.write(buffer,0,count);}
            items=new JSONObject(out.toString(StandardCharsets.UTF_8.name())).getJSONObject("items");
        }catch(Exception missing){items=new JSONObject();}return items;
    }
    private static synchronized int resource(Context c,String key,String level){
        String lookup=key+":"+level;Integer known=cache.get(lookup);if(known!=null)return known;
        JSONObject map=items(c).optJSONObject(key);String name=map==null?"":map.optString(level);
        int id=name.isEmpty()?0:c.getResources().getIdentifier(name,"drawable",c.getPackageName());cache.put(lookup,id);return id;
    }
    static int icon(Context c,int dataId,int level){return resource(c,String.valueOf(dataId),String.valueOf(level));}
    static int currency(Context c,String name){return resource(c,"currency",name.toLowerCase(Locale.ROOT).replace(' ','_').replace("glowing_ore","glowy_ore"));}
    private LevelArt(){}
}

package com.villageclock.app;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Bundled, dated source notes. Browsing is an explicit user action. */
public final class OfficialUpdates {
    public static void show(Activity activity){
        try(InputStream input=activity.getAssets().open("official-updates.json")){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1)bytes.write(buffer,0,n);
            JSONObject notes=new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));JSONArray entries=notes.getJSONArray("notes");String[] titles=new String[entries.length()];
            for(int i=0;i<titles.length;i++)titles[i]=entries.getJSONObject(i).getString("title");
            new AlertDialog.Builder(activity).setTitle("Game Updates · "+notes.getString("reviewed")).setItems(titles,(dialog,which)->{
                try{JSONObject entry=entries.getJSONObject(which);new AlertDialog.Builder(activity).setTitle(entry.getString("title")).setMessage(entry.getString("date")+"\n\n"+entry.getString("summary")+"\n\nIncluded with this release; not a live feed.")
                    .setPositiveButton("Official Source",(d,w)->open(activity,entry.optString("url"))).setNegativeButton("Close",null).show();}catch(JSONException ignored){ }
            }).setPositiveButton("Official Blog",(d,w)->open(activity,notes.optString("blog"))).setNeutralButton("Official Channels",(d,w)->open(activity,notes.optString("channels"))).setNegativeButton("Close",null).show();
        }catch(Exception unavailable){Toast.makeText(activity,"Source notes are unavailable in this build.",Toast.LENGTH_LONG).show();}
    }
    private static void open(Activity a,String address){Uri u=Uri.parse(address);String host=u.getHost();if(!"https".equals(u.getScheme())||!("supercell.com".equals(host)||"sc-clashofclans.zendesk.com".equals(host)))return;
        try{a.startActivity(new Intent(Intent.ACTION_VIEW,u).addCategory(Intent.CATEGORY_BROWSABLE));}catch(ActivityNotFoundException missing){Toast.makeText(a,address,Toast.LENGTH_LONG).show();}}
    private OfficialUpdates(){ }
}

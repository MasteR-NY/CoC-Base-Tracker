package com.villageclock.app;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bundled notes for the installed application, independent of network update checks. */
public final class ReleaseNotes {
    private static final String SEEN="release-guide-seen-version";
    private static volatile ReleaseNotes bundled;
    public static final class Highlight {
        public final String title,body;
        Highlight(JSONObject json)throws JSONException{title=json.getString("title");body=json.getString("body");}
    }
    public static final class Entry {
        public final String version,title;
        public final int versionCode;
        public final List<Highlight> highlights;
        public final List<String> steps;
        Entry(JSONObject json)throws JSONException{
            version=json.getString("version");title=json.getString("title");versionCode=json.getInt("versionCode");
            List<Highlight> points=new ArrayList<>();JSONArray source=json.getJSONArray("highlights");
            for(int i=0;i<source.length();i++)points.add(new Highlight(source.getJSONObject(i)));
            highlights=Collections.unmodifiableList(points);
            List<String> how=new ArrayList<>();JSONArray instructions=json.optJSONArray("steps");
            if(instructions!=null)for(int i=0;i<instructions.length();i++)how.add(instructions.getString(i));
            steps=Collections.unmodifiableList(how);
        }
    }
    public final List<Entry> entries;
    public ReleaseNotes(String text)throws JSONException{
        JSONArray source=new JSONObject(text).getJSONArray("releases");List<Entry> parsed=new ArrayList<>();
        for(int i=0;i<source.length();i++)parsed.add(new Entry(source.getJSONObject(i)));
        parsed.sort((a,b)->Integer.compare(b.versionCode,a.versionCode));entries=Collections.unmodifiableList(parsed);
    }
    public Entry current(){
        for(Entry entry:entries)if(entry.versionCode==BuildConfig.VERSION_CODE&&entry.version.equals(BuildConfig.VERSION_NAME))return entry;
        throw new IllegalStateException("Add release notes for the installed version before publishing.");
    }
    public static ReleaseNotes load(Context c)throws Exception{
        if(bundled==null)synchronized(ReleaseNotes.class){if(bundled==null)try(InputStream input=c.getAssets().open("release-notes.json")){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int size;
            while((size=input.read(buffer))!=-1)bytes.write(buffer,0,size);
            bundled=new ReleaseNotes(bytes.toString(StandardCharsets.UTF_8.name()));
        }}
        return bundled;
    }
    public static boolean needsSummary(Context c){return Store.prefs(c).getInt(SEEN,0)<BuildConfig.VERSION_CODE;}
    public static void markSeen(Context c){Store.prefs(c).edit().putInt(SEEN,Math.max(BuildConfig.VERSION_CODE,Store.prefs(c).getInt(SEEN,0))).apply();}
}

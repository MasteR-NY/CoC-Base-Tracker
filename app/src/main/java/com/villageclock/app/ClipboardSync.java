package com.villageclock.app;

import android.content.ClipData;
import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Foreground-only export detection. Never follows clipboard URIs or saves unrelated text. */
public final class ClipboardSync {
    static final String PREF="auto-sync-on-return",LAST="auto-sync-last-export";
    public static boolean enabled(Context c){return Store.prefs(c).getBoolean(PREF,true);}
    static String candidate(ClipData clip){
        if(clip==null||clip.getItemCount()!=1)return null;
        CharSequence value=clip.getItemAt(0).getText();
        if(value==null||value.length()>ExportParser.MAX_CHARS)return null;
        String text=value.toString().trim();
        return text.startsWith("{")&&text.contains("\"tag\"")&&text.contains("\"timestamp\"")
            &&(text.contains("\"buildings\"")||text.contains("\"buildings2\""))?text:null;
    }
    static String fingerprint(String text){
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder result=new StringBuilder(64);for(byte b:digest)result.append(Character.forDigit((b&255)>>4,16)).append(Character.forDigit(b&15,16));return result.toString();
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    static boolean newer(Village incoming,Village saved){return saved==null||incoming.exportedAt>saved.exportedAt;}
    private ClipboardSync(){ }
}

package com.villageclock.app;

import org.json.JSONArray;
import org.json.JSONObject;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Explicit, anonymous release checks. No saved account data enters a request. */
public final class UpdateChecker {
    public static final String API_URL="https://api.github.com/repos/MasteR-NY/CoC-Base-Tracker/releases/latest";
    public static final String RELEASES_URL="https://github.com/MasteR-NY/CoC-Base-Tracker/releases/latest";
    private static final int MAX_BYTES=512*1024;
    public static final class CheckException extends IOException {
        CheckException(String message){super(message);}
    }
    public static final class Release {
        public final String version;
        public final boolean hasApk;
        private Release(String version,boolean hasApk){this.version=version;this.hasApk=hasApk;}
    }
    interface ConnectionFactory { HttpsURLConnection open(URL url)throws IOException; }
    public static Release fetchLatest(String installedVersion)throws IOException {
        return fetchLatest(installedVersion,url->(HttpsURLConnection)url.openConnection());
    }
    static Release fetchLatest(String installedVersion,ConnectionFactory factory)throws IOException {
        HttpsURLConnection connection=factory.open(new URL(API_URL));
        try {
            connection.setConnectTimeout(8000);connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept","application/vnd.github+json");
            connection.setRequestProperty("User-Agent","CoC-Base-Tracker/"+installedVersion);
            int status=connection.getResponseCode();
            if(status==404)throw new CheckException("No public release was found. You can check the Releases page manually.");
            if(status==403||status==429)throw new CheckException("GitHub could not accept this update check. Try again later or open Releases.");
            if(status!=200)throw new CheckException("GitHub is unavailable for this update check. Try again later or open Releases.");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream input=connection.getInputStream()){
                byte[] buffer=new byte[8192];int count;
                while((count=input.read(buffer))!=-1){
                    if(bytes.size()+count>MAX_BYTES)throw new CheckException("The release response is too large. Open Releases to check manually.");
                    bytes.write(buffer,0,count);
                }
            }
            return parse(bytes.toString(StandardCharsets.UTF_8.name()));
        }finally{connection.disconnect();}
    }
    public static Release parse(String json)throws CheckException {
        if(json==null||json.length()>MAX_BYTES)throw new CheckException("The release response could not be read. Open Releases to check manually.");
        try {
            JSONObject root=new JSONObject(json);
            if(!Boolean.FALSE.equals(root.opt("draft"))||!Boolean.FALSE.equals(root.opt("prerelease")))
                throw new CheckException("No stable published release was returned. Open Releases to check manually.");
            Object tag=root.opt("tag_name");
            if(!(tag instanceof String))throw new CheckException("The release has no readable version. Open Releases to check manually.");
            String version=((String)tag).trim();parts(version);
            boolean apk=false;JSONArray assets=root.optJSONArray("assets");
            if(assets!=null)for(int i=0;i<assets.length();i++){
                JSONObject asset=assets.optJSONObject(i);
                if(asset!=null&&"uploaded".equals(asset.optString("state"))&&asset.optString("name").toLowerCase(java.util.Locale.ROOT).endsWith(".apk"))apk=true;
            }
            // Navigation always uses our fixed repository URL, never a URL supplied by JSON.
            return new Release(version.replaceFirst("^[vV]",""),apk);
        }catch(CheckException e){throw e;}
        catch(Exception e){throw new CheckException("The release response could not be read. Open Releases to check manually.");}
    }
    public static int compare(String a,String b)throws CheckException {
        BigInteger[] left=parts(a),right=parts(b);
        for(int i=0;i<Math.max(left.length,right.length);i++){
            int result=(i<left.length?left[i]:BigInteger.ZERO).compareTo(i<right.length?right[i]:BigInteger.ZERO);
            if(result!=0)return result;
        }
        return 0;
    }
    private static BigInteger[] parts(String value)throws CheckException {
        if(value==null||value.length()>80||!value.trim().matches("[vV]?[0-9]+(?:\\.[0-9]+){0,3}(?:\\+[0-9A-Za-z.-]+)?"))
            throw new CheckException("This release uses an unrecognized version format. Open Releases to check manually.");
        String number=value.trim().replaceFirst("^[vV]","").split("\\+",2)[0];String[] strings=number.split("\\.");
        BigInteger[] numbers=new BigInteger[strings.length];for(int i=0;i<strings.length;i++)numbers[i]=new BigInteger(strings[i]);return numbers;
    }
    private UpdateChecker(){ }
}

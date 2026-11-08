package com.villageclock.app;

import org.json.JSONArray;
import org.json.JSONObject;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.math.BigInteger;
import java.net.URL;
import java.net.URI;
import java.net.URLDecoder;
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
        public final boolean apkStatusKnown;
        public final String tag,downloadUrl,fileName,sha256;
        public final long bytes;
        private Release(String version,boolean hasApk){this(version,hasApk,true);}
        private Release(String version,boolean hasApk,boolean known){this(version,hasApk,known,"v"+version,"","","",0);}
        private Release(String version,boolean hasApk,boolean known,String tag,String url,String name,String hash,long bytes){this.version=version;this.hasApk=hasApk;this.apkStatusKnown=known;this.tag=tag;this.downloadUrl=url;this.fileName=name;this.sha256=hash;this.bytes=bytes;}
        String downloadUrl(){return downloadUrl.isEmpty()?UpdateChecker.downloadUrl(tag,"CoC-Base-Tracker-"+version+".apk"):downloadUrl;}
    }
    interface ConnectionFactory { HttpsURLConnection open(URL url)throws IOException; }
    public static Release fetchLatest(String installedVersion)throws IOException {
        return fetchLatest(installedVersion,url->(HttpsURLConnection)url.openConnection());
    }
    static Release fetchLatest(String installedVersion,ConnectionFactory factory)throws IOException {
        try{return fetchApi(installedVersion,factory);}
        catch(IOException apiFailure){
            try{return fetchReleaseRedirect(installedVersion,factory);}
            catch(IOException pageFailure){throw new CheckException("GitHub API: "+failureDetail(apiFailure)+"\n\nGitHub Releases: "+failureDetail(pageFailure)+"\n\nOpen Releases to check in your browser.");}
        }
    }
    private static void configure(HttpsURLConnection connection,String installedVersion)throws IOException{
        connection.setConnectTimeout(12000);connection.setReadTimeout(12000);
        connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent","CoC-Base-Tracker/"+installedVersion);
        // Avoid reusing a stale pooled socket on older Android network stacks.
        connection.setRequestProperty("Connection","close");
    }
    private static Release fetchApi(String installedVersion,ConnectionFactory factory)throws IOException {
        HttpsURLConnection connection=factory.open(new URL(API_URL));
        try {
            configure(connection,installedVersion);
            connection.setRequestProperty("Accept","application/vnd.github+json");
            int status=connection.getResponseCode();
            if(status==404)throw new CheckException("No public release was found. You can check the Releases page manually.");
            if(status==403||status==429)throw new CheckException("GitHub denied or limited the request (HTTP "+status+"). Try again later.");
            if(status!=200)throw new CheckException("The server returned HTTP "+status+".");
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
    /** The public latest-release redirect is independent of the API's anonymous request limit. */
    private static Release fetchReleaseRedirect(String installedVersion,ConnectionFactory factory)throws IOException{
        HttpsURLConnection connection=factory.open(new URL(RELEASES_URL));
        try{
            configure(connection,installedVersion);connection.setRequestProperty("Accept","text/html");
            int status=connection.getResponseCode();
            if(status!=301&&status!=302&&status!=303&&status!=307&&status!=308)throw new CheckException("No latest-release redirect was returned (HTTP "+status+").");
            return parseReleaseRedirect(connection.getHeaderField("Location"));
        }finally{connection.disconnect();}
    }
    static Release parseReleaseRedirect(String location)throws CheckException{
        try{
            if(location==null||location.length()>1024)throw new IllegalArgumentException();
            URI uri=new URI(RELEASES_URL).resolve(location);
            String prefix="/MasteR-NY/CoC-Base-Tracker/releases/tag/";
            if(!"https".equalsIgnoreCase(uri.getScheme())||!"github.com".equalsIgnoreCase(uri.getHost())
                ||uri.getUserInfo()!=null||(uri.getPort()!=-1&&uri.getPort()!=443)||uri.getRawQuery()!=null||uri.getRawFragment()!=null
                ||!uri.getRawPath().startsWith(prefix))throw new IllegalArgumentException();
            String version=URLDecoder.decode(uri.getRawPath().substring(prefix.length()),"UTF-8");parts(version);
            return new Release(version.replaceFirst("^[vV]",""),false,false,version,"","","",0);
        }catch(Exception invalid){throw new CheckException("The release redirect could not be verified for this repository.");}
    }
    public static String failureDetail(Exception failure){
        if(failure instanceof CheckException)return failure.getMessage();
        if(failure instanceof javax.net.ssl.SSLException)return "Secure connection failed ("+failure.getClass().getSimpleName()+"). Check the phone’s date/time, Android security updates, or network filtering.";
        if(failure instanceof java.net.UnknownHostException)return "GitHub’s address could not be resolved. Check Private DNS, VPN or network filtering.";
        if(failure instanceof java.net.SocketTimeoutException)return "The connection timed out. Retry or try another network.";
        if(failure instanceof java.net.ConnectException)return "GitHub could not be reached. Check VPN, firewall or network filtering.";
        if(failure instanceof java.net.SocketException)return "SocketException: "+diagnostic(failure)+". Check this app’s Wi-Fi/mobile-data access in Android Settings and any VPN/firewall rules. Android’s download service is also tried. Include this system detail when reporting a failure.";
        return "The check failed ("+failure.getClass().getSimpleName()+"). Try another network or open Releases.";
    }
    private static String diagnostic(Throwable failure){
        StringBuilder out=new StringBuilder();
        for(int depth=0;failure!=null&&depth<3;depth++,failure=failure.getCause()){
            String message=failure.getMessage();if(message!=null&&!message.trim().isEmpty()){
                if(out.length()>0)out.append("; ");out.append(message.replaceAll("[\\p{Cntrl}]"," "));
            }
        }
        String value=out.length()==0?"no further system detail":out.toString();return value.substring(0,Math.min(300,value.length()));
    }
    static String downloadUrl(String tag,String name){
        if(tag==null||!tag.matches("[vV]?[0-9]+(?:\\.[0-9]+){0,3}(?:\\+[0-9A-Za-z.-]+)?")||name==null||!name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,120}\\.apk"))return "";
        return "https://github.com/MasteR-NY/CoC-Base-Tracker/releases/download/"+tag+"/"+name;
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
            boolean apk=false;String url="",name="",hash="";long size=0;JSONArray assets=root.optJSONArray("assets");
            if(assets!=null)for(int i=0;i<assets.length();i++){
                JSONObject asset=assets.optJSONObject(i);
                if(asset!=null&&"uploaded".equals(asset.optString("state"))&&asset.optString("name").endsWith(".apk")){
                    String candidate=asset.optString("name"),expected=downloadUrl(version,candidate),actual=asset.optString("browser_download_url");
                    if(!expected.isEmpty()&&expected.equals(actual)){
                        apk=true;name=candidate;url=expected;size=asset.optLong("size",0);
                        String digest=asset.optString("digest");hash=digest.matches("sha256:[a-fA-F0-9]{64}")?digest.substring(7).toLowerCase(java.util.Locale.ROOT):"";break;
                    }
                }
            }
            // Navigation always uses our fixed repository URL, never a URL supplied by JSON.
            return new Release(version.replaceFirst("^[vV]",""),apk,true,version,url,name,hash,size);
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

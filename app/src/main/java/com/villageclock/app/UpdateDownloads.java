package com.villageclock.app;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** System-managed downloads. Only one explicitly requested update is retained. */
final class UpdateDownloads {
    static final long MAX_APK=100L*1024*1024;
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("app-update",Context.MODE_PRIVATE);}
    static DownloadManager manager(Context c)throws IOException{
        DownloadManager dm=(DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE);
        if(dm==null)throw new IOException("Android Download Manager is unavailable. Enable Downloads in Android Settings.");return dm;
    }
    static long id(Context c){return prefs(c).getLong("id",-1);}
    static String version(Context c){return prefs(c).getString("version","");}
    interface ReleaseCheck { UpdateChecker.Release fetch()throws Exception; }
    static UpdateChecker.Release fetchLatest(Context context)throws Exception{
        return fetchLatest(()->UpdateChecker.fetchLatest(BuildConfig.VERSION_NAME),()->fetchMetadata(context));
    }
    static UpdateChecker.Release fetchLatest(ReleaseCheck app,ReleaseCheck system)throws Exception{
        try{return app.fetch();}catch(IOException failure){
            try{return system.fetch();}catch(Exception fallback){throw new UpdateChecker.CheckException(UpdateChecker.failureDetail(failure)+"\n\nAndroid Downloads: "+(fallback.getMessage()==null?fallback.getClass().getSimpleName():fallback.getMessage()));}
        }
    }
    static File directory(Context c)throws IOException{
        File dir=c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if(dir==null)throw new IOException("Download storage is unavailable. Free some storage and retry.");return dir;
    }
    static final class Status {
        final int state,reason;final long downloaded,total;
        Status(int s,int r,long d,long t){state=s;reason=r;downloaded=d;total=t;}
    }
    static Status status(Context c,long id)throws IOException{
        try(Cursor cursor=manager(c).query(new DownloadManager.Query().setFilterById(id))){
            if(cursor==null||!cursor.moveToFirst())throw new IOException("This download is no longer available. Start it again.");
            return new Status(cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)));
        }catch(RuntimeException failure){throw new IOException("Android Downloads could not be read: "+failure.getClass().getSimpleName(),failure);}
    }
    static DownloadManager.Request request(String url,String title){
        return new DownloadManager.Request(Uri.parse(url)).setTitle(title).setAllowedOverMetered(true).setAllowedOverRoaming(false)
            .addRequestHeader("User-Agent","CoC-Base-Tracker/"+BuildConfig.VERSION_NAME);
    }
    /** Independent system service fallback for phones whose app sockets fail. Called on a worker. */
    static UpdateChecker.Release fetchMetadata(Context context)throws IOException{
        Context c=context.getApplicationContext();DownloadManager dm=manager(c);directory(c);
        String name="release-check-"+UUID.randomUUID()+".json";
        DownloadManager.Request request=request(UpdateChecker.API_URL,"Checking CoC Base Tracker updates")
            .addRequestHeader("Accept","application/vnd.github+json").setMimeType("application/json")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(c,Environment.DIRECTORY_DOWNLOADS,name);
        long download=-1;
        try{
            download=dm.enqueue(request);long deadline=android.os.SystemClock.elapsedRealtime()+35000;
            while(android.os.SystemClock.elapsedRealtime()<deadline){
                Status s=status(c,download);
                if(s.total>512*1024||s.downloaded>512*1024)throw new IOException("The release response is too large.");
                if(s.state==DownloadManager.STATUS_FAILED)throw new IOException(downloadFailure(s.reason));
                if(s.state==DownloadManager.STATUS_SUCCESSFUL){
                    try(InputStream input=c.getContentResolver().openInputStream(dm.getUriForDownloadedFile(download))){
                        return UpdateChecker.parse(new String(readBounded(input,512*1024),StandardCharsets.UTF_8));
                    }
                }
                try{Thread.sleep(300);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("The update check was cancelled.");}
            }
            throw new IOException("Android Downloads did not finish the check within 35 seconds. Check that Downloads is enabled and can access the network.");
        }catch(RuntimeException failure){throw new IOException("Android Downloads: "+failure.getClass().getSimpleName()+". Check that Downloads is enabled in Android Settings.",failure);}
        finally{if(download!=-1)dm.remove(download);new File(directory(c),name).delete();}
    }
    static byte[] readBounded(InputStream input,int max)throws IOException{
        if(input==null)throw new IOException("The downloaded file could not be opened.");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
        while((count=input.read(buffer))!=-1){if(bytes.size()+count>max)throw new IOException("The downloaded response is too large.");bytes.write(buffer,0,count);}return bytes.toByteArray();
    }
    static synchronized long start(Context c,UpdateChecker.Release release)throws IOException{
        if(release.apkStatusKnown&&!release.hasApk)throw new IOException("The published release has no downloadable APK yet.");
        if(UpdateChecker.compare(release.version,BuildConfig.VERSION_NAME)<=0)throw new IOException("This release is not newer than the installed app.");
        if(release.bytes>MAX_APK)throw new IOException("This APK exceeds the 100 MB download limit.");
        String url=release.downloadUrl();if(url.isEmpty())throw new IOException("The APK download address could not be verified.");
        if(id(c)!=-1&&url.equals(prefs(c).getString("url","")))return id(c);
        cancel(c);File file=new File(directory(c),"tracker-update.apk");
        if(file.exists()&&!file.delete())throw new IOException("The previous update file could not be cleared.");
        DownloadManager.Request request=request(url,"CoC Base Tracker v"+release.version)
            .setDescription("Downloading update · return to the tracker to install")
            .setMimeType("application/vnd.android.package-archive").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(c,Environment.DIRECTORY_DOWNLOADS,"tracker-update.apk");
        long download;
        try{download=manager(c).enqueue(request);}catch(RuntimeException error){throw new IOException("The download could not be started. Enable Downloads in Android Settings.",error);}
        prefs(c).edit().putLong("id",download).putString("version",release.version).putString("url",url).putString("sha256",release.sha256).putLong("size",release.bytes).apply();return download;
    }
    static synchronized void cancel(Context c)throws IOException{
        long download=id(c);if(download!=-1)manager(c).remove(download);prefs(c).edit().clear().apply();
        android.app.NotificationManager nm=c.getSystemService(android.app.NotificationManager.class);if(nm!=null)nm.cancel(90);
    }
    static String downloadFailure(int reason){
        if(reason==DownloadManager.ERROR_INSUFFICIENT_SPACE)return "Not enough storage. Free some space and retry.";
        if(reason==DownloadManager.ERROR_DEVICE_NOT_FOUND)return "Download storage is unavailable.";
        if(reason==404)return "The APK is not attached to this release yet (HTTP 404). Try again later.";
        return "Android Downloads failed (code "+reason+"). Retry or open Releases.";
    }
    /** Checks the completed APK without executing it. Android verifies it again during installation. */
    static synchronized Uri verify(Context c)throws Exception{
        long download=id(c);if(download==-1||status(c,download).state!=DownloadManager.STATUS_SUCCESSFUL)throw new IOException("The update download has not completed.");
        File file=new File(directory(c),"tracker-update.apk");long size=file.length(),expected=prefs(c).getLong("size",0);
        if(size<=0||size>MAX_APK||(expected>0&&expected!=size))throw new IOException("The APK size does not match. Cancel this download and retry.");
        String expectedHash=prefs(c).getString("sha256","");
        try(InputStream input=new FileInputStream(file)){checkDigest(input,expectedHash);}
        PackageManager pm=c.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo archive=pm.getPackageArchiveInfo(file.getAbsolutePath(),flags),installed=pm.getPackageInfo(c.getPackageName(),flags);
        checkPackage(archive,installed,version(c));
        Uri uri=manager(c).getUriForDownloadedFile(download);
        if(uri==null||!"content".equals(uri.getScheme()))throw new IOException("Android could not share the verified APK with its installer.");return uri;
    }
    static void checkDigest(InputStream input,String expected)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[32768];int count;long size=0;
        while((count=input.read(buffer))!=-1){size+=count;if(size>MAX_APK)throw new IOException("The APK is too large.");digest.update(buffer,0,count);}
        StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));
        if(!expected.isEmpty()&&!expected.equals(hash.toString()))throw new IOException("The APK checksum does not match GitHub. Cancel this download and retry.");
    }
    static void checkPackage(PackageInfo archive,PackageInfo installed,String version)throws IOException{
        if(archive==null||installed==null||!installed.packageName.equals(archive.packageName))throw new IOException("The downloaded file is not a CoC Base Tracker APK.");
        long incoming=Build.VERSION.SDK_INT>=28?archive.getLongVersionCode():archive.versionCode,current=Build.VERSION.SDK_INT>=28?installed.getLongVersionCode():installed.versionCode;
        if(incoming<=current||UpdateChecker.compare(archive.versionName,version)!=0)throw new IOException("The APK version does not match the newer release.");
        Set<String> actual=signatures(archive),expected=signatures(installed);
        if(expected.isEmpty()||!actual.equals(expected))throw new IOException("The APK signing key does not match your installed app. Installation has been blocked.");
    }
    private static Set<String> signatures(PackageInfo info){
        Signature[] values=Build.VERSION.SDK_INT>=28&&info.signingInfo!=null?info.signingInfo.getApkContentsSigners():info.signatures;
        Set<String> result=new HashSet<>();if(values!=null)for(Signature value:values)result.add(value.toCharsString());return result;
    }
    private UpdateDownloads(){}
}

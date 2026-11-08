package com.villageclock.app;

import android.app.DownloadManager;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import java.io.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDownloadManager;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk={26,29,35})
public class UpdateDownloadsTest {
    private Context context;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();UpdateDownloads.prefs(context).edit().clear().commit();}
    static UpdateChecker.Release release(String url)throws Exception{
        return UpdateChecker.parse(new JSONObject().put("tag_name","v99.0").put("draft",false).put("prerelease",false)
            .put("assets",new JSONArray().put(new JSONObject().put("name","tracker.apk").put("state","uploaded").put("browser_download_url",url).put("size",12345).put("digest","sha256:"+"a".repeat(64)))).toString());
    }
    @Test public void downloadUsesExactReleaseAssetPrivateStorageAndRetainsOwnedId()throws Exception{
        String url=UpdateChecker.downloadUrl("v99.0","tracker.apk");UpdateChecker.Release release=release(url);
        long id=UpdateDownloads.start(context,release);assertEquals(id,UpdateDownloads.id(context));assertEquals("99.0",UpdateDownloads.version(context));
        ShadowDownloadManager dm=shadowOf((DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE));
        ShadowDownloadManager.ShadowRequest request=shadowOf(dm.getRequest(id));
        assertEquals(url,request.getUri().toString());assertEquals("application/vnd.android.package-archive",request.getMimeType());
        assertEquals(new File(UpdateDownloads.directory(context),"tracker-update.apk").toURI().getPath(),request.getDestination().getPath().replace('\\','/'));
        assertEquals(DownloadManager.Request.VISIBILITY_VISIBLE,request.getNotificationVisibility());assertTrue(request.getAllowedOverMetered());assertFalse(request.getAllowedOverRoaming());
        assertEquals(id,UpdateDownloads.start(context,release));assertEquals(1,dm.getRequestCount());
        UpdateDownloads.cancel(context);assertEquals(-1,UpdateDownloads.id(context));assertNull(dm.getRequest(id));
    }
    @Test public void unsafeAssetUrlsCannotStartDownloads()throws Exception{
        for(String url:new String[]{"http://github.com/MasteR-NY/CoC-Base-Tracker/releases/download/v99.0/tracker.apk","https://example.invalid/tracker.apk",UpdateChecker.downloadUrl("v98.0","tracker.apk"),UpdateChecker.downloadUrl("v99.0","tracker.apk")+"?next=evil"}){
            UpdateChecker.Release release=release(url);assertFalse(release.hasApk);
            try{UpdateDownloads.start(context,release);fail("Unsafe download started");}catch(IOException expected){assertEquals(-1,UpdateDownloads.id(context));}
        }
        assertEquals("",UpdateChecker.downloadUrl("v99.0","../tracker.apk"));
    }
    @Test public void installerGetsReadOnlyContentUriGrant(){
        Uri uri=Uri.parse("content://downloads/my_downloads/1");Intent install=UpdateActivity.installIntent(uri);
        assertEquals(Intent.ACTION_VIEW,install.getAction());assertEquals(uri,install.getData());assertEquals("application/vnd.android.package-archive",install.getType());
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,install.getFlags());assertEquals(uri,install.getClipData().getItemAt(0).getUri());
    }
    @Test public void signingKeyPackageAndVersionMustAllMatch()throws Exception{
        PackageInfo installed=info("com.villageclock.app",16,"1.15","abc"),archive=info("com.villageclock.app",99,"99.0","abc");
        UpdateDownloads.checkPackage(archive,installed,"99.0");
        for(PackageInfo invalid:new PackageInfo[]{null,info("other.app",99,"99.0","abc"),info("com.villageclock.app",16,"99.0","abc"),info("com.villageclock.app",99,"98.0","abc"),info("com.villageclock.app",99,"99.0","def"),info("com.villageclock.app",99,"99.0",null)}){
            try{UpdateDownloads.checkPackage(invalid,installed,"99.0");fail("Accepted wrong APK metadata");}catch(IOException expected){ }
        }
    }
    @Test public void digestDetectsChangedAndTruncatedFiles()throws Exception{
        byte[] data="abc".getBytes(java.nio.charset.StandardCharsets.UTF_8);String hash="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        UpdateDownloads.checkDigest(new ByteArrayInputStream(data),hash);
        try{UpdateDownloads.checkDigest(new ByteArrayInputStream(new byte[]{1,2}),hash);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("checksum"));}
        try{UpdateDownloads.readBounded(new ByteArrayInputStream(new byte[9]),8);fail();}catch(IOException expected){ }
    }
    @Test public void verificationCannotRunBeforeSuccessfulDownload()throws Exception{
        UpdateDownloads.start(context,release(UpdateChecker.downloadUrl("v99.0","tracker.apk")));
        try{UpdateDownloads.verify(context);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("not completed"));}
    }
    @Test public void socketDiagnosticsIncludeActualSystemCause(){
        java.net.SocketException error=new java.net.SocketException("Socket failed: EPERM (Operation not permitted)");
        assertTrue(UpdateChecker.failureDetail(error).contains("EPERM"));assertTrue(UpdateChecker.failureDetail(error).contains("download service"));
    }
    @Test public void systemMetadataFallbackRunsOnlyAfterAppConnectionFailure()throws Exception{
        int[] calls={0,0};UpdateChecker.Release wanted=release(UpdateChecker.downloadUrl("v99.0","tracker.apk"));
        UpdateChecker.Release got=UpdateDownloads.fetchLatest(()->{calls[0]++;throw new java.net.SocketException("EPERM");},()->{calls[1]++;return wanted;});
        assertSame(wanted,got);assertArrayEquals(new int[]{1,1},calls);assertEquals("a".repeat(64),got.sha256);
        got=UpdateDownloads.fetchLatest(()->wanted,()->{fail("Fallback on a successful check");return null;});assertSame(wanted,got);
        try{UpdateDownloads.fetchLatest(()->{throw new java.net.SocketException("EPERM");},()->{throw new IOException("Downloads disabled");});fail();}
        catch(IOException failure){assertTrue(failure.getMessage().contains("EPERM"));assertTrue(failure.getMessage().contains("Downloads disabled"));}
    }
    @Test public void installPermissionPromptOpensOnlyThisAppsSettings(){
        UpdateActivity activity=Robolectric.buildActivity(UpdateActivity.class).setup().get();activity.showReady("99.0");
        activity.findViewById(R.id.install_update).performClick();android.app.AlertDialog prompt=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(prompt);assertNull(shadowOf(activity).getNextStartedActivity());prompt.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(android.os.Looper.getMainLooper()).idle();
        Intent intent=shadowOf(activity).getNextStartedActivity();assertNotNull(intent);assertEquals(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,intent.getAction());assertEquals("package:com.villageclock.app",intent.getDataString());
    }
    private PackageInfo info(String name,int code,String version,String signature){PackageInfo result=new PackageInfo();result.packageName=name;result.versionCode=code;result.versionName=version;result.signatures=signature==null?null:new Signature[]{new Signature(signature.getBytes())};return result;}
}

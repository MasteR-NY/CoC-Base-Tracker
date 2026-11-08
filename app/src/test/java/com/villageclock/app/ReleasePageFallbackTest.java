package com.villageclock.app;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.TextView;
import java.io.IOException;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk={26,29,35})
public class ReleasePageFallbackTest {
    private String page(String version){return "<html><head><meta content='/MasteR-NY/CoC-Base-Tracker/releases/tag/"+version+"' property='og:url'></head></html>";}
    @Test public void publicPageConfirmsVersionWithoutClaimingAnUploadedApk()throws Exception{UpdateChecker.Release release=UpdateChecker.parseReleasePage(page("v99.0"));assertEquals("99.0",release.version);assertFalse(release.apkStatusKnown);assertEquals(UpdateChecker.downloadUrl("v99.0","CoC-Base-Tracker-99.0.apk"),release.downloadUrl());}
    @Test public void invalidOrConflictingHtmlNeverReportsUpToDate()throws Exception{for(String body:new String[]{page("v99.0-beta"),page("v99.0").replace("/MasteR-NY/","https://evil.invalid/MasteR-NY/"),"<html>Forbidden <a href='/MasteR-NY/CoC-Base-Tracker/releases/tag/v99.0'>release</a></html>",page("v99.0")+page("v98.0"),page("v99.0?target=other")}){try{UpdateChecker.parseReleasePage(body);fail("Accepted unverified page");}catch(IOException expected){}}}
    @Test public void publicPageSuccessNeedsNoApiAndRateLimitIsNotRetried()throws Exception{List<Boolean> calls=new ArrayList<>();UpdateChecker.Release release=UpdateDownloads.fetchMetadata(publicPage->{calls.add(publicPage);assertTrue(publicPage);return UpdateChecker.parseReleasePage(page("v99.0"));});assertEquals("99.0",release.version);assertEquals(Collections.singletonList(true),calls);calls.clear();try{UpdateDownloads.fetchMetadata(publicPage->{calls.add(publicPage);throw new UpdateDownloads.TransferFailure(403);});fail();}catch(IOException expected){assertEquals(Arrays.asList(true,false),calls);}}
    @Test public void gatewayFailureRetriesPublicPageOnce()throws Exception{int[] calls={0};UpdateChecker.Release release=UpdateDownloads.fetchMetadata(publicPage->{assertTrue(publicPage);if(calls[0]++==0)throw new UpdateDownloads.TransferFailure(504);return UpdateChecker.parseReleasePage(page("v99.0"));});assertEquals(2,calls[0]);assertEquals("99.0",release.version);}
    @Test public void unavailableCheckHasShortVisibleSummaryAndHiddenTechnicalDetails(){Context c=RuntimeEnvironment.getApplication();MainActivity activity=Robolectric.buildActivity(MainActivity.class).setup().get();String reason="GitHub API: socket failed EPERM\nGitHub Releases: socket failed EPERM\nAndroid Downloads: HTTP 403";activity.finishUpdateCheck(null,reason);AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();TextView summary=dialog.findViewById(R.id.update_error_summary);assertNotNull(summary);assertTrue(summary.getText().length()<300);assertTrue(summary.getText().toString().contains("EPERM"));assertTrue(summary.getText().toString().contains("403"));assertEquals("Open Releases",dialog.getButton(AlertDialog.BUTTON_NEUTRAL).getText().toString());assertTrue(UpdateChecker.userMessage("HTTP 504").contains("processing"));}
}

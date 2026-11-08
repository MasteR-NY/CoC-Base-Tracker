package com.villageclock.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk={26,29,35})
public class UpdateUiTest {
    private UpdateChecker.Release release(String tag)throws Exception{return UpdateChecker.parse("{\"tag_name\":\""+tag+"\",\"draft\":false,\"prerelease\":false,\"assets\":[{\"name\":\"app.apk\",\"state\":\"uploaded\",\"browser_download_url\":\"https://github.com/MasteR-NY/CoC-Base-Tracker/releases/download/"+tag+"/app.apk\"}]}");}
    @Test public void openingShowsInstalledVersionAndBottomUpdateButton(){
        MainActivity activity=Robolectric.buildActivity(MainActivity.class).setup().get();
        assertEquals("CoC Base Tracker v"+BuildConfig.VERSION_NAME,((TextView)activity.findViewById(R.id.app_version_title)).getText().toString());
        assertEquals("Check for Updates",((TextView)activity.findViewById(R.id.check_updates)).getText().toString());
        assertNull(shadowOf(activity).getNextStartedActivity());
    }
    @Test public void sameVersionNotifiesUpToDateWithoutOpeningBrowser()throws Exception{
        MainActivity activity=Robolectric.buildActivity(MainActivity.class).setup().get();activity.finishUpdateCheck(release(BuildConfig.VERSION_NAME),null);
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing());
        assertTrue(((TextView)dialog.findViewById(android.R.id.message)).getText().toString().contains("latest published version"));
        assertNull(shadowOf(activity).getNextStartedActivity());
    }
    @Test public void newerReleasePromptsBeforeStartingPrivateDownloadScreen()throws Exception{
        MainActivity activity=Robolectric.buildActivity(MainActivity.class).setup().get();activity.finishUpdateCheck(release("99.0"),null);
        assertNull(shadowOf(activity).getNextStartedActivity());
        AlertDialog prompt=ShadowAlertDialog.getLatestAlertDialog();assertEquals("Download Update",prompt.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());prompt.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(android.os.Looper.getMainLooper()).idle();
        Intent intent=shadowOf(activity).getNextStartedActivity();assertNotNull(intent);assertEquals(UpdateActivity.class.getName(),intent.getComponent().getClassName());assertNull(intent.getData());
        assertTrue(((TextView)activity.findViewById(R.id.update_status)).getText().toString().contains("99.0"));
    }
    @Test public void errorsAndOlderPublishedVersionsNeverLaunchAnUpdate()throws Exception{
        MainActivity activity=Robolectric.buildActivity(MainActivity.class).setup().get();activity.finishUpdateCheck(null,"No connection. Try again.");
        assertNull(shadowOf(activity).getNextStartedActivity());assertTrue(activity.findViewById(R.id.check_updates).isEnabled());ShadowAlertDialog.getLatestAlertDialog().dismiss();
        activity.finishUpdateCheck(release("0.1"),null);assertNull(shadowOf(activity).getNextStartedActivity());
        assertTrue(((TextView)ShadowAlertDialog.getLatestAlertDialog().findViewById(android.R.id.message)).getText().toString().contains("installed build is newer"));
    }
    @Test public void checkCompletingInBackgroundWaitsUntilAppResumes()throws Exception{
        ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();MainActivity activity=controller.get();controller.pause();
        activity.finishUpdateCheck(release("99.0"),null);assertNull(shadowOf(activity).getNextStartedActivity());
        controller.resume();shadowOf(android.os.Looper.getMainLooper()).idle();assertNull(shadowOf(activity).getNextStartedActivity());assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing());
    }
    @Test public void websiteFallbackDoesNotFalselyClaimApkIsMissing()throws Exception{
        MainActivity activity=Robolectric.buildActivity(MainActivity.class).setup().get();
        activity.finishUpdateCheck(UpdateChecker.parseReleaseRedirect("/MasteR-NY/CoC-Base-Tracker/releases/tag/v99.0"),null);
        assertTrue(((TextView)activity.findViewById(R.id.update_status)).getText().toString().contains("99.0"));
        assertFalse(((TextView)activity.findViewById(R.id.update_status)).getText().toString().contains("not been uploaded"));
    }
}

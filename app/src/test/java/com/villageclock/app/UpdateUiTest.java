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

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class UpdateUiTest {
    private UpdateChecker.Release release(String tag)throws Exception{return UpdateChecker.parse("{\"tag_name\":\""+tag+"\",\"draft\":false,\"prerelease\":false,\"assets\":[{\"name\":\"app.apk\",\"state\":\"uploaded\"}]}");}
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
    @Test public void newerReleaseOpensOnlyOurGitHubReleasesPage()throws Exception{
        MainActivity activity=Robolectric.buildActivity(MainActivity.class).setup().get();activity.finishUpdateCheck(release("99.0"),null);
        Intent intent=shadowOf(activity).getNextStartedActivity();assertNotNull(intent);assertEquals(Intent.ACTION_VIEW,intent.getAction());assertEquals(UpdateChecker.RELEASES_URL,intent.getDataString());
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
        controller.resume();shadowOf(android.os.Looper.getMainLooper()).idle();assertNotNull(shadowOf(activity).getNextStartedActivity());
    }
}

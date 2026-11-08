package com.villageclock.app;

import android.app.AlertDialog;
import android.content.Context;
import android.view.*;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk={26,29,35})
public class CompactHomeTest {
    @Test public void settingsAreCollapsedAndHelpKeepsPrivacyAndSyncInstructions()throws Exception{
        Context c=RuntimeEnvironment.getApplication();ReleaseNotes.markSeen(c);
        String raw="{\"tag\":\"#COMPACT\",\"timestamp\":"+System.currentTimeMillis()/1000+",\"buildings\":[]}";
        Store.save(c,raw,ExportParser.parse(raw,Store.catalog(c),System.currentTimeMillis()));Store.setInGameName(c,"#COMPACT","Chief");
        MainActivity activity=Robolectric.buildActivity(MainActivity.class).setup().get();
        assertNull(activity.findViewById(R.id.settings_panel));assertEquals(View.GONE,activity.findViewById(R.id.update_status).getVisibility());
        activity.findViewById(R.id.settings_toggle).performClick();assertNotNull(activity.findViewById(R.id.settings_panel));
        activity.findViewById(R.id.settings_toggle).performClick();assertNull(activity.findViewById(R.id.settings_panel));
        find(activity.getWindow().getDecorView(),"Help & About").performClick();AlertDialog help=ShadowAlertDialog.getLatestAlertDialog();
        String text=((TextView)help.findViewById(android.R.id.message)).getText().toString();
        assertTrue(text.contains("Export village data"));assertTrue(text.contains("No Supercell login"));assertTrue(text.contains("Android 8.0"));
        assertEquals("#COMPACT",Store.activeTag(c));
    }
    private View find(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
}

package com.villageclock.app;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.HashSet;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class ReleaseGuideTest {
    private Context context;
    @Before public void reset(){context=RuntimeEnvironment.getApplication();Store.prefs(context).edit().clear().commit();}
    @Test public void bundledNotesMatchInstalledVersionAndHaveUniqueOrderedHistory()throws Exception{
        ReleaseNotes notes=ReleaseNotes.load(context);
        assertEquals(BuildConfig.VERSION_NAME,notes.current().version);
        assertEquals(BuildConfig.VERSION_CODE,notes.current().versionCode);
        assertSame(notes.current(),notes.entries.get(0));
        HashSet<String> versions=new HashSet<>();int previous=Integer.MAX_VALUE;
        for(ReleaseNotes.Entry entry:notes.entries){
            assertTrue(versions.add(entry.version));assertTrue(entry.versionCode<previous);previous=entry.versionCode;
            assertFalse(entry.title.isBlank());assertFalse(entry.highlights.isEmpty());
            for(ReleaseNotes.Highlight point:entry.highlights){assertFalse(point.title.isBlank());assertFalse(point.body.isBlank());}
            for(String step:entry.steps)assertFalse(step.isBlank());
        }
        assertTrue(versions.contains("1.9"));assertTrue(versions.contains("1.8"));
    }
    @Test public void summaryDismissalPersistsAcrossLaunchesAndKeepsGuideButton(){
        MainActivity first=Robolectric.buildActivity(MainActivity.class).setup().get();
        assertNotNull(first.findViewById(R.id.release_summary));assertNull(shadowOf(first).getNextStartedActivity());
        first.findViewById(R.id.release_summary_dismiss).performClick();
        assertNull(first.findViewById(R.id.release_summary));assertFalse(ReleaseNotes.needsSummary(context));
        MainActivity reopened=Robolectric.buildActivity(MainActivity.class).setup().get();
        assertNull(reopened.findViewById(R.id.release_summary));assertNotNull(reopened.findViewById(R.id.whats_new));
    }
    @Test public void olderSeenVersionShowsSummaryAndReadingPreservesAccounts()throws Exception{
        Store.prefs(context).edit().putInt("release-guide-seen-version",BuildConfig.VERSION_CODE-1).commit();
        String raw="{\"tag\":\"#GUIDE\",\"timestamp\":"+System.currentTimeMillis()/1000+",\"buildings\":[]}";
        Village village=ExportParser.parse(raw,Store.catalog(context),System.currentTimeMillis());
        Store.save(context,raw,village);Store.setInGameName(context,village.tag,"Guide Chief");Store.bind(context,123,village.tag);
        MainActivity activity=Robolectric.buildActivity(MainActivity.class).setup().get();
        assertNotNull(activity.findViewById(R.id.release_summary));activity.findViewById(R.id.release_summary_open).performClick();
        Intent opened=shadowOf(activity).getNextStartedActivity();assertNotNull(opened);
        assertEquals(WhatsNewActivity.class.getName(),opened.getComponent().getClassName());
        assertFalse(ReleaseNotes.needsSummary(context));assertNull(activity.findViewById(R.id.release_summary));
        assertEquals(village.tag,Store.activeTag(context));assertEquals(village.tag,Store.widgetTag(context,123));
        assertEquals("Guide Chief",Store.inGameName(context,Store.load(context)));assertEquals(raw,Store.raw(context,village.tag));
    }
    @Test public void manualGuideRemainsAvailableAfterDismissal(){
        ReleaseNotes.markSeen(context);MainActivity activity=Robolectric.buildActivity(MainActivity.class).setup().get();
        activity.findViewById(R.id.whats_new).performClick();
        assertEquals(WhatsNewActivity.class.getName(),shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
    }
    @Test public void guideIsOfflineAndEarlierReleaseExpandsWithoutBrowser(){
        WhatsNewActivity guide=Robolectric.buildActivity(WhatsNewActivity.class).setup().get();
        assertFalse(ReleaseNotes.needsSummary(context));assertNull(shadowOf(guide).getNextStartedActivity());
        View root=guide.getWindow().getDecorView();TextView toggle=findText(root,"v1.9 · App Version and Update Checks");
        assertNotNull(toggle);TextView detail=findText(root,"Installed Version in the Header");assertNotNull(detail);
        View details=(View)detail.getParent();assertEquals(View.GONE,details.getVisibility());
        toggle.performClick();assertEquals(View.VISIBLE,details.getVisibility());
        toggle.performClick();assertEquals(View.GONE,details.getVisibility());
        assertNull(shadowOf(guide).getNextStartedActivity());
        findText(root,"View GitHub Releases").performClick();
        assertEquals(UpdateChecker.RELEASES_URL,shadowOf(guide).getNextStartedActivity().getDataString());
    }
    @Test public void openingAnOlderBuildDoesNotResetSeenVersion(){
        Store.prefs(context).edit().putInt("release-guide-seen-version",BuildConfig.VERSION_CODE+1).commit();
        assertFalse(ReleaseNotes.needsSummary(context));ReleaseNotes.markSeen(context);
        assertEquals(BuildConfig.VERSION_CODE+1,Store.prefs(context).getInt("release-guide-seen-version",0));
    }
    private TextView findText(View view,String text){
        if(view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return (TextView)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){TextView found=findText(group.getChildAt(i),text);if(found!=null)return found;}}
        return null;
    }
}

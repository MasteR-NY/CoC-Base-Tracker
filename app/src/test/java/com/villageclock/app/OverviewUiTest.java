package com.villageclock.app;

import android.content.*;
import android.view.*;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk={26,29,35})
public class OverviewUiTest {
    @Test public void overviewTabsAndBothBasesStayWithinSelectedAccount()throws Exception{
        Context c=RuntimeEnvironment.getApplication();String raw=OverviewActivity.sampleExport().replace("#SAMPLE","#OVERVIEW");Village village=ExportParser.parse(raw,Store.catalog(c),System.currentTimeMillis());Store.save(c,raw,village);Store.bind(c,701,village.tag);
        ActivityController<OverviewActivity> controller=Robolectric.buildActivity(OverviewActivity.class,new Intent(c,OverviewActivity.class).putExtra("tag",village.tag)).setup();OverviewActivity a=controller.get();
        assertEquals("Progress",((TextView)a.findViewById(R.id.overview_heading)).getText().toString());ListView list=a.findViewById(R.id.overview_list);assertTrue(list.getAdapter().getCount()>0);
        find(a.getWindow().getDecorView(),"Crafting").performClick();assertEquals(12,list.getAdapter().getCount());
        find(a.getWindow().getDecorView(),"Helpers").performClick();assertNotNull(a.findViewById(R.id.availability_alerts));
        find(a.getWindow().getDecorView(),"Builder Base").performClick();assertTrue(list.getAdapter().getCount()>0);
        find(a.getWindow().getDecorView(),"Collection").performClick();assertEquals("Collection",((TextView)a.findViewById(R.id.overview_heading)).getText().toString());
        assertEquals(village.tag,Store.activeTag(c));assertEquals(village.tag,Store.widgetTag(c,701));assertNull(shadowOf(a).getNextStartedActivity());controller.pause().stop().destroy();
    }
    @Test public void sampleOverviewNeverSavesAccountsOrBindsWidgets(){
        Context c=RuntimeEnvironment.getApplication();ActivityController<OverviewActivity> controller=Robolectric.buildActivity(OverviewActivity.class,new Intent(c,OverviewActivity.class).putExtra("sample",true)).setup();
        assertTrue(Store.all(c).isEmpty());assertEquals("",Store.widgetTag(c,987));controller.pause().stop().destroy();
    }
    @Test public void notificationIntentOpensRequestedHelpersBase()throws Exception{
        Context c=RuntimeEnvironment.getApplication();String raw=OverviewActivity.sampleExport().replace("#SAMPLE","#READYUI");Village v=ExportParser.parse(raw,Store.catalog(c),System.currentTimeMillis());Store.save(c,raw,v);
        ActivityController<OverviewActivity> controller=Robolectric.buildActivity(OverviewActivity.class,new Intent(c,OverviewActivity.class).putExtra("tag",v.tag).putExtra("tab","Helpers").putExtra("builder",true)).setup();
        assertEquals("Helpers",((TextView)controller.get().findViewById(R.id.overview_heading)).getText().toString());assertNotNull(find(controller.get().getWindow().getDecorView(),"Builder Base"));controller.pause().stop().destroy();
    }
    static View find(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View found=find(g.getChildAt(i),text);if(found!=null)return found;}}return null;}
}

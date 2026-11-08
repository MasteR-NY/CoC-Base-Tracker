package com.villageclock.app;

import android.content.Intent;
import android.view.*;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class BuildMenuTest {
    private Button button(View root,String title){if(root instanceof Button&&((Button)root).getText().toString().equals(title))return (Button)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){Button b=button(((ViewGroup)root).getChildAt(i),title);if(b!=null)return b;}return null;}
    @Test public void switchesAllHomeTabsAndBuilderBaseHasOnlyTwo() {
        PlannerActivity a=Robolectric.buildActivity(PlannerActivity.class,new Intent(RuntimeEnvironment.getApplication(),PlannerActivity.class).putExtra("sample",true)).setup().get();View decor=a.getWindow().getDecorView();
        assertNotNull(button(decor,"Hero Equipment"));button(decor,"Hero Equipment").performClick();assertNotNull(button(decor,"Hero Pets"));button(decor,"Hero Pets").performClick();
        button(decor,"Builder Base").performClick();assertNull(button(decor,"Hero Pets"));assertNull(button(decor,"Hero Equipment"));assertNotNull(button(decor,"Army Upgrades"));
        button(decor,"Army Upgrades").performClick();button(decor,"Village Base").performClick();assertNotNull(button(decor,"Hero Equipment"));assertFalse(a.isFinishing());
    }
    @Test public void sampleMenuNeverPersistsAnAccount() {
        android.content.Context c=RuntimeEnvironment.getApplication();int accounts=Store.all(c).size();
        PlannerActivity a=Robolectric.buildActivity(PlannerActivity.class,new Intent(c,PlannerActivity.class).putExtra("sample",true)).setup().get();
        assertNotNull(button(a.getWindow().getDecorView(),"Buildings & Heroes"));assertEquals(accounts,Store.all(c).size());
    }
}

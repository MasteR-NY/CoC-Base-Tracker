package com.villageclock.app;

import android.content.*;
import android.net.Uri;
import android.os.Looper;
import android.widget.Switch;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk={26,29,35})
public class ClipboardSyncTest {
    private Context c;private ClipboardManager clipboard;private ActivityController<MainActivity> controller;private long timestamp;
    @Before public void prepare(){c=RuntimeEnvironment.getApplication();Store.prefs(c).edit().clear().putBoolean("asked-notify",true).commit();clipboard=c.getSystemService(ClipboardManager.class);timestamp=System.currentTimeMillis()/1000;}
    @After public void close(){if(controller!=null)controller.pause().stop().destroy();}
    private String export(String tag,long at){return "{\"tag\":\""+tag+"\",\"name\":\"Test Chief\",\"timestamp\":"+at+",\"buildings\":[{\"data\":1000001,\"lvl\":12},{\"data\":1000008,\"lvl\":10,\"timer\":3600}]}";}
    private void put(String text){clipboard.setPrimaryClip(ClipData.newPlainText("Village export",text));}
    private void open(){controller=Robolectric.buildActivity(MainActivity.class).setup().visible().windowFocusChanged(true);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));}
    private void waitSaved(String tag)throws Exception{for(int i=0;i<400&&Store.load(c,tag)==null;i++){shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);}shadowOf(Looper.getMainLooper()).idle();assertNotNull("Expected automatic import",Store.load(c,tag));}
    private void settle()throws Exception{shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));for(int i=0;i<30;i++){shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5);}}
    private void save(String text)throws Exception{Store.save(c,text,ExportParser.parse(text,Store.catalog(c),System.currentTimeMillis()));}
    @Test public void returningWithExportImportsWithoutSyncTapAndKeepsOtherWidgets()throws Exception{
        save(export("#FIRST",timestamp-20));Store.bind(c,901,"#FIRST");open();
        controller.windowFocusChanged(false).pause();put(export("#SECOND",timestamp));settle();assertNull(Store.load(c,"#SECOND"));
        controller.resume().windowFocusChanged(true);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));waitSaved("#SECOND");
        assertEquals("#SECOND",Store.activeTag(c));assertEquals("#FIRST",Store.widgetTag(c,901));assertEquals(2,Store.all(c).size());
    }
    @Test public void focusedClipboardChangeImportsFreshExport()throws Exception{open();put(export("#FOCUSED",timestamp));shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));waitSaved("#FOCUSED");}
    @Test public void duplicateAndOlderExportsDoNotSwitchAccountsOrOverwriteNames()throws Exception{
        String first=export("#FIRST",timestamp);save(first);Store.setInGameName(c,"#FIRST","Saved Name");save(export("#SECOND",timestamp));
        put(first);open();settle();assertEquals("#SECOND",Store.activeTag(c));
        put(export("#FIRST",timestamp-60));settle();assertEquals(first,Store.raw(c,"#FIRST"));assertEquals("Saved Name",Store.inGameName(c,Store.load(c,"#FIRST")));assertEquals("#SECOND",Store.activeTag(c));
    }
    @Test public void disabledModeStaysManualAndCanBeEnabledBeforeFirstImport()throws Exception{
        Store.prefs(c).edit().putBoolean(ClipboardSync.PREF,false).commit();put(export("#ENABLE",timestamp));open();settle();assertTrue(Store.all(c).isEmpty());
        controller.get().findViewById(R.id.settings_toggle).performClick();((Switch)controller.get().findViewById(R.id.auto_sync)).setChecked(true);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));waitSaved("#ENABLE");
    }
    @Test public void unrelatedTextMalformedDataAndUrisAreIgnored()throws Exception{
        put("unrelated clipboard text");open();settle();assertTrue(Store.all(c).isEmpty());assertEquals("",Store.prefs(c).getString(ClipboardSync.LAST,""));
        put("{\"tag\":\"#BAD\",\"timestamp\":1,\"buildings\":[]}");settle();assertTrue(Store.all(c).isEmpty());
        assertNull(ClipboardSync.candidate(ClipData.newRawUri("URI",Uri.parse("content://untrusted/export"))));
        assertNull(ClipboardSync.candidate(ClipData.newPlainText("Large","x".repeat(ExportParser.MAX_CHARS+1))));
    }
    @Test public void acknowledgedClipboardDoesNotReAddRemovedAccount()throws Exception{
        put(export("#REMOVE",timestamp));open();waitSaved("#REMOVE");Store.clear(c);assertTrue(Store.all(c).isEmpty());
        controller.windowFocusChanged(false).windowFocusChanged(true);settle();assertTrue(Store.all(c).isEmpty());
    }
    @Test public void widgetSyncRejectsWrongAccountAndAutomaticRetryKeepsThatGuard()throws Exception{
        save(export("#FIRST",timestamp-20));Store.bind(c,901,"#FIRST");put(export("#WRONG",timestamp));
        controller=Robolectric.buildActivity(MainActivity.class,new Intent(c,MainActivity.class).putExtra("sync",true).putExtra("tag","#FIRST")).setup().visible().windowFocusChanged(true);
        settle();assertNull(Store.load(c,"#WRONG"));controller.windowFocusChanged(false).windowFocusChanged(true);settle();
        assertNull(Store.load(c,"#WRONG"));assertEquals("#FIRST",Store.widgetTag(c,901));assertEquals("#FIRST",Store.activeTag(c));
    }
}

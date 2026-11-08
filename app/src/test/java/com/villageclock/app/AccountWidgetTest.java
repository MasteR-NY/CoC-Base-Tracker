package com.villageclock.app;

import android.content.Context;
import android.util.AtomicFile;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ListView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.File;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AccountWidgetTest {
    private Context context;
    private long now;
    @Before public void prepare() throws Exception {
        context = RuntimeEnvironment.getApplication(); now = System.currentTimeMillis() / 1000 * 1000;
        Store.prefs(context).edit().clear().commit();
        File[] children = context.getFilesDir().listFiles();
        if (children != null) for (File file : children) delete(file);
    }
    private void delete(File file) throws Exception {
        if (file.isDirectory()) { File[] children = file.listFiles(); if (children != null) for (File child : children) delete(child); }
        java.nio.file.Files.deleteIfExists(file.toPath());
    }
    private String text(String tag, int count) {
        StringBuilder entries = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) entries.append(',');
            entries.append("{\"data\":1000008,\"lvl\":").append(i).append(",\"timer\":").append(1000 + i).append('}');
        }
        return "{\"tag\":\"" + tag + "\",\"timestamp\":" + now/1000 + ",\"buildings\":[" + entries + "]}";
    }
    private Village save(String tag, int count) throws Exception {
        String text = text(tag, count); Village v = ExportParser.parse(text, id -> "Archer Tower", now); Store.save(context, text, v); return v;
    }
    @Test public void widgetsKeepDifferentAccountsWhenActiveAccountChanges() throws Exception {
        save("#AAA111", 1); Store.bind(context, 101, "#AAA111");
        save("#BBB222", 2); Store.bind(context, 202, "#BBB222");
        Store.select(context, "#AAA111");
        assertEquals("#AAA111", Store.widgetTag(context, 101)); assertEquals("#BBB222", Store.widgetTag(context, 202));
        assertEquals(1, Store.load(context, Store.widgetTag(context, 101)).upgrades.size());
        assertEquals(2, Store.load(context, Store.widgetTag(context, 202)).upgrades.size());
    }
    @Test public void oldWidgetBindsOnceRatherThanFollowingImports() throws Exception {
        save("#AAA111", 1); assertEquals("#AAA111", Store.widgetTag(context, 303));
        save("#BBB222", 2); assertEquals("#AAA111", Store.widgetTag(context, 303));
    }
    @Test public void deletingAnAccountDoesNotRedirectItsWidget() throws Exception {
        save("#BBB222", 1); save("#AAA111", 1); Store.bind(context, 101, "#AAA111"); Store.clear(context);
        assertEquals("#AAA111", Store.widgetTag(context, 101)); assertNull(Store.load(context, "#AAA111")); assertNotNull(Store.load(context, "#BBB222"));
    }
    @Test public void namesAndNotificationCursorsArePerAccount() throws Exception {
        Village a = save("#AAA111", 1), b = save("#BBB222", 1);
        Store.rename(context, a.tag, "Main"); Store.rename(context, b.tag, "Alt");
        Store.setNotifiedThrough(context, a.tag, 111); Store.setNotifiedThrough(context, b.tag, 222);
        assertEquals("Main", Store.title(context, a)); assertEquals("Alt", Store.title(context, b));
        assertEquals(111, Store.notifiedThrough(context, a.tag)); assertEquals(222, Store.notifiedThrough(context, b.tag));
    }
    @Test public void singleAccountVersionMigratesWithoutDataLoss() throws Exception {
        String legacy = text("#AAA111", 3);
        java.nio.file.Files.write(new File(context.getFilesDir(), "village.json").toPath(), legacy.getBytes(StandardCharsets.UTF_8));
        Store.prefs(context).edit().putString("name", "Original village").putLong("notified-through", 123).commit();
        Village migrated = Store.load(context);
        assertEquals(3, migrated.upgrades.size()); assertEquals("Original village", Store.title(context, migrated));
        assertEquals(123, Store.notifiedThrough(context, migrated.tag));
        assertFalse(new File(context.getFilesDir(), "village.json").exists());
        save("#BBB222", 1); assertEquals(2, Store.all(context).size()); assertEquals(3, Store.load(context, "#AAA111").upgrades.size());
    }
    @Test public void olderExportCannotOverwriteNewerAccount() throws Exception {
        save("#AAA111", 2);
        String old = text("#AAA111", 1).replace(String.valueOf(now/1000), String.valueOf(now/1000 - 100));
        try { Store.save(context, old, ExportParser.parse(old, id -> "Tower", now)); fail(); } catch (java.io.IOException expected) { }
        assertEquals(2, Store.load(context, "#AAA111").upgrades.size());
    }
    @Test public void scrollableWidgetInflatesOnLegacyAndroid() throws Exception {
        Village v = save("#AAA111", 20); Store.bind(context, 101, v.tag);
        View root = VillageWidget.render(context, v, 101, v.tag).apply(context, new FrameLayout(context));
        assertTrue(root.findViewById(R.id.rows) instanceof ListView);
        assertNotNull(root.findViewById(R.id.choose));
    }
    @Test @Config(sdk = 35) public void modernScrollableWidgetContainsEveryUpgrade() throws Exception {
        Village v = save("#AAA111", 20); Store.bind(context, 101, v.tag);
        android.appwidget.AppWidgetHostView host = new android.appwidget.AppWidgetHostView(context);
        View root = VillageWidget.render(context, v, 101, v.tag).apply(context, host);
        ListView list = root.findViewById(R.id.rows);
        assertNotNull(list.getAdapter()); assertEquals(20, list.getAdapter().getCount());
    }
    @Test public void mainActivityStartsWithExistingMultipleAccounts() throws Exception {
        save("#AAA111", 2); save("#BBB222", 3);
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertFalse(activity.isFinishing()); assertEquals(2, Store.all(context).size());
    }
}

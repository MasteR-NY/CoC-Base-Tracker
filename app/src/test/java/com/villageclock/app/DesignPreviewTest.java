package com.villageclock.app;

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DesignPreviewTest {
    @Test public void rendersAppAndScrollableWidgetWithGameAssets() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        long timestamp = System.currentTimeMillis()/1000 - 43200;
        String export = "{\"tag\":\"#PREVIEW\",\"timestamp\":" + timestamp
            + ",\"buildings\":[{\"data\":1000001,\"lvl\":17},{\"data\":1000015,\"cnt\":5},{\"data\":1000064,\"lvl\":1},"
            + "{\"data\":1000003,\"lvl\":16,\"timer\":3600},{\"data\":1000008,\"lvl\":20,\"timer\":60000},{\"data\":1000008,\"lvl\":20,\"timer\":90000,\"extra\":true}],"
            + "\"heroes\":[{\"data\":28000000,\"lvl\":99,\"timer\":200000}],"
            + "\"units\":[{\"data\":4000008,\"lvl\":11,\"timer\":400000}],"
            + "\"pets\":[{\"data\":73000000,\"lvl\":11,\"timer\":350000}],"
            + "\"buildings2\":[{\"data\":1000034,\"lvl\":10},{\"data\":1000078,\"lvl\":1},{\"data\":1000036,\"lvl\":9,\"timer\":150000}]}";
        Village village = ExportParser.parse(export, Store.catalog(context), System.currentTimeMillis());
        Store.save(context, export, village); Store.rename(context, village.tag, "Kuswa"); Store.bind(context, 777, village.tag);
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        View app = activity.getWindow().getDecorView();
        render(app, 411, 1000, "app-design-1.3.png");
        AppWidgetHostView host = new AppWidgetHostView(context);
        View widget = VillageWidget.render(context, village, 777, village.tag).apply(context, host);
        render(widget, 400, 560, "widget-design-1.3.png");
        assertNotNull(context.getResources().getFont(R.font.lilita_one));
        assertNotEquals(R.drawable.ic_village, ItemArt.icon(context, 1000008));
    }
    private void render(View view, int width, int height, String name) throws Exception {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        File root = new File(System.getProperty("user.dir")).getCanonicalFile();
        if (root.getName().equals("app")) root = root.getParentFile();
        assertTrue(new File(root, "app/build.gradle.kts").exists());
        File out = new File(root, "outputs/previews"); assertTrue(out.isDirectory() || out.mkdirs());
        try (FileOutputStream stream = new FileOutputStream(new File(out, name))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)); }
    }
}

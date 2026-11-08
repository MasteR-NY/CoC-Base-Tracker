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
        long timestamp = System.currentTimeMillis()/1000 - 900;
        String export = "{\"tag\":\"#PREVIEW\",\"timestamp\":" + timestamp
            + ",\"boosts\":{\"lab_boost\":3600,\"builder_consumable\":1800},\"buildings\":[{\"data\":1000001,\"lvl\":17},{\"data\":1000015,\"cnt\":5},{\"data\":1000064,\"lvl\":1},"
            + "{\"data\":1000003,\"lvl\":16,\"timer\":3600},{\"data\":1000008,\"lvl\":20,\"timer\":60000},{\"data\":1000008,\"lvl\":20,\"timer\":90000,\"extra\":true}],"
            + "\"heroes\":[{\"data\":28000000,\"lvl\":99,\"timer\":200000}],"
            + "\"units\":[{\"data\":4000008,\"lvl\":11,\"timer\":400000}],"
            + "\"pets\":[{\"data\":73000000,\"lvl\":11,\"timer\":350000}],"
            + "\"buildings2\":[{\"data\":1000034,\"lvl\":10},{\"data\":1000078,\"lvl\":1},{\"data\":1000036,\"lvl\":9,\"timer\":150000}]}";
        Village village = ExportParser.parse(export, Store.catalog(context), System.currentTimeMillis());
        Store.save(context, export, village); Store.setInGameName(context, village.tag, "Anonymous"); Store.rename(context, village.tag, "Main Account"); Store.bind(context, 777, village.tag);
        ReleaseNotes.markSeen(context);
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        View app = activity.getWindow().getDecorView();
        render(app, 411, 1000, "app-design.png");
        org.json.JSONObject busyRoot=new org.json.JSONObject(export);busyRoot.put("timestamp",System.currentTimeMillis()/1000);busyRoot.getJSONObject("boosts").put("builder_boost",2700);
        for(int n=0;n<busyRoot.getJSONArray("buildings").length();n++){org.json.JSONObject item=busyRoot.getJSONArray("buildings").getJSONObject(n);if(item.optInt("data")==1000015)item.put("cnt",2);}
        String busyRaw=busyRoot.toString();Village busyVillage=ExportParser.parse(busyRaw,Store.catalog(context),System.currentTimeMillis());Store.save(context,busyRaw,busyVillage);
        MainActivity busy=Robolectric.buildActivity(MainActivity.class).setup().get();render(busy.getWindow().getDecorView(),411,1000,"busy-builders-design.png");
        busy.findViewById(R.id.settings_toggle).performClick();busy.findViewById(R.id.time_format_setting).performClick();android.app.AlertDialog clockDialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();render(clockDialog.getWindow().getDecorView(),411,320,"time-format-design.png");clockDialog.dismiss();

        PlannerActivity planner = Robolectric.buildActivity(PlannerActivity.class,
            new android.content.Intent(context, PlannerActivity.class).putExtra("sample", true)).setup().get();
        render(planner.getWindow().getDecorView(), 411, 891, "build-menu-design.png");
        AppWidgetHostView host = new AppWidgetHostView(context);
        // Same readable row renderer as the live service, with a fixed collection for the test host.
        View widget = VillageWidget.render(context, village, 777, village.tag, true, false).apply(context, host);
        render(widget, 400, 560, "widget-design.png");
        WhatsNewActivity guide = Robolectric.buildActivity(WhatsNewActivity.class).setup().get();
        render(guide.getWindow().getDecorView(), 411, 1000, "whats-new-design.png");
        UpdateActivity update = Robolectric.buildActivity(UpdateActivity.class,new android.content.Intent(context,UpdateActivity.class).putExtra("preview",true)).setup().get();
        render(update.getWindow().getDecorView(),411,891,"update-design.png");
        OverviewActivity overview = Robolectric.buildActivity(OverviewActivity.class,
            new android.content.Intent(context, OverviewActivity.class).putExtra("sample",true)).setup().get();
        render(overview.getWindow().getDecorView(),411,1000,"overview-design.png");
        OverviewUiTest.find(overview.getWindow().getDecorView(),"Crafting").performClick();render(overview.getWindow().getDecorView(),411,1000,"crafting-design.png");
        OverviewUiTest.find(overview.getWindow().getDecorView(),"Helpers").performClick();render(overview.getWindow().getDecorView(),411,1000,"helpers-design.png");
        OverviewUiTest.find(overview.getWindow().getDecorView(),"Collection").performClick();render(overview.getWindow().getDecorView(),411,1000,"collection-design.png");
        ((android.widget.Spinner)overview.findViewById(R.id.collection_selector)).setSelection(1);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        render(overview.getWindow().getDecorView(),411,1000,"cosmetics-design.png");
        assertNotNull(context.getResources().getFont(R.font.lilita_one));
        assertNotEquals(R.drawable.ic_village, ItemArt.icon(context, 1000008));
    }
    @Test public void rendersBuildFiltersArmyEquipmentAndBuilderLayouts()throws Exception{
        Context context=RuntimeEnvironment.getApplication();PlannerActivity planner=Robolectric.buildActivity(PlannerActivity.class,new android.content.Intent(context,PlannerActivity.class).putExtra("sample",true)).setup().get();
        planner.findViewById(R.id.planner_filters).performClick();android.app.AlertDialog filters=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        render(filters.getWindow().getDecorView(),411,670,"build-menu-filter-design.png");filters.dismiss();
        PlannerUiTest.find(planner.getWindow().getDecorView(),"Army Upgrades").performClick();render(planner.getWindow().getDecorView(),411,891,"build-menu-army-design.png");
        PlannerUiTest.find(planner.getWindow().getDecorView(),"Hero Equipment").performClick();render(planner.getWindow().getDecorView(),411,891,"build-menu-equipment-design.png");
        render(planner.getWindow().getDecorView(),320,740,"build-menu-home-compact-qa.png");
        PlannerUiTest.find(planner.getWindow().getDecorView(),"Builder Base").performClick();render(planner.getWindow().getDecorView(),411,891,"build-menu-builder-design.png");
        render(planner.getWindow().getDecorView(),320,740,"build-menu-compact-qa.png");render(planner.getWindow().getDecorView(),800,480,"build-menu-landscape-qa.png");
        String raw=OverviewActivity.sampleExport();Village village=ExportParser.parse(raw,Store.catalog(context),System.currentTimeMillis());Store.save(context,raw,village);
        PlannerActivity crafting=Robolectric.buildActivity(PlannerActivity.class,new android.content.Intent(context,PlannerActivity.class).putExtra("tag",village.tag)).setup().get();
        ListViewScroll.crafting(crafting);render(crafting.getWindow().getDecorView(),411,891,"build-menu-crafting-qa.png");
    }
    private static class ListViewScroll { static void crafting(PlannerActivity activity){android.widget.ListView list=activity.findViewById(R.id.planner_list);for(int n=0;n<list.getAdapter().getCount();n++){Object item=list.getAdapter().getItem(n);if(item instanceof UpgradePlanner.Row&&((UpgradePlanner.Row)item).item.category.equals("crafted-module")){list.setSelection(n);org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();return;}}fail("Missing crafting rows");} }
    @Test public void categoryLabelsFitAtLargeFontOnCompactPhone()throws Exception{
        Context context=RuntimeEnvironment.getApplication();android.content.res.Configuration configuration=new android.content.res.Configuration(context.getResources().getConfiguration());configuration.fontScale=1.3f;context.getResources().updateConfiguration(configuration,context.getResources().getDisplayMetrics());
        PlannerActivity planner=Robolectric.buildActivity(PlannerActivity.class,new android.content.Intent(context,PlannerActivity.class).putExtra("sample",true)).setup().get();
        render(planner.getWindow().getDecorView(),320,740,"build-menu-large-font-qa.png");
        for(String name:new String[]{"Buildings & Heroes","Army Upgrades","Hero Equipment","Hero Pets"}){
            android.widget.Button button=(android.widget.Button)PlannerUiTest.find(planner.getWindow().getDecorView(),name);assertNotNull(button);assertNotNull(button.getLayout());
            assertEquals(name.length(),button.getLayout().getLineEnd(button.getLayout().getLineCount()-1));assertEquals(0,button.getLayout().getEllipsisCount(button.getLayout().getLineCount()-1));
            assertTrue(button.getCompoundPaddingTop()+button.getLayout().getHeight()+button.getPaddingBottom()<=button.getHeight());
        }
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

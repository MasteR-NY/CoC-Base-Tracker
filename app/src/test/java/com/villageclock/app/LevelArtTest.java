package com.villageclock.app;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ImageView;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk={26,29,35})
public class LevelArtTest {
    @Test public void differentLevelAppearancesResolveAndUnknownLevelsUseGenericArt(){Context c=RuntimeEnvironment.getApplication();assertNotEquals(ItemArt.icon(c,1000008,1),ItemArt.icon(c,1000008,20));assertNotEquals(ItemArt.icon(c,1000010,1),ItemArt.icon(c,1000010,15));assertNotEquals(ItemArt.icon(c,4000000,1),ItemArt.icon(c,4000000,12));assertEquals(ItemArt.icon(c,1000008),ItemArt.icon(c,1000008,999));assertEquals(ItemArt.icon(c,28000000),ItemArt.icon(c,28000000,50));}
    @Test public void widgetUsesDestinationLevelArt()throws Exception{Context c=RuntimeEnvironment.getApplication();long now=System.currentTimeMillis();Village village=ExportParser.parse("{\"tag\":\"#ART\",\"timestamp\":"+now/1000+",\"buildings\":[{\"data\":1000008,\"lvl\":19,\"timer\":100}]}",Store.catalog(c),now);Village.Upgrade upgrade=village.upgrades.get(0);View row=VillageWidget.row(c,upgrade,now).apply(c,new android.widget.FrameLayout(c));assertEquals(ItemArt.icon(c,upgrade.dataId,20),shadowOf(((ImageView)row.findViewById(R.id.row_icon)).getDrawable()).getCreatedFromResId());}
    @Test public void allCostCurrenciesHaveArtwork(){Context c=RuntimeEnvironment.getApplication();for(String name:new String[]{"Gold","Elixir","Dark Elixir","Builder Gold","Builder Elixir","Shiny Ore","Glowing Ore","Starry Ore"})assertNotEquals(name,0,LevelArt.currency(c,name));assertEquals(0,LevelArt.currency(c,"unknown"));}
    @Test @Config(sdk=35) public void everyLevelResourceExistsAndDecodes()throws Exception{Context c=RuntimeEnvironment.getApplication();JSONObject items=new JSONObject(new String(c.getAssets().open("level-art.json").readAllBytes(),StandardCharsets.UTF_8)).getJSONObject("items");Set<String> unique=new HashSet<>();int levels=0;for(Iterator<String> ids=items.keys();ids.hasNext();){String id=ids.next();JSONObject map=items.getJSONObject(id);for(Iterator<String> keys=map.keys();keys.hasNext();){String level=keys.next();String name=map.getString(level);if(!id.equals("currency"))levels++;if(unique.add(name)){int res=c.getResources().getIdentifier(name,"drawable",c.getPackageName());assertNotEquals(name,0,res);android.graphics.Bitmap bitmap=BitmapFactory.decodeResource(c.getResources(),res);assertNotNull(name,bitmap);assertTrue(bitmap.getWidth()<=128);assertTrue(bitmap.getHeight()<=128);bitmap.recycle();}}}assertTrue(levels>1300);assertTrue(unique.size()>1000);}
}

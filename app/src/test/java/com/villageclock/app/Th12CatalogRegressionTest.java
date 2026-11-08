package com.villageclock.app;

import android.content.Context;
import android.widget.ListView;
import android.widget.ListAdapter;
import android.content.Intent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk={26,29,35})
public class Th12CatalogRegressionTest {
    static String snapshot(){return "{\"tag\":\"#TH12TEST\",\"timestamp\":"+System.currentTimeMillis()/1000+",\"buildings\":[{\"data\":1000001,\"lvl\":12},{\"data\":1000007,\"lvl\":10},{\"data\":1000059,\"lvl\":3},{\"data\":1000070,\"lvl\":5},{\"data\":1000071,\"lvl\":6}],\"heroes\":[{\"data\":28000001,\"lvl\":52}],\"siege_machines\":[{\"data\":4000062,\"lvl\":1}],\"equipment\":[{\"data\":90000016,\"lvl\":17}]}";}
    @Test public void realIdsNamesArtworkAndTh12GatesAgree()throws Exception{
        Context c=RuntimeEnvironment.getApplication();UpgradePlanner planner=Store.planner(c);String raw=snapshot();Village village=ExportParser.parse(raw,Store.catalog(c),System.currentTimeMillis());assertEquals("Monolith Arrow",Store.catalog(c).name(90000016));assertEquals("Electro Fangs",Store.catalog(c).name(90000059));assertEquals("archer-queen",planner.item(90000016).data.getString("hero"));assertNotEquals(ItemArt.icon(c,90000016),ItemArt.icon(c,90000059));assertEquals(0,planner.capAtHall(planner.item(90000059),12));
        UpgradePlanner.Row arrow=find(UpgradePlanner.select(planner.rows(raw,village,"home",UpgradePlanner.Tab.EQUIPMENT,System.currentTimeMillis()),new UpgradePlanner.Filter()),90000016);assertEquals(17,arrow.level);assertEquals(18,arrow.targetLevel());assertEquals(UpgradePlanner.State.AVAILABLE,arrow.state);assertEquals(Long.valueOf(2700),arrow.costs().get("Shiny Ore"));assertEquals(Long.valueOf(600),arrow.costs().get("Glowing Ore"));assertEquals(Long.valueOf(50),arrow.costs().get("Starry Ore"));
        UpgradePlanner.Row slammer=find(UpgradePlanner.select(planner.rows(raw,village,"home",UpgradePlanner.Tab.ARMY,System.currentTimeMillis()),new UpgradePlanner.Filter()),4000062);assertEquals(1,slammer.level);assertEquals(2,slammer.targetLevel());assertEquals(UpgradePlanner.State.AVAILABLE,slammer.state);assertEquals(3,slammer.hallCap);
    }
    @Test public void bothItemsReachBuildMenuInCurrentHallDefault()throws Exception{
        Context c=RuntimeEnvironment.getApplication();String raw=snapshot();Village village=ExportParser.parse(raw,Store.catalog(c),System.currentTimeMillis());Store.save(c,raw,village);PlannerActivity activity=Robolectric.buildActivity(PlannerActivity.class,new Intent(c,PlannerActivity.class).putExtra("tag",village.tag)).setup().get();PlannerUiTest.find(activity.getWindow().getDecorView(),"Hero Equipment").performClick();assertTrue(contains(activity,90000016));PlannerUiTest.find(activity.getWindow().getDecorView(),"Army Upgrades").performClick();assertTrue(contains(activity,4000062));
    }
    private UpgradePlanner.Row find(List<UpgradePlanner.Row> rows,int id){for(UpgradePlanner.Row row:rows)if(row.item.id==id)return row;throw new AssertionError("Missing "+id);}
    private boolean contains(PlannerActivity activity,int id){ListAdapter rows=((ListView)activity.findViewById(R.id.planner_list)).getAdapter();for(int n=0;n<rows.getCount();n++)if(rows.getItem(n) instanceof UpgradePlanner.Row&&((UpgradePlanner.Row)rows.getItem(n)).item.id==id)return true;return false;}
}

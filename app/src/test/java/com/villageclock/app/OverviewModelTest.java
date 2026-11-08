package com.villageclock.app;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class OverviewModelTest {
    private UpgradePlanner planner()throws Exception{return new UpgradePlanner("{\"items\":["
        +"{\"dataId\":1000001,\"name\":\"Town Hall\",\"base\":\"home\",\"category\":\"town-hall\",\"levels\":[{\"level\":3},{\"level\":5}]},"
        +"{\"dataId\":1000010,\"name\":\"Wall\",\"base\":\"home\",\"category\":\"wall\",\"availablePerTownHall\":[{\"townHallLevel\":3,\"count\":300}],\"levels\":[{\"level\":1,\"townHallRequired\":1},{\"level\":3,\"townHallRequired\":3},{\"level\":5,\"townHallRequired\":5}]},"
        +"{\"dataId\":1000007,\"name\":\"Laboratory\",\"base\":\"home\",\"category\":\"army\",\"levels\":[{\"level\":1,\"townHallRequired\":3},{\"level\":2,\"townHallRequired\":5}]},"
        +"{\"dataId\":4000000,\"name\":\"Barbarian\",\"base\":\"home\",\"category\":\"troop\",\"levels\":[{\"level\":2,\"laboratoryRequired\":1},{\"level\":4,\"laboratoryRequired\":2}]}]}");}
    @Test public void currentHallFollowsFacilityGatesAndCountsEveryWall()throws Exception{
        UpgradePlanner planner=planner();assertEquals(2,planner.capAtHall(planner.item(4000000),3));assertEquals(4,planner.capAtHall(planner.item(4000000),5));
        VillageOverview data=new VillageOverview("{\"buildings\":[{\"data\":1000001,\"lvl\":3},{\"data\":1000010,\"lvl\":2,\"cnt\":300}],\"units\":[{\"data\":4000000,\"lvl\":1,\"timer\":1}]}");
        VillageOverview.Progress walls=find(data.progress(planner,false,false),"Walls");assertEquals(300,walls.owned);assertEquals(300,walls.target);assertEquals(600,walls.levels);assertEquals(900,walls.goal);assertEquals(66,walls.percent());
        assertEquals(1,find(data.progress(planner,false,false),"Army").levels);assertEquals(2,find(data.progress(planner,false,false),"Army").goal);
        assertEquals(1500,find(data.progress(planner,false,true),"Walls").goal);assertTrue(data.progress(planner,true,false).isEmpty());
    }
    @Test public void allNestedModulesSurviveAndUnknownCapsAreNotFabricated()throws Exception{
        JSONObject root=new JSONObject();JSONArray types=new JSONArray();for(int t=0;t<3;t++){JSONArray modules=new JSONArray();for(int m=0;m<3;m++)modules.put(new JSONObject().put("data",102000033+t*3+m).put("lvl",2));types.put(new JSONObject().put("data",103000011+t).put("modules",modules));}
        root.put("buildings",new JSONArray().put(new JSONObject().put("data",1000097).put("types",types)));VillageOverview data=new VillageOverview(root.toString());assertEquals(9,data.crafts().size());assertEquals(2,data.crafts().get(8).level);assertEquals(103000013,data.crafts().get(8).type);assertNull(planner().item(data.crafts().get(0).id));
    }
    @Test public void cosmeticCountsAreCombinedAndEquipmentLevelsRemainExported()throws Exception{
        VillageOverview data=new VillageOverview("{\"skins\":[52000001,52000002],\"decos\":[{\"data\":18000001,\"cnt\":4},{\"data\":18000001,\"cnt\":2}],\"equipment\":[{\"data\":90000001,\"lvl\":12}]}");
        assertEquals(2,data.collection("skins").size());assertEquals(6,data.collection("decos").get(0).count);assertEquals(12,data.collection("equipment").get(0).level);assertTrue(data.collection("skins2").isEmpty());
    }
    @Test public void cooldownMissingAndZeroAreDistinctAndUnknownHelpersStayIdentifiable()throws Exception{
        List<Availability.Entry> entries=Availability.parse("{\"helpers\":[{\"data\":93000001,\"lvl\":1,\"helper_cooldown\":60},{\"data\":93000003,\"lvl\":1}],\"boosts\":{\"clocktower_cooldown\":0}}",100000);
        assertEquals(3,entries.size());assertEquals(160000,entries.get(0).readyAt);assertEquals(-1,entries.get(1).readyAt);assertTrue(entries.get(1).name.contains("93000003"));assertEquals(100000,entries.get(2).readyAt);
        assertTrue(Availability.parse("{\"buildings2\":[{\"data\":1000039,\"lvl\":7}],\"boosts\":{\"clocktower_boost\":600}}",100000).get(0).status(100000).contains("not supplied"));
    }
    private VillageOverview.Progress find(List<VillageOverview.Progress> list,String name){return list.stream().filter(p->p.name.equals(name)).findFirst().orElseThrow();}
}

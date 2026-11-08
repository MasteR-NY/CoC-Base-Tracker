package com.villageclock.app;

import org.junit.Before;
import org.junit.Test;
import org.json.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class UpgradePlannerTest {
    private UpgradePlanner planner; private long now;
    @Before public void catalog() throws Exception {Path file=Paths.get("src/main/assets/planner-catalog.json");if(!Files.exists(file))file=Paths.get("app").resolve(file);
        planner=new UpgradePlanner(Files.readString(file));now=System.currentTimeMillis()/1000*1000;}
    private String snapshot(String fields){return "{\"tag\":\"#TEST\",\"timestamp\":"+now/1000+","+fields+"}";}
    private List<UpgradePlanner.Row> rows(String raw,String base,UpgradePlanner.Tab tab)throws Exception{return planner.rows(raw,ExportParser.parse(raw,id->planner.item(id)==null?null:planner.item(id).name,now),base,tab,now);}
    private UpgradePlanner.Row find(List<UpgradePlanner.Row> rows,int id,int level){return rows.stream().filter(r->r.item.id==id&&r.level==level).findFirst().orElseThrow();}
    @Test public void catalogContainsBothBasesEquipmentAndPetsWithoutSuperchargeLevelCollision(){assertTrue(planner.items().size()>200);assertEquals("Archer Tower",planner.item(1000009).name);
        assertEquals(UpgradePlanner.Tab.EQUIPMENT,planner.item(90000000).tab);assertEquals("builder",planner.item(4000031).base);
        assertTrue(planner.item(1000085).max()>2);assertFalse(planner.item(1000085).next(1).optBoolean("supercharge"));}
    @Test public void groupsWallCountsAndPreservesDifferentLevels()throws Exception{String raw=snapshot("\"buildings\":[{\"data\":1000001,\"lvl\":15},{\"data\":1000010,\"lvl\":14,\"cnt\":200},{\"data\":1000010,\"lvl\":14,\"cnt\":75},{\"data\":1000010,\"lvl\":15,\"cnt\":50}]");
        List<UpgradePlanner.Row> r=rows(raw,"home",UpgradePlanner.Tab.BUILDINGS);assertEquals(275,find(r,1000010,14).count);assertEquals(50,find(r,1000010,15).count);assertEquals(15,find(r,1000010,14).next.optInt("level"));}
    @Test public void heroGateUsesCurrentHeroHallNotUpgradingTarget()throws Exception{String raw=snapshot("\"buildings\":[{\"data\":1000001,\"lvl\":15},{\"data\":1000071,\"lvl\":1,\"timer\":100}],\"heroes\":[{\"data\":28000000,\"lvl\":10}]");
        UpgradePlanner.Row row=find(rows(raw,"home",UpgradePlanner.Tab.BUILDINGS),28000000,10);assertEquals(UpgradePlanner.State.LOCKED,row.state);assertTrue(row.missing.contains("Hero Hall"));}
    @Test public void equipmentShowsThreeOreCostsAndBlacksmithGate()throws Exception{String raw=snapshot("\"buildings\":[{\"data\":1000001,\"lvl\":15},{\"data\":1000070,\"lvl\":1}],\"equipment\":[{\"data\":90000015,\"lvl\":26}]");
        UpgradePlanner.Row row=find(rows(raw,"home",UpgradePlanner.Tab.EQUIPMENT),90000015,26);assertEquals(3,row.costs().size());assertTrue(row.costs().get("Starry Ore")>0);assertEquals(UpgradePlanner.State.LOCKED,row.state);assertTrue(row.missing.contains("Blacksmith"));}
    @Test public void darkArmyGatesUseDarkBuildings()throws Exception{String raw=snapshot("\"buildings\":[{\"data\":1000001,\"lvl\":15},{\"data\":1000007,\"lvl\":15},{\"data\":1000006,\"lvl\":18},{\"data\":1000020,\"lvl\":8}]");
        List<UpgradePlanner.Row> r=rows(raw,"home",UpgradePlanner.Tab.ARMY);assertTrue(find(r,4000011,0).missing.contains("Dark Barracks"));assertTrue(find(r,26000009,0).missing.contains("Dark Spell Factory"));}
    @Test public void equalLevelUpgradesKeepSeparateBoostAdjustedTimers()throws Exception{String raw=snapshot("\"boosts\":{\"builder_boost\":1000},\"buildings\":[{\"data\":1000001,\"lvl\":15},{\"data\":1000008,\"lvl\":19,\"timer\":20000},{\"data\":1000008,\"lvl\":19,\"timer\":50000}]");
        List<UpgradePlanner.Row> r=rows(raw,"home",UpgradePlanner.Tab.BUILDINGS);List<UpgradePlanner.Row> running=new ArrayList<>();for(UpgradePlanner.Row row:r)if(row.state==UpgradePlanner.State.RUNNING)running.add(row);
        assertEquals(2,running.size());assertEquals(30000000,running.get(1).finishAt-running.get(0).finishAt);assertEquals(now+11000000,running.get(0).finishAt);}
    @Test public void elapsedTimerDoesNotInventNewSavedLevel()throws Exception{String raw=snapshot("\"buildings\":[{\"data\":1000001,\"lvl\":15},{\"data\":1000008,\"lvl\":19,\"timer\":10}]");Village v=ExportParser.parse(raw,id->"test",now);
        UpgradePlanner.Row r=find(planner.rows(raw,v,"home",UpgradePlanner.Tab.BUILDINGS,now+20000),1000008,19);assertEquals(UpgradePlanner.State.SYNC,r.state);assertEquals(19,r.level);}
    @Test public void omittedBuilderBaseIsNotReportedAsMissingBuildings()throws Exception{String raw=snapshot("\"buildings\":[{\"data\":1000001,\"lvl\":15}]");assertTrue(rows(raw,"builder",UpgradePlanner.Tab.BUILDINGS).isEmpty());}
    @Test public void newBuildingCountsAndMergeConsumedItemsAreNotInvented()throws Exception{String raw=snapshot("\"buildings\":[{\"data\":1000001,\"lvl\":17},{\"data\":1000009,\"lvl\":20,\"cnt\":2}]");
        List<UpgradePlanner.Row> r=rows(raw,"home",UpgradePlanner.Tab.BUILDINGS);assertFalse(r.stream().anyMatch(x->x.item.id==1000009&&x.fresh));assertTrue(r.stream().anyMatch(x->x.fresh&&x.count>0&&x.item.id!=1000009));}
    @Test public void filtersCombineResourceAndTypeAndSortUnitCosts()throws Exception{String raw=snapshot("\"buildings\":[{\"data\":1000001,\"lvl\":12},{\"data\":1000008,\"lvl\":8},{\"data\":1000009,\"lvl\":9},{\"data\":1000010,\"lvl\":8,\"cnt\":100}]");
        UpgradePlanner.Filter f=new UpgradePlanner.Filter();f.resources.add("Gold");f.types.add("Defense");f.sort=UpgradePlanner.Sort.COST_MIN;
        List<UpgradePlanner.Row> selected=UpgradePlanner.select(rows(raw,"home",UpgradePlanner.Tab.BUILDINGS),f);assertTrue(selected.size()>2);long previous=-1;for(UpgradePlanner.Row r:selected){assertEquals("Defense",r.item.type());assertTrue(r.price(f.resources)>=previous);previous=r.price(f.resources);}
        f.sort=UpgradePlanner.Sort.COST_MAX;selected=UpgradePlanner.select(rows(raw,"home",UpgradePlanner.Tab.BUILDINGS),f);assertTrue(selected.get(0).price(f.resources)>=selected.get(selected.size()-1).price(f.resources));}
    @Test public void unknownIdsRemainVisibleAndEquipmentOwnershipIsNotAssumed()throws Exception{String raw=snapshot("\"buildings\":[{\"data\":1000001,\"lvl\":15},{\"data\":1999999,\"lvl\":7}]");
        assertEquals(UpgradePlanner.State.UNKNOWN,find(rows(raw,"home",UpgradePlanner.Tab.BUILDINGS),1999999,7).state);
        assertEquals(UpgradePlanner.State.UNOWNED,find(rows(raw,"home",UpgradePlanner.Tab.EQUIPMENT),90000000,0).state);}
    @Test public void builderTroopUnlockStartsAtItsActualInitialLevel()throws Exception{String raw=snapshot("\"buildings\":[],\"buildings2\":[{\"data\":1000034,\"lvl\":10}]");
        assertEquals(17,find(rows(raw,"builder",UpgradePlanner.Tab.ARMY),4000106,0).next.optInt("level"));}
}

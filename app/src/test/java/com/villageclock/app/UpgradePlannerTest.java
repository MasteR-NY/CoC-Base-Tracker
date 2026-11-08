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
    @Test public void defaultScopeStopsAtCurrentHallButIncludesNextHall()throws Exception{
        int cap=planner.capAtHall(planner.item(1000008),12);assertTrue(cap>1);assertTrue(cap<planner.item(1000008).max());
        String raw=snapshot("\"buildings\":[{\"data\":1000001,\"lvl\":12},{\"data\":1000008,\"lvl\":"+cap+"},{\"data\":1000008,\"lvl\":"+(cap-1)+"}]");
        List<UpgradePlanner.Row> all=rows(raw,"home",UpgradePlanner.Tab.BUILDINGS);UpgradePlanner.Filter filter=new UpgradePlanner.Filter();List<UpgradePlanner.Row> current=UpgradePlanner.select(all,filter);
        assertFalse(current.contains(find(all,1000008,cap)));assertTrue(current.contains(find(all,1000008,cap-1)));assertEquals(13,find(current,1000001,12).targetLevel());
        filter.currentHallOnly=false;assertTrue(UpgradePlanner.select(all,filter).contains(find(all,1000008,cap)));
    }
    @Test public void currentHallAllowsFacilityUpgradesThatAreStillNeeded()throws Exception{
        int cap=planner.capAtHall(planner.item(4000000),12);String raw=snapshot("\"buildings\":[{\"data\":1000001,\"lvl\":12},{\"data\":1000007,\"lvl\":1}],\"units\":[{\"data\":4000000,\"lvl\":"+(cap-1)+"}]");
        UpgradePlanner.Row row=find(rows(raw,"home",UpgradePlanner.Tab.ARMY),4000000,cap-1);assertEquals(UpgradePlanner.State.LOCKED,row.state);assertTrue(row.withinCurrentHall);assertTrue(new UpgradePlanner.Filter().accepts(row));assertTrue(row.missing.contains("Laboratory"));
    }
    @Test public void builderHallScopeUsesBuilderLevelsAndKeepsNextBuilderHall()throws Exception{
        String raw=snapshot("\"buildings2\":[{\"data\":1000034,\"lvl\":8},{\"data\":1000044,\"lvl\":9}]");
        List<UpgradePlanner.Row> selected=UpgradePlanner.select(rows(raw,"builder",UpgradePlanner.Tab.BUILDINGS),new UpgradePlanner.Filter());assertEquals(9,find(selected,1000034,8).targetLevel());for(UpgradePlanner.Row row:selected)assertEquals("builder",row.item.base);
    }
    @Test public void activeUpgradesStayFirstForEitherCostSortEvenBeyondHallCap()throws Exception{
        int cap=planner.capAtHall(planner.item(1000008),12);String raw=snapshot("\"buildings\":[{\"data\":1000001,\"lvl\":12},{\"data\":1000008,\"lvl\":"+cap+",\"timer\":5000}]");
        List<UpgradePlanner.Row> all=rows(raw,"home",UpgradePlanner.Tab.BUILDINGS);UpgradePlanner.Filter filter=new UpgradePlanner.Filter();
        for(UpgradePlanner.Sort sort:new UpgradePlanner.Sort[]{UpgradePlanner.Sort.COST_MIN,UpgradePlanner.Sort.COST_MAX}){filter.sort=sort;List<UpgradePlanner.Row> selected=UpgradePlanner.select(all,filter);assertEquals(UpgradePlanner.State.RUNNING,selected.get(0).state);assertEquals(cap+1,selected.get(0).targetLevel());}
    }
    @Test public void unknownActiveIdsKeepTheirTimersAndTargetLevel()throws Exception{
        String raw=snapshot("\"buildings\":[{\"data\":1000001,\"lvl\":12},{\"data\":1999999,\"lvl\":3,\"timer\":100}]");UpgradePlanner.Row row=find(UpgradePlanner.select(rows(raw,"home",UpgradePlanner.Tab.BUILDINGS),new UpgradePlanner.Filter()),1999999,3);assertEquals(UpgradePlanner.State.RUNNING,row.state);assertEquals(now+100000,row.finishAt);assertEquals(4,row.targetLevel());
    }
    @Test public void newAndPermanentModifyTypeRatherThanMatchingUnrelatedItems()throws Exception{
        String raw=snapshot("\"buildings\":[{\"data\":1000001,\"lvl\":12},{\"data\":1000008,\"lvl\":8}]");UpgradePlanner.Filter f=new UpgradePlanner.Filter();f.types.addAll(Arrays.asList("Defense","New","Permanent"));List<UpgradePlanner.Row> selected=UpgradePlanner.select(rows(raw,"home",UpgradePlanner.Tab.BUILDINGS),f);assertFalse(selected.isEmpty());for(UpgradePlanner.Row row:selected){assertEquals("Defense",row.item.type());assertTrue(row.fresh);assertFalse(row.item.seasonal());}
    }
    @Test public void equipmentForAnUnavailableHeroIsOutsideCurrentHall()throws Exception{
        assertEquals(0,planner.capAtHall(planner.item(90000013),12));assertTrue(planner.capAtHall(planner.item(90000013),15)>0);
    }
    @Test public void upgradingHallDoesNotUnlockItsFutureCapsBeforeSync()throws Exception{
        int cap=planner.capAtHall(planner.item(1000008),12);String raw=snapshot("\"buildings\":[{\"data\":1000001,\"lvl\":12,\"timer\":100},{\"data\":1000008,\"lvl\":"+cap+"}]");List<UpgradePlanner.Row> all=rows(raw,"home",UpgradePlanner.Tab.BUILDINGS);assertFalse(new UpgradePlanner.Filter().accepts(find(all,1000008,cap)));assertTrue(new UpgradePlanner.Filter().accepts(find(all,1000001,12)));
    }
    @Test public void unknownTimersStaySeparateAndLevelZeroModulesRemainVisible()throws Exception{
        String raw=snapshot("\"buildings\":[{\"data\":1000001,\"lvl\":12},{\"data\":1999999,\"lvl\":3,\"timer\":100},{\"data\":1999999,\"lvl\":3,\"timer\":200},{\"data\":102999999,\"lvl\":0}]");
        List<UpgradePlanner.Row> selected=UpgradePlanner.select(rows(raw,"home",UpgradePlanner.Tab.BUILDINGS),new UpgradePlanner.Filter());List<UpgradePlanner.Row> timers=new ArrayList<>();for(UpgradePlanner.Row row:selected)if(row.item.id==1999999)timers.add(row);assertEquals(2,timers.size());assertEquals(100000,timers.get(1).finishAt-timers.get(0).finishAt);assertNotNull(find(selected,102999999,0));
    }
}

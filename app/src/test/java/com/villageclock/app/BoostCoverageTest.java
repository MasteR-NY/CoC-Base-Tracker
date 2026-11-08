package com.villageclock.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class BoostCoverageTest {
    private static final long NOW=1794081600000L;
    private Village parse(String fields)throws Exception{return ExportParser.parse("{\"tag\":\"#BOOST12\",\"timestamp\":"+NOW/1000+","+fields+"}",id->"Item "+id,NOW);}
    private Village.Upgrade find(Village village,int id){return village.upgrades.stream().filter(u->u.dataId==id).findFirst().orElseThrow();}
    @Test public void researchPotionConsumes24SecondsOfWorkPerSecondOnlyForItsRemainingHour()throws Exception{
        Village.Upgrade lab=find(parse("\"buildings\":[],\"boosts\":{\"lab_boost\":3600},\"units\":[{\"data\":4000000,\"lvl\":9,\"timer\":172800}]"),4000000);
        assertEquals(90000,(lab.finishAt-NOW)/1000);assertEquals(172776,lab.timing.remainingWork(1));assertEquals(24,lab.timing.speed(3599));assertEquals(1,lab.timing.speed(3600));
        assertEquals(86400,lab.timing.remainingWork(3600));assertEquals(0,lab.timing.remainingWork(90000));assertTrue(lab.boostText(NOW).contains("24×"));
    }
    @Test public void researchPotionCanFinishBeforeTheDoseExpiresAndStacksExtendDuration()throws Exception{
        Village one=parse("\"buildings\":[],\"boosts\":{\"lab_boost\":3600},\"units\":[{\"data\":4000000,\"lvl\":9,\"timer\":43200}]");assertEquals(1800,(one.upgrades.get(0).finishAt-NOW)/1000);
        Village two=parse("\"buildings\":[],\"boosts\":{\"lab_boost\":7200},\"units\":[{\"data\":4000000,\"lvl\":9,\"timer\":259200}]");assertEquals(93600,(two.upgrades.get(0).finishAt-NOW)/1000);assertEquals(24,two.upgrades.get(0).timing.speed(4000));
    }
    @Test public void potionsAndSoupStayInTheCorrectUpgradeQueues()throws Exception{
        Village v=parse("\"boosts\":{\"builder_boost\":100,\"lab_boost\":200,\"pet_boost\":300,\"lab_consumable\":400,\"clocktower_boost\":500},\"buildings\":[{\"data\":1000008,\"timer\":100000}],\"units\":[{\"data\":4000000,\"timer\":100000}],\"spells\":[{\"data\":26000000,\"timer\":100000}],\"siege_machines\":[{\"data\":4000051,\"timer\":100000}],\"pets\":[{\"data\":73000000,\"timer\":100000}],\"buildings2\":[{\"data\":1000044,\"timer\":100000}],\"units2\":[{\"data\":4000031,\"timer\":100000}]");
        assertEquals(10,find(v,1000008).timing.speed(0));for(int id:new int[]{4000000,26000000,4000051})assertEquals(27,find(v,id).timing.speed(0));
        assertEquals(27,find(v,73000000).timing.speed(0));assertEquals(10,find(v,1000044).timing.speed(0));assertEquals(10,find(v,4000031).timing.speed(0));
        assertEquals(4,find(v,4000000).timing.speed(200));assertEquals(4,find(v,73000000).timing.speed(300));assertEquals(1,find(v,73000000).timing.speed(400));
    }
    @Test public void builderBiteResumesAfterStrongerPotionEnds()throws Exception{
        Village.Upgrade u=parse("\"buildings\":[{\"data\":1000008,\"timer\":40000}],\"boosts\":{\"builder_boost\":3600,\"builder_consumable\":7200}").upgrades.get(0);
        assertEquals(10,u.timing.speed(0));assertEquals(2,u.timing.speed(3600));assertEquals(5600,(u.finishAt-NOW)/1000);assertEquals(1,u.timing.speed(7200));
    }
    @Test public void helperAddsOnlyItsOwnWorkAndOnlyOnAssignedUpgrade()throws Exception{
        Village v=parse("\"helpers\":[{\"data\":93000000,\"lvl\":8},{\"data\":93000001,\"lvl\":12}],\"buildings\":[{\"data\":1000008,\"timer\":200000,\"helper_timer\":100},{\"data\":1000009,\"timer\":200000}],\"units\":[{\"data\":4000000,\"timer\":200000,\"helper_timer\":100}],\"boosts\":{\"builder_boost\":200,\"builder_consumable\":300,\"lab_boost\":200,\"lab_consumable\":300}");
        assertEquals(18,find(v,1000008).timing.speed(0));assertEquals(10,find(v,1000009).timing.speed(0));assertEquals(39,find(v,4000000).timing.speed(0));
        assertEquals(27,find(v,4000000).timing.speed(100));assertEquals(4,find(v,4000000).timing.speed(200));
    }
    @Test public void explicitlyRepeatingHelperUsesUnified23HourWorkDays()throws Exception{
        Village v=parse("\"helpers\":[{\"data\":93000000,\"lvl\":8,\"helper_cooldown\":1000},{\"data\":93000001,\"lvl\":12}],\"buildings\":[],\"units\":[{\"data\":4000000,\"timer\":300000,\"recurrent_helper\":true}]");
        UpgradeTiming.Plan p=v.upgrades.get(0).timing;assertEquals(1,p.speed(999));assertEquals(13,p.speed(1000));assertEquals(1,p.speed(4600));assertEquals(13,p.speed(83800));assertEquals(13,p.speed(166600));
        assertEquals(12*3600+4600,p.workDone(4600));
    }
    @Test public void recurringFlagWithoutExportedCooldownDoesNotInventAHelperSchedule()throws Exception{
        Village v=parse("\"helpers\":[{\"data\":93000000,\"lvl\":8}],\"buildings\":[{\"data\":1000008,\"timer\":200000,\"recurrent_helper\":true}]");
        assertEquals(NOW+200000000,v.upgrades.get(0).finishAt);assertFalse(v.upgrades.get(0).hasBoosts());
    }
    @Test public void currentSessionAndFutureSessionsDoNotOverlapOrDoubleCount() {
        UpgradeTiming.Helper helper=new UpgradeTiming.Helper(8,2000,1000,"Apprentice");UpgradeTiming.Plan p=new UpgradeTiming.Plan(200000,Collections.emptyList(),helper);
        assertEquals(9,p.speed(0));assertEquals(9,p.speed(1999));assertEquals(9,p.speed(2000));assertEquals(1,p.speed(5600));assertEquals(8*5600+5600,p.workDone(5600));
    }
    @Test public void unassignedHelpersStoredMagicItemsAndCombatBoostsDoNotAccelerateWork()throws Exception{
        Village v=parse("\"helpers\":[{\"data\":93000000,\"lvl\":8},{\"data\":93000002,\"lvl\":5}],\"magic_items\":[{\"data\":92000000,\"cnt\":5}],\"boosts\":{\"resource_boost\":3600,\"power_boost\":3600,\"hero_boost\":3600},\"buildings\":[{\"data\":1000008,\"timer\":7200}]");
        assertEquals(NOW+7200000,v.upgrades.get(0).finishAt);assertFalse(v.upgrades.get(0).hasBoosts());
        assertTrue(parse("\"buildings\":[{\"data\":1000008,\"lvl\":21}],\"magic_items\":[{\"data\":92000000,\"cnt\":4}]").upgrades.isEmpty());
    }
    @Test public void elapsedSnapshotPreservesBothCorrectWorkRemainingAndFinishEstimate()throws Exception{
        Village.Upgrade u=parse("\"buildings\":[],\"boosts\":{\"lab_boost\":3600},\"units\":[{\"data\":4000000,\"timer\":172800}]").upgrades.get(0);
        assertEquals("Work left: 1d 0h 0m 0s",u.workText(NOW+3600000));assertEquals("Finishes in: 1d 0h 0m 0s",u.etaText(NOW+3600000));assertTrue(u.boostText(NOW+3600000).contains("1×"));
    }
    @Test public void fractionalWorkIsContinuousAcrossBoostAndHelperChanges(){
        UpgradeTiming.Plan p=new UpgradeTiming.Plan(200,Collections.singletonList(new UpgradeTiming.Boost("research",23,1)),new UpgradeTiming.Helper(2,2,-1,"Helper"));
        assertEquals(199,p.remainingWorkAtMillis(40));assertEquals(187,p.remainingWorkAtMillis(500));
        assertEquals(175,p.remainingWorkAtMillis(999));assertEquals(174,p.remainingWorkAtMillis(1000));
        assertEquals(173,p.remainingWorkAtMillis(1500));assertEquals(171,p.remainingWorkAtMillis(2000));
        assertEquals(171,p.remainingWorkAtMillis(2500));assertEquals(170,p.remainingWorkAtMillis(3000));
        assertEquals(0,p.remainingWorkAtMillis(300000));assertEquals(200,p.remainingWorkAtMillis(-1));
    }
    @Test public void planMatchesIndependentSecondBySecondSimulation() {
        Random random=new Random(741);
        for(int test=0;test<40;test++){
            long potion=20+random.nextInt(200),bite=20+random.nextInt(400),helper=10+random.nextInt(100),work=100+random.nextInt(9000);
            UpgradeTiming.Plan p=new UpgradeTiming.Plan(work,Arrays.asList(new UpgradeTiming.Boost("potion",9,potion,"builders","Potion"),new UpgradeTiming.Boost("bite",1,bite,"builders","Bite")),new UpgradeTiming.Helper(8,helper,500,"Helper"));
            long done=0,seconds=0;
            while(done<work){long speed=Math.max(seconds<potion?10:1,seconds<bite?2:1);if(seconds<helper||seconds>=500&&(seconds-500)%82800<3600)speed+=8;done+=speed;seconds++;}
            assertEquals(seconds,p.secondsToFinish());assertTrue(p.workDone(seconds)>=work);assertTrue(p.workDone(seconds-1)<work);
        }
    }
}

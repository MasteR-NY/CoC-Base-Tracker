package com.villageclock.app;

import org.junit.Test;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class UpgradeTimingTest {
    private UpgradeTiming.Boost potion(long seconds) { return new UpgradeTiming.Boost("builder-potion", 9, seconds); }
    @Test public void twoBuilderPotionsExtendDurationNotSpeed() {
        assertEquals(7200, UpgradeTiming.secondsToFinish(72000, Arrays.asList(potion(7200))));
        assertEquals(97200, UpgradeTiming.secondsToFinish(162000, Arrays.asList(potion(7200))));
    }
    @Test public void samePotionFamilyCannotBeCountedAsIndependentSpeed() {
        assertEquals(7200, UpgradeTiming.secondsToFinish(72000, Arrays.asList(potion(3600), potion(7200))));
    }
    @Test public void partialPotionThenNormalSpeed() {
        assertEquals(2700, UpgradeTiming.secondsToFinish(3600, Arrays.asList(potion(100))));
    }
    @Test public void finishesBeforePotionExpiresAndRoundsUp() {
        assertEquals(361, UpgradeTiming.secondsToFinish(3601, Arrays.asList(potion(7200))));
    }
    @Test public void potionAndSnackAddTheirExtraWorkRates() {
        assertEquals(3600, UpgradeTiming.secondsToFinish(39600, Arrays.asList(potion(3600), new UpgradeTiming.Boost("bite", 1, 3600))));
    }
    @Test public void apprenticeIsNotMultipliedByBuilderPotion() {
        assertEquals(3600, UpgradeTiming.secondsToFinish(64800, Arrays.asList(potion(3600), new UpgradeTiming.Boost("helper", 8, 3600))));
    }
    @Test public void potionSnackAndHelperExpireIndependently() {
        // 0..100: rate 19 =>1900; 100..200: rate11=>1100;
        // 200..300: rate2=>200; remaining100 at rate1.
        assertEquals(400, UpgradeTiming.secondsToFinish(3300, Arrays.asList(potion(200), new UpgradeTiming.Boost("bite", 1, 300), new UpgradeTiming.Boost("helper", 8, 100))));
    }
    @Test public void researchPotionAndSoupUseDistinctFamilies() {
        assertEquals(3600, UpgradeTiming.secondsToFinish(97200, Arrays.asList(new UpgradeTiming.Boost("research", 23, 3600), new UpgradeTiming.Boost("soup", 3, 3600))));
    }
    @Test public void petPotionHasItsOwnDuration() {
        assertEquals(7200, UpgradeTiming.secondsToFinish(172800, Arrays.asList(new UpgradeTiming.Boost("pet", 23, 7200))));
    }
    @Test public void clockTowerRevertsToNormalAfterExpiry() {
        assertEquals(2400, UpgradeTiming.secondsToFinish(18600, Arrays.asList(new UpgradeTiming.Boost("clock", 9, 1800))));
    }
    @Test public void expiredAndZeroBoostsAreIgnored() {
        assertEquals(60, UpgradeTiming.secondsToFinish(60, Arrays.asList(potion(0), new UpgradeTiming.Boost("bad", 0, 100))));
        assertEquals(0, UpgradeTiming.secondsToFinish(0, Arrays.asList(potion(3600))));
    }
    @Test public void integerMathHandlesVeryLargeValues() {
        assertEquals(Long.MAX_VALUE, UpgradeTiming.secondsToFinish(Long.MAX_VALUE, Collections.emptyList()));
        assertEquals(Long.MAX_VALUE - 9, UpgradeTiming.secondsToFinish(Long.MAX_VALUE, Arrays.asList(potion(1))));
    }
    @Test public void hammerJamIsInformationalAndNeverHalvesExportAgain() throws Exception {
        long now = Instant.parse("2026-11-08T00:00:00Z").toEpochMilli();
        Village village = ExportParser.parse("{\"tag\":\"#P0LY98\",\"timestamp\":" + now/1000 + ",\"buildings\":[{\"data\":1000008,\"lvl\":20,\"timer\":172800}]}", id -> "Tower", now);
        assertEquals(now + 172800000, village.upgrades.get(0).finishAt);
        assertTrue(FastEvents.noteAt(now).contains("Hammer Jam"));
        assertFalse(FastEvents.noteAt(Instant.parse("2026-11-16T08:00:00Z").toEpochMilli()).contains("Hammer Jam"));
    }
    @Test public void discountIsRetainedWhenPotionIsAlsoActive() throws Exception {
        long now = Instant.parse("2026-11-08T00:00:00Z").toEpochMilli();
        Village village = ExportParser.parse("{\"tag\":\"#P0LY98\",\"timestamp\":" + now/1000 + ",\"boosts\":{\"builder_boost\":7200},\"buildings\":[{\"data\":1000008,\"lvl\":20,\"timer\":172800}]}", id -> "Tower", now);
        assertEquals(now + 108000000, village.upgrades.get(0).finishAt);
    }
    @Test public void eventDoesNotDiscountBuilderBaseOrCraftedModulesAgain() throws Exception {
        long now = Instant.parse("2026-11-08T00:00:00Z").toEpochMilli();
        Village village = ExportParser.parse("{\"tag\":\"#P0LY98\",\"timestamp\":" + now/1000 + ",\"buildings\":[{\"data\":1,\"types\":[{\"modules\":[{\"data\":2,\"timer\":1000,\"lvl\":1}]}]}],\"buildings2\":[{\"data\":3,\"lvl\":1,\"timer\":1000}]}", id -> "Item", now);
        for (Village.Upgrade upgrade : village.upgrades) assertEquals(now + 1000000, upgrade.finishAt);
    }
}

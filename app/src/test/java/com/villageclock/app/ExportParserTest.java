package com.villageclock.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class ExportParserTest {
    private static final long NOW = 1794081600000L;
    private final ExportParser.Names names = id -> id == 1000008 ? "Archer Tower" : null;
    private String export(String fields) {
        return "{\"tag\":\"#P0LY98\",\"timestamp\":" + NOW / 1000 + ",\"buildings\":[{\"data\":1000001,\"lvl\":15},{\"data\":1000015,\"cnt\":5},{\"data\":1000064,\"lvl\":1},{\"data\":1000008,\"lvl\":20,\"timer\":3600}]" + fields + "}";
    }
    private Village parse(String json) throws Exception { return ExportParser.parse(json, names, NOW); }
    @Test public void parsesActualSnapshotShapeAndCountsBuilders() throws Exception {
        Village v = parse(export(""));
        assertEquals("#P0LY98", v.tag); assertEquals(15, v.townHall); assertEquals(6, v.builders);
        assertEquals(1, v.upgrades.size()); assertEquals(5, v.freeBuilders(NOW));
        assertEquals("Archer Tower", v.upgrades.get(0).name); assertEquals(21, v.upgrades.get(0).level);
        assertEquals(NOW + 3600000, v.upgrades.get(0).finishAt);
    }
    @Test public void countdownRemainsBasedOnExportTimeNotImportTime() throws Exception {
        Village v = ExportParser.parse(export(""), names, NOW + 1800000);
        assertEquals(NOW + 3600000, v.nextFinish(NOW + 1800000));
        assertEquals(0, v.running(NOW + 3600000)); assertEquals(6, v.freeBuilders(NOW + 3600000));
    }
    @Test public void distinguishesQueuesAndUnknownItems() throws Exception {
        Village v = parse(export(",\"units\":[{\"data\":4000001,\"lvl\":5,\"timer\":7000}],\"pets\":[{\"data\":73000000,\"lvl\":2,\"timer\":9000}],\"buildings2\":[{\"data\":1000034,\"lvl\":8},{\"data\":1000078,\"lvl\":1}],\"units2\":[{\"data\":4000031,\"lvl\":10,\"timer\":8000}]"));
        assertEquals(4, v.upgrades.size()); assertEquals(2, v.baseBuilders);
        assertEquals(1, v.running(Village.Queue.LAB, NOW)); assertEquals(1, v.running(Village.Queue.PETS, NOW));
        assertEquals(1, v.running(Village.Queue.STAR_LAB, NOW)); assertTrue(v.upgrades.get(1).name.contains("#4000001"));
    }
    @Test public void builderPotionAddsOnlyWhileActive() throws Exception {
        Village v = parse(export(",\"boosts\":{\"builder_boost\":100}"));
        assertEquals(NOW + 2700000, v.upgrades.get(0).finishAt);
    }
    @Test public void simultaneousBoostsAndHelpersArePiecewise() {
        assertEquals(100, UpgradeTiming.secondsToFinish(1400, Arrays.asList(new UpgradeTiming.Boost("potion", 9, 100), new UpgradeTiming.Boost("helper", 4, 100))));
        assertEquals(200, UpgradeTiming.secondsToFinish(1500, Arrays.asList(new UpgradeTiming.Boost("potion", 9, 100), new UpgradeTiming.Boost("helper", 4, 100))));
        assertEquals(600, UpgradeTiming.secondsToFinish(2000, Arrays.asList(new UpgradeTiming.Boost("potion", 9, 100), new UpgradeTiming.Boost("bite", 1, 500))));
        assertEquals(100, UpgradeTiming.secondsToFinish(100, Collections.emptyList()));
    }
    @Test public void helperLevelAffectsOnlyAssignedJob() throws Exception {
        Village v = parse(export(",\"helpers\":[{\"data\":93000000,\"lvl\":4}]").replace("\"timer\":3600", "\"timer\":3600,\"helper_timer\":100"));
        assertEquals(NOW + 3200000, v.upgrades.get(0).finishAt);
    }
    @Test public void goblinDoesNotOccupyRegularBuilder() throws Exception {
        Village v = parse(export("").replace("\"timer\":3600", "\"timer\":3600,\"extra\":true"));
        assertEquals(6, v.freeBuilders(NOW));
    }
    @Test public void nestedModulesAreIncluded() throws Exception {
        Village v = parse("{\"tag\":\"#P0LY98\",\"timestamp\":" + NOW / 1000 + ",\"buildings\":[{\"data\":1000008,\"types\":[{\"modules\":[{\"data\":1000008,\"lvl\":2,\"timer\":42}]}]}]}");
        assertEquals(1, v.upgrades.size()); assertEquals(NOW + 42000, v.upgrades.get(0).finishAt);
    }
    @Test public void completedItemsDoNotHideActiveItemsInWidget() throws Exception {
        Village v = parse(export(",\"units\":[{\"data\":4000001,\"lvl\":5,\"timer\":7200}]"));
        assertEquals(Village.Queue.LAB, v.forWidget(NOW + 3600000).get(0).queue);
        assertEquals(0, v.nextFinish(NOW + 7200000));
    }
    @Test public void acceptsIdleVillageWithoutFabricatingTimers() throws Exception {
        Village v = parse("{\"tag\":\"#P0LY98\",\"timestamp\":" + NOW / 1000 + ",\"buildings\":[]}");
        assertTrue(v.upgrades.isEmpty()); assertEquals(0, v.builders);
    }
    @Test public void rejectsInvalidJsonTimestampsAndCredentialText() {
        for (String bad : Arrays.asList("not json", "{\"password\":\"example\"}", export("").replace("1794081600", "999999999999"), export("").replace("1794081600", "0"), export("").replace("1794081600", "\"1794081600\""))) {
            try { parse(bad); fail("Accepted invalid export"); } catch (ExportParser.InvalidExport expected) { } catch (Exception other) { throw new AssertionError(other); }
        }
    }
    @Test public void rejectsOverflowNegativeAndNonfiniteTimers() {
        for (String timer : Arrays.asList("-1", "999999999999999999", "1.5", "\"3600\"", "null")) {
            try { parse(export("").replace("\"timer\":3600", "\"timer\":" + timer)); fail("Accepted invalid timer " + timer); }
            catch (ExportParser.InvalidExport expected) { } catch (Exception other) { throw new AssertionError(other); }
        }
    }
    @Test public void limitsImportSize() {
        try { parse("x".repeat(ExportParser.MAX_CHARS + 1)); fail(); }
        catch (ExportParser.InvalidExport expected) { } catch (Exception other) { throw new AssertionError(other); }
    }
}

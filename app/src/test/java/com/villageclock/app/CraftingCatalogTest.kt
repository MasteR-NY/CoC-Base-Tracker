package com.villageclock.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class CraftingCatalogTest {
    private lateinit var planner: UpgradePlanner
    private val now = System.currentTimeMillis() / 1000 * 1000
    @Before fun catalog() {
        var path = Path.of("src/main/assets/planner-catalog.json")
        if (!Files.exists(path)) path = Path.of("app").resolve(path)
        planner = UpgradePlanner(Files.readString(path))
    }
    private fun snapshot(hall: Int, level: Int, timer: Long = 0): String {
        val types = JSONArray()
        repeat(3) { t ->
            val modules = JSONArray()
            repeat(3) { m -> modules.put(JSONObject().put("data", 102000033 + t * 3 + m).put("lvl", level).apply { if (t == 0 && m == 0 && timer > 0) put("timer", timer) }) }
            types.put(JSONObject().put("data", 103000011 + t).put("modules", modules))
        }
        return JSONObject().put("tag", "#CRAFTTEST").put("timestamp", now / 1000)
            .put("buildings", JSONArray().put(JSONObject().put("data", 1000001).put("lvl", hall)).put(JSONObject().put("data", 1000097).put("types", types))).toString()
    }
    private fun rows(raw: String, time: Long = now): List<UpgradePlanner.Row> = planner.rows(raw, ExportParser.parse(raw, { planner.item(it)?.name }, now), "home", UpgradePlanner.Tab.BUILDINGS, time)
    @Test fun allNineModulesHavePerHallCapsAndStaySeparateFromPermanentProgress() {
        for (id in 102000033..102000041) {
            val item = requireNotNull(planner.item(id))
            assertEquals(10, item.max()); assertEquals("Defense", item.type()); assertTrue(item.seasonal())
            for ((hall, cap) in listOf(10 to 0, 11 to 1, 12 to 2, 13 to 3, 17 to 7, 18 to 10)) assertEquals("$id at TH$hall", cap, planner.capAtHall(item, hall))
            assertNull(VillageOverview.group(item))
        }
    }
    @Test fun quotesUseCorrectResourceAndWorkForEachModule() {
        val costs = longArrayOf(3500000, 4000000, 30000, 40000, 3000000, 3500000, 3000000, 35000, 4000000)
        val resources = listOf("Elixir", "Gold", "Dark Elixir", "Dark Elixir", "Elixir", "Gold", "Gold", "Dark Elixir", "Elixir")
        val hours = longArrayOf(6, 8, 4, 8, 4, 6, 4, 6, 8)
        val modules = rows(snapshot(12, 1)).filter { it.item.category == "crafted-module" }.sortedBy { it.item.id }
        assertEquals(9, modules.size)
        modules.forEachIndexed { i, row ->
            assertEquals(UpgradePlanner.State.AVAILABLE, row.state); assertFalse(row.fresh)
            assertEquals(costs[i], row.costs().getValue(resources[i])); assertEquals(hours[i] * 3600, row.seconds())
            assertTrue(row.requirements.contains("Crafting Station 1"))
        }
    }
    @Test fun placedStationWithoutLevelIsRecognizedWithoutChangingExport() {
        val raw = snapshot(12, 1)
        val station = rows(raw).single { it.item.id == 1000097 }
        assertEquals(1, station.level); assertEquals(UpgradePlanner.State.MAXED, station.state)
        val overview = VillageOverview(raw)
        assertEquals(1, overview.owned(false).single { it.id == 1000097 }.level)
        assertFalse(overview.root.getJSONArray("buildings").getJSONObject(1).has("lvl"))
    }
    @Test fun th12FinishedModulesRemainInOverviewAndOnlyFutureCatalogView() {
        val raw = snapshot(12, 2); val all = rows(raw); val filter = UpgradePlanner.Filter()
        assertEquals(9, VillageOverview(raw).crafts().size)
        assertFalse(UpgradePlanner.select(all, filter).any { it.item.category == "crafted-module" })
        filter.currentHallOnly = false
        val future = UpgradePlanner.select(all, filter).filter { it.item.category == "crafted-module" }
        assertEquals(9, future.size)
        assertTrue(future.all { it.state == UpgradePlanner.State.LOCKED && it.missing.contains("Town Hall 13") && it.targetLevel() == 3 })
    }
    @Test fun absentModulesAndDefenseParentsDoNotBecomeNewConstruction() {
        val root = JSONObject(snapshot(12, 1)); root.getJSONArray("buildings").remove(1)
        assertFalse(rows(root.toString()).any { it.item.category == "crafted-module" || it.item.id in 103000011..103000013 })
        assertFalse(rows(snapshot(12, 1)).any { it.item.id in 103000011..103000013 })
    }
    @Test fun exportedRunningModuleKeepsBoostedDeadlinePastHallCapAndRequestsSyncWhenEnded() {
        val root = JSONObject(snapshot(12, 2, 20000)).put("boosts", JSONObject().put("builder_boost", 1000))
        val raw = root.toString(); val village = ExportParser.parse(raw, { planner.item(it)?.name }, now)
        val expected = village.upgrades.single { it.dataId == 102000033 }.finishAt
        val row = rows(raw).single { it.item.id == 102000033 }
        assertEquals(expected, row.finishAt); assertTrue(expected < now + 20000000)
        assertTrue(UpgradePlanner.Filter().accepts(row)); assertEquals(2, row.level)
        val ended = rows(raw, expected + 1).single { it.item.id == row.item.id }
        assertEquals(UpgradePlanner.State.SYNC, ended.state); assertEquals(2, ended.level)
    }
    @Test fun permanentFilterExcludesSeasonalModulesAndUnknownFutureModulesRemainVisible() {
        val root = JSONObject(snapshot(12, 1))
        root.getJSONArray("buildings").getJSONObject(1).getJSONArray("types").getJSONObject(0).getJSONArray("modules").put(JSONObject().put("data", 102000099).put("lvl", 2).put("timer", 3600))
        val all = rows(root.toString()); val filter = UpgradePlanner.Filter().apply { types.add("Permanent") }
        assertFalse(UpgradePlanner.select(all, filter).any { it.item.category == "crafted-module" })
        val unknown = all.single { it.item.id == 102000099 }
        assertTrue(unknown.active()); assertTrue(UpgradePlanner.Filter().accepts(unknown)); assertEquals(0, unknown.item.max())
    }
}

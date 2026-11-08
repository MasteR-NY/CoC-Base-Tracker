package com.villageclock.app

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Snapshot inventory and level progress; timers never promote exported levels. */
class VillageOverview @Throws(JSONException::class) constructor(raw: String) {
    class Owned(@JvmField val data: JSONObject) {
        @JvmField val id = data.optInt("data", -1)
        @JvmField val level = if (id == 1000097 && !data.has("lvl") && (data.optJSONArray("types")?.length() ?: 0) > 0) 1 else data.optInt("lvl").coerceIn(0, 1000)
        @JvmField val count = data.optInt("cnt", 1).coerceIn(1, 1000)
    }
    class Progress(@JvmField val name: String) {
        @JvmField var owned = 0L; @JvmField var target = 0L; @JvmField var levels = 0L
        @JvmField var goal = 0L; @JvmField var maxed = 0L; @JvmField var unknown = 0
        fun percent(): Int = if (goal == 0L) 0 else minOf(100, levels * 100 / goal).toInt()
    }
    class Craft(@JvmField val type: Int, @JvmField val data: JSONObject) {
        @JvmField val id = data.optInt("data", -1)
        @JvmField val level = data.optInt("lvl").coerceIn(0, 1000)
    }
    class CollectionItem(@JvmField val section: String, @JvmField val id: Int, @JvmField val count: Int, @JvmField val level: Int)
    @JvmField val root = JSONObject(raw)
    fun hall(builder: Boolean): Int = root.optJSONArray(if (builder) "buildings2" else "buildings").objects().lastOrNull { it.optInt("data") == (if (builder) 1000034 else 1000001) }?.optInt("lvl") ?: 0
    fun hasBase(builder: Boolean): Boolean = root.optJSONArray(if (builder) "buildings2" else "buildings") != null
    fun owned(builder: Boolean): List<Owned> = (if (builder) listOf("buildings2", "traps2", "heroes2", "units2", "spells2") else listOf("buildings", "traps", "heroes", "guardians", "units", "spells", "siege_machines", "pets", "equipment")).flatMap { section -> root.optJSONArray(section).objects().filter { it.optInt("data", -1) >= 0 }.map(::Owned) }
    fun progress(planner: UpgradePlanner, builder: Boolean, global: Boolean): List<Progress> {
        if (!hasBase(builder)) return emptyList()
        val base = if (builder) "builder" else "home"
        var hall = hall(builder)
        if (global) for (item in planner.items()) if (item.base == base && item.category == (if (builder) "builder-hall" else "town-hall")) hall = item.max()
        val inventory = owned(builder).groupBy { it.id }
        val totals = listOf("Buildings", "Walls", "Traps", "Army", "Heroes", "Pets").associateWith { Progress(it) }
        for (item in planner.items()) {
            if (item.base != base) continue
            val group = group(item) ?: continue
            val progress = totals.getValue(group)
            val existing = inventory[item.id].orEmpty()
            val cap = if (global) item.max() else planner.capAtHall(item, hall)
            val count = existing.sumOf { it.count.toLong() }
            progress.owned += count
            if (cap <= 0) { if (count > 0) progress.unknown += count.toInt(); continue }
            var expected = planner.countAtHall(item, hall)
            if (expected < 0) expected = count.toInt()
            if (count == 0L && expected == 0 && group in setOf("Army", "Heroes", "Pets")) expected = 1
            val target = maxOf(count, expected.toLong())
            progress.target += target
            progress.goal += target * cap
            for (entry in existing) { progress.levels += minOf(cap, entry.level).toLong() * entry.count; if (entry.level >= cap) progress.maxed += entry.count }
        }
        for (entry in owned(builder)) if (planner.item(entry.id) == null) totals.getValue("Buildings").unknown += entry.count
        return totals.values.filter { it.target > 0 || it.owned > 0 || it.unknown > 0 }
    }
    fun crafts(): List<Craft> = buildList {
        for (building in root.optJSONArray("buildings").objects()) for (type in building.optJSONArray("types").objects().take(100)) for (module in type.optJSONArray("modules").objects().take(100)) if (module.optInt("data", -1) >= 0) add(Craft(type.optInt("data", -1), module))
    }
    fun collection(section: String): List<CollectionItem> {
        val source = root.optJSONArray(section) ?: return emptyList()
        val combined = sortedMapOf<Int, CollectionItem>()
        for (i in 0 until source.length()) {
            val value = source.opt(i)
            val obj = value as? JSONObject
            val id = obj?.optInt("data", -1) ?: (value as? Number)?.toInt() ?: -1
            if (id < 0) continue
            val count = obj?.optInt("cnt", 1)?.coerceIn(1, 1000) ?: 1
            combined[id] = CollectionItem(section, id, count + (combined[id]?.count ?: 0), obj?.optInt("lvl", -1) ?: -1)
        }
        return combined.values.toList()
    }
    companion object {
        private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
        @JvmStatic fun group(item: UpgradePlanner.Item): String? = when {
            item.category == "wall" -> "Walls"; item.category == "trap" -> "Traps"; item.category == "hero" -> "Heroes"
            item.tab == UpgradePlanner.Tab.ARMY -> "Army"; item.tab == UpgradePlanner.Tab.PETS -> "Pets"
            item.tab == UpgradePlanner.Tab.EQUIPMENT || item.seasonal() || item.category == "guardian" -> null
            else -> "Buildings"
        }
    }
}

package com.villageclock.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.Locale

/** Offline planning. Catalog prices never replace exported running timers. */
class UpgradePlanner @Throws(Exception::class) constructor(json: String) {
    enum class Tab(@JvmField val label: String) { BUILDINGS("Buildings & Heroes"), ARMY("Army Upgrades"), EQUIPMENT("Hero Equipment"), PETS("Hero Pets") }
    enum class State { RUNNING, SYNC, AVAILABLE, LOCKED, UNOWNED, MAXED, UNKNOWN }
    enum class Sort { DEFAULT, COST_MIN, COST_MAX, TIME_MIN, NAME }
    class Item(@JvmField val data: JSONObject) {
        @JvmField val id = data.optInt("dataId")
        @JvmField val name: String = data.optString("name")
        @JvmField val base: String = data.optString("base")
        @JvmField val category: String = data.optString("category")
        @JvmField val hero: String = title(data.optString("hero"))
        @JvmField val tab = when (category) { "hero-equipment" -> Tab.EQUIPMENT; "pet" -> Tab.PETS; "troop", "spell", "siege-machine" -> Tab.ARMY; else -> Tab.BUILDINGS }
        fun next(level: Int): JSONObject? = data.optJSONArray("levels").objects().filter { !it.optBoolean("supercharge") && it.optInt("level") > level }.minByOrNull { it.optInt("level") }
        fun max(): Int = data.optJSONArray("levels").objects().filter { !it.optBoolean("supercharge") }.maxOfOrNull { it.optInt("level") } ?: 0
        fun seasonal(): Boolean = category == "crafted-defense"
        fun type(): String = when {
            category == "hero" || category == "guardian" -> "Hero"
            category == "army" || category == "research" -> "Army"
            category == "resource" -> "Resource"
            category in listOf("defense", "trap", "wall", "crafted-defense") -> "Defense"
            tab == Tab.ARMY -> when (category) { "troop" -> "Troops"; "spell" -> "Spells"; else -> "Siege" }
            else -> "Other"
        }
    }
    class Row(@JvmField val item: Item, @JvmField val level: Int, @JvmField val count: Int,
        @JvmField val state: State, @JvmField val finishAt: Long, @JvmField val requirements: String,
        @JvmField val missing: String, @JvmField val fresh: Boolean) {
        @JvmField val next: JSONObject? = item.next(level)
        @JvmField var hall = 0
        @JvmField var hallCap = 0
        @JvmField var withinCurrentHall = true
        fun seconds(): Long {
            val next = next ?: return 0
            for (key in listOf("buildTime", "researchTime", "upgradeTime")) next.optJSONObject(key)?.let { return it.optLong("days") * 86400 + it.optLong("hours") * 3600 + it.optLong("minutes") * 60 + it.optLong("seconds") }
            return 0
        }
        fun costs(): Map<String, Long> {
            val result = linkedMapOf<String, Long>()
            val next = next ?: return result
            for (prefix in listOf("build", "research", "upgrade")) if (next.has(prefix + "Cost")) { result[next.optString(prefix + "CostResource", "Unknown resource")] = next.optLong(prefix + "Cost"); return result }
            for (ore in listOf("Shiny", "Glowing", "Starry")) if (next.optLong("upgrade${ore}Ore") > 0) result["$ore Ore"] = next.optLong("upgrade${ore}Ore")
            return result
        }
        fun price(resource: Set<String>): Long = costs().filterKeys { resource.isEmpty() || matchesResource(it, resource) }.values.sum()
        fun costText(): String {
            if (next == null) return ""
            val costs = costs()
            if (costs.isEmpty()) return if (item.tab == Tab.EQUIPMENT) "No ore cost listed" else "Cost not listed"
            return costs.entries.joinToString(" · ") { String.format(Locale.US, "%,d %s", it.value, it.key) }
        }
        fun status(): String = when (state) {
            State.RUNNING -> "Upgrading"; State.SYNC -> "Timer ended · sync to confirm"; State.LOCKED -> "Requires $missing"
            State.MAXED -> "Catalog max level"; State.UNOWNED -> "Not in export · check unlock in game"; State.UNKNOWN -> "Check requirements in game"
            else -> if (fresh) "New · requirements listed below" else "Next upgrade"
        }
        fun levelsLeft(): Int = (item.max() - level).coerceAtLeast(0)
        fun active(): Boolean = state == State.RUNNING || state == State.SYNC
        fun targetLevel(): Int = next?.optInt("level", level) ?: if (active()) level + 1 else level
    }
    class Filter {
        @JvmField val resources: MutableSet<String> = hashSetOf()
        @JvmField val types: MutableSet<String> = hashSetOf()
        @JvmField val heroes: MutableSet<String> = hashSetOf()
        @JvmField var status = "Remaining"
        @JvmField var sort = Sort.DEFAULT
        @JvmField var currentHallOnly = true
        fun clear() { resources.clear(); types.clear(); heroes.clear(); status = "Remaining"; sort = Sort.DEFAULT; currentHallOnly = true }
        fun copy(): Filter = Filter().also { it.resources.addAll(resources); it.types.addAll(types); it.heroes.addAll(heroes); it.status = status; it.sort = sort; it.currentHallOnly = currentHallOnly }
        fun accepts(row: Row): Boolean {
            if (currentHallOnly && !row.withinCurrentHall) return false
            if (resources.isNotEmpty() && row.costs().keys.none { matchesResource(it, resources) }) return false
            val categories = types - setOf("New", "Permanent", "Non-Seasonal")
            if (categories.isNotEmpty() && row.item.type() !in categories) return false
            if ("New" in types && !row.fresh) return false
            if (("Permanent" in types || "Non-Seasonal" in types) && row.item.seasonal()) return false
            if (heroes.isNotEmpty() && row.item.hero !in heroes) return false
            return when (status) { "All" -> true; "Requirements met" -> row.state == State.AVAILABLE; "In progress" -> row.active(); "Locked / unowned" -> row.state in setOf(State.LOCKED, State.UNOWNED, State.UNKNOWN); "Maxed" -> row.state == State.MAXED; else -> row.state != State.MAXED }
        }
    }
    private val items = mutableListOf<Item>()
    private val byId = mutableMapOf<Int, Item>()
    private val hallCaps = mutableMapOf<String, Int>()
    private val displayNames = mutableMapOf<Int, String>()
    @JvmField val revision: String
    init {
        val root = JSONObject(json)
        revision = root.optString("revision")
        for (data in root.getJSONArray("items").objects()) Item(data).let { items.add(it); byId[it.id] = it }
    }
    fun items(): List<Item> = Collections.unmodifiableList(items)
    fun item(id: Int): Item? = byId[id]
    fun setDisplayName(id: Int, name: String?) { if (!name.isNullOrEmpty()) displayNames[id] = name }
    fun capAtHall(item: Item, hall: Int): Int = capAtHall(item, hall, mutableSetOf())
    private fun capAtHall(item: Item, hall: Int, visiting: MutableSet<Int>): Int {
        val key = "${item.id}:$hall"
        hallCaps[key]?.let { return it }
        if (!visiting.add(item.id)) return 0
        try {
            if (item.category == "town-hall" || item.category == "builder-hall") return minOf(hall, item.max())
            if (allowed(item, hall, item.base == "builder") == 0) return 0
            if (item.tab == Tab.EQUIPMENT && item.hero.isNotEmpty()) for (owner in items) if (owner.category == "hero" && owner.name == item.hero && capAtHall(owner, hall, visiting) == 0) return 0
            var cap = 0
            for (level in item.data.optJSONArray("levels").objects()) {
                if (level.optBoolean("supercharge")) continue
                val allowed = reqMap(item, level).all { (gate, value) ->
                    if (gate == "Town Hall" || gate == "Builder Hall") value <= hall
                    else items.firstOrNull { it.base == item.base && it.name == gate }?.let { capAtHall(it, hall, visiting) >= value } ?: false
                }
                if (allowed) cap = maxOf(cap, level.optInt("level"))
            }
            hallCaps[key] = cap
            return cap
        } finally { visiting.remove(item.id) }
    }
    fun countAtHall(item: Item, hall: Int): Int = if (hasMergeCounts(item, hall, item.base == "builder")) -1 else allowed(item, hall, item.base == "builder")
    @Throws(Exception::class) fun rows(json: String, village: Village, base: String, tab: Tab, now: Long): List<Row> {
        val root = JSONObject(json)
        val owned = mutableMapOf<Int, MutableList<JSONObject>>()
        val levels = mutableMapOf<Int, Int>()
        val builder = base == "builder"
        val sections = if (builder) listOf("buildings2", "traps2", "heroes2", "units2", "spells2") else listOf("buildings", "traps", "heroes", "guardians", "units", "spells", "siege_machines", "pets", "equipment")
        var hasBase = false
        var entries = 0
        for (section in sections) {
            val source = root.optJSONArray(section) ?: continue
            hasBase = true
            for (i in 0 until source.length()) {
                require(++entries <= 10000) { "Too many inventory entries" }
                val entry = source.optJSONObject(i) ?: continue
                addOwned(entry, owned, levels)
                for (type in entry.optJSONArray("types").objects().take(100)) for (module in type.optJSONArray("modules").objects().take(100)) addOwned(module, owned, levels)
            }
        }
        if (!hasBase) return emptyList()
        val hall = levels[if (builder) 1000034 else 1000001] ?: 0
        val result = mutableListOf<Row>()
        val exportOrder = village.upgrades.sortedBy { it.key.substringAfterLast(':').toInt() }
        val offsets = mutableMapOf<String, Int>()
        fun finish(id: Int, level: Int, count: Int, timer: Long): Long {
            if (timer <= 0) return 0
            val key = "$id:$level"
            val skip = offsets[key] ?: 0
            val end = exportOrder.filter { it.dataId == id && it.level == level + 1 }.getOrNull(skip)?.finishAt ?: 0
            offsets[key] = skip + count
            return if (end != 0L) end else village.exportedAt + minOf(10L * 366 * 86400, timer) * 1000
        }
        for (item in items) {
            if (item.base != base || item.tab != tab) continue
            val inventory = owned[item.id].orEmpty()
            var total = 0
            for (entry in inventory) {
                val level = entry.optInt("lvl").coerceIn(0, 1000)
                val count = entry.optInt("cnt", 1).coerceIn(1, 1000)
                total += count
                val next = item.next(level)
                val end = finish(item.id, level, count, entry.optLong("timer"))
                val missing = missing(item, next, levels, builder)
                val state = when { entry.optLong("timer") > 0 -> if (end > now) State.RUNNING else State.SYNC; next == null -> State.MAXED; missing.isNotEmpty() -> State.LOCKED; needsGameCheck(item) -> State.UNKNOWN; else -> State.AVAILABLE }
                result.add(Row(item, level, count, state, end, requirements(item, next).joinToString(" · "), missing, false))
            }
            val allowed = allowed(item, hall, builder)
            var absent = (allowed - total).coerceAtLeast(0)
            if (hasMergeCounts(item, hall, builder)) absent = 0
            if (total == 0 && allowed < 0) absent = 1
            if (absent > 0) {
                val next = item.next(0)
                val missing = missing(item, next, levels, builder)
                val state = when { item.tab == Tab.EQUIPMENT -> State.UNOWNED; missing.isNotEmpty() -> State.LOCKED; needsGameCheck(item) -> State.UNKNOWN; else -> State.AVAILABLE }
                result.add(Row(item, 0, absent, state, 0, requirements(item, next).joinToString(" · "), missing, true))
            }
        }
        for ((id, inventory) in owned) if (id !in byId) {
            val meta = JSONObject().put("dataId", id).put("name", displayNames[id] ?: "Unknown item #$id").put("base", base).put("category", "other").put("levels", JSONArray())
            val unknown = Item(meta)
            if (tab != Tab.BUILDINGS) continue
            for (entry in inventory) {
                val level = entry.optInt("lvl")
                val count = entry.optInt("cnt", 1).coerceAtLeast(1)
                val end = finish(id, level, count, entry.optLong("timer"))
                val state = if (end == 0L) State.UNKNOWN else if (end > now) State.RUNNING else State.SYNC
                result.add(Row(unknown, level, count, state, end, "Catalog update needed", "", false))
            }
        }
        return group(result).also { rows -> for (row in rows) {
            row.hall = hall
            row.hallCap = if (row.item.category == "town-hall" || row.item.category == "builder-hall") minOf(row.item.max(), hall + 1) else capAtHall(row.item, hall)
            row.withinCurrentHall = row.active() || row.state == State.UNKNOWN && !row.fresh && row.item.max() == 0 || row.next != null && row.targetLevel() <= row.hallCap || row.next == null && row.level > 0
        } }
    }
    private fun reqMap(item: Item, level: JSONObject?): Map<String, Int> {
        val out = linkedMapOf<String, Int>()
        val root = item.data.optJSONObject("requirements")
        for ((key, defaultName) in REQUIREMENT_NAMES) {
            val value = maxOf(root?.optInt(key) ?: 0, level?.optInt(key) ?: 0)
            if (value <= 0) continue
            val name = when { key == "barrackLevelRequired" && item.data.optString("troopType") == "dark" -> "Dark Barracks"; key == "spellFactoryLevelRequired" && item.data.optString("spellType") == "dark" -> "Dark Spell Factory"; else -> defaultName }
            out[name] = maxOf(out[name] ?: 0, value)
        }
        return out
    }
    private fun requirements(item: Item, level: JSONObject?): List<String> = reqMap(item, level).map { "${it.key} ${it.value}" } + if (item.tab == Tab.EQUIPMENT && item.hero.isNotEmpty()) listOf(item.hero) else emptyList()
    private fun missing(item: Item, level: JSONObject?, owned: Map<Int, Int>, builder: Boolean): String = reqMap(item, level).filter { (name, value) ->
        (items.filter { it.base == (if (builder) "builder" else "home") && it.name == name }.maxOfOrNull { owned[it.id] ?: 0 } ?: 0) < value
    }.map { "${it.key} ${it.value}" }.joinToString(" · ")
    companion object {
        private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
        @JvmStatic fun select(rows: List<Row>, filter: Filter): List<Row> {
            val name = compareBy<Row> { it.item.name }
            val order = when (filter.sort) {
                Sort.COST_MIN -> compareBy { row: Row -> row.price(filter.resources) }
                Sort.COST_MAX -> compareByDescending { row: Row -> row.price(filter.resources) }
                Sort.TIME_MIN -> compareBy(Row::seconds)
                Sort.NAME -> name
                else -> compareBy<Row> { when (it.state) { State.RUNNING -> 0; State.SYNC -> 1; State.AVAILABLE -> 2; else -> 3 } }.thenBy { if (it.state == State.RUNNING) it.finishAt else 0 }
            }
            val active = compareBy<Row> { when (it.state) { State.RUNNING -> 0; State.SYNC -> 1; else -> 2 } }.thenBy { if (it.active()) it.finishAt else 0 }
            return rows.filter(filter::accepts).sortedWith(active.then(order).then(name).thenBy { it.level })
        }
        private fun group(rows: List<Row>): List<Row> {
            val grouped = linkedMapOf<String, Row>()
            for (row in rows) {
                val key = "${row.item.id}:${row.level}:${row.state}:${row.finishAt}:${row.fresh}"
                val previous = grouped[key]
                grouped[key] = if (previous == null) row else Row(row.item, row.level, previous.count + row.count, row.state, row.finishAt, row.requirements, row.missing, row.fresh)
            }
            return grouped.values.toList()
        }
        private fun addOwned(entry: JSONObject, owned: MutableMap<Int, MutableList<JSONObject>>, levels: MutableMap<Int, Int>) {
            val id = entry.optInt("data", -1)
            if (id < 0) return
            owned.getOrPut(id) { mutableListOf() }.add(entry)
            levels[id] = maxOf(levels[id] ?: 0, entry.optInt("lvl").coerceIn(0, 1000))
        }
        private fun allowed(item: Item, hall: Int, builder: Boolean): Int {
            val source = item.data.optJSONArray(if (builder) "availablePerBuilderHall" else "availablePerTownHall") ?: return -1
            var count = 0; var best = 0
            for (entry in source.objects()) { val at = entry.optInt(if (builder) "builderHallLevel" else "townHallLevel"); if (at <= hall && at >= best) { best = at; count = entry.optInt("count") } }
            return count
        }
        private fun hasMergeCounts(item: Item, hall: Int, builder: Boolean): Boolean = item.data.optJSONArray(if (builder) "availablePerBuilderHall" else "availablePerTownHall").objects().any { it.optInt(if (builder) "builderHallLevel" else "townHallLevel") <= hall && it.has("countAfterMerges") }
        private val REQUIREMENT_NAMES = linkedMapOf("townHallRequired" to "Town Hall", "builderHallRequired" to "Builder Hall", "builderHallLevelRequired" to "Builder Hall", "laboratoryRequired" to "Laboratory", "starLabRequired" to "Star Laboratory", "heroHallLevelRequired" to "Hero Hall", "blacksmithLevelRequired" to "Blacksmith", "petHouseLevelRequired" to "Pet House", "builderBarracksRequired" to "Builder Barracks", "barrackLevelRequired" to "Barracks", "spellFactoryLevelRequired" to "Spell Factory", "workshopLevelRequired" to "Workshop")
        private fun needsGameCheck(item: Item): Boolean = item.seasonal() || item.category in setOf("guardian", "town-hall", "builder-hall") || item.data.optString("id") in setOf("ricochet-cannon", "multi-archer-tower", "multi-gear-tower", "inferno-artillery")
        private fun matchesResource(cost: String, selected: Set<String>): Boolean = selected.any { cost == it || it in cost.split(" or ") }
        @JvmStatic fun title(slug: String): String = slug.split('-').filter { it.isNotEmpty() }.joinToString(" ") { Character.toUpperCase(it[0]) + it.substring(1) }
    }
}

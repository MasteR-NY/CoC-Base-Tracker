package com.villageclock.app

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.floor

/** Treat every export as untrusted input; never parses a login or session. */
object ExportParser {
    const val MAX_CHARS = 1_000_000
    private const val MAX_SECONDS = 10L * 366 * 86400
    fun interface Names { fun name(id: Int): String? }
    class InvalidExport(message: String) : Exception(message)
    @JvmStatic @Throws(InvalidExport::class) fun parse(text: String?, names: Names, now: Long): Village {
        if (text == null || text.length > MAX_CHARS) throw InvalidExport("Export is empty or too large (maximum 1 MB).")
        try {
            val root = JSONObject(text.trim())
            val seconds = bounded(root, "timestamp", Long.MAX_VALUE)
            if (seconds < 1420070400L || seconds > now / 1000 + 300) throw InvalidExport("The export timestamp is invalid. Copy a fresh village export from Clash.")
            if (root.opt("buildings") !is JSONArray && root.opt("buildings2") !is JSONArray) throw InvalidExport("This is not a village export. Use Settings → More Settings → Export village data.")
            val tag = root.optString("tag", "").trim()
            if (!tag.matches(Regex("#[A-Za-z0-9]{2,16}"))) throw InvalidExport("This export has no valid player tag.")
            val at = seconds * 1000
            val levels = mutableMapOf<Int, Int>()
            val cooldowns = mutableMapOf<Int, Long>()
            val helpers = root.optJSONArray("helpers")
            if (helpers != null) for (i in 0 until helpers.length()) {
                val helper = helpers.optJSONObject(i) ?: continue
                levels[helper.optInt("data")] = helper.optInt("lvl").coerceIn(0, 20)
                if (helper.has("helper_cooldown")) cooldowns[helper.optInt("data")] = bounded(helper, "helper_cooldown", MAX_SECONDS)
            }
            val boosts = root.optJSONObject("boosts")
            val cooldown = boosts?.let { bounded(it, "helper_cooldown", MAX_SECONDS) } ?: 0
            val sections = linkedMapOf("buildings" to Village.Queue.BUILDERS, "traps" to Village.Queue.BUILDERS, "heroes" to Village.Queue.BUILDERS,
                "guardians" to Village.Queue.BUILDERS, "units" to Village.Queue.LAB, "spells" to Village.Queue.LAB, "siege_machines" to Village.Queue.LAB,
                "pets" to Village.Queue.PETS, "buildings2" to Village.Queue.BASE, "traps2" to Village.Queue.BASE, "heroes2" to Village.Queue.BASE, "units2" to Village.Queue.STAR_LAB)
            val jobs = mutableListOf<Village.Upgrade>()
            var hall = 0; var builders = 0; var baseBuilders = 0; var total = 0
            for ((section, queue) in sections) {
                val entries = root.optJSONArray(section) ?: continue
                total += entries.length()
                if (total > 10000) throw InvalidExport("This export contains too many entries.")
                for (i in 0 until entries.length()) {
                    val entry = entries.optJSONObject(i) ?: continue
                    val id = entry.optInt("data", -1)
                    val count = entry.optInt("cnt", 1).coerceIn(1, 50)
                    if (section == "buildings") {
                        if (id == 1000001) hall = entry.optInt("lvl").coerceIn(0, 100)
                        if (id == 1000015) builders += count
                        if (id == 1000064) builders++
                    }
                    if (section == "buildings2") {
                        if (id == 1000034) baseBuilders = maxOf(1, baseBuilders)
                        if (id == 1000078 || id == 1000047) baseBuilders = 2
                    }
                    add(entry, count, queue, at, boosts, levels, cooldowns, cooldown, names, jobs)
                    val types = entry.optJSONArray("types") ?: continue
                    for (t in 0 until minOf(types.length(), 100)) {
                        val modules = types.optJSONObject(t)?.optJSONArray("modules") ?: continue
                        for (m in 0 until minOf(modules.length(), 100)) modules.optJSONObject(m)?.let { add(it, 1, queue, at, boosts, levels, cooldowns, cooldown, names, jobs) }
                    }
                }
            }
            val effects = mutableListOf<ActiveEffects.Effect>()
            if (boosts != null) for (key in listOf("builder_boost", "builder_consumable", "lab_boost", "pet_boost", "lab_consumable", "clocktower_boost")) {
                val duration = bounded(boosts, key, MAX_SECONDS)
                if (duration > 0) ActiveEffects.effect(key, at + duration * 1000)?.let(effects::add)
            }
            return Village(tag, AccountNames.clean(root.opt("name")), at, hall, minOf(builders, 7), baseBuilders, jobs, effects)
        } catch (_: JSONException) { throw InvalidExport("The copied text is not valid village JSON.") }
        catch (_: StackOverflowError) { throw InvalidExport("The copied text is not valid village JSON.") }
    }
    private fun add(item: JSONObject, count: Int, queue: Village.Queue, at: Long, boosts: JSONObject?, levels: Map<Int, Int>, cooldowns: Map<Int, Long>, helperCooldown: Long, names: Names, out: MutableList<Village.Upgrade>) {
        val work = bounded(item, "timer", MAX_SECONDS)
        if (work == 0L) return
        val id = item.optInt("data", -1)
        if (id < 0) throw InvalidExport("An upgrade has no valid item ID.")
        val level = item.optInt("lvl", 0)
        if (level !in 0..1000) throw InvalidExport("An upgrade level is out of range.")
        val windows = mutableListOf<UpgradeTiming.Boost>()
        fun boost(key: String, extra: Long, group: String, label: String) {
            if (boosts == null) return
            val duration = bounded(boosts, key, MAX_SECONDS)
            if (duration > 0) windows.add(UpgradeTiming.Boost(key, extra, duration, group, label))
        }
        when (queue) {
            Village.Queue.BUILDERS -> { boost("builder_boost", 9, "builder-speed", "Builder Potion"); boost("builder_consumable", 1, "builder-speed", "Builder Bite") }
            Village.Queue.LAB -> { boost("lab_boost", 23, "research", "Research Potion"); boost("lab_consumable", 3, "soup", "Study Soup") }
            Village.Queue.PETS -> { boost("pet_boost", 23, "pet", "Pet Potion"); boost("lab_consumable", 3, "soup", "Study Soup") }
            Village.Queue.BASE, Village.Queue.STAR_LAB -> boost("clocktower_boost", 9, "clock-tower", "Clock Tower")
        }
        val helper = when (queue) { Village.Queue.BUILDERS -> 93000000; Village.Queue.LAB -> 93000001; else -> 0 }
        val helperDuration = bounded(item, "helper_timer", MAX_SECONDS)
        val cooldown = cooldowns[helper] ?: helperCooldown
        val recurring = if (item.has("helper_recurrent")) item.optBoolean("helper_recurrent", false) else item.optBoolean("recurrent_helper", false)
        val assignment = if (helper == 0 || (levels[helper] ?: 0) == 0) null else UpgradeTiming.Helper(levels.getValue(helper).toLong(), helperDuration, if (recurring && cooldown > 0) cooldown else -1, if (queue == Village.Queue.BUILDERS) "Builder's Apprentice" else "Lab Assistant")
        val plan = UpgradeTiming.Plan(work, windows, assignment)
        val finish = at + plan.secondsToFinish() * 1000
        val name = names.name(id)?.takeIf { it.isNotEmpty() } ?: "${queue.label} item #$id"
        if (out.size + count > 500) throw InvalidExport("Too many active upgrades in this export.")
        repeat(count) { out.add(Village.Upgrade(id, level + 1, name, queue, finish, item.optBoolean("extra", false), out.size, at, plan)) }
    }
    private fun bounded(obj: JSONObject, field: String, max: Long): Long {
        if (!obj.has(field)) return 0
        val value = obj.opt(field)
        if (value !is Number) throw InvalidExport("Invalid numeric field: $field")
        val d = value.toDouble()
        if (!d.isFinite() || d < 0 || d > max || d != floor(d)) throw InvalidExport("Out-of-range field: $field")
        return value.toLong()
    }
}

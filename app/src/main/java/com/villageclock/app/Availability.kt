package com.villageclock.app

import android.content.Context
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.floor

/** Exported deadlines only; never invents recurring availability alarms. */
object Availability {
    const val PREF = "notify-availability"
    class Entry(@JvmField val key: String, @JvmField val name: String, @JvmField val id: Int, @JvmField val level: Int, @JvmField val readyAt: Long) {
        fun status(now: Long): String = when { readyAt < 0 -> "Cooldown not supplied"; readyAt <= now -> "Ready · sync to confirm"; else -> "Ready in: " + DurationText.remaining(readyAt, now) }
    }
    @JvmStatic @Throws(JSONException::class) fun parse(raw: String, exportedAt: Long): List<Entry> {
        val root = JSONObject(raw)
        val result = mutableListOf<Entry>()
        val seen = mutableSetOf<Int>()
        val helpers = root.optJSONArray("helpers")
        if (helpers != null) for (i in 0 until helpers.length()) {
            val helper = helpers.optJSONObject(i) ?: continue
            val id = helper.optInt("data", -1)
            if (id < 0 || !seen.add(id)) continue
            result.add(Entry("helper-$id", ItemIdentities.helperName(id), id, helper.optInt("lvl"), deadline(helper, "helper_cooldown", exportedAt)))
        }
        val boosts = root.optJSONObject("boosts")
        var clock = boosts?.has("clocktower_cooldown") == true
        val buildings = root.optJSONArray("buildings2")
        if (buildings != null) for (i in 0 until buildings.length()) if (buildings.optJSONObject(i)?.optInt("data") == 1000039) clock = true
        if (clock) result.add(Entry("clock-tower", "Clock Tower", 1000039, -1, boosts?.let { deadline(it, "clocktower_cooldown", exportedAt) } ?: -1))
        return result
    }
    private fun deadline(obj: JSONObject, key: String, at: Long): Long {
        val number = obj.opt(key) as? Number ?: return -1
        val value = number.toDouble()
        return if (!value.isFinite() || value < 0 || value > 10L * 366 * 86400 || value != floor(value)) -1 else at + number.toLong() * 1000
    }
    @JvmStatic fun load(c: Context, village: Village): List<Entry> = try { parse(Store.raw(c, village.tag), village.exportedAt) } catch (_: Exception) { emptyList() }
    @JvmStatic fun cursor(c: Context, tag: String): Long {
        val key = "availability-through.${VillageArchive.canonical(tag)}"
        if (!Store.prefs(c).contains(key)) Store.prefs(c).edit().putLong(key, System.currentTimeMillis()).commit()
        return Store.prefs(c).getLong(key, System.currentTimeMillis())
    }
    @JvmStatic fun through(c: Context, tag: String, at: Long) { Store.prefs(c).edit().putLong("availability-through.${VillageArchive.canonical(tag)}", at).apply() }
}

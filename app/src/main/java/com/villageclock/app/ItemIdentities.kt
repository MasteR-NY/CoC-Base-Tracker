package com.villageclock.app

import android.content.Context
import org.json.JSONException
import org.json.JSONObject
import java.util.Collections

/** Versioned offline overlay; unknown IDs retain their own identity. */
class ItemIdentities @Throws(JSONException::class) constructor(raw: String) {
    class Entry(@JvmField val id: Int, data: JSONObject) {
        @JvmField val name: String = data.optString("name")
        @JvmField val shortName: String = data.optString("short_name", name)
        @JvmField val kind: String = data.optString("kind")
        @JvmField val icon: String = data.optString("icon")
        @JvmField val parent: Int = data.optInt("parent")
        @JvmField val cap: Int = data.optInt("cap")
        @JvmField val sharedArtwork: Boolean = data.optBoolean("shared_artwork")
    }
    private val entries = mutableMapOf<Int, Entry>()
    init { val data = JSONObject(raw).getJSONObject("items"); for (key in data.keys()) entries[key.toInt()] = Entry(key.toInt(), data.getJSONObject(key)) }
    fun item(id: Int): Entry? = entries[id]
    fun items(): Collection<Entry> = Collections.unmodifiableCollection(entries.values)
    companion object {
        @Volatile private var instance: ItemIdentities? = null
        @JvmStatic fun load(context: Context): ItemIdentities = instance ?: synchronized(ItemIdentities::class.java) {
            instance ?: (try { context.assets.open("item-identities.json").bufferedReader(Charsets.UTF_8).use { ItemIdentities(it.readText()) } }
            catch (_: Exception) { ItemIdentities("{\"items\":{}}") }).also { instance = it }
        }
        @JvmStatic fun helperName(id: Int): String = when (id) { 93000000 -> "Builder’s Apprentice"; 93000001 -> "Lab Assistant"; 93000002 -> "Alchemist"; 93000003 -> "Prospector"; else -> "Helper #$id" }
        @JvmStatic fun moduleSymbol(name: String): Int = when {
            name.contains("Hitpoints") -> R.drawable.module_health
            name.contains("Poison") -> R.drawable.module_poison
            name.contains("Seconds") || name.contains("Duration") -> R.drawable.module_time
            name.contains("Explosion") || name.contains("Splash") -> R.drawable.module_explosion
            else -> R.drawable.module_damage
        }
    }
}

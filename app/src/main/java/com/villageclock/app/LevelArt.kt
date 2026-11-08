package com.villageclock.app

import android.content.Context
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

/** Exact level mappings; never borrows artwork from a neighboring level. */
object LevelArt {
    private var items: JSONObject? = null
    private val cache = mutableMapOf<String, Int>()
    @Synchronized private fun items(c: Context): JSONObject {
        items?.let { return it }
        val loaded = try {
            c.assets.open("level-art.json").use { input ->
                val bytes = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) { val n = input.read(buffer); if (n == -1) break; if (bytes.size() + n > 2 * 1024 * 1024) throw IOException("Artwork index too large"); bytes.write(buffer, 0, n) }
                JSONObject(bytes.toString("UTF-8")).getJSONObject("items")
            }
        } catch (_: Exception) { JSONObject() }
        items = loaded
        return loaded
    }
    @Synchronized private fun resource(c: Context, key: String, level: String): Int = cache.getOrPut("$key:$level") {
        val name = items(c).optJSONObject(key)?.optString(level).orEmpty()
        if (name.isEmpty()) 0 else c.resources.getIdentifier(name, "drawable", c.packageName)
    }
    @JvmStatic fun icon(c: Context, dataId: Int, level: Int): Int = resource(c, dataId.toString(), level.toString())
    @JvmStatic fun currency(c: Context, name: String): Int = resource(c, "currency", name.lowercase(Locale.ROOT).replace(' ', '_').replace("glowing_ore", "glowy_ore"))
}

package com.villageclock.app

import android.content.Context
import org.json.JSONObject

class Catalog(context: Context) : ExportParser.Names {
    private val names = mutableMapOf<Int, String>()
    init {
        try {
            val data = context.assets.open("gamedata.json").bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
            for (key in data.keys()) names[key.toInt()] = data.getJSONObject(key).getString("n")
            for (item in Store.planner(context).items()) names[item.id] = item.name
            for (entry in ItemIdentities.load(context).items()) names[entry.id] = entry.name
        } catch (_: Exception) { /* Unknown IDs retain a visible, stable fallback. */ }
    }
    override fun name(id: Int): String? = names[id]
}

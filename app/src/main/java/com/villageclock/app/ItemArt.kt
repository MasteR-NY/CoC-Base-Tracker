package com.villageclock.app

import android.annotation.SuppressLint
import android.content.Context
import java.util.concurrent.ConcurrentHashMap

object ItemArt {
    private val icons = ConcurrentHashMap<Int, Int>()
    @JvmStatic fun icon(c: Context, dataId: Int, level: Int): Int = LevelArt.icon(c, dataId, level).takeIf { it != 0 } ?: icon(c, dataId)
    @JvmStatic @SuppressLint("DiscouragedApi") fun icon(c: Context, dataId: Int): Int = icons.getOrPut(dataId) {
        var id = c.resources.getIdentifier("item_$dataId", "drawable", c.packageName)
        ItemIdentities.load(c).item(dataId)?.icon?.takeIf { it.isNotEmpty() }?.let {
            val resolved = c.resources.getIdentifier(it, "drawable", c.packageName)
            if (resolved != 0) id = resolved
        }
        id.takeIf { it != 0 } ?: R.drawable.ic_village
    }
}

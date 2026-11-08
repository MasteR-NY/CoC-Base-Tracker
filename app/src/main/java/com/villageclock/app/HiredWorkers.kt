package com.villageclock.app

import android.widget.TextView

/** The export's extra flag identifies hired work, never a permanent slot. */
object HiredWorkers {
    @JvmStatic fun active(village: Village, queue: Village.Queue, now: Long): Boolean = village.upgrades.any { it.queue == queue && it.extraBuilder && !it.done(now) }
    @JvmStatic fun plannerRow(village: Village, row: UpgradePlanner.Row): Boolean = row.active() && village.upgrades.any { it.dataId == row.item.id && it.finishAt == row.finishAt && it.extraBuilder }
    @JvmStatic fun mark(text: TextView) {
        val marker = text.context.getDrawable(R.drawable.ic_hired_goblin)!!
        val side = (text.resources.displayMetrics.density * 14 + .5f).toInt()
        marker.setBounds(0, 0, side, side)
        text.setCompoundDrawables(null, null, marker, null)
        text.compoundDrawablePadding = (text.resources.displayMetrics.density * 3 + .5f).toInt()
        text.contentDescription = text.text.toString() + " Hired Goblin worker"
    }
}

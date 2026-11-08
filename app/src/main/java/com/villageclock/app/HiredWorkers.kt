package com.villageclock.app

import android.widget.TextView

/** The export's extra flag identifies hired work, never a permanent slot. */
object HiredWorkers {
    @JvmStatic fun active(village: Village, queue: Village.Queue, now: Long): Boolean = village.upgrades.any { it.queue == queue && it.extraBuilder && !it.done(now) }
    @JvmStatic fun plannerRow(village: Village, row: UpgradePlanner.Row): Boolean = row.active() && village.upgrades.any { it.dataId == row.item.id && it.finishAt == row.finishAt && it.extraBuilder }
    @JvmStatic fun mark(text: TextView) {
        text.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_hired_goblin, 0)
        text.compoundDrawablePadding = (text.resources.displayMetrics.density * 4 + .5f).toInt()
        text.contentDescription = text.text.toString() + " Hired Goblin worker"
    }
}

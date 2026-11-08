package com.villageclock.app

import java.util.Collections

class Village(@JvmField val tag: String, playerName: String, @JvmField val exportedAt: Long,
    @JvmField val townHall: Int, @JvmField val builders: Int, @JvmField val baseBuilders: Int,
    upgrades: List<Upgrade>, effects: List<ActiveEffects.Effect>) {
    enum class Queue(@JvmField val label: String, @JvmField val glyph: String) {
        BUILDERS("Builders", "B"), LAB("Laboratory", "L"), PETS("Pet House", "P"), BASE("Builder Base", "BB"), STAR_LAB("Star Laboratory", "SL")
    }
    class Upgrade @JvmOverloads constructor(@JvmField val dataId: Int, @JvmField val level: Int,
        @JvmField val name: String, @JvmField val queue: Queue, @JvmField val finishAt: Long,
        @JvmField val extraBuilder: Boolean, ordinal: Int, @JvmField val snapshotAt: Long = 0,
        @JvmField val timing: UpgradeTiming.Plan? = null) {
        @JvmField val key = "${queue.name}:$dataId:$level:$ordinal"
        fun done(now: Long): Boolean = finishAt <= now
        fun label(): String = "$name  → $level"
        fun elapsed(now: Long): Long = ((now - snapshotAt) / 1000).coerceAtLeast(0)
        fun hasBoosts(): Boolean = timing?.boosted() == true
        fun workText(now: Long): String = "Work left: " + DurationText.seconds(requireNotNull(timing).remainingWorkAtMillis((now - snapshotAt).coerceAtLeast(0)))
        fun nextWorkTickMillis(now: Long): Long {
            val millis = (now - snapshotAt).coerceAtLeast(0)
            val rate = requireNotNull(timing).speed(millis / 1000)
            val remainder = (rate % 1000) * (millis % 1000) % 1000
            val next = (999 - remainder) / rate + 1
            return maxOf(16, minOf(next, 1000 - millis % 1000))
        }
        fun etaText(now: Long): String = "Finishes in: " + DurationText.remaining(finishAt, now)
        fun boostText(now: Long): String {
            if (!hasBoosts()) return ""
            val plan = requireNotNull(timing)
            val elapsed = elapsed(now)
            val sources = plan.sources(elapsed)
            return "${plan.speed(elapsed)}× · " + if (sources.isEmpty()) "Normal speed" else sources.joinToString(" + ")
        }
    }
    @JvmField val playerName = AccountNames.clean(playerName)
    @JvmField val upgrades: List<Upgrade> = Collections.unmodifiableList(upgrades.sortedBy { it.finishAt })
    @JvmField val effects: List<ActiveEffects.Effect> = Collections.unmodifiableList(effects.toList())
    constructor(tag: String, exportedAt: Long, townHall: Int, builders: Int, baseBuilders: Int, upgrades: List<Upgrade>) : this(tag, "", exportedAt, townHall, builders, baseBuilders, upgrades)
    constructor(tag: String, playerName: String, exportedAt: Long, townHall: Int, builders: Int, baseBuilders: Int, upgrades: List<Upgrade>) : this(tag, playerName, exportedAt, townHall, builders, baseBuilders, upgrades, ActiveEffects.fromJobs(upgrades))
    fun running(queue: Queue, now: Long): Int = upgrades.count { it.queue == queue && !it.done(now) }
    fun running(now: Long): Int = upgrades.count { !it.done(now) }
    fun freeBuilders(now: Long): Int = (builders - upgrades.count { it.queue == Queue.BUILDERS && !it.extraBuilder && !it.done(now) }).coerceAtLeast(0)
    fun nextFinish(now: Long): Long = upgrades.firstOrNull { it.finishAt > now }?.finishAt ?: 0
    /** A hired Goblin never frees a regular slot; research queues are independent. */
    fun nextFree(queue: Queue, now: Long): Long {
        val capacity = when (queue) { Queue.BUILDERS -> builders; Queue.BASE -> baseBuilders; else -> 1 }
        if (capacity <= 0) return 0
        val busy = upgrades.filter { it.queue == queue && !it.extraBuilder && !it.done(now) }
        return if (busy.size >= capacity) busy.minOf { it.finishAt } else 0
    }
    fun forWidget(now: Long): List<Upgrade> = upgrades.filter { !it.done(now) } + upgrades.filter { it.done(now) }
}

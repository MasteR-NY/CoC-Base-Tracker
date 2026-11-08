package com.villageclock.app

import java.util.Collections

/** Finite boost windows and optional, explicitly assigned recurring helper work. */
object UpgradeTiming {
    const val HELPER_WORK_SECONDS = 3600L
    const val HELPER_DAY_SECONDS = 23 * 3600L
    class Boost @JvmOverloads constructor(@JvmField val family: String, @JvmField val extraSpeed: Long,
        @JvmField val remainingSeconds: Long, @JvmField val group: String = family, @JvmField val label: String = family)
    class Helper(level: Long, currentSeconds: Long, nextStartSeconds: Long, @JvmField val label: String) {
        @JvmField val level = level.coerceAtLeast(0)
        @JvmField val currentSeconds = currentSeconds.coerceAtLeast(0)
        @JvmField val nextStartSeconds = if (nextStartSeconds < 0) -1 else maxOf(currentSeconds, nextStartSeconds)
        fun activeSeconds(elapsed: Long): Long {
            var active = minOf(elapsed, currentSeconds)
            if (nextStartSeconds >= 0 && elapsed > nextStartSeconds) {
                val since = elapsed - nextStartSeconds
                active = add(active, add(multiply(since / HELPER_DAY_SECONDS, HELPER_WORK_SECONDS), minOf(since % HELPER_DAY_SECONDS, HELPER_WORK_SECONDS)))
            }
            return active
        }
        fun active(elapsed: Long): Boolean = level > 0 && (elapsed < currentSeconds || nextStartSeconds >= 0 && elapsed >= nextStartSeconds && (elapsed - nextStartSeconds) % HELPER_DAY_SECONDS < HELPER_WORK_SECONDS)
        fun nextChange(elapsed: Long): Long {
            if (elapsed < currentSeconds) return currentSeconds
            if (nextStartSeconds < 0) return Long.MAX_VALUE
            if (elapsed < nextStartSeconds) return nextStartSeconds
            val cycle = (elapsed - nextStartSeconds) / HELPER_DAY_SECONDS
            val start = add(nextStartSeconds, multiply(cycle, HELPER_DAY_SECONDS))
            return add(start, if (elapsed - start < HELPER_WORK_SECONDS) HELPER_WORK_SECONDS else HELPER_DAY_SECONDS)
        }
    }
    class Plan(@JvmField val workSeconds: Long, incoming: List<Boost>, @JvmField val helper: Helper?) {
        @JvmField val boosts: List<Boost>
        private val groups: Map<String, List<Boost>>
        init {
            require(workSeconds >= 0) { "Negative work" }
            val families = linkedMapOf<String, Boost>()
            for (boost in incoming) {
                if (boost.extraSpeed <= 0 || boost.remainingSeconds <= 0) continue
                if ((families[boost.family]?.remainingSeconds ?: Long.MIN_VALUE) < boost.remainingSeconds) families[boost.family] = boost
            }
            boosts = Collections.unmodifiableList(families.values.toList())
            groups = boosts.groupBy { it.group }
        }
        fun boosted(): Boolean = boosts.isNotEmpty() || helper != null && helper.level > 0 && (helper.currentSeconds > 0 || helper.nextStartSeconds >= 0)
        fun workDone(elapsed: Long): Long {
            val time = elapsed.coerceAtLeast(0)
            var work = time
            for (group in groups.values) {
                if (group.size == 1) { val boost = group[0]; work = add(work, multiply(minOf(time, boost.remainingSeconds), boost.extraSpeed)); continue }
                var previous = 0L
                for (end in group.map { minOf(time, it.remainingSeconds) }.toSortedSet()) {
                    val speed = group.filter { it.remainingSeconds >= end }.maxOfOrNull { it.extraSpeed } ?: 0
                    work = add(work, multiply(end - previous, speed))
                    previous = end
                }
            }
            if (helper != null) work = add(work, multiply(helper.activeSeconds(time), helper.level))
            return work
        }
        fun remainingWork(elapsed: Long): Long = (workSeconds - minOf(workSeconds, workDone(elapsed))).coerceAtLeast(0)
        /** Split products avoid overflow; progress within a real second stays smooth. */
        fun remainingWorkAtMillis(elapsedMillis: Long): Long {
            val time = elapsedMillis.coerceAtLeast(0)
            val second = time / 1000
            val part = time % 1000
            val remaining = remainingWork(second)
            val rate = speed(second)
            val partial = add(multiply(rate / 1000, part), (rate % 1000) * part / 1000)
            return remaining - minOf(remaining, partial)
        }
        fun speed(elapsed: Long): Long {
            val time = elapsed.coerceAtLeast(0)
            var speed = 1L
            for (group in groups.values) speed = add(speed, group.filter { time < it.remainingSeconds }.maxOfOrNull { it.extraSpeed } ?: 0)
            if (helper?.active(time) == true) speed = add(speed, helper.level)
            return speed
        }
        fun nextChange(elapsed: Long): Long = minOf(boosts.filter { it.remainingSeconds > elapsed }.minOfOrNull { it.remainingSeconds } ?: Long.MAX_VALUE, helper?.nextChange(elapsed) ?: Long.MAX_VALUE)
        fun sources(elapsed: Long): List<String> = buildList {
            for (group in groups.values) group.filter { elapsed < it.remainingSeconds }.maxByOrNull { it.extraSpeed }?.let { add(it.label) }
            if (helper != null) {
                if (helper.active(elapsed)) add(helper.label)
                else if (helper.nextStartSeconds >= 0) add(helper.label + " scheduled")
            }
        }
        /** Earliest real second that completes the exported work, including expiries. */
        fun secondsToFinish(): Long {
            var low = 0L
            var high = workSeconds
            while (low < high) { val middle = low + (high - low) / 2; if (workDone(middle) >= workSeconds) high = middle else low = middle + 1 }
            return low
        }
    }
    @JvmStatic fun secondsToFinish(exportedWorkSeconds: Long, incoming: List<Boost>): Long = Plan(exportedWorkSeconds, incoming, null).secondsToFinish()
    private fun add(a: Long, b: Long): Long = if (a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
    private fun multiply(a: Long, b: Long): Long = if (a != 0L && b > Long.MAX_VALUE / a) Long.MAX_VALUE else a * b
}

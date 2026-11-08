package com.villageclock.app

/** Shared, unambiguous durations; remaining time is rounded up. */
object DurationText {
    @JvmStatic fun seconds(seconds: Long): String {
        val value = seconds.coerceAtLeast(0)
        return "${value / 86400}d ${value % 86400 / 3600}h ${value % 3600 / 60}m ${value % 60}s"
    }
    @JvmStatic fun remaining(finishAt: Long, now: Long): String {
        val ms = (finishAt - now).coerceAtLeast(0)
        return seconds(ms / 1000 + if (ms % 1000 == 0L) 0 else 1)
    }
    @JvmStatic fun compactRemaining(end: Long, now: Long): String {
        val ms = (end - now).coerceAtLeast(0)
        val seconds = ms / 1000 + if (ms % 1000 == 0L) 0 else 1
        val days = seconds / 86400
        val hours = seconds % 86400 / 3600
        val minutes = seconds % 3600 / 60
        return buildString {
            if (days > 0) append("${days}d ")
            if (days > 0 || hours > 0) append("${hours}h ")
            if (days > 0 || hours > 0 || minutes > 0) append("${minutes}m ")
            append("${seconds % 60}s")
        }
    }
}

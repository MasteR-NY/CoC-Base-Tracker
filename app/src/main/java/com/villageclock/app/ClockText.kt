package com.villageclock.app

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Local finish clocks, separate from elapsed durations. */
object ClockText {
    const val PREF = "clock-format"
    @JvmStatic fun mode(c: Context): String = Store.prefs(c).getString(PREF, "device").let { if (it == "12" || it == "24") it else "device" }
    @JvmStatic fun settingLabel(c: Context): String = when (mode(c)) { "12" -> "12-Hour"; "24" -> "24-Hour"; else -> "Device Default" }
    @JvmStatic @JvmOverloads fun at(c: Context, end: Long, now: Long, multiline: Boolean = true): String {
        if (end <= 0) return ""
        val mode = mode(c)
        val twentyFour = mode == "24" || mode == "device" && DateFormat.is24HourFormat(c)
        val locale = c.resources.configuration.locales[0]
        val zone = ZoneId.systemDefault()
        val finish = Instant.ofEpochMilli(end).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val clock = finish.format(DateTimeFormatter.ofPattern(if (twentyFour) "HH:mm" else "h:mm a", locale))
        val day = finish.toLocalDate()
        if (day == today) return clock
        val date = if (day == today.plusDays(1)) "Tomorrow" else finish.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "MMM d" else "MMM d, yyyy", locale))
        return date + (if (multiline) "\n" else " · ") + clock
    }
}

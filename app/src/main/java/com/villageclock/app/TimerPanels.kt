package com.villageclock.app

import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.function.LongFunction
import java.util.function.LongSupplier

object TimerPanels {
    @JvmStatic fun effect(c: Context, effect: ActiveEffects.Effect): View {
        val row = LinearLayout(c).apply { id = R.id.active_effect_row; tag = effect.key; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(c, 7), dp(c, 5), dp(c, 7), dp(c, 5)); setBackgroundResource(R.drawable.row_bg) }
        row.addView(ImageView(c).apply { setImageResource(ActiveEffects.icon(c, effect)); contentDescription = effect.name; scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(dp(c, 28), dp(c, 30)))
        val left = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(c, 6), 0, dp(c, 4), 0) }
        left.addView(text(c, "${effect.name} ${effect.rate}", 13, true))
        left.addView(text(c, "", 11, false).also { CountdownText.bind(it, effect.endsAt, "Ended · sync to refresh", LongSupplier(System::currentTimeMillis), LongFunction { now -> DurationText.compactRemaining(effect.endsAt, now) }) })
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(text(c, "", 10, false).apply { gravity = Gravity.END }.also { CountdownText.bind(it, effect.endsAt, "Ended", LongSupplier(System::currentTimeMillis), LongFunction { now -> "Ends at\n" + ClockText.at(c, effect.endsAt, now) }) }, LinearLayout.LayoutParams(dp(c, 84), -2))
        row.setOnClickListener {
            val message = when {
                effect.event -> "Official event: 50% lower cost and duration for eligible Home Village upgrades. Supercharges and Crafted Defenses are excluded. Exported timers already include applicable reductions; no second discount is applied.\n\nSupercell’s November 1, 2026 announcement gives the end as November 16 at 08:00 UTC."
                effect.key == "lab_consumable" -> "Study Soup adds 3× work speed: 4× alone, or 27× with a Research/Pet Potion. Duration comes from your last export."
                else -> "Duration comes from your last export. Stacked doses extend the deadline. Builder Potion takes priority over Builder Bite while both are active. Sync after using another item."
            }
            AlertDialog.Builder(c).setTitle(effect.name).setMessage(message).setPositiveButton("Close", null).show()
        }
        return row
    }
    @JvmStatic fun nextFree(c: Context, village: Village, queue: Village.Queue, now: Long): View? {
        val finish = village.nextFree(queue, now)
        if (finish == 0L) return null
        val row = LinearLayout(c).apply { id = R.id.next_free_row; gravity = Gravity.CENTER_VERTICAL; setBackgroundResource(R.drawable.row_idle); setPadding(dp(c, 8), dp(c, 5), dp(c, 8), dp(c, 5)) }
        val left = text(c, "", 12, true)
        val right = text(c, "", 11, true).apply { gravity = Gravity.END }
        CountdownText.bind(left, finish, "Slot may be free · sync", LongSupplier(System::currentTimeMillis), LongFunction { time -> "Next free in " + DurationText.compactRemaining(finish, time) })
        CountdownText.bind(right, finish, ClockText.at(c, finish, now), LongSupplier(System::currentTimeMillis), LongFunction { time -> ClockText.at(c, finish, time) })
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(right, LinearLayout.LayoutParams(dp(c, 84), -2))
        return row
    }
    private fun text(c: Context, value: String, size: Int, bold: Boolean): TextView = TextView(c).apply { text = value; textSize = size.toFloat(); setTextColor(0xFFFFF6E6.toInt()); if (bold) typeface = c.resources.getFont(R.font.lilita_one) }
    private fun dp(c: Context, value: Int): Int = (c.resources.displayMetrics.density * value + .5f).toInt()
}

package com.villageclock.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.*

/** Offline guide; earlier releases expand individually. */
class WhatsNewActivity : Activity() {
    private lateinit var content: LinearLayout
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(0xFF24180F.toInt()) }
        content = column().apply { setPadding(dp(20), dp(24), dp(20), dp(24)) }
        scroll.addView(content); setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 30) { val bars = insets.getInsets(WindowInsets.Type.systemBars()); content.setPadding(dp(20) + bars.left, dp(24) + bars.top, dp(20) + bars.right, dp(24) + bars.bottom) }
            else content.setPadding(dp(20), dp(24) + insets.systemWindowInsetTop, dp(20), dp(24) + insets.systemWindowInsetBottom)
            insets
        }
        button(content, "‹ Back", false) { finish() }; space(content, 16)
        content.addView(text("What’s New", 30, GOLD, true)); content.addView(text(getString(R.string.app_name) + " v" + BuildConfig.VERSION_NAME, 15, TEXT, true)); space(content, 18)
        try {
            val notes = ReleaseNotes.load(this)
            val current = notes.current()
            ReleaseNotes.markSeen(this)
            addRelease(current, true); space(content, 20); content.addView(text("Earlier Releases", 21, GOLD, true)); space(content, 10)
            for (entry in notes.entries) if (entry.versionCode < current.versionCode) addRelease(entry, false)
        } catch (_: Exception) { content.addView(text("Release notes are not available for this build. You can view published notes on GitHub Releases.", 15, TEXT, false)) }
        space(content, 20)
        button(content, "View GitHub Releases", false) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.RELEASES_URL)).addCategory(Intent.CATEGORY_BROWSABLE)) }
            catch (_: ActivityNotFoundException) { Toast.makeText(this, "Open GitHub Releases in a browser: " + UpdateChecker.RELEASES_URL, Toast.LENGTH_LONG).show() }
        }
        space(content, 10); content.addView(text("This guide is included with the app and can be read offline.", 12, MUTED, false))
    }
    private fun addRelease(entry: ReleaseNotes.Entry, current: Boolean) {
        val card = column().apply { setPadding(dp(14), dp(14), dp(14), dp(14)); setBackgroundResource(R.drawable.card_bg) }
        val details = column()
        if (current) { card.addView(text("v${entry.version} · ${entry.title}", 22, GOLD, true)); space(card, 10) }
        else button(card, "v${entry.version} · ${entry.title}", false) { view ->
            val open = details.visibility != View.VISIBLE
            details.visibility = if (open) View.VISIBLE else View.GONE
            view.contentDescription = (if (open) "Collapse " else "Expand ") + "v${entry.version} release notes"
        }.apply { textSize = 16f; contentDescription = "Expand v${entry.version} release notes"; details.visibility = View.GONE }
        for (point in entry.highlights) { space(details, 10); details.addView(text(point.title, 17, TEXT, true)); space(details, 4); details.addView(text(point.body, 14, MUTED, false)) }
        if (entry.steps.isNotEmpty()) {
            space(details, 16); details.addView(text("How to Use It", 17, GOLD, true))
            for ((i, step) in entry.steps.withIndex()) { space(details, 7); details.addView(text("${i + 1}. $step", 14, TEXT, false)) }
        }
        card.addView(details); content.addView(card); space(content, 10)
    }
    private fun text(value: String, size: Int, color: Int, bold: Boolean): TextView = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(color); typeface = if (bold) resources.getFont(R.font.lilita_one) else Typeface.create("sans-serif", Typeface.NORMAL); setLineSpacing(dp(2).toFloat(), 1f) }
    private fun button(parent: LinearLayout, value: String, primary: Boolean, click: View.OnClickListener): Button = Button(this).apply { text = value; isAllCaps = false; textSize = 16f; typeface = resources.getFont(R.font.lilita_one); setTextColor(if (primary) 0xFF3B2508.toInt() else GOLD); setBackgroundResource(if (primary) R.drawable.btn_primary else R.drawable.btn_secondary); minHeight = dp(48); setPadding(dp(10), dp(5), dp(10), dp(5)); setOnClickListener(click); parent.addView(this, LinearLayout.LayoutParams(-1, -2)) }
    private fun column(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun space(parent: LinearLayout, height: Int) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height))) }
    private fun dp(value: Int): Int = (resources.displayMetrics.density * value + .5f).toInt()
    companion object { private val GOLD = 0xFFFFC531.toInt(); private val TEXT = 0xFFFFF6E6.toInt(); private val MUTED = 0xFFCDBDA5.toInt() }
}

package com.villageclock.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.*

/** Each launcher instance keeps its own account selection. */
class WidgetConfigActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); setResult(RESULT_CANCELED)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider != ComponentName(this, VillageWidget::class.java)) finish()
    }
    override fun onResume() {
        super.onResume()
        if (isFinishing) return
        val pad = (24 * resources.displayMetrics.density).toInt()
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFF24180F.toInt()) }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad * 2, pad, pad * 2) }
        scroll.addView(list); setContentView(scroll)
        list.addView(TextView(this).apply { text = "Choose this widget’s village"; setTextColor(0xFFFFC531.toInt()); textSize = 25f; typeface = resources.getFont(R.font.lilita_one) })
        list.addView(TextView(this).apply { text = "Each widget keeps its own selection, even when you switch villages in the app."; setTextColor(0xFFAAB9AB.toInt()); textSize = 14f; setPadding(0, pad / 2, 0, pad) })
        val villages = Store.all(this)
        for (village in villages) list.addView(Button(this).apply {
            isAllCaps = false; text = Store.title(this@WidgetConfigActivity, village) + " · TH ${village.townHall}\n" + Store.identity(this@WidgetConfigActivity, village); minHeight = pad * 3
            typeface = resources.getFont(R.font.lilita_one); setTextColor(0xFF3B2508.toInt()); setBackgroundResource(R.drawable.btn_primary)
            setOnClickListener { Store.bind(this@WidgetConfigActivity, widgetId, village.tag); VillageWidget.refresh(this@WidgetConfigActivity); setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)); finish() }
        })
        list.addView(Button(this).apply { isAllCaps = false; text = if (villages.isEmpty()) "Import your first village" else "Import another village"; typeface = resources.getFont(R.font.lilita_one); setTextColor(0xFFFFC531.toInt()); setBackgroundResource(R.drawable.btn_secondary); setOnClickListener { startActivity(Intent(this@WidgetConfigActivity, MainActivity::class.java)) } })
    }
}

package com.villageclock.app

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/** Lazy collection for Android 8–11 and unusually large imports. */
class UpgradeListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        return object : RemoteViewsFactory {
            private var jobs: List<Any> = emptyList()
            private var snapshot: Village? = null
            override fun onCreate() = onDataSetChanged()
            override fun onDataSetChanged() { snapshot = LiveTimerService.snapshot(this@UpgradeListService, id); jobs = VillageWidget.displayItems(snapshot, System.currentTimeMillis()) }
            override fun onDestroy() { jobs = emptyList() }
            override fun getCount(): Int = jobs.size
            override fun getViewAt(position: Int): RemoteViews? = if (position !in jobs.indices) null else VillageWidget.styledItem(this@UpgradeListService, snapshot, jobs, position, System.currentTimeMillis(), LiveTimerService.readable(this@UpgradeListService))
            override fun getLoadingView(): RemoteViews? = null
            override fun getViewTypeCount(): Int = 2
            override fun getItemId(position: Int): Long = if (position !in jobs.indices) -1 else VillageWidget.itemId(jobs[position])
            override fun hasStableIds(): Boolean = true
        }
    }
}

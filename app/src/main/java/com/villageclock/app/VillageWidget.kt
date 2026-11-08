package com.villageclock.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews

class VillageWidget : AppWidgetProvider() {
    override fun onReceive(c: Context, intent: Intent) {
        super.onReceive(c, intent)
        if (intent.action == PINNED) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val tag = intent.getStringExtra("tag")
            val info = AppWidgetManager.getInstance(c).getAppWidgetInfo(id)
            if (info?.provider == ComponentName(c, VillageWidget::class.java) && Store.load(c, tag) != null && tag != null) { Store.bind(c, id, tag); refresh(c) }
        }
    }
    override fun onDeleted(c: Context, ids: IntArray) { for (id in ids) { Store.removeWidget(c, id); LiveTimerService.forget(id) }; LiveTimerService.reconcile(c) }
    override fun onUpdate(c: Context, manager: AppWidgetManager, ids: IntArray) { Reminders.deliver(c); refresh(c); Reminders.schedule(c) }
    override fun onAppWidgetOptionsChanged(c: Context, manager: AppWidgetManager, id: Int, opts: Bundle) { refresh(c) }
    companion object {
        const val COC = "com.supercell.clashofclans"
        const val PINNED = "com.villageclock.app.WIDGET_PINNED"
        @JvmStatic fun open(c: Context, sync: Boolean, widgetId: Int, tag: String): PendingIntent = PendingIntent.getActivity(c, if (sync) 1 else 0, Intent(c, MainActivity::class.java).putExtra("sync", sync).putExtra("tag", tag).putExtra("widget-id", widgetId).setData(Uri.parse("villageclock://widget/$widgetId/" + if (sync) "sync" else "open")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        @JvmStatic fun configure(c: Context, id: Int): PendingIntent = PendingIntent.getActivity(c, id, Intent(c, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).setData(Uri.parse("villageclock://configure/$id")), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        @JvmStatic fun game(c: Context): PendingIntent = PendingIntent.getActivity(c, 2, (c.packageManager.getLaunchIntentForPackage(COC) ?: Intent(c, MainActivity::class.java)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        @JvmStatic @JvmOverloads fun refresh(c: Context, reconcileLiveTimers: Boolean = true) {
            val manager = AppWidgetManager.getInstance(c)
            for (id in manager.getAppWidgetIds(ComponentName(c, VillageWidget::class.java))) {
                val tag = Store.widgetTag(c, id)
                val village = Store.load(c, tag)
                LiveTimerService.cache(id, tag, village)
                manager.updateAppWidget(id, render(c, village, id, tag))
                if (LiveTimerService.readable(c) || Build.VERSION.SDK_INT < 31 || village != null && displayItems(village, System.currentTimeMillis()).size > 50) manager.notifyAppWidgetViewDataChanged(id, R.id.rows)
            }
            if (reconcileLiveTimers) LiveTimerService.reconcile(c)
        }
        @JvmStatic fun render(c: Context, village: Village?, widgetId: Int, tag: String): RemoteViews = render(c, village, widgetId, tag, LiveTimerService.readable(c), true)
        @JvmStatic fun render(c: Context, village: Village?, widgetId: Int, tag: String, readable: Boolean, liveAdapter: Boolean): RemoteViews {
            val now = System.currentTimeMillis()
            val view = RemoteViews(c.packageName, R.layout.widget)
            val title = SpannableStringBuilder(Store.title(c, village))
            if (village != null && village.townHall > 0) title.append(" TH${village.townHall}", ForegroundColorSpan(0xFFFFC531.toInt()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            view.setTextViewText(R.id.title, title)
            view.setTextViewText(R.id.account_identity, Store.identity(c, village))
            view.setOnClickPendingIntent(R.id.sync, open(c, true, widgetId, tag)); view.setOnClickPendingIntent(R.id.title, open(c, false, widgetId, tag))
            view.setOnClickPendingIntent(R.id.account_identity, open(c, false, widgetId, tag)); view.setOnClickPendingIntent(R.id.choose, configure(c, widgetId)); view.setOnClickPendingIntent(R.id.freshness, open(c, false, widgetId, tag))
            view.setEmptyView(R.id.rows, R.id.empty_text)
            val jobs = displayItems(village, now)
            if ((!readable || !liveAdapter) && Build.VERSION.SDK_INT >= 31 && jobs.size <= 50) {
                val items = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(2)
                for (i in jobs.indices) items.addItem(itemId(jobs[i]), styledItem(c, village, jobs, i, now, readable))
                view.setRemoteAdapter(R.id.rows, items.build())
            } else view.setRemoteAdapter(R.id.rows, Intent(c, UpgradeListService::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId).putExtra("tag", tag).setData(Uri.parse("villageclock://list/$widgetId/${Uri.encode(tag)}")))
            val tap = Intent(c, MainActivity::class.java).setAction("com.villageclock.app.OPEN_GAME").putExtra("tag", tag).setData(Uri.parse("villageclock://row/$widgetId"))
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            view.setPendingIntentTemplate(R.id.rows, PendingIntent.getActivity(c, widgetId, tap, flags))
            if (village == null) {
                view.setTextViewText(R.id.summary, "Your next upgrade starts here.")
                view.setTextViewText(R.id.empty_text, if (tag.isEmpty()) "Import a village, then tap ▾ to choose it." else "Village unavailable. Tap ▾ to select a saved village.")
                return view
            }
            view.setTextViewText(R.id.summary, "${village.running(now)} running" + (if (village.builders > 0) "  ·  ${village.freeBuilders(now)} builders free" else "") + "  ·  scroll for all")
            if (jobs.isEmpty()) view.setTextViewText(R.id.empty_text, "No upgrades in this export.\nStart an upgrade in Clash, then sync.")
            view.setTextViewText(R.id.freshness, "${village.running(now)} running · ${village.freeBuilders(now)} builders idle · synced ${Store.age(village.exportedAt, now)}")
            return view
        }
        private fun stableId(value: String): Long { var hash = -3750763034362895579L; for (ch in value) hash = (hash xor ch.code.toLong()) * 0x100000001b3L; return hash }
        @JvmStatic fun itemId(item: Any): Long = if (item is Village.Upgrade) itemId(item) else stableId("effect:" + (item as ActiveEffects.Effect).key)
        @JvmStatic fun itemId(upgrade: Village.Upgrade): Long = stableId(upgrade.key)
        @JvmStatic fun displayItems(village: Village?, now: Long): List<Any> = ActiveEffects.active(village, now) + displayJobs(village, now)
        @JvmStatic fun styledItem(c: Context, village: Village?, items: List<Any>, position: Int, now: Long, readable: Boolean): RemoteViews {
            val item = items[position]
            if (item is ActiveEffects.Effect) return RemoteViews(c.packageName, R.layout.effect_row).apply {
                setViewVisibility(R.id.effect_heading, if (position == 0) View.VISIBLE else View.GONE)
                setImageViewResource(R.id.effect_icon, ActiveEffects.icon(c, item)); setTextViewText(R.id.effect_name, "${item.name} ${item.rate}")
                setTextViewText(R.id.effect_remaining, DurationText.compactRemaining(item.endsAt, now)); setTextViewText(R.id.effect_end, "Ends at\n" + ClockText.at(c, item.endsAt, now))
                setViewVisibility(R.id.effect_remaining, if (readable) View.VISIBLE else View.GONE); setViewVisibility(R.id.effect_timer, if (readable) View.GONE else View.VISIBLE)
                setChronometer(R.id.effect_timer, SystemClock.elapsedRealtime() + item.endsAt - now, null, !readable); setChronometerCountDown(R.id.effect_timer, true); setOnClickFillInIntent(R.id.effect_body, Intent())
            }
            val previous = items.getOrNull(position - 1) as? Village.Upgrade
            val upgrade = item as Village.Upgrade
            val first = previous == null || group(previous.queue) != group(upgrade.queue)
            return decorate(c, requireNotNull(village), upgrade, first, now, readable)
        }
        @JvmStatic fun row(c: Context, u: Village.Upgrade, now: Long): RemoteViews = row(c, u, now, now)
        @JvmStatic fun row(c: Context, u: Village.Upgrade, now: Long, snapshotAt: Long): RemoteViews = row(c, u, now, snapshotAt, LiveTimerService.readable(c))
        @JvmStatic @JvmOverloads fun row(c: Context, u: Village.Upgrade, now: Long, snapshotAt: Long, readable: Boolean, activityWork: Boolean = false): RemoteViews = RemoteViews(c.packageName, R.layout.widget_row).apply {
            val done = u.done(now)
            val boosted = readable && !done && u.hasBoosts()
            setImageViewResource(R.id.row_icon, ItemArt.icon(c, u.dataId, u.level)); setTextViewText(R.id.row_name, "${u.name} → ${u.level}")
            setViewVisibility(R.id.goblin_tag, if (u.extraBuilder) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.row_bar, if (done) View.GONE else View.VISIBLE); setViewVisibility(R.id.row_bar_done, if (done) View.VISIBLE else View.GONE); setViewVisibility(R.id.row_clock, if (done) View.GONE else View.VISIBLE)
            setProgressBar(R.id.row_bar, 1000, progress(snapshotAt, u.finishAt, now), false)
            setViewVisibility(R.id.readable_timer, if (!done && readable) View.VISIBLE else View.GONE); setViewVisibility(R.id.row_boost, View.GONE); setViewVisibility(R.id.row_eta, if (boosted && activityWork) View.VISIBLE else View.GONE)
            if (boosted) setTextViewText(R.id.row_eta, u.etaText(now))
            setTextViewText(R.id.row_finish_at, (if (done) "Ended at\n" else "Finishes at\n") + ClockText.at(c, u.finishAt, now))
            if (done) { setChronometer(R.id.timer, 0, null, false); setViewVisibility(R.id.timer, View.GONE); setViewVisibility(R.id.done, View.VISIBLE) }
            else {
                setViewVisibility(R.id.timer, if (readable) View.GONE else View.VISIBLE); setViewVisibility(R.id.done, View.GONE)
                setTextViewText(R.id.readable_timer, if (boosted && activityWork) u.workText(now) else DurationText.remaining(u.finishAt, now))
                setChronometer(R.id.timer, SystemClock.elapsedRealtime() + u.finishAt - now, null, !readable); setChronometerCountDown(R.id.timer, true)
            }
            setOnClickFillInIntent(R.id.row_root, Intent())
        }
        @JvmStatic fun progress(snapshot: Long, finish: Long, now: Long): Int = when { now >= finish -> 1000; now <= snapshot || finish <= snapshot -> 0; else -> minOf(999, (now - snapshot) * 1000 / (finish - snapshot)).toInt() }
        @JvmStatic fun group(queue: Village.Queue): Village.Queue = if (queue == Village.Queue.STAR_LAB) Village.Queue.BASE else queue
        @JvmStatic fun displayJobs(village: Village?, now: Long): List<Village.Upgrade> = if (village == null) emptyList() else listOf(Village.Queue.BUILDERS, Village.Queue.LAB, Village.Queue.PETS, Village.Queue.BASE).flatMap { queue -> village.forWidget(now).filter { group(it.queue) == queue } }
        @JvmStatic fun sectionTitle(village: Village, queue: Village.Queue, now: Long): String {
            val title = when (queue) {
                Village.Queue.BUILDERS -> "BUILDERS " + village.upgrades.count { it.queue == queue && !it.done(now) && !it.extraBuilder } + if (village.builders > 0) "/${village.builders}" else ""
                Village.Queue.LAB -> "LAB"
                Village.Queue.PETS -> "PETS"
                else -> "BUILDER BASE ${village.running(Village.Queue.BASE, now)}" + if (village.baseBuilders > 0) "/${village.baseBuilders}" else ""
            }
            return title + if (HiredWorkers.active(village, queue, now)) " +" else ""
        }
        @JvmStatic @JvmOverloads fun styledRow(c: Context, village: Village, jobs: List<Village.Upgrade>, position: Int, now: Long, readable: Boolean = LiveTimerService.readable(c)): RemoteViews {
            val upgrade = jobs[position]
            return decorate(c, village, upgrade, position == 0 || group(jobs[position - 1].queue) != group(upgrade.queue), now, readable)
        }
        private fun decorate(c: Context, village: Village, upgrade: Village.Upgrade, first: Boolean, now: Long, readable: Boolean): RemoteViews = row(c, upgrade, now, village.exportedAt, readable).apply {
            val queue = group(upgrade.queue)
            setViewVisibility(R.id.section_heading, if (first) View.VISIBLE else View.GONE)
            if (first) {
                setTextViewText(R.id.section_heading, sectionTitle(village, queue, now))
                setTextViewCompoundDrawables(R.id.section_heading, 0, 0, if (HiredWorkers.active(village, queue, now)) R.drawable.ic_hired_goblin else 0, 0)
            }
            if (first && queue == Village.Queue.BUILDERS && village.builders > 0 && village.freeBuilders(now) > 0) { setViewVisibility(R.id.idle_slots, View.VISIBLE); setTextViewText(R.id.idle_slots, "${village.freeBuilders(now)} builders idle") }
            val next = if (first) village.nextFree(queue, now) else 0
            if (next > 0) {
                setViewVisibility(R.id.availability_panel, View.VISIBLE); setTextViewText(R.id.availability_countdown, "Next free in " + DurationText.compactRemaining(next, now)); setTextViewText(R.id.availability_clock, ClockText.at(c, next, now))
                setViewVisibility(R.id.availability_countdown, if (readable) View.VISIBLE else View.GONE); setViewVisibility(R.id.availability_timer, if (readable) View.GONE else View.VISIBLE)
                setChronometer(R.id.availability_timer, SystemClock.elapsedRealtime() + next - now, "Next free in %s", !readable); setChronometerCountDown(R.id.availability_timer, true)
            }
        }
    }
}

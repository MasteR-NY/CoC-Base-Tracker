package com.villageclock.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Build;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public final class VillageWidget extends AppWidgetProvider {
    public static final String COC = "com.supercell.clashofclans";
    public static final String PINNED = "com.villageclock.app.WIDGET_PINNED";
    static PendingIntent open(Context c, boolean sync, int widgetId, String tag) {
        return PendingIntent.getActivity(c, sync ? 1 : 0, new Intent(c, MainActivity.class).putExtra("sync", sync).putExtra("tag", tag)
            .putExtra("widget-id", widgetId).setData(Uri.parse("villageclock://widget/" + widgetId + "/" + (sync ? "sync" : "open")))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static PendingIntent configure(Context c, int id) {
        return PendingIntent.getActivity(c, id, new Intent(c, WidgetConfigActivity.class)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).setData(Uri.parse("villageclock://configure/" + id)), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static PendingIntent game(Context c) {
        Intent intent = c.getPackageManager().getLaunchIntentForPackage(COC);
        if (intent == null) intent = new Intent(c, MainActivity.class);
        return PendingIntent.getActivity(c, 2, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    public static void refresh(Context c) {
        refresh(c, true);
    }
    static void refresh(Context c, boolean reconcileLiveTimers) {
        AppWidgetManager manager = AppWidgetManager.getInstance(c);
        for (int id : manager.getAppWidgetIds(new ComponentName(c, VillageWidget.class))) {
            String tag = Store.widgetTag(c, id);
            Village village = Store.load(c, tag);
            LiveTimerService.cache(id, tag, village);
            manager.updateAppWidget(id, render(c, village, id, tag));
            if (LiveTimerService.readable(c) || Build.VERSION.SDK_INT < 31 || (village != null && displayItems(village,System.currentTimeMillis()).size() > 50))
                manager.notifyAppWidgetViewDataChanged(id, R.id.rows);
        }
        if (reconcileLiveTimers) LiveTimerService.reconcile(c);
    }
    static RemoteViews render(Context c, Village village, int widgetId, String tag) {
        return render(c, village, widgetId, tag, LiveTimerService.readable(c), true);
    }
    static RemoteViews render(Context c, Village village, int widgetId, String tag, boolean readable, boolean liveAdapter) {
        long now = System.currentTimeMillis();
        RemoteViews view = new RemoteViews(c.getPackageName(), R.layout.widget);
        String name = Store.title(c, village);
        android.text.SpannableStringBuilder title = new android.text.SpannableStringBuilder(name);
        if (village != null && village.townHall > 0) title.append(" TH" + village.townHall, new android.text.style.ForegroundColorSpan(0xFFFFC531), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setTextViewText(R.id.title, title);
        view.setTextViewText(R.id.account_identity, Store.identity(c, village));
        view.setOnClickPendingIntent(R.id.sync, open(c, true, widgetId, tag));
        view.setOnClickPendingIntent(R.id.title, open(c, false, widgetId, tag));
        view.setOnClickPendingIntent(R.id.account_identity, open(c, false, widgetId, tag));
        view.setOnClickPendingIntent(R.id.choose, configure(c, widgetId));
        view.setOnClickPendingIntent(R.id.freshness, open(c, false, widgetId, tag));
        view.setEmptyView(R.id.rows, R.id.empty_text);
        List<Object> jobs = displayItems(village, now);
        if ((!readable || !liveAdapter) && Build.VERSION.SDK_INT >= 31 && jobs.size() <= 50) {
            RemoteViews.RemoteCollectionItems.Builder items = new RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(2);
            for (int i = 0; i < jobs.size(); i++) items.addItem(itemId(jobs.get(i)), styledItem(c, village, jobs, i, now, readable));
            view.setRemoteAdapter(R.id.rows, items.build());
        } else {
            Intent adapter = new Intent(c, UpgradeListService.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .putExtra("tag", tag).setData(Uri.parse("villageclock://list/" + widgetId + "/" + Uri.encode(tag)));
            view.setRemoteAdapter(R.id.rows, adapter);
        }
        Intent tap = new Intent(c, MainActivity.class).setAction("com.villageclock.app.OPEN_GAME").putExtra("tag", tag)
            .setData(Uri.parse("villageclock://row/" + widgetId));
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        view.setPendingIntentTemplate(R.id.rows, PendingIntent.getActivity(c, widgetId, tap, flags));
        if (village == null) {
            view.setTextViewText(R.id.summary, "Your next upgrade starts here.");
            view.setTextViewText(R.id.empty_text, tag.isEmpty() ? "Import a village, then tap ▾ to choose it." : "Village unavailable. Tap ▾ to select a saved village.");
            return view;
        }
        String summary = village.running(now) + " running";
        if (village.builders > 0) summary += "  ·  " + village.freeBuilders(now) + " builders free";
        summary += "  ·  scroll for all";
        view.setTextViewText(R.id.summary, summary);
        if (jobs.isEmpty()) {
            view.setTextViewText(R.id.empty_text, "No upgrades in this export.\nStart an upgrade in Clash, then sync.");
        }
        String freshness = village.running(now) + " running · " + village.freeBuilders(now) + " builders idle · synced " + Store.age(village.exportedAt, now);
        view.setTextViewText(R.id.freshness, freshness);
        return view;
    }
    static long itemId(Object item){if(item instanceof Village.Upgrade)return itemId((Village.Upgrade)item);long hash=0xcbf29ce484222325L;for(char ch:("effect:"+((ActiveEffects.Effect)item).key).toCharArray())hash=(hash^ch)*0x100000001b3L;return hash;}
    static java.util.List<Object> displayItems(Village village,long now){java.util.List<Object> items=new java.util.ArrayList<>(ActiveEffects.active(village,now));items.addAll(displayJobs(village,now));return items;}
    static RemoteViews styledItem(Context c,Village village,List<Object> items,int position,long now,boolean readable){
        Object item=items.get(position);
        if(item instanceof ActiveEffects.Effect){ActiveEffects.Effect effect=(ActiveEffects.Effect)item;RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.effect_row);row.setViewVisibility(R.id.effect_heading,position==0?View.VISIBLE:View.GONE);row.setImageViewResource(R.id.effect_icon,ActiveEffects.icon(c,effect));row.setTextViewText(R.id.effect_name,effect.name+" "+effect.rate);row.setTextViewText(R.id.effect_remaining,DurationText.compactRemaining(effect.endsAt,now));row.setTextViewText(R.id.effect_end,"Ends at\n"+ClockText.at(c,effect.endsAt,now));row.setViewVisibility(R.id.effect_remaining,readable?View.VISIBLE:View.GONE);row.setViewVisibility(R.id.effect_timer,readable?View.GONE:View.VISIBLE);row.setChronometer(R.id.effect_timer,SystemClock.elapsedRealtime()+effect.endsAt-now,null,!readable);row.setChronometerCountDown(R.id.effect_timer,true);row.setOnClickFillInIntent(R.id.effect_body,new Intent());return row;}
        Village.Upgrade upgrade=(Village.Upgrade)item;boolean first=position==0||!(items.get(position-1) instanceof Village.Upgrade)||group(((Village.Upgrade)items.get(position-1)).queue)!=group(upgrade.queue);
        return decorate(c,village,upgrade,first,now,readable);
    }
    static long itemId(Village.Upgrade u) {
        long hash = 0xcbf29ce484222325L;
        for (char ch : u.key.toCharArray()) hash = (hash ^ ch) * 0x100000001b3L;
        return hash;
    }
    static RemoteViews row(Context c, Village.Upgrade u, long now) {
        return row(c, u, now, now);
    }
    static RemoteViews row(Context c, Village.Upgrade u, long now, long snapshotAt) {
        return row(c, u, now, snapshotAt, LiveTimerService.readable(c));
    }
    static RemoteViews row(Context c, Village.Upgrade u, long now, long snapshotAt, boolean readable) {
        return row(c,u,now,snapshotAt,readable,false);
    }
    static RemoteViews row(Context c, Village.Upgrade u, long now, long snapshotAt, boolean readable,boolean activityWork) {
            RemoteViews row = new RemoteViews(c.getPackageName(), R.layout.widget_row);
            row.setImageViewResource(R.id.row_icon, ItemArt.icon(c, u.dataId,u.level));
            row.setTextViewText(R.id.row_name, u.name + " → " + u.level);
            row.setViewVisibility(R.id.goblin_tag, u.extraBuilder ? View.VISIBLE : View.GONE);
            row.setViewVisibility(R.id.row_bar, u.done(now) ? View.GONE : View.VISIBLE);
            row.setViewVisibility(R.id.row_bar_done, u.done(now) ? View.VISIBLE : View.GONE);
            row.setViewVisibility(R.id.row_clock, u.done(now) ? View.GONE : View.VISIBLE);
            row.setProgressBar(R.id.row_bar, 1000, progress(snapshotAt, u.finishAt, now), false);
            row.setViewVisibility(R.id.readable_timer, !u.done(now) && readable ? View.VISIBLE : View.GONE);
            boolean boosted = readable && !u.done(now) && u.hasBoosts();
            row.setViewVisibility(R.id.row_boost, View.GONE);
            row.setViewVisibility(R.id.row_eta, boosted && activityWork ? View.VISIBLE : View.GONE);
            if (boosted) row.setTextViewText(R.id.row_eta, u.etaText(now));
            row.setTextViewText(R.id.row_finish_at,(u.done(now)?"Ended at\n":"Finishes at\n")+ClockText.at(c,u.finishAt,now));
            if (u.done(now)) { row.setChronometer(R.id.timer, 0, null, false); row.setViewVisibility(R.id.timer, View.GONE); row.setViewVisibility(R.id.done, View.VISIBLE); }
            else {
                row.setViewVisibility(R.id.timer, readable ? View.GONE : View.VISIBLE); row.setViewVisibility(R.id.done, View.GONE);
                row.setTextViewText(R.id.readable_timer, boosted && activityWork ? u.workText(now) : DurationText.remaining(u.finishAt, now));
                // Native Chronometer ticks in the launcher; no second-by-second background task.
                row.setChronometer(R.id.timer, SystemClock.elapsedRealtime() + u.finishAt - now, null, !readable);
                row.setChronometerCountDown(R.id.timer, true);
            }
            row.setOnClickFillInIntent(R.id.row_root, new Intent());
            return row;
    }
    static int progress(long snapshot, long finish, long now) {
        if (now >= finish) return 1000;
        if (now <= snapshot || finish <= snapshot) return 0;
        return (int)Math.min(999, (now - snapshot) * 1000 / (finish - snapshot));
    }
    static Village.Queue group(Village.Queue queue) { return queue == Village.Queue.STAR_LAB ? Village.Queue.BASE : queue; }
    static List<Village.Upgrade> displayJobs(Village v, long now) {
        java.util.ArrayList<Village.Upgrade> result = new java.util.ArrayList<>();
        if (v == null) return result;
        for (Village.Queue group : new Village.Queue[]{Village.Queue.BUILDERS, Village.Queue.LAB, Village.Queue.PETS, Village.Queue.BASE})
            for (Village.Upgrade u : v.forWidget(now)) if (group(u.queue) == group) result.add(u);
        return result;
    }
    static String sectionTitle(Village v, Village.Queue queue, long now) {
        if (queue == Village.Queue.BUILDERS) {
            int regular = 0; boolean goblin = false;
            for (Village.Upgrade u : v.upgrades) if (u.queue == queue && !u.done(now)) { if (u.extraBuilder) goblin = true; else regular++; }
            return "BUILDERS " + regular + (v.builders > 0 ? "/" + v.builders : "") + (goblin ? " + Goblin" : "");
        }
        if (queue == Village.Queue.LAB) return "LAB";
        if (queue == Village.Queue.PETS) return "PETS";
        return "BUILDER BASE " + v.running(Village.Queue.BASE, now) + (v.baseBuilders > 0 ? "/" + v.baseBuilders : "");
    }
    static RemoteViews styledRow(Context c, Village village, List<Village.Upgrade> jobs, int position, long now) {
        return styledRow(c, village, jobs, position, now, LiveTimerService.readable(c));
    }
    static RemoteViews styledRow(Context c, Village village, List<Village.Upgrade> jobs, int position, long now, boolean readable) {
        Village.Upgrade u=jobs.get(position);boolean first=position==0||group(jobs.get(position-1).queue)!=group(u.queue);return decorate(c,village,u,first,now,readable);
    }
    private static RemoteViews decorate(Context c,Village village,Village.Upgrade u,boolean first,long now,boolean readable){
        RemoteViews row=row(c,u,now,village.exportedAt,readable);row.setViewVisibility(R.id.section_heading,first?View.VISIBLE:View.GONE);
        if (first) row.setTextViewText(R.id.section_heading, sectionTitle(village, group(u.queue), now));
        if (first && group(u.queue) == Village.Queue.BUILDERS && village.builders > 0 && village.freeBuilders(now) > 0) {
            row.setViewVisibility(R.id.idle_slots, View.VISIBLE);
            row.setTextViewText(R.id.idle_slots, village.freeBuilders(now) + " builders idle");
        }
        long next=first?village.nextFree(group(u.queue),now):0;
        if(next>0){row.setViewVisibility(R.id.availability_panel,View.VISIBLE);row.setTextViewText(R.id.availability_countdown,"Next free in "+DurationText.compactRemaining(next,now));row.setTextViewText(R.id.availability_clock,ClockText.at(c,next,now));row.setViewVisibility(R.id.availability_countdown,readable?View.VISIBLE:View.GONE);row.setViewVisibility(R.id.availability_timer,readable?View.GONE:View.VISIBLE);row.setChronometer(R.id.availability_timer,SystemClock.elapsedRealtime()+next-now,"Next free in %s",!readable);row.setChronometerCountDown(R.id.availability_timer,true);}
        return row;
    }
    @Override public void onReceive(Context c, Intent intent) {
        super.onReceive(c, intent);
        if (PINNED.equals(intent.getAction())) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            String tag = intent.getStringExtra("tag");
            android.appwidget.AppWidgetProviderInfo info = AppWidgetManager.getInstance(c).getAppWidgetInfo(id);
            if (info != null && new ComponentName(c, VillageWidget.class).equals(info.provider) && Store.load(c, tag) != null) { Store.bind(c, id, tag); refresh(c); }
        }
    }
    @Override public void onDeleted(Context c, int[] ids) { for (int id : ids) { Store.removeWidget(c, id); LiveTimerService.forget(id); } LiveTimerService.reconcile(c); }
    @Override public void onUpdate(Context c, AppWidgetManager manager, int[] ids) { Reminders.deliver(c); refresh(c); Reminders.schedule(c); }
    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager manager, int id, Bundle opts) { refresh(c); }
}

package com.villageclock.app;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import java.util.Collections;
import java.util.List;

/** Lazy loading for Android 8–11 and unusually large imports. */
public final class UpgradeListService extends RemoteViewsService {
    @Override public RemoteViewsFactory onGetViewFactory(Intent intent) {
        int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        return new RemoteViewsFactory() {
            private List<Village.Upgrade> jobs = Collections.emptyList();
            private Village snapshot;
            @Override public void onCreate() { onDataSetChanged(); }
            @Override public void onDataSetChanged() {
                Village village = LiveTimerService.snapshot(UpgradeListService.this, id);
                snapshot = village;
                jobs = VillageWidget.displayJobs(village, System.currentTimeMillis());
            }
            @Override public void onDestroy() { jobs = Collections.emptyList(); }
            @Override public int getCount() { return jobs.size(); }
            @Override public RemoteViews getViewAt(int position) { return position < 0 || position >= jobs.size() ? null : VillageWidget.styledRow(UpgradeListService.this, snapshot, jobs, position, System.currentTimeMillis()); }
            @Override public RemoteViews getLoadingView() { return null; }
            @Override public int getViewTypeCount() { return 1; }
            @Override public long getItemId(int position) { return position < 0 || position >= jobs.size() ? -1 : VillageWidget.itemId(jobs.get(position)); }
            @Override public boolean hasStableIds() { return true; }
        };
    }
}

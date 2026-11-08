package com.villageclock.app;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Each launcher instance stores its own village binding. */
public final class WidgetConfigActivity extends Activity {
    private int widgetId;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId);
        if (info == null || !new ComponentName(this, VillageWidget.class).equals(info.provider)) finish();
    }
    @Override public void onResume() {
        super.onResume();
        if (isFinishing()) return;
        int pad = (int)(24 * getResources().getDisplayMetrics().density);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(0xFF24180F);
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(pad, pad * 2, pad, pad * 2);
        scroll.addView(list); setContentView(scroll);
        TextView heading = new TextView(this); heading.setText("Choose this widget’s village"); heading.setTextColor(0xFFFFC531); heading.setTextSize(25); heading.setTypeface(getResources().getFont(R.font.lilita_one)); list.addView(heading);
        TextView detail = new TextView(this); detail.setText("Each widget keeps its own selection, even when you switch villages in the app."); detail.setTextColor(0xFFAAB9AB); detail.setTextSize(14); detail.setPadding(0, pad / 2, 0, pad); list.addView(detail);
        java.util.List<Village> villages = Store.all(this);
        for (Village village : villages) {
            Button choose = new Button(this); choose.setAllCaps(false); choose.setText(Store.title(this, village) + " · TH " + village.townHall + "\n" + Store.identity(this, village)); choose.setMinHeight(pad * 3);
            choose.setTypeface(getResources().getFont(R.font.lilita_one)); choose.setTextColor(0xFF3B2508); choose.setBackgroundResource(R.drawable.btn_primary);
            choose.setOnClickListener(v -> {
                Store.bind(this, widgetId, village.tag); VillageWidget.refresh(this);
                setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)); finish();
            });
            list.addView(choose);
        }
        Button open = new Button(this); open.setAllCaps(false); open.setText(villages.isEmpty() ? "Import your first village" : "Import another village");
        open.setTypeface(getResources().getFont(R.font.lilita_one)); open.setTextColor(0xFFFFC531); open.setBackgroundResource(R.drawable.btn_secondary);
        open.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class))); list.addView(open);
    }
}

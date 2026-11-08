package com.villageclock.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int BG = 0xFF24180F, CARD = 0xFF35251A, TEXT = 0xFFFFF6E6, MUTED = 0xFFCDBDA5, GOLD = 0xFFFFC531, GREEN = 0xFF8EE04A;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Runnable refresh = this::render;
    private LinearLayout content;
    private Village demo;
    private boolean pendingClipboard, importing, resumed;
    private String message = "";
    private String syncTarget = "";

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        createShell();
        handleIntent(getIntent());
    }
    private void createShell() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(24), dp(22), dp(24));
        scroll.addView(content);
        setContentView(scroll);
        View root = scroll;
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.ime());
                content.setPadding(dp(22) + bars.left, dp(24) + bars.top, dp(22) + bars.right, dp(24) + bars.bottom);
            } else content.setPadding(dp(22), dp(24) + insets.getSystemWindowInsetTop(), dp(22), dp(24) + insets.getSystemWindowInsetBottom());
            return insets;
        });
    }
    private void handleIntent(Intent intent) {
        if (intent == null) return;
        pendingClipboard = intent.getBooleanExtra("sync", false);
        String requestedTag = intent.getStringExtra("tag");
        if (Store.load(this, requestedTag) != null) Store.select(this, requestedTag);
        syncTarget = pendingClipboard && requestedTag != null ? requestedTag : "";
        intent.removeExtra("sync");
        if ("com.villageclock.app.OPEN_GAME".equals(intent.getAction())) { intent.setAction(Intent.ACTION_MAIN); handler.post(this::openGame); }
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            intent.setAction(Intent.ACTION_MAIN);
            if (text != null) importText(text);
        }
    }
    @Override public void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent); }
    @Override public void onResume() { super.onResume(); resumed = true; Reminders.deliver(this); VillageWidget.refresh(this); Reminders.schedule(this); render(); }
    @Override public void onPause() { resumed = false; handler.removeCallbacks(refresh); super.onPause(); }
    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); worker.shutdown(); super.onDestroy(); }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused && pendingClipboard) { pendingClipboard = false; handler.post(this::importClipboard); }
    }
    private void render() {
        if (isFinishing() || isDestroyed() || content == null) return;
        int scrollY = ((ScrollView)content.getParent()).getScrollY();
        handler.removeCallbacks(refresh);
        content.removeAllViews();
        Village village = demo != null ? demo : Store.load(this);
        long now = System.currentTimeMillis();
        content.addView(text(getString(R.string.app_name), 30, GOLD, true));
        gap(content, 14);
        if (demo == null && !Store.all(this).isEmpty()) {
            button(content, "Villages (" + Store.all(this).size() + ") · choose account", false, v -> chooseVillage());
            gap(content, 10);
        }
        if (demo != null) { content.addView(text("SAMPLE VILLAGE · PREVIEW ONLY", 12, GOLD, true)); gap(content, 8); }
        if (!message.isEmpty()) { gap(content, 14); TextView status = text(message, 13, GOLD, false); status.setPadding(dp(14), dp(12), dp(14), dp(12)); status.setBackground(shape(CARD, 12)); content.addView(status); }
        gap(content, 8);
        if (village == null) emptyState(); else {
            button(content, "Build Menu · what’s left to upgrade", true, v -> startActivity(new Intent(this, PlannerActivity.class)
                .putExtra("tag", village.tag).putExtra("sample", demo != null)));
            gap(content, 12);
            villageState(village, now);
        }
        gap(content, 16);
        button(content, importing ? "Importing…" : "Sync from clipboard / add account", true, v -> { syncTarget = ""; importClipboard(); }).setEnabled(!importing);
        gap(content, 8);
        LinearLayout actions = horizontal();
        Button widget = button(actions, "Add widget", false, v -> pinWidget());
        Button game = button(actions, "Open Clash ↗", false, v -> openGame());
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(50), 1); left.rightMargin = dp(5); widget.setLayoutParams(left);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(50), 1); right.leftMargin = dp(5); game.setLayoutParams(right);
        content.addView(actions);
        if (demo != null) { gap(content, 8); button(content, "Leave sample preview", false, v -> { demo = null; message = ""; render(); }); }
        else if (village == null) { gap(content, 8); button(content, "Explore a sample village", false, v -> { demo = sample(); message = "Sample only. Import your village to track real upgrades."; render(); }); }
        if (demo == null && village != null) settings(village);
        gap(content, 24);
        TextView note = text("Countdowns keep ticking when Clash is closed. Sync after new upgrades, early finishes, potions, or helper changes. Green bars show countdown progress since the last sync.", 12, MUTED, false);
        note.setLineSpacing(dp(3), 1); content.addView(note);
        gap(content, 16);
        content.addView(text("Independent fan companion. Not affiliated with or endorsed by Supercell. Item art © Supercell. Lilita One font: SIL OFL. No account login, ads, or internet access.", 11, MUTED, false));
        if (village != null && resumed) {
            long next = village.nextFinish(now);
            if (next > now) handler.postDelayed(refresh, Math.min(60000, Math.max(1000, next - now + 100)));
        }
        if (scrollY > 0) content.post(() -> ((ScrollView)content.getParent()).scrollTo(0, scrollY));
    }
    private void emptyState() {
        LinearLayout card = card(); card.setBackgroundResource(R.drawable.widget_bg);
        card.addView(text("Your village", 24, TEXT, true)); gap(card, 10);
        card.addView(text("Bring in your village once. Watch the timers tick every second, even after you close the game.", 14, MUTED, false));
        gap(card, 20);
        step(card, "01", "Open Clash of Clans", "Go to Settings → More Settings.");
        step(card, "02", "Copy your village data", "Find Export village data and tap Copy.");
        step(card, "03", "Come back and paste", "Tap Paste village export below.");
        content.addView(card);
    }
    private void step(LinearLayout parent, String number, String title, String detail) {
        LinearLayout row = horizontal(); row.setPadding(0, dp(8), 0, dp(8));
        TextView num = text(number, 12, GOLD, true); num.setWidth(dp(38)); row.addView(num);
        LinearLayout stack = vertical(); stack.addView(text(title, 14, TEXT, true)); stack.addView(text(detail, 12, MUTED, false));
        row.addView(stack, new LinearLayout.LayoutParams(0, -2, 1)); parent.addView(row);
    }
    private void villageState(Village village, long now) {
        LinearLayout panel = vertical(); panel.setBackgroundResource(R.drawable.widget_bg); panel.setPadding(dp(12), dp(10), dp(12), dp(9));
        LinearLayout header = horizontal();
        header.setBackgroundResource(R.drawable.build_header); header.setPadding(dp(6), dp(5), dp(6), dp(5));
        TextView villageTitle = text(Store.title(this, village) + "  TH" + village.townHall, 21, Color.WHITE, true);
        android.text.SpannableStringBuilder title = new android.text.SpannableStringBuilder(Store.title(this, village));
        title.append(" TH" + village.townHall, new android.text.style.ForegroundColorSpan(GOLD), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); villageTitle.setText(title);
        villageTitle.setMaxLines(1); villageTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        villageTitle.setShadowLayer(dp(1.5f), 0, dp(1.5f), 0xC0000000);
        header.addView(villageTitle, new LinearLayout.LayoutParams(0, -2, 1));
        Button sync = button(header, "↻ Sync", true, v -> { syncTarget = ""; importClipboard(); });
        sync.setTextSize(16); sync.setLayoutParams(new LinearLayout.LayoutParams(dp(78), dp(36)));
        panel.addView(header); gap(panel, 8);
        if (village.upgrades.isEmpty()) {
            panel.addView(text("No upgrades in this export. Start an upgrade in Clash, then sync again.", 14, TEXT, false));
        }
        for (Village.Queue queue : Village.Queue.values()) {
            ArrayList<Village.Upgrade> jobs = new ArrayList<>();
            for (Village.Upgrade u : village.upgrades) if (u.queue == queue) jobs.add(u);
            if (jobs.isEmpty()) continue;
            panel.addView(text(queue == Village.Queue.STAR_LAB ? "STAR LAB" : VillageWidget.sectionTitle(village, queue, now), 16, GOLD, true)); gap(panel, 4);
            if (queue == Village.Queue.BUILDERS && village.freeBuilders(now) > 0) {
                TextView idle = text(village.freeBuilders(now) + " builders idle", 14, GREEN, true);
                idle.setBackgroundResource(R.drawable.row_idle); idle.setPadding(dp(9), dp(5), dp(9), dp(5)); panel.addView(idle); gap(panel, 3);
            }
            for (Village.Upgrade u : jobs) { panel.addView(upgradeCard(u, now, village.exportedAt)); gap(panel, 3); }
            gap(panel, 8);
        }
        TextView footer = text("synced " + Store.age(village.exportedAt, now) + " · " + village.tag, 12, 0xFFEFE3CF, true);
        footer.setMaxLines(1); panel.addView(footer); content.addView(panel);
    }
    private LinearLayout upgradeCard(Village.Upgrade u, long now, long snapshotAt) {
        LinearLayout card = (LinearLayout)VillageWidget.row(this, u, now, snapshotAt).apply(this, new android.widget.FrameLayout(this));
        card.setOnClickListener(v -> openGame());
        return card;
    }
    private void stat(LinearLayout row, String value, String label) {
        LinearLayout block = vertical(); block.setPadding(0, 0, dp(8), 0); block.addView(text(value, 26, TEXT, true)); block.addView(text(label, 8, MUTED, true)); row.addView(block, new LinearLayout.LayoutParams(0, -2, 1));
    }
    private void settings(Village village) {
        gap(content, 22); LinearLayout panel = card();
        panel.addView(text("Settings", 18, GOLD, true)); gap(panel, 12);
        panel.addView(text(FastEvents.noteAt(village.exportedAt), 12, MUTED, false)); gap(panel, 8);
        panel.addView(text("Stacked copies extend boost duration. Potions, snacks, and the active helper contribute independently until each expires. Sync after changing boosts or helper assignments.", 11, MUTED, false)); gap(panel, 10);
        button(panel, "Village name: " + Store.title(this, village), false, v -> rename(village)); gap(panel, 10);
        Switch toggle = new Switch(this); toggle.setText("Notifications for all saved villages"); toggle.setTextColor(TEXT); toggle.setTextSize(13); toggle.setMinHeight(dp(48));
        toggle.setChecked(Store.prefs(this).getBoolean("notify", true));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            Store.prefs(this).edit().putBoolean("notify", checked).apply();
            if (checked) requestNotifications(); Reminders.schedule(this);
        }); panel.addView(toggle);
        if (!Reminders.canNotify(this) && Store.prefs(this).getBoolean("notify", true)) {
            button(panel, "Allow notifications", false, v -> requestNotifications()); gap(panel, 8);
        }
        panel.addView(text(Reminders.exact(this) ? "Precise timer alerts are enabled." : "Android may delay alerts and finished-widget updates. Allow precise timers for accurate timing.", 11, MUTED, false));
        if (Build.VERSION.SDK_INT >= 31 && !Reminders.exact(this)) { gap(panel, 8); button(panel, "Allow precise timers", false, v -> {
            try { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))); }
            catch (android.content.ActivityNotFoundException ex) { show("Precise alarm settings are unavailable on this device."); }
        }); }
        gap(panel, 12); button(panel, "Clear village data", false, v -> new AlertDialog.Builder(this).setTitle("Clear this village?")
            .setMessage("This removes only " + village.tag + ". Other villages and their widgets stay saved. Widgets showing this village will ask you to choose a village.").setNegativeButton("Keep village", null)
            .setPositiveButton("Clear", (d, w) -> { try { Store.clear(this); Reminders.schedule(this); VillageWidget.refresh(this); message = "Village removed. Other accounts were kept."; render(); } catch (java.io.IOException ex) { show("Could not remove the saved village."); } }).show());
        content.addView(panel);
    }
    private void importClipboard() {
        if (importing) return;
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        android.content.ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) { show("Copy your village export from Clash first."); return; }
        // Only accept actual text. Do not dereference arbitrary clipboard URIs.
        CharSequence value = clip.getItemAt(0).getText();
        if (value == null) { show("The clipboard does not contain a text export."); return; }
        if (value.length() > ExportParser.MAX_CHARS) { show("That export is too large (maximum 1 MB)."); return; }
        importText(value.toString());
    }
    private void importText(String text) {
        if (importing) return;
        importing = true; message = "Checking your village export…"; render();
        worker.execute(() -> {
            try {
                Village parsed = ExportParser.parse(text, Store.catalog(this), System.currentTimeMillis());
                Village previous = Store.load(this, parsed.tag);
                handler.post(() -> {
                    if (isDestroyed()) return;
                    if (!syncTarget.isEmpty() && !syncTarget.equalsIgnoreCase(parsed.tag)) {
                        importing = false; show("This widget is for " + syncTarget + ", but the clipboard contains " + parsed.tag + ". Copy the correct village, or use Paste / add account to save another account."); return;
                    }
                    if (previous != null && previous.tag.equalsIgnoreCase(parsed.tag) && parsed.exportedAt < previous.exportedAt) {
                        importing = false; show("That export is older than your saved village. Copy a fresh export from Clash."); return;
                    }
                    saveImport(text, parsed, false);
                });
            } catch (ExportParser.InvalidExport ex) { handler.post(() -> { importing = false; show(ex.getMessage()); }); }
        });
    }
    private void saveImport(String text, Village village, boolean switchVillage) {
        worker.execute(() -> {
            try {
                Store.save(this, text, village);
                getSystemService(android.app.NotificationManager.class).cancel(VillageArchive.canonical(village.tag), 7);
                VillageWidget.refresh(this); Reminders.schedule(this);
                handler.post(() -> {
                    if (isDestroyed()) return;
                    importing = false; demo = null;
                    message = "Synced " + village.tag + ". " + village.running(System.currentTimeMillis()) + " upgrades running.";
                    render();
                    if (!Store.prefs(this).getBoolean("asked-notify", false)) requestNotifications();
                });
            } catch (java.io.IOException ex) { handler.post(() -> { importing = false; show("Could not save the export. Your previous village is unchanged."); }); }
        });
    }
    private void requestNotifications() {
        Store.prefs(this).edit().putBoolean("asked-notify", true).apply();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (!Store.prefs(this).getBoolean("requested-notify", false) || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                Store.prefs(this).edit().putBoolean("requested-notify", true).apply(); requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9); return;
            }
        }
        if (!Reminders.canNotify(this)) {
            try { startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())); }
            catch (android.content.ActivityNotFoundException ex) { show("Notification settings are unavailable."); }
        }
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) { super.onRequestPermissionsResult(code, permissions, results); if (code == 9) { Reminders.schedule(this); render(); } }
    private void rename(Village village) {
        EditText input = new EditText(this); input.setSingleLine(true); input.setText(Store.title(this, village)); input.setSelectAllOnFocus(true);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32)});
        LinearLayout box = vertical(); box.setPadding(dp(20), dp(8), dp(20), 0); box.addView(input);
        new AlertDialog.Builder(this).setTitle("Village name").setView(box).setNegativeButton("Cancel", null)
            .setPositiveButton("Save", (d, w) -> { Store.rename(this, village.tag, input.getText().toString()); VillageWidget.refresh(this); render(); }).show();
    }
    private void chooseVillage() {
        java.util.List<Village> villages = Store.all(this);
        String[] names = new String[villages.size()];
        for (int i = 0; i < names.length; i++) names[i] = Store.title(this, villages.get(i)) + " · " + villages.get(i).tag;
        new AlertDialog.Builder(this).setTitle("Saved villages").setItems(names, (d, which) -> {
            Store.select(this, villages.get(which).tag); syncTarget = ""; message = "Viewing " + villages.get(which).tag + ". Your widget selections are unchanged."; render();
        }).setNegativeButton("Close", null).show();
    }
    private void pinWidget() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        java.util.List<Village> villages = Store.all(this);
        if (villages.isEmpty()) { show("Import a village first, then add a widget for it."); return; }
        String[] names = new String[villages.size()];
        for (int i = 0; i < names.length; i++) names[i] = Store.title(this, villages.get(i)) + " · " + villages.get(i).tag;
        new AlertDialog.Builder(this).setTitle("Village for the new widget").setItems(names, (d, which) -> {
            if (!manager.isRequestPinAppWidgetSupported()) { show("Long-press your home screen, choose Widgets, then find " + getString(R.string.app_name) + ". Choose this widget’s village when prompted."); return; }
            String tag = villages.get(which).tag;
            Intent callback = new Intent(this, VillageWidget.class).setAction(VillageWidget.PINNED).putExtra("tag", tag).setData(Uri.parse("villageclock://pin/" + Uri.encode(tag)));
            int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? android.app.PendingIntent.FLAG_MUTABLE : 0);
            android.app.PendingIntent pinned = android.app.PendingIntent.getBroadcast(this, tag.hashCode(), callback, flags);
            manager.requestPinAppWidget(new ComponentName(this, VillageWidget.class), null, pinned);
        }).setNegativeButton("Cancel", null).show();
    }
    private void openGame() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(VillageWidget.COC);
        if (intent == null) { show("Clash of Clans is not installed in this Android profile."); return; }
        try { startActivity(intent); } catch (android.content.ActivityNotFoundException ex) { show("Could not open Clash of Clans."); }
    }
    private void show(String text) { if (isDestroyed()) return; message = text; Toast.makeText(this, text, Toast.LENGTH_LONG).show(); render(); }
    private Village sample() {
        long now = System.currentTimeMillis(); ArrayList<Village.Upgrade> jobs = new ArrayList<>();
        jobs.add(new Village.Upgrade(1000009, 19, "Archer Tower", Village.Queue.BUILDERS, now + 27 * 3600000L + 42 * 60000L, false, 0));
        jobs.add(new Village.Upgrade(1000004, 16, "Gold Mine", Village.Queue.BUILDERS, now + 3 * 3600000L + 18 * 60000L, false, 1));
        jobs.add(new Village.Upgrade(4000059, 6, "Electro Dragon", Village.Queue.LAB, now + 2 * 86400000L, false, 2));
        jobs.add(new Village.Upgrade(73000000, 8, "L.A.S.S.I", Village.Queue.PETS, now + 7 * 3600000L, false, 3));
        jobs.add(new Village.Upgrade(1000034, 10, "Builder Hall", Village.Queue.BASE, now + 19 * 3600000L, false, 4));
        return new Village("#SAMPLE", now - 12 * 3600000L, 15, 6, 2, jobs);
    }
    private String shortDuration(long ms) { long minutes = Math.max(1, ms / 60000); return minutes >= 1440 ? (minutes / 1440) + "d " + (minutes % 1440 / 60) + "h" : minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m"; }
    private int dp(float n) { return (int)(getResources().getDisplayMetrics().density * n + 0.5f); }
    private TextView text(String s, int size, int color, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); t.setTypeface(bold ? getResources().getFont(R.font.lilita_one) : Typeface.create("sans-serif", Typeface.NORMAL)); t.setLineSpacing(dp(2), 1); return t; }
    private LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout horizontal() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private LinearLayout card() { LinearLayout c = vertical(); c.setPadding(dp(16), dp(16), dp(16), dp(16)); c.setBackgroundResource(R.drawable.card_bg); return c; }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(-2, -2); }
    private void gap(LinearLayout parent, int size) { View gap = new View(this); parent.addView(gap, new LinearLayout.LayoutParams(1, dp(size))); }
    private Button button(LinearLayout parent, String label, boolean primary, View.OnClickListener click) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(primary ? 18 : 16); b.setTypeface(getResources().getFont(R.font.lilita_one));
        b.setTextColor(primary ? 0xFF3B2508 : GOLD); b.setBackgroundResource(primary ? R.drawable.btn_primary : R.drawable.btn_secondary); b.setMinHeight(dp(50)); b.setPadding(dp(12), dp(4), dp(12), dp(4)); b.setOnClickListener(click);
        parent.addView(b, new LinearLayout.LayoutParams(-1, dp(50))); return b;
    }
}

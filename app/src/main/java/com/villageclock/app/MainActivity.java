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
    private final ExecutorService updateWorker = Executors.newSingleThreadExecutor();
    private final Runnable refresh = this::render;
    private LinearLayout content;
    private Village demo;
    private boolean pendingClipboard, importing, resumed, settingsExpanded;
    private String message = "";
    private String syncTarget = "";
    private boolean checkingUpdates, updateResultPending;
    private String updateStatus = "", pendingUpdateError;
    private UpdateChecker.Release pendingRelease;
    private boolean clipboardListening,autoChecking;
    private final Runnable autoClipboard=this::tryAutoClipboard;
    private final ClipboardManager.OnPrimaryClipChangedListener clipboardChanged=()->queueAutoClipboard();

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
    @Override public void onResume() { super.onResume(); resumed = true; updateClipboardListener(); Reminders.deliver(this); VillageWidget.refresh(this); Reminders.schedule(this); render(); if (updateResultPending) handler.post(this::deliverUpdateResult); }
    @Override public void onPause() { resumed = false; updateClipboardListener();handler.removeCallbacks(autoClipboard);handler.removeCallbacks(refresh); super.onPause(); }
    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); worker.shutdown(); updateWorker.shutdown(); super.onDestroy(); }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused && pendingClipboard) { pendingClipboard = false; handler.removeCallbacks(autoClipboard);handler.post(this::importClipboard); }
        else if(focused)queueAutoClipboard();else handler.removeCallbacks(autoClipboard);
    }
    private void render() {
        if (isFinishing() || isDestroyed() || content == null) return;
        int scrollY = ((ScrollView)content.getParent()).getScrollY();
        handler.removeCallbacks(refresh);
        content.removeAllViews();
        Village village = demo != null ? demo : Store.load(this);
        long now = System.currentTimeMillis();
        TextView appTitle = text(getString(R.string.app_title_version, getString(R.string.app_name), BuildConfig.VERSION_NAME), 30, GOLD, true);
        appTitle.setId(R.id.app_version_title); content.addView(appTitle);
        gap(content, 14);
        if (demo == null && ReleaseNotes.needsSummary(this)) releaseSummary();
        if (demo == null && !Store.all(this).isEmpty()) {
            button(content, "Accounts (" + Store.all(this).size() + ") ▾", false, v -> chooseVillage());
            gap(content, 10);
        }
        if (demo != null) { content.addView(text("SAMPLE VILLAGE · PREVIEW ONLY", 12, GOLD, true)); gap(content, 8); }
        if (!message.isEmpty()) { gap(content, 14); TextView status = text(message, 13, GOLD, false); status.setPadding(dp(14), dp(12), dp(14), dp(12)); status.setBackground(shape(CARD, 12)); content.addView(status); }
        gap(content, 8);
        if (village == null) emptyState(); else {
            LinearLayout tools = horizontal();
            Button planner = button(tools, "Build Menu", true, v -> startActivity(new Intent(this, PlannerActivity.class)
                .putExtra("tag", village.tag).putExtra("sample", demo != null)));
            Button overview=button(tools,"Overview",false,v->startActivity(new Intent(this,OverviewActivity.class).putExtra("tag",village.tag).putExtra("sample",demo!=null)));overview.setId(R.id.open_overview);
            Button boosts = button(tools, "Boosts", false, v -> showBoosts(village));
            for(Button action:new Button[]{planner,overview,boosts}){LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(50),1);params.setMargins(dp(2),0,dp(2),0);action.setLayoutParams(params);action.setTextSize(14);action.setPadding(dp(3),0,dp(3),0);}content.addView(tools);
            gap(content, 12);
            villageState(village, now);
        }
        gap(content, 16);
        button(content, importing ? "Importing…" : "Sync / Add Account", true, v -> { syncTarget = ""; importClipboard(); }).setEnabled(!importing);
        gap(content, 8);
        LinearLayout actions = horizontal();
        Button widget = button(actions, "Add widget", false, v -> pinWidget());
        Button game = button(actions, "Open Clash ↗", false, v -> openGame());
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(50), 1); left.rightMargin = dp(5); widget.setLayoutParams(left);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(50), 1); right.leftMargin = dp(5); game.setLayoutParams(right);
        content.addView(actions);
        if (demo != null) { gap(content, 8); button(content, "Leave sample preview", false, v -> { demo = null; message = ""; render(); }); }
        else if (village == null) { gap(content, 8); button(content, "Explore a sample village", false, v -> { demo = sample(); message = ""; render(); }); }
        if (demo == null) settings(village);
        gap(content, 12);button(content, "Help & About", false, v -> showHelp());gap(content, 8);
        Button whatsNew = button(content, "What’s New", false, v -> openReleaseGuide());
        whatsNew.setId(R.id.whats_new); gap(content, 8);
        Button updates = button(content, checkingUpdates ? "Checking for Updates…" : "Check for Updates", false, v -> checkForUpdates());
        updates.setId(R.id.check_updates); updates.setEnabled(!checkingUpdates);
        TextView updateNote = text(updateStatus, 12, MUTED, false);
        updateNote.setId(R.id.update_status);updateNote.setVisibility(updateStatus.isEmpty()?View.GONE:View.VISIBLE);gap(content, 6);content.addView(updateNote);
        if (village != null && resumed) {
            long next = village.nextFinish(now);
            if (next > now) handler.postDelayed(refresh, Math.min(60000, Math.max(1000, next - now + 100)));
        }
        if (scrollY > 0) content.post(() -> ((ScrollView)content.getParent()).scrollTo(0, scrollY));
    }
    private void emptyState() {
        LinearLayout card = card(); card.setBackgroundResource(R.drawable.widget_bg);
        card.addView(text("Your village", 24, TEXT, true)); gap(card, 10);
        card.addView(text("In Clash, copy Settings → More Settings → Export village data, then tap Sync / Add Account here.", 14, MUTED, false));
        content.addView(card);
    }
    private void releaseSummary() {
        try {
            ReleaseNotes.Entry release = ReleaseNotes.load(this).current();
            LinearLayout card = card(); card.setId(R.id.release_summary);
            card.addView(text("What’s New in v" + release.version, 22, GOLD, true)); gap(card, 8);
            card.addView(text(release.title, 14, TEXT, false));
            gap(card, 7);
            LinearLayout actions=horizontal();
            Button read = button(actions, "Read Guide", true, v -> openReleaseGuide()); read.setId(R.id.release_summary_open);
            Button dismiss = button(actions, "Got It", false, v -> { ReleaseNotes.markSeen(this); render(); }); dismiss.setId(R.id.release_summary_dismiss);
            equalButtons(read,dismiss);card.addView(actions);
            content.addView(card); gap(content, 14);
        } catch (Exception unavailable) { /* The manual guide can still offer the release-page fallback. */ }
    }
    private void openReleaseGuide() {
        startActivity(new Intent(this, WhatsNewActivity.class)); ReleaseNotes.markSeen(this); render();
    }
    private void checkForUpdates() {
        if (checkingUpdates) return;
        checkingUpdates = true; updateStatus = "Checking the latest GitHub release…"; render();
        updateWorker.execute(() -> {
            UpdateChecker.Release release = null; String error = null;
            try { release = UpdateChecker.fetchLatest(BuildConfig.VERSION_NAME); }
            catch (UpdateChecker.CheckException failure) { error = failure.getMessage(); }
            catch (Exception failure) { error = UpdateChecker.failureDetail(failure); }
            UpdateChecker.Release result = release; String failure = error;
            handler.post(() -> finishUpdateCheck(result, failure));
        });
    }
    void finishUpdateCheck(UpdateChecker.Release release, String error) {
        if (isDestroyed() || isFinishing()) return;
        checkingUpdates = false; pendingRelease = release; pendingUpdateError = error; updateResultPending = true;
        if (resumed) deliverUpdateResult();
    }
    private void deliverUpdateResult() {
        if (!resumed || !updateResultPending || isDestroyed() || isFinishing()) return;
        updateResultPending = false;
        if (pendingUpdateError != null || pendingRelease == null) {
            String reason = pendingUpdateError == null ? "Couldn’t read the release information." : pendingUpdateError;
            updateStatus = "Update check failed. Retry or open Releases.";
            render();
            new AlertDialog.Builder(this).setTitle("Update Check Unavailable").setMessage(reason)
                .setPositiveButton("Retry", (d,w) -> checkForUpdates()).setNegativeButton("Close", null).setNeutralButton("Open Releases", (d,w) -> openReleases()).show();
            return;
        }
        try {
            int comparison = UpdateChecker.compare(pendingRelease.version, BuildConfig.VERSION_NAME);
            if (comparison > 0) {
                updateStatus = "Update available: v" + pendingRelease.version + (!pendingRelease.apkStatusKnown ? ". Check Assets for the APK." : pendingRelease.hasApk ? "." : ". The APK has not been uploaded yet.");
                render(); Toast.makeText(this, updateStatus + " Opening GitHub Releases…", Toast.LENGTH_LONG).show(); openReleases();
            } else {
                boolean current = comparison == 0;
                updateStatus = current ? "You’re up to date · v" + BuildConfig.VERSION_NAME : "No newer release · installed v" + BuildConfig.VERSION_NAME + ", published v" + pendingRelease.version;
                render();
                new AlertDialog.Builder(this).setTitle(current ? "You’re Up to Date" : "No Update Available")
                    .setMessage(current ? "CoC Base Tracker v" + BuildConfig.VERSION_NAME + " is the latest published version."
                        : "You have v" + BuildConfig.VERSION_NAME + " installed. GitHub currently offers v" + pendingRelease.version + ". Your installed build is newer.")
                    .setPositiveButton("Close", null).show();
            }
        } catch (UpdateChecker.CheckException failure) {
            pendingUpdateError = failure.getMessage(); updateResultPending = true; deliverUpdateResult();
        }
    }
    private void openReleases() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.RELEASES_URL)).addCategory(Intent.CATEGORY_BROWSABLE)); }
        catch (android.content.ActivityNotFoundException unavailable) {
            new AlertDialog.Builder(this).setTitle("GitHub Releases").setMessage("Open this address in a browser:\n" + UpdateChecker.RELEASES_URL).setPositiveButton("Close", null).show();
        }
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
        TextView identity = text(Store.identity(this, village), 12, TEXT, false);
        identity.setMaxLines(2); panel.addView(identity); gap(panel, 6);
        if (demo == null && Store.inGameName(this, village).isEmpty()) {
            Button name = button(panel, "Set In-game Name", false, v -> editInGameName(village, () -> {}));
            name.setTextSize(15); name.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(42))); gap(panel, 8);
        }
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
        TextView footer = text("Synced " + Store.age(village.exportedAt, now), 12, 0xFFEFE3CF, true);
        footer.setMaxLines(1); panel.addView(footer); content.addView(panel);
    }
    private LinearLayout upgradeCard(Village.Upgrade u, long now, long snapshotAt) {
        LinearLayout card = (LinearLayout)VillageWidget.row(this, u, now, snapshotAt, true, true).apply(this, new android.widget.FrameLayout(this));
        if (!u.done(now)) {
            if (u.hasBoosts()) {
                CountdownText.bindWork(card.findViewById(R.id.readable_timer), u, "Done ✓");
                CountdownText.bind(card.findViewById(R.id.row_boost), u.finishAt, "", System::currentTimeMillis, u::boostText);
                CountdownText.bind(card.findViewById(R.id.row_eta), u.finishAt, "Sync to confirm completion", System::currentTimeMillis, u::etaText);
            } else CountdownText.bind(card.findViewById(R.id.readable_timer), u.finishAt, "Done ✓");
        }
        card.setOnClickListener(v -> openGame());
        return card;
    }
    private void stat(LinearLayout row, String value, String label) {
        LinearLayout block = vertical(); block.setPadding(0, 0, dp(8), 0); block.addView(text(value, 26, TEXT, true)); block.addView(text(label, 8, MUTED, true)); row.addView(block, new LinearLayout.LayoutParams(0, -2, 1));
    }
    private void settings(Village village) {
        gap(content, 12);Button settings=button(content,settingsExpanded?"Settings ▴":"Settings ▾",false,v->{settingsExpanded=!settingsExpanded;render();});settings.setId(R.id.settings_toggle);
        if(!settingsExpanded)return;
        gap(content,8);LinearLayout panel = card();panel.setId(R.id.settings_panel);
        Switch auto=new Switch(this);auto.setId(R.id.auto_sync);auto.setText("Auto-sync on return");auto.setTextColor(TEXT);auto.setTextSize(13);auto.setMinHeight(dp(48));auto.setChecked(ClipboardSync.enabled(this));
        auto.setOnCheckedChangeListener((button,checked)->{Store.prefs(this).edit().putBoolean(ClipboardSync.PREF,checked).apply();updateClipboardListener();if(checked)queueAutoClipboard();});panel.addView(auto);
        panel.addView(text("Copy in Clash, then return here to import automatically.",11,MUTED,false));gap(panel,12);
        Switch live = new Switch(this); live.setText("Live readable widget timers"); live.setTextColor(TEXT); live.setTextSize(13); live.setMinHeight(dp(48));
        live.setChecked(LiveTimerService.enabled(this));
        live.setOnCheckedChangeListener((button, checked) -> {
            Store.prefs(this).edit().putBoolean(LiveTimerService.PREF, checked).apply();
            VillageWidget.refresh(this);
            if (checked && !Reminders.canNotify(this)) requestNotifications();
        }); panel.addView(live);
        panel.addView(text("Ongoing notification · pauses while locked or screen off", 11, MUTED, false)); gap(panel, 12);
        if(village!=null){
            button(panel, "In-game Name: " + (Store.inGameName(this, village).isEmpty() ? "Not Set" : Store.inGameName(this, village)), false, v -> editInGameName(village, () -> {})); gap(panel, 10);
            button(panel, "Account Nickname: " + (Store.nickname(this, village.tag).isEmpty() ? "Not Set" : Store.nickname(this, village.tag)), false, v -> rename(village)); gap(panel, 10);
        }
        Switch toggle = new Switch(this); toggle.setText("Notifications for all saved villages"); toggle.setTextColor(TEXT); toggle.setTextSize(13); toggle.setMinHeight(dp(48));
        toggle.setChecked(Store.prefs(this).getBoolean("notify", true));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            Store.prefs(this).edit().putBoolean("notify", checked).apply();
            if (checked) requestNotifications(); Reminders.schedule(this);
        }); panel.addView(toggle);
        Switch availability=new Switch(this);availability.setId(R.id.availability_alerts);availability.setText("Helper & Clock Tower alerts");availability.setTextColor(TEXT);availability.setTextSize(13);availability.setMinHeight(dp(48));availability.setChecked(Store.prefs(this).getBoolean(Availability.PREF,true));availability.setOnCheckedChangeListener((b,on)->{Store.prefs(this).edit().putBoolean(Availability.PREF,on).apply();if(on)requestNotifications();Reminders.deliver(this);Reminders.schedule(this);});panel.addView(availability);
        if (!Reminders.canNotify(this) && Store.prefs(this).getBoolean("notify", true)) {
            button(panel, "Allow notifications", false, v -> requestNotifications()); gap(panel, 8);
        }
        panel.addView(text(Reminders.exact(this) ? "Precise alerts enabled" : "Precise alerts disabled", 11, MUTED, false));
        if (Build.VERSION.SDK_INT >= 31 && !Reminders.exact(this)) { gap(panel, 8); button(panel, "Allow precise timers", false, v -> {
            try { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))); }
            catch (android.content.ActivityNotFoundException ex) { show("Precise alarm settings are unavailable on this device."); }
        }); }
        if(village!=null){gap(panel, 12); button(panel, "Clear village data", false, v -> new AlertDialog.Builder(this).setTitle("Clear this village?")
            .setMessage("This removes only " + village.tag + ". Other villages and their widgets stay saved. Widgets showing this village will ask you to choose a village.").setNegativeButton("Keep village", null)
            .setPositiveButton("Clear", (d, w) -> { try { Store.clear(this); Reminders.schedule(this); VillageWidget.refresh(this); message = "Village removed. Other accounts were kept."; render(); } catch (java.io.IOException ex) { show("Could not remove the saved village."); } }).show());}
        content.addView(panel);
    }
    private void importClipboard() {
        if (importing) return;
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        android.content.ClipData clip;
        try{clip=clipboard.getPrimaryClip();}catch(SecurityException unavailable){show("Clipboard unavailable. Return to the app and try Sync again.");return;}
        if (clip == null || clip.getItemCount() == 0) { show("Copy your village export from Clash first."); return; }
        // Only accept actual text. Do not dereference arbitrary clipboard URIs.
        CharSequence value = clip.getItemAt(0).getText();
        if (value == null) { show("The clipboard does not contain a text export."); return; }
        if (value.length() > ExportParser.MAX_CHARS) { show("That export is too large (maximum 1 MB)."); return; }
        importText(value.toString());
    }
    private void updateClipboardListener(){
        ClipboardManager clipboard=getSystemService(ClipboardManager.class);
        if(clipboardListening){clipboard.removePrimaryClipChangedListener(clipboardChanged);clipboardListening=false;}
        if(resumed&&ClipboardSync.enabled(this)){clipboard.addPrimaryClipChangedListener(clipboardChanged);clipboardListening=true;}
    }
    private void queueAutoClipboard(){
        handler.removeCallbacks(autoClipboard);
        if(resumed&&hasWindowFocus()&&ClipboardSync.enabled(this)&&!pendingClipboard)handler.postDelayed(autoClipboard,180);
    }
    private void tryAutoClipboard(){
        if(!resumed||!hasWindowFocus()||!ClipboardSync.enabled(this)||demo!=null||importing||autoChecking||pendingClipboard||isDestroyed())return;
        String text;
        try{text=ClipboardSync.candidate(getSystemService(ClipboardManager.class).getPrimaryClip());}catch(SecurityException unavailable){return;}
        if(text==null)return;
        autoChecking=true;
        worker.execute(()->{
            String fingerprint=ClipboardSync.fingerprint(text);Village parsed=null;Village previous=null;
            if(!fingerprint.equals(Store.prefs(this).getString(ClipboardSync.LAST,"")))try{
                parsed=ExportParser.parse(text,Store.catalog(this),System.currentTimeMillis());previous=Store.load(this,parsed.tag);
            }catch(ExportParser.InvalidExport invalid){ /* Ignore non-exports quietly. */ }
            Village incoming=parsed,saved=previous;
            handler.post(()->{
                autoChecking=false;
                if(isDestroyed()||!resumed||!hasWindowFocus()||!ClipboardSync.enabled(this)||importing||demo!=null)return;
                Store.prefs(this).edit().putString(ClipboardSync.LAST,fingerprint).apply();
                if(incoming==null||!ClipboardSync.newer(incoming,saved))return;
                if(!syncTarget.isEmpty()&&!syncTarget.equalsIgnoreCase(incoming.tag))return;
                syncTarget="";importing=true;message="";render();saveImport(text,incoming,true);
            });
        });
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
    private void saveImport(String text, Village village, boolean automatic) {
        worker.execute(() -> {
            try {
                boolean firstImport = Store.load(this, village.tag) == null;
                Store.save(this, text, village);
                Store.prefs(this).edit().putString(ClipboardSync.LAST,ClipboardSync.fingerprint(text.trim())).apply();
                getSystemService(android.app.NotificationManager.class).cancel(VillageArchive.canonical(village.tag), 7);
                VillageWidget.refresh(this); Reminders.schedule(this);
                handler.post(() -> {
                    if (isDestroyed()) return;
                    importing = false; demo = null;
                    message = "";Toast.makeText(this,(automatic?"Auto-synced ":"Synced ")+Store.title(this,village),Toast.LENGTH_SHORT).show();
                    render();
                    Runnable next = () -> { if (!Store.prefs(this).getBoolean("asked-notify", false)) requestNotifications(); };
                    if (firstImport && Store.inGameName(this, village).isEmpty()) editInGameName(village, next); else next.run();
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
        EditText input = new EditText(this); input.setSingleLine(true); input.setText(Store.nickname(this, village.tag)); input.setSelectAllOnFocus(true);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32)});
        LinearLayout box = vertical(); box.setPadding(dp(20), dp(8), dp(20), 0); box.addView(input);
        new AlertDialog.Builder(this).setTitle("Account Nickname").setMessage("Optional label such as Main or Mini. Your in-game name stays separate.").setView(box).setNegativeButton("Cancel", null)
            .setPositiveButton("Save", (d, w) -> { Store.rename(this, village.tag, input.getText().toString()); VillageWidget.refresh(this); render(); }).show();
    }
    private void showBoosts(Village village) {
        long now = System.currentTimeMillis(); StringBuilder detail = new StringBuilder("From your latest village export. Use items in Clash, then sync here.\n\n");
        boolean any = false;
        for (Village.Upgrade u : village.upgrades) if (!u.done(now) && u.hasBoosts()) {
            any = true; detail.append(u.label()).append('\n').append(u.boostText(now)).append('\n')
                .append(u.workText(now)).append('\n').append(u.etaText(now)).append('\n');
            long change = u.timing.nextChange(u.elapsed(now));
            if (change != Long.MAX_VALUE) detail.append("Next speed change in: ").append(DurationText.remaining(u.snapshotAt + change * 1000, now)).append('\n');
            if (u.timing.helper != null && u.timing.helper.nextStartSeconds >= 0)
                detail.append("Repeat-helper forecast: 1-hour sessions on the exported 23-hour work-day schedule.\n");
            detail.append('\n');
        }
        if (!any) detail.append("No timed upgrade boosts are recorded for your running upgrades.\n\n");
        detail.append(FastEvents.noteAt(village.exportedAt)).append("\n\nPotions and Magic Snacks in storage do not change timers until used. Books, Hammers and gem finishes are reflected after a fresh export. Resource and combat boosts, Alchemist and Prospector do not speed up these upgrade queues. Repeating helpers are forecast only when the export marks that assignment and includes its cooldown.");
        new AlertDialog.Builder(this).setTitle("Boosts & Helpers").setMessage(detail.toString()).setPositiveButton("Close", null)
            .setNeutralButton("Open Clash", (d,w) -> openGame()).show();
    }
    private void editInGameName(Village village, Runnable after) {
        EditText input = new EditText(this); input.setSingleLine(true); input.setHint("Name shown on your Clash profile");
        input.setText(Store.inGameName(this, village)); input.setSelectAllOnFocus(true);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(64)});
        LinearLayout box = vertical(); box.setPadding(dp(20), dp(8), dp(20), 0); box.addView(input);
        new AlertDialog.Builder(this).setTitle("In-game Name · " + village.tag)
            .setMessage("Exports may omit the player name. Enter it once to identify this account in the app and widgets. Saved only on this phone; this does not rename your Clash account. Leave blank to use any exported name or saved nickname.")
            .setView(box).setNegativeButton("Later", (d,w) -> after.run()).setOnCancelListener(d -> after.run())
            .setPositiveButton("Save", (d,w) -> {
                Store.setInGameName(this, village.tag, input.getText().toString()); VillageWidget.refresh(this); render(); after.run();
            }).show();
    }
    private void chooseVillage() {
        java.util.List<Village> villages = Store.all(this);
        String[] names = new String[villages.size()];
        for (int i = 0; i < names.length; i++) names[i] = Store.accountLabel(this, villages.get(i));
        new AlertDialog.Builder(this).setTitle("Saved Accounts").setItems(names, (d, which) -> {
            Store.select(this, villages.get(which).tag); syncTarget = ""; message = ""; render();
        }).setNegativeButton("Close", null).show();
    }
    private void pinWidget() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        java.util.List<Village> villages = Store.all(this);
        if (villages.isEmpty()) { show("Import a village first, then add a widget for it."); return; }
        String[] names = new String[villages.size()];
        for (int i = 0; i < names.length; i++) names[i] = Store.accountLabel(this, villages.get(i));
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
    private void showHelp(){
        new AlertDialog.Builder(this).setTitle("Help & About").setMessage(
            "Sync Your Village\nIn Clash: Settings → More Settings → Export village data → Copy. Return here; Auto-sync on return imports fresh exports automatically while this app has focus. Turn it off in Settings for manual Sync / Add Account. Android does not let the tracker watch Clash’s Copy button in the background or redirect from it. Sync after upgrades, items or helper changes.\n\n"
            +"Timers & Boosts\nWork left is upgrade work; a 24× Research Potion makes it count down faster. Finishes in is the estimated real time to completion, including boost expiry. Widgets show real time remaining in steady seconds. Potions stack by extending duration. Review imported effects in Boosts & Helpers. Green bars show progress since export.\n\n"
            +"Widgets & Alerts\nEach widget keeps its own account. Live readable widget timers use an ongoing notification, pause while locked or screen off, and recalculate on unlock. Pause live updates to use Android’s native timer. Battery restrictions can delay widgets or alerts. Reopen the app after force-stop.\n\n"
            +"Privacy & Updates\nVillage data stays on this phone. No Supercell login, ads or analytics. Clipboard access happens when you tap Sync or while this app is focused with Auto-sync on return enabled. Unrelated text is ignored; clipboard URIs are never opened. Check for Updates contacts GitHub’s public API, with a public Releases fallback, only when tapped. No village or account data is sent.\n\n"
            +"Overview\nProgress compares exported levels and counts with Current Hall or Catalog Max targets. Crafting shows all exported modules. Helpers shows cooldown estimates and availability alerts; sync after use. Collection contains equipment and cosmetic records, using stable IDs when names are unavailable.\n\n"
            +"About\nCoC Base Tracker v"+BuildConfig.VERSION_NAME+" supports Android 8.0 and later. Independent fan companion; not affiliated with or endorsed by Supercell. Item art © Supercell, with additional offline artwork and identity data via ClashKing. Lilita One font: SIL OFL.")
            .setPositiveButton("Close",null).show();
    }
    private void equalButtons(Button left,Button right){
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(50),1);a.rightMargin=dp(4);left.setLayoutParams(a);
        LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,dp(50),1);b.leftMargin=dp(4);right.setLayoutParams(b);
        left.setTextSize(15);right.setTextSize(15);
    }
    private void show(String text) { if (isDestroyed()) return; message = text; Toast.makeText(this, text, Toast.LENGTH_LONG).show(); render(); }
    private Village sample() {
        long now = System.currentTimeMillis(); ArrayList<Village.Upgrade> jobs = new ArrayList<>();
        jobs.add(new Village.Upgrade(1000009, 19, "Archer Tower", Village.Queue.BUILDERS, now + 27 * 3600000L + 42 * 60000L, false, 0));
        jobs.add(new Village.Upgrade(1000004, 16, "Gold Mine", Village.Queue.BUILDERS, now + 3 * 3600000L + 18 * 60000L, false, 1));
        jobs.add(new Village.Upgrade(4000059, 6, "Electro Dragon", Village.Queue.LAB, now + 2 * 86400000L, false, 2));
        jobs.add(new Village.Upgrade(73000000, 8, "L.A.S.S.I", Village.Queue.PETS, now + 7 * 3600000L, false, 3));
        jobs.add(new Village.Upgrade(1000034, 10, "Builder Hall", Village.Queue.BASE, now + 19 * 3600000L, false, 4));
        return new Village("#SAMPLE", "Sample Chief", now - 12 * 3600000L, 15, 6, 2, jobs);
    }
    private String shortDuration(long ms) { return DurationText.remaining(ms, 0); }
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

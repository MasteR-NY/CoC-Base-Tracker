package com.villageclock.app;

import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.util.concurrent.ConcurrentHashMap;

/** User-selected foreground widget updates. Never holds a wake lock or opens a network. */
public final class LiveTimerService extends Service {
    static final String PREF = "live-widget-timers";
    static final String PAUSE = "com.villageclock.app.PAUSE_LIVE_TIMERS";
    private static final String CHANNEL = "live-widget-timers";
    private static final int NOTIFICATION = 16;
    private static volatile boolean running, starting;
    private static final ConcurrentHashMap<Integer, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean listening;
    private static final class Snapshot {
        final String tag; final Village village;
        Snapshot(String tag, Village village) { this.tag = tag; this.village = village; }
    }
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) { queueTick(); }
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!wanted(LiveTimerService.this)) { stopSelf(); return; }
            if (screenReady()) {
                AppWidgetManager manager = AppWidgetManager.getInstance(LiveTimerService.this);
                // Notify the existing adapter rather than replacing it: preserve home-screen scroll.
                manager.notifyAppWidgetViewDataChanged(widgetIds(LiveTimerService.this), R.id.rows);
                handler.postDelayed(this, 1000);
            }
        }
    };
    static int[] widgetIds(Context c) {
        return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, VillageWidget.class));
    }
    public static boolean enabled(Context c) { return Store.prefs(c).getBoolean(PREF, true); }
    static boolean readable(Context c) { return enabled(c) && running; }
    static void cache(int id, String tag, Village village) {
        if (village == null) snapshots.remove(id); else snapshots.put(id, new Snapshot(tag, village));
    }
    static void forget(int id) { snapshots.remove(id); }
    static Village snapshot(Context c, int id) {
        String tag = Store.widgetTag(c, id);
        Snapshot cached = snapshots.get(id);
        if (cached != null && cached.tag.equals(tag)) return cached.village;
        Village village = Store.load(c, tag); cache(id, tag, village); return village;
    }
    static boolean wanted(Context c) {
        if (!enabled(c)) return false;
        long now = System.currentTimeMillis();
        for (int id : widgetIds(c)) {
            Village village = snapshot(c, id);
            if (village != null && (village.running(now) > 0 || !ActiveEffects.active(village,now).isEmpty())) return true;
        }
        return false;
    }
    static void reconcile(Context c) {
        if (!wanted(c)) { c.stopService(new Intent(c, LiveTimerService.class)); return; }
        if (running || starting) return;
        starting = true;
        try { c.startForegroundService(new Intent(c, LiveTimerService.class)); }
        catch (IllegalStateException | SecurityException blockedByAndroid) {
            // Background-start restrictions: use the launcher-native timer until the app opens.
            starting = false;
        }
    }
    @Override public void onCreate() {
        super.onCreate();
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "Live widget countdowns", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 16, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pause = PendingIntent.getService(this, 17, new Intent(this, LiveTimerService.class).setAction(PAUSE), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification note = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_village)
            .setContentTitle("Live widget countdowns")
            .setContentText("Days, hours, minutes and seconds · pauses with screen off")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE).setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(new Notification.Action.Builder(null, "Pause live updates", pause).build()).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTIFICATION, note);
        running = true; starting = false;
        IntentFilter events = new IntentFilter(); events.addAction(Intent.ACTION_SCREEN_ON); events.addAction(Intent.ACTION_SCREEN_OFF); events.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, events, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screen, events);
        listening = true;
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && PAUSE.equals(intent.getAction())) {
            Store.prefs(this).edit().putBoolean(PREF, false).apply(); stopSelf(); return START_NOT_STICKY;
        }
        if (!wanted(this)) { stopSelf(); return START_NOT_STICKY; }
        VillageWidget.refresh(this, false);
        queueTick();
        return START_STICKY;
    }
    private boolean screenReady() {
        return getSystemService(PowerManager.class).isInteractive() && !getSystemService(KeyguardManager.class).isKeyguardLocked();
    }
    private void queueTick() { handler.removeCallbacks(tick); tick.run(); }
    @Override public void onDestroy() {
        running = false; starting = false; handler.removeCallbacksAndMessages(null);
        if (listening) unregisterReceiver(screen);
        stopForeground(STOP_FOREGROUND_REMOVE);
        // An honest native live countdown replaces custom text when the service stops.
        VillageWidget.refresh(this, false);
        snapshots.clear();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}

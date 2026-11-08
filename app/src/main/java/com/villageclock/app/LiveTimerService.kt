package com.villageclock.app

import android.app.*
import android.appwidget.AppWidgetManager
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import java.util.concurrent.ConcurrentHashMap

/** User-selected widget updates; no wake lock, clipboard monitoring or network. */
class LiveTimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var listening = false
    private val screen = object : BroadcastReceiver() { override fun onReceive(c: Context, intent: Intent) = queueTick() }
    private val tick = object : Runnable {
        override fun run() {
            if (!wanted(this@LiveTimerService)) { stopSelf(); return }
            if (screenReady()) { AppWidgetManager.getInstance(this@LiveTimerService).notifyAppWidgetViewDataChanged(widgetIds(this@LiveTimerService), R.id.rows); handler.postDelayed(this, 1000) }
        }
    }
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Live widget countdowns", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 16, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pause = PendingIntent.getService(this, 17, Intent(this, LiveTimerService::class.java).setAction(PAUSE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val note = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_village).setContentTitle("Live widget countdowns").setContentText("Days, hours, minutes and seconds · pauses with screen off").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).setCategory(Notification.CATEGORY_SERVICE).setVisibility(Notification.VISIBILITY_PRIVATE).addAction(Notification.Action.Builder(null, "Pause live updates", pause).build()).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(NOTIFICATION, note)
        running = true; starting = false
        val events = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, events, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(screen, events)
        listening = true
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == PAUSE) { Store.prefs(this).edit().putBoolean(PREF, false).apply(); stopSelf(); return START_NOT_STICKY }
        if (!wanted(this)) { stopSelf(); return START_NOT_STICKY }
        VillageWidget.refresh(this, false); queueTick(); return START_STICKY
    }
    private fun screenReady(): Boolean = getSystemService(PowerManager::class.java).isInteractive && !getSystemService(KeyguardManager::class.java).isKeyguardLocked
    private fun queueTick() { handler.removeCallbacks(tick); tick.run() }
    override fun onDestroy() {
        running = false; starting = false; handler.removeCallbacksAndMessages(null)
        if (listening) unregisterReceiver(screen)
        stopForeground(STOP_FOREGROUND_REMOVE)
        VillageWidget.refresh(this, false)
        snapshots.clear()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val PREF = "live-widget-timers"
        const val PAUSE = "com.villageclock.app.PAUSE_LIVE_TIMERS"
        private const val CHANNEL = "live-widget-timers"
        private const val NOTIFICATION = 16
        @Volatile private var running = false
        @Volatile private var starting = false
        private class Snapshot(val tag: String, val village: Village)
        private val snapshots = ConcurrentHashMap<Int, Snapshot>()
        @JvmStatic fun widgetIds(c: Context): IntArray = AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c, VillageWidget::class.java))
        @JvmStatic fun enabled(c: Context): Boolean = Store.prefs(c).getBoolean(PREF, true)
        @JvmStatic fun readable(c: Context): Boolean = enabled(c) && running
        @JvmStatic fun cache(id: Int, tag: String, village: Village?) { if (village == null) snapshots.remove(id) else snapshots[id] = Snapshot(tag, village) }
        @JvmStatic fun forget(id: Int) { snapshots.remove(id) }
        @JvmStatic fun snapshot(c: Context, id: Int): Village? {
            val tag = Store.widgetTag(c, id)
            val cached = snapshots[id]
            if (cached?.tag == tag) return cached.village
            return Store.load(c, tag).also { cache(id, tag, it) }
        }
        @JvmStatic fun wanted(c: Context): Boolean {
            if (!enabled(c)) return false
            val now = System.currentTimeMillis()
            return widgetIds(c).any { id -> snapshot(c, id)?.let { it.running(now) > 0 || ActiveEffects.active(it, now).isNotEmpty() } ?: false }
        }
        @JvmStatic fun reconcile(c: Context) {
            if (!wanted(c)) { c.stopService(Intent(c, LiveTimerService::class.java)); return }
            if (running || starting) return
            starting = true
            try { c.startForegroundService(Intent(c, LiveTimerService::class.java)) }
            catch (_: IllegalStateException) { starting = false }
            catch (_: SecurityException) { starting = false }
        }
    }
}

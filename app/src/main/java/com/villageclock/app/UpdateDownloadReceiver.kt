package com.villageclock.app

import android.app.*
import android.content.*

/** Completion notification only; installation requires a foreground button. */
class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = UpdateDownloads.id(context)
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE || id == -1L || intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -2) != id) return
        val pending = goAsync()
        val app = context.applicationContext
        Thread({
            try {
                UpdateDownloads.verify(app)
                val manager = app.getSystemService(NotificationManager::class.java)
                if (manager != null && manager.areNotificationsEnabled()) {
                    manager.createNotificationChannel(NotificationChannel("app-updates", "App Updates", NotificationManager.IMPORTANCE_DEFAULT))
                    val open = PendingIntent.getActivity(app, 90, UpdateActivity.intent(app, null), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    manager.notify(90, Notification.Builder(app, "app-updates").setSmallIcon(R.drawable.ic_village).setContentTitle("CoC Base Tracker update ready").setContentText("v${UpdateDownloads.version(app)} · Tap to review and install").setContentIntent(open).setAutoCancel(true).build())
                }
            } catch (_: Exception) { /* The download screen reports verification failures. */ }
            finally { pending.finish() }
        }, "update-verification").start()
    }
}

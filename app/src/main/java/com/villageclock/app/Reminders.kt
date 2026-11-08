package com.villageclock.app

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

object Reminders {
    private const val CHANNEL = "upgrade-finishes"
    const val ACTION = "com.villageclock.app.FINISH"
    private fun alarm(c: Context): PendingIntent = PendingIntent.getBroadcast(c, 13, Intent(c, AlarmReceiver::class.java).setAction(ACTION), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    @JvmStatic fun exact(c: Context): Boolean = Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    @JvmStatic fun canNotify(c: Context): Boolean = c.getSystemService(NotificationManager::class.java).areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    @JvmStatic fun schedule(c: Context) {
        val alarms = c.getSystemService(AlarmManager::class.java)
        val pending = alarm(c)
        alarms.cancel(pending)
        val now = System.currentTimeMillis()
        var next = Long.MAX_VALUE
        for (village in Store.all(c)) {
            val cursor = Store.notifiedThrough(c, village.tag)
            next = minOf(next, ActiveEffects.nextChange(village, now))
            for (upgrade in village.upgrades) if (upgrade.finishAt > cursor) next = minOf(next, upgrade.finishAt)
            if (Store.prefs(c).getBoolean("notify", true) && Store.prefs(c).getBoolean(Availability.PREF, true)) {
                val readyCursor = Availability.cursor(c, village.tag)
                for (entry in Availability.load(c, village)) if (entry.readyAt > readyCursor) next = minOf(next, entry.readyAt)
            }
        }
        if (next == Long.MAX_VALUE) return
        val trigger = maxOf(next, now + 1000)
        try { if (exact(c)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending) else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending) }
        catch (_: SecurityException) { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending) }
    }
    @JvmStatic fun deliver(c: Context) { for (village in Store.all(c)) { deliverVillage(c, village); deliverAvailability(c, village) } }
    private fun deliverVillage(c: Context, village: Village) {
        val now = System.currentTimeMillis()
        val cursor = Store.notifiedThrough(c, village.tag)
        val due = village.upgrades.filter { it.finishAt > cursor && it.finishAt <= now }
        if (due.isEmpty()) return
        if (Store.prefs(c).getBoolean("notify", true) && canNotify(c)) {
            val manager = c.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Upgrade finishes", NotificationManager.IMPORTANCE_DEFAULT))
            val headline = if (due.size == 1) "${due[0].queue.label} upgrade finished" else "${due.size} upgrades finished"
            val detail = due[0].label() + if (due.size > 1) " and ${due.size - 1} more" else ""
            manager.notify(VillageArchive.canonical(village.tag), 7, Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_village).setContentTitle(headline).setContentText(detail).setSubText(Store.title(c, village)).setColor(0xFFDBC387.toInt()).setContentIntent(VillageWidget.open(c, false, village.tag.hashCode(), village.tag)).setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE).build())
        }
        Store.setNotifiedThrough(c, village.tag, maxOf(cursor, due.maxOf { it.finishAt }))
    }
    private fun deliverAvailability(c: Context, village: Village) {
        val now = System.currentTimeMillis()
        val cursor = Availability.cursor(c, village.tag)
        val due = Availability.load(c, village).filter { it.readyAt > cursor && it.readyAt <= now }
        if (due.isEmpty()) return
        if (Store.prefs(c).getBoolean("notify", true) && Store.prefs(c).getBoolean(Availability.PREF, true) && canNotify(c)) {
            val manager = c.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("availability", "Helper and Clock Tower availability", NotificationManager.IMPORTANCE_DEFAULT))
            val detail = due[0].name + (if (due.size > 1) " and ${due.size - 1} more" else "") + " · sync to confirm"
            val open = Intent(c, OverviewActivity::class.java).putExtra("tag", village.tag).putExtra("tab", "Helpers").putExtra("builder", due[0].key == "clock-tower").setData(Uri.parse("villageclock://availability/${Uri.encode(village.tag)}"))
            val tap = PendingIntent.getActivity(c, village.tag.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.notify(VillageArchive.canonical(village.tag), 8, Notification.Builder(c, "availability").setSmallIcon(R.drawable.ic_village).setContentTitle(if (due.size == 1) "Ready to use" else "Helpers and boosts ready").setContentText(detail).setSubText(Store.title(c, village)).setContentIntent(tap).setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE).build())
        }
        Availability.through(c, village.tag, maxOf(cursor, due.maxOf { it.readyAt }))
    }
}

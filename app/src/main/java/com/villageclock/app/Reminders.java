package com.villageclock.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.ArrayList;

public final class Reminders {
    private static final String CHANNEL = "upgrade-finishes";
    public static final String ACTION = "com.villageclock.app.FINISH";
    private static PendingIntent alarm(Context c) {
        return PendingIntent.getBroadcast(c, 13, new Intent(c, AlarmReceiver.class).setAction(ACTION), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    public static boolean exact(Context c) { return Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager.class).canScheduleExactAlarms(); }
    public static boolean canNotify(Context c) {
        return c.getSystemService(NotificationManager.class).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
    }
    public static void schedule(Context c) {
        AlarmManager alarms = c.getSystemService(AlarmManager.class);
        PendingIntent pending = alarm(c);
        alarms.cancel(pending);
        long now = System.currentTimeMillis();
        long next = Long.MAX_VALUE;
        for (Village village : Store.all(c)) {
            long cursor = Store.notifiedThrough(c, village.tag);
            for (Village.Upgrade u : village.upgrades) if (u.finishAt > cursor) next = Math.min(next, u.finishAt);
        }
        if (next == Long.MAX_VALUE) return;
        long trigger = Math.max(next, now + 1000);
        try {
            if (exact(c)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending);
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending);
        } catch (SecurityException revoked) { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending); }
    }
    public static void deliver(Context c) {
        for (Village village : Store.all(c)) deliverVillage(c, village);
    }
    private static void deliverVillage(Context c, Village v) {
        long now = System.currentTimeMillis(), cursor = Store.notifiedThrough(c, v.tag);
        ArrayList<Village.Upgrade> due = new ArrayList<>();
        for (Village.Upgrade u : v.upgrades) if (u.finishAt > cursor && u.finishAt <= now) due.add(u);
        if (due.isEmpty()) return;
        if (Store.prefs(c).getBoolean("notify", true) && canNotify(c)) {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Upgrade finishes", NotificationManager.IMPORTANCE_DEFAULT));
            String headline = due.size() == 1 ? due.get(0).queue.label + " upgrade finished" : due.size() + " upgrades finished";
            String detail = due.get(0).label() + (due.size() > 1 ? " and " + (due.size() - 1) + " more" : "");
            Notification notification = new Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_village)
                .setContentTitle(headline).setContentText(detail).setSubText(Store.title(c, v)).setColor(0xFFDBC387)
                .setContentIntent(VillageWidget.open(c, false, v.tag.hashCode(), v.tag)).setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE).build();
            nm.notify(VillageArchive.canonical(v.tag), 7, notification);
        }
        long last = cursor;
        for (Village.Upgrade u : due) last = Math.max(last, u.finishAt);
        Store.setNotifiedThrough(c, v.tag, last);
    }
}

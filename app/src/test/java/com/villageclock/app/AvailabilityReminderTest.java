package com.villageclock.app;

import android.app.*;
import android.content.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk={26,29,35})
public class AvailabilityReminderTest {
    private Context c;private long at;
    @Before public void reset(){c=RuntimeEnvironment.getApplication();Store.prefs(c).edit().clear().commit();at=(System.currentTimeMillis()/1000-10)*1000;shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);}
    private Village save(String tag,int seconds)throws Exception{String raw="{\"tag\":\""+tag+"\",\"timestamp\":"+at/1000+",\"buildings\":[],\"helpers\":[{\"data\":93000001,\"lvl\":1,\"helper_cooldown\":"+seconds+"}]}";Village v=ExportParser.parse(raw,Store.catalog(c),System.currentTimeMillis());Store.save(c,raw,v);Availability.through(c,tag,at);return v;}
    @Test public void dueAlertsAreOneShotAndSeparateFromUpgradeNotifications()throws Exception{
        Village v=save("#READY",1);Reminders.deliver(c);NotificationManager manager=c.getSystemService(NotificationManager.class);
        assertEquals(1,shadowOf(manager).getAllNotifications().size());Notification notification=shadowOf(manager).getNotification(v.tag,8);assertNotNull(notification);assertNull(shadowOf(manager).getNotification(v.tag,7));
        manager.cancel(v.tag,8);Reminders.deliver(c);assertTrue(shadowOf(manager).getAllNotifications().isEmpty());
    }
    @Test public void accountCursorsAndRemovalStayIndependent()throws Exception{
        Village a=save("#FIRST",1),b=save("#SECOND",300);Reminders.deliver(c);assertTrue(Availability.cursor(c,a.tag)>at);assertEquals(at,Availability.cursor(c,b.tag));
        Store.select(c,a.tag);Store.clear(c);assertNotNull(Store.load(c,b.tag));assertNull(shadowOf(c.getSystemService(NotificationManager.class)).getNotification(a.tag,8));
    }
    @Test public void disabledAlertsDoNotNotifyAndFreshSyncReplacesDeadline()throws Exception{
        Village v=save("#OFF",1);Store.prefs(c).edit().putBoolean(Availability.PREF,false).commit();Reminders.deliver(c);assertTrue(shadowOf(c.getSystemService(NotificationManager.class)).getAllNotifications().isEmpty());
        long oldDeadline=Availability.load(c,v).get(0).readyAt;
        Store.prefs(c).edit().putBoolean(Availability.PREF,true).commit();String newRaw="{\"tag\":\"#OFF\",\"timestamp\":"+System.currentTimeMillis()/1000+",\"buildings\":[],\"helpers\":[{\"data\":93000001,\"lvl\":1,\"helper_cooldown\":500}]}";Village fresh=ExportParser.parse(newRaw,Store.catalog(c),System.currentTimeMillis());Store.save(c,newRaw,fresh);
        assertTrue(Availability.load(c,fresh).get(0).readyAt>oldDeadline+400000);Reminders.deliver(c);assertTrue(shadowOf(c.getSystemService(NotificationManager.class)).getAllNotifications().isEmpty());
    }
    @Test public void clockTowerDeadlineIsScheduledWithoutAnyUpgradeTimers()throws Exception{
        long snapshot=System.currentTimeMillis()/1000*1000;String raw="{\"tag\":\"#CLOCK\",\"timestamp\":"+snapshot/1000+",\"buildings\":[],\"buildings2\":[{\"data\":1000039,\"lvl\":7}],\"boosts\":{\"clocktower_cooldown\":120}}";
        Village village=ExportParser.parse(raw,Store.catalog(c),System.currentTimeMillis());Store.save(c,raw,village);Reminders.schedule(c);
        assertNotNull(shadowOf(c.getSystemService(AlarmManager.class)).peekNextScheduledAlarm());
        assertEquals(snapshot+120000,shadowOf(c.getSystemService(AlarmManager.class)).peekNextScheduledAlarm().triggerAtTime);
        Store.prefs(c).edit().putBoolean(Availability.PREF,false).commit();Reminders.schedule(c);assertNull(shadowOf(c.getSystemService(AlarmManager.class)).peekNextScheduledAlarm());
    }
}

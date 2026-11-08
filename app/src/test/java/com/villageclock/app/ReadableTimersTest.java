package com.villageclock.app;

import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.os.*;
import android.view.View;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ServiceController;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class ReadableTimersTest {
    private Context context; private long now;
    @Before public void prepare(){context=RuntimeEnvironment.getApplication();now=System.currentTimeMillis();Store.prefs(context).edit().clear().commit();}
    @Test public void readableRemoteRowAndNativeFallbackBothInflate(){
        Village.Upgrade job=new Village.Upgrade(1000008,20,"Cannon",Village.Queue.BUILDERS,now+130736000,false,0);
        View row=VillageWidget.row(context,job,now,now,true).apply(context,new FrameLayout(context));
        TextView text=row.findViewById(R.id.readable_timer);assertEquals("1d 12h 18m 56s",text.getText().toString());assertEquals(View.VISIBLE,text.getVisibility());assertEquals(View.GONE,row.findViewById(R.id.timer).getVisibility());
        View fallback=VillageWidget.row(context,job,now,now,false).apply(context,new FrameLayout(context));assertEquals(View.VISIBLE,fallback.findViewById(R.id.timer).getVisibility());assertEquals(View.GONE,fallback.findViewById(R.id.readable_timer).getVisibility());
        View ended=VillageWidget.row(context,job,job.finishAt,now,true).apply(context,new FrameLayout(context));assertEquals(View.VISIBLE,ended.findViewById(R.id.done).getVisibility());assertEquals(View.GONE,ended.findViewById(R.id.readable_timer).getVisibility());
    }
    @Test public void activityCountdownTicksAcrossDayBoundaryAndStopsAtFinish(){
        org.robolectric.android.controller.ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).setup();
        Activity activity=controller.get();TextView text=new TextView(activity);activity.setContentView(text);
        controller.visible().windowFocusChanged(true);
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue("Countdown row must be attached",text.isAttachedToWindow());
        assertTrue("Countdown window must have focus",text.hasWindowFocus());
        // Robolectric advances Android uptime, not Java's wall clock. Supply a simulated epoch clock.
        long initialUptime=SystemClock.elapsedRealtime();long epoch=1700000000000L;
        CountdownText.bind(text,epoch+86401000,"Done",()->epoch+SystemClock.elapsedRealtime()-initialUptime);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertEquals("0d 23h 59m 59s",text.getText().toString());
        controller.windowFocusChanged(false);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertEquals("0d 23h 59m 59s",text.getText().toString());
        controller.windowFocusChanged(true);
        assertEquals("0d 23h 59m 57s",text.getText().toString());
        TextView ending=new TextView(activity);CountdownText.bind(ending,System.currentTimeMillis()-1,"Done");assertEquals("Done",ending.getText().toString());
        controller.pause().stop().destroy();
    }
    @Test public void foregroundServiceHasNotificationPauseAndAccountBoundSnapshots()throws Exception{
        String raw="{\"tag\":\"#LIVE123\",\"timestamp\":"+now/1000+",\"buildings\":[{\"data\":1000001,\"lvl\":15},{\"data\":1000008,\"lvl\":10,\"timer\":10000}]}";
        Village village=ExportParser.parse(raw,Store.catalog(context),now);Store.save(context,raw,village);
        AppWidgetManager manager=AppWidgetManager.getInstance(context);
        int id=shadowOf(manager).createWidget(VillageWidget.class,R.layout.widget);Store.bind(context,id,village.tag);LiveTimerService.cache(id,village.tag,village);
        assertTrue(LiveTimerService.wanted(context));
        ServiceController<LiveTimerService> controller=Robolectric.buildService(LiveTimerService.class).create();LiveTimerService service=controller.get();
        controller.startCommand(0,1);assertTrue(LiveTimerService.readable(context));
        assertNotNull(shadowOf(service).getLastForegroundNotification());
        assertEquals(village.tag,LiveTimerService.snapshot(context,id).tag);
        service.onStartCommand(new Intent(context,LiveTimerService.class).setAction(LiveTimerService.PAUSE),0,2);
        assertFalse(LiveTimerService.enabled(context));assertFalse(LiveTimerService.readable(context));assertFalse(LiveTimerService.wanted(context));
        controller.destroy();assertFalse(LiveTimerService.readable(context));LiveTimerService.forget(id);
    }
    @Test public void boostedWidgetsShowSteadyRealTimeAndActivityKeepsFastWork()throws Exception{
        long at=now/1000*1000;
        String raw="{\"tag\":\"#BOOST12\",\"timestamp\":"+at/1000+",\"buildings\":[],\"boosts\":{\"lab_boost\":3600},\"units\":[{\"data\":4000000,\"timer\":172800}]}";
        Village.Upgrade upgrade=ExportParser.parse(raw,Store.catalog(context),at).upgrades.get(0);
        View first=VillageWidget.row(context,upgrade,at,at,true).apply(context,new FrameLayout(context));
        assertEquals("1d 1h 0m 0s",((TextView)first.findViewById(R.id.readable_timer)).getText().toString());
        assertEquals(View.GONE,first.findViewById(R.id.row_eta).getVisibility());
        assertEquals(View.GONE,first.findViewById(R.id.row_boost).getVisibility());assertTrue(((TextView)first.findViewById(R.id.row_finish_at)).getText().toString().contains("Finishes at"));
        View second=VillageWidget.row(context,upgrade,at+1000,at,true).apply(context,new FrameLayout(context));
        assertEquals("1d 0h 59m 59s",((TextView)second.findViewById(R.id.readable_timer)).getText().toString());
        View activity=VillageWidget.row(context,upgrade,at+500,at,true,true).apply(context,new FrameLayout(context));
        assertEquals("Work left: 1d 23h 59m 48s",((TextView)activity.findViewById(R.id.readable_timer)).getText().toString());
        assertEquals(View.VISIBLE,activity.findViewById(R.id.row_eta).getVisibility());
    }
    @Test public void activityWorkCountdownReallyAdvances24SecondsPerTick()throws Exception{
        org.robolectric.android.controller.ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).setup();
        Activity activity=controller.get();TextView text=new TextView(activity);activity.setContentView(text);controller.visible().windowFocusChanged(true);shadowOf(Looper.getMainLooper()).idle();
        long epoch=1700000000000L,initialUptime=SystemClock.elapsedRealtime();
        UpgradeTiming.Plan plan=new UpgradeTiming.Plan(172800,java.util.Collections.singletonList(new UpgradeTiming.Boost("research",23,3600)),null);
        Village.Upgrade upgrade=new Village.Upgrade(4000000,10,"Barbarian",Village.Queue.LAB,epoch+90000000,false,0,epoch,plan);
        CountdownText.bindWork(text,upgrade,"Done",()->epoch+SystemClock.elapsedRealtime()-initialUptime);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));assertEquals("Work left: 1d 23h 59m 59s",text.getText().toString());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(450));assertEquals("Work left: 1d 23h 59m 48s",text.getText().toString());
        controller.windowFocusChanged(false);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));assertEquals("Work left: 1d 23h 59m 48s",text.getText().toString());
        controller.windowFocusChanged(true);assertEquals("Work left: 1d 23h 59m 42s",text.getText().toString());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));assertEquals("Work left: 1d 23h 59m 36s",text.getText().toString());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));assertEquals("Work left: 1d 23h 59m 12s",text.getText().toString());
        controller.pause().stop().destroy();
    }
}

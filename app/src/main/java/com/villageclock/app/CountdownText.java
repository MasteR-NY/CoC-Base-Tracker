package com.villageclock.app;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import java.util.function.LongSupplier;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;

/** Activity-only live text. Detaching a row always cancels its scheduled tick. */
public final class CountdownText implements View.OnAttachStateChangeListener, Runnable {
    private final TextView view;
    private final long finishAt;
    private final String done;
    private final LongSupplier clock;
    private final LongFunction<String> formatter;
    private final LongUnaryOperator tickDelay;
    private long now;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ViewTreeObserver.OnWindowFocusChangeListener focus = focused -> {
        if (focused) run(); else handler.removeCallbacks(this);
    };
    private CountdownText(TextView view, long finishAt, String done, LongSupplier clock, LongFunction<String> formatter, LongUnaryOperator tickDelay) {
        this.view = view; this.finishAt = finishAt; this.done = done; this.clock = clock; this.formatter = formatter;this.tickDelay=tickDelay;
    }
    public static void bind(TextView view, long finishAt, String done) {
        bind(view, finishAt, done, System::currentTimeMillis);
    }
    static void bind(TextView view, long finishAt, String done, LongSupplier clock) {
        bind(view, finishAt, done, clock, null);
    }
    static void bind(TextView view, long finishAt, String done, LongSupplier clock, LongFunction<String> formatter) {
        attach(view,finishAt,done,clock,formatter,null);
    }
    public static void bindWork(TextView view,Village.Upgrade upgrade,String done){bindWork(view,upgrade,done,System::currentTimeMillis);}
    static void bindWork(TextView view,Village.Upgrade upgrade,String done,LongSupplier clock){
        attach(view,upgrade.finishAt,done,clock,upgrade::workText,upgrade::nextWorkTickMillis);
    }
    private static void attach(TextView view,long finishAt,String done,LongSupplier clock,LongFunction<String> formatter,LongUnaryOperator tickDelay){
        CountdownText countdown = new CountdownText(view, finishAt, done, clock, formatter,tickDelay);
        view.addOnAttachStateChangeListener(countdown);
        countdown.update();
        if (view.isAttachedToWindow()) countdown.onViewAttachedToWindow(view);
    }
    private boolean update() {
        now = clock.getAsLong();
        String value=finishAt <= now ? done : formatter == null ? DurationText.remaining(finishAt, now) : formatter.apply(now);
        if(!value.contentEquals(view.getText()))view.setText(value);
        return finishAt > now;
    }
    @Override public void run() {
        handler.removeCallbacks(this);
        if (update() && view.isAttachedToWindow() && view.hasWindowFocus()) handler.postDelayed(this, Math.min(finishAt-now,tickDelay==null?1000:tickDelay.applyAsLong(now)));
    }
    @Override public void onViewAttachedToWindow(View v) { view.getViewTreeObserver().addOnWindowFocusChangeListener(focus); run(); }
    @Override public void onViewDetachedFromWindow(View v) { handler.removeCallbacks(this); if (view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnWindowFocusChangeListener(focus); }
}

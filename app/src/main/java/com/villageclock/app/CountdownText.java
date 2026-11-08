package com.villageclock.app;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import java.util.function.LongSupplier;
import java.util.function.LongFunction;

/** Activity-only live text. Detaching a row always cancels its scheduled tick. */
public final class CountdownText implements View.OnAttachStateChangeListener, Runnable {
    private final TextView view;
    private final long finishAt;
    private final String done;
    private final LongSupplier clock;
    private final LongFunction<String> formatter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ViewTreeObserver.OnWindowFocusChangeListener focus = focused -> {
        if (focused) run(); else handler.removeCallbacks(this);
    };
    private CountdownText(TextView view, long finishAt, String done, LongSupplier clock, LongFunction<String> formatter) {
        this.view = view; this.finishAt = finishAt; this.done = done; this.clock = clock; this.formatter = formatter;
    }
    public static void bind(TextView view, long finishAt, String done) {
        bind(view, finishAt, done, System::currentTimeMillis);
    }
    static void bind(TextView view, long finishAt, String done, LongSupplier clock) {
        bind(view, finishAt, done, clock, null);
    }
    static void bind(TextView view, long finishAt, String done, LongSupplier clock, LongFunction<String> formatter) {
        CountdownText countdown = new CountdownText(view, finishAt, done, clock, formatter);
        view.addOnAttachStateChangeListener(countdown);
        countdown.update();
        if (view.isAttachedToWindow()) countdown.onViewAttachedToWindow(view);
    }
    private boolean update() {
        long now = clock.getAsLong();
        view.setText(finishAt <= now ? done : formatter == null ? DurationText.remaining(finishAt, now) : formatter.apply(now));
        return finishAt > now;
    }
    @Override public void run() {
        handler.removeCallbacks(this);
        if (update() && view.isAttachedToWindow() && view.hasWindowFocus()) handler.postDelayed(this, 1000);
    }
    @Override public void onViewAttachedToWindow(View v) { view.getViewTreeObserver().addOnWindowFocusChangeListener(focus); run(); }
    @Override public void onViewDetachedFromWindow(View v) { handler.removeCallbacks(this); if (view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnWindowFocusChangeListener(focus); }
}

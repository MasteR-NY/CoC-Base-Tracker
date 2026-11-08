package com.villageclock.app

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.widget.TextView
import java.util.function.LongFunction
import java.util.function.LongSupplier
import java.util.function.LongUnaryOperator

/** Activity-only ticking; detaching a row always cancels the scheduled callback. */
class CountdownText private constructor(private val view: TextView, private val finishAt: Long, private val done: String,
    private val clock: LongSupplier, private val formatter: LongFunction<String>?, private val tickDelay: LongUnaryOperator?) : View.OnAttachStateChangeListener, Runnable {
    private var now = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val focus = ViewTreeObserver.OnWindowFocusChangeListener { focused -> if (focused) run() else handler.removeCallbacks(this) }
    private fun update(): Boolean {
        now = clock.asLong
        val value = if (finishAt <= now) done else formatter?.apply(now) ?: DurationText.remaining(finishAt, now)
        if (!value.contentEquals(view.text)) view.text = value
        return finishAt > now
    }
    override fun run() {
        handler.removeCallbacks(this)
        if (update() && view.isAttachedToWindow && view.hasWindowFocus()) handler.postDelayed(this, minOf(finishAt - now, tickDelay?.applyAsLong(now) ?: 1000))
    }
    override fun onViewAttachedToWindow(v: View) { view.viewTreeObserver.addOnWindowFocusChangeListener(focus); run() }
    override fun onViewDetachedFromWindow(v: View) { handler.removeCallbacks(this); if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(focus) }
    companion object {
        @JvmStatic @JvmOverloads fun bind(view: TextView, finishAt: Long, done: String, clock: LongSupplier = LongSupplier(System::currentTimeMillis), formatter: LongFunction<String>? = null) { attach(view, finishAt, done, clock, formatter, null) }
        @JvmStatic @JvmOverloads fun bindWork(view: TextView, upgrade: Village.Upgrade, done: String, clock: LongSupplier = LongSupplier(System::currentTimeMillis)) { attach(view, upgrade.finishAt, done, clock, LongFunction(upgrade::workText), LongUnaryOperator(upgrade::nextWorkTickMillis)) }
        private fun attach(view: TextView, finishAt: Long, done: String, clock: LongSupplier, formatter: LongFunction<String>?, delay: LongUnaryOperator?) {
            val countdown = CountdownText(view, finishAt, done, clock, formatter, delay)
            view.addOnAttachStateChangeListener(countdown)
            countdown.update()
            if (view.isAttachedToWindow) countdown.onViewAttachedToWindow(view)
        }
    }
}

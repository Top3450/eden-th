// SPDX-FileCopyrightText: Eden TH
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.views

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Eden TH: makes a game card a little physical object.
 *
 * - it leans as the phone is tilted ([TiltSensor]),
 * - it swings with the scroll, as if it hung on the list and lagged behind a flick,
 * - it dips under the finger while pressed,
 * - a soft highlight slides across it with the lean.
 *
 * Every lean goes through a damped spring, so a quick tilt or flick overshoots and settles instead
 * of snapping. The card keeps any rotation its list gives it ([baseRotationX], [baseRotationY] -
 * the carousel turns its side cards) and adds the lean on top.
 */
class TiltCard(private val view: View) : TiltSensor.Listener, Choreographer.FrameCallback {

    var baseRotationX = 0f
        set(value) {
            field = value
            apply()
        }
    var baseRotationY = 0f
        set(value) {
            field = value
            apply()
        }

    /** Corner radius of the card, for clipping the highlight. */
    var cornerRadius = 0f

    // Spring state, degrees and degrees per second; x = around the horizontal axis, y = vertical.
    private var x = 0f
    private var y = 0f
    private var vx = 0f
    private var vy = 0f

    private var sensorX = 0f
    private var sensorY = 0f
    private var touchX = 0f
    private var touchY = 0f

    private var running = false
    private var lastFrameNanos = 0L

    private var lastScreenX = Int.MIN_VALUE
    private var lastScreenY = Int.MIN_VALUE
    private val location = IntArray(2)

    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()

    private val density = view.resources.displayMetrics.density

    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { onMoved() }

    init {
        view.cameraDistance = 9000f * density
    }

    private val enabled: Boolean
        get() = TiltSensor.isEnabled(view.context)

    // Each card floats on its own beat, so the list never looks frozen.
    private val phase = (System.identityHashCode(view) % 6283) / 1000f

    fun onAttached() {
        TiltSensor.add(view.context, this)
        view.viewTreeObserver.addOnScrollChangedListener(scrollListener)
        lastScreenX = Int.MIN_VALUE
        start()
    }

    fun onDetached() {
        TiltSensor.remove(view.context, this)
        if (view.viewTreeObserver.isAlive) {
            view.viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        }
        stop()
        x = 0f; y = 0f; vx = 0f; vy = 0f
        touchX = 0f; touchY = 0f
        apply()
    }

    override fun onTilt(pitch: Float, roll: Float) {
        sensorX = -pitch
        sensorY = roll
        start()
    }

    /** Called for every touch event the card sees; never consumes it. */
    fun onTouch(event: MotionEvent) {
        if (!enabled) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val w = view.width.coerceAtLeast(1)
                val h = view.height.coerceAtLeast(1)
                val nx = ((event.x / w) * 2f - 1f).coerceIn(-1f, 1f)
                val ny = ((event.y / h) * 2f - 1f).coerceIn(-1f, 1f)
                touchX = ny * 9f
                touchY = -nx * 9f
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchX = 0f
                touchY = 0f
            }
        }
        start()
    }

    private fun onMoved() {
        if (!enabled || !view.isAttachedToWindow) return
        view.getLocationOnScreen(location)
        val sx = location[0]
        val sy = location[1]
        if (lastScreenX != Int.MIN_VALUE) {
            val dx = (sx - lastScreenX) / density
            val dy = (sy - lastScreenY) / density
            // A jump bigger than the card is a re-layout, not motion.
            if (abs(dx) < view.width / density && abs(dy) < view.height / density) {
                vy += dx * 7f
                vx -= dy * 7f
                start()
            }
        }
        lastScreenX = sx
        lastScreenY = sy
    }

    private fun start() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun stop() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val dt = if (lastFrameNanos == 0L) 1f / 60f
        else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceIn(0.001f, 0.05f)
        lastFrameNanos = frameTimeNanos

        val on = enabled
        // A slow idle sway on top of everything else.
        val t = frameTimeNanos / 1e9f
        val swayX = if (on) kotlin.math.cos(t * 0.8f + phase) * 3f else 0f
        val swayY = if (on) kotlin.math.sin(t * 1.1f + phase) * 4f else 0f
        val tx = if (on) (sensorX + touchX + swayX).coerceIn(-20f, 20f) else 0f
        val ty = if (on) (sensorY + touchY + swayY).coerceIn(-20f, 20f) else 0f

        // Damped spring, a little under critical damping so a flick overshoots once.
        val stiffness = 170f
        val damping = 13f
        vx += (stiffness * (tx - x) - damping * vx) * dt
        vy += (stiffness * (ty - y) - damping * vy) * dt
        x = (x + vx * dt).coerceIn(-24f, 24f)
        y = (y + vy * dt).coerceIn(-24f, 24f)

        apply()

        val settled = !on && abs(tx - x) < 0.05f && abs(ty - y) < 0.05f &&
            abs(vx) < 0.05f && abs(vy) < 0.05f
        if (settled || !view.isAttachedToWindow) {
            x = tx; y = ty; vx = 0f; vy = 0f
            apply()
            running = false
        } else {
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun apply() {
        view.rotationX = baseRotationX + x
        view.rotationY = baseRotationY + y
        view.invalidate()
    }

    /** Draws the highlight; call after the card itself has drawn. */
    fun drawShine(canvas: Canvas) {
        val w = view.width.toFloat()
        val h = view.height.toFloat()
        if (w <= 0f || h <= 0f) return
        if (!enabled) return
        val lean = (hypot(x, y) / 14f).coerceIn(0f, 1f)
        // The light sits opposite the lean, like a lamp above the screen.
        val cx = w / 2f - (y / 18f) * w * 0.6f
        val cy = h / 2f + (x / 18f) * h * 0.6f
        val radius = max(w, h) * 0.85f
        val alpha = (28f + lean * 90f).toInt().coerceIn(0, 118)
        shinePaint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(Color.argb(alpha, 255, 255, 255), Color.argb(0, 255, 255, 255)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        clipPath.reset()
        clipPath.addRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawRect(0f, 0f, w, h, shinePaint)
        canvas.restoreToCount(save)
    }
}

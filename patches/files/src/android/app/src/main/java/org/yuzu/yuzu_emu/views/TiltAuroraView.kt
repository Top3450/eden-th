// SPDX-FileCopyrightText: Eden TH
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Choreographer
import android.view.View
import org.yuzu.yuzu_emu.R
import kotlin.math.abs
import kotlin.math.max

/**
 * Eden TH: the glow behind the game list - three soft blobs in the theme's colours that drift the
 * other way when the phone is tilted, so the cards seem to float above it. It only redraws while
 * it is moving.
 */
class TiltAuroraView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), TiltSensor.Listener, Choreographer.FrameCallback {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors = IntArray(3)

    private var targetX = 0f
    private var targetY = 0f
    private var shiftX = 0f
    private var shiftY = 0f
    private var running = false

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        loadColors()
    }

    private fun themeColor(attr: Int, fallback: Int): Int {
        val tv = TypedValue()
        return if (context.theme.resolveAttribute(attr, tv, true)) tv.data else fallback
    }

    private fun loadColors() {
        colors[0] = themeColor(com.google.android.material.R.attr.colorPrimary, Color.MAGENTA)
        colors[1] = context.getColor(R.color.eden_border_gradient_end)
        colors[2] = themeColor(com.google.android.material.R.attr.colorTertiary, Color.CYAN)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        loadColors()
        TiltSensor.add(context, this)
        kick()
    }

    private fun kick() {
        if (!running) {
            running = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    // Slow drift of the blobs, seconds; advances only while the effects are on.
    private var time = 0f
    private var lastFrame = 0L
    private var skip = false

    override fun onDetachedFromWindow() {
        TiltSensor.remove(context, this)
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        super.onDetachedFromWindow()
    }

    override fun onTilt(pitch: Float, roll: Float) {
        targetX = -roll / TiltSensor.MAX_TILT
        targetY = pitch / TiltSensor.MAX_TILT
        kick()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val on = TiltSensor.isEnabled(context)
        if (lastFrame != 0L) time += ((frameTimeNanos - lastFrame) / 1e9f).coerceIn(0f, 0.1f)
        lastFrame = frameTimeNanos
        shiftX += (targetX - shiftX) * 0.12f
        shiftY += (targetY - shiftY) * 0.12f
        // 30 frames a second is plenty for a glow.
        skip = !skip
        if (!skip) invalidate()
        if (!on && abs(targetX - shiftX) < 0.002f && abs(targetY - shiftY) < 0.002f) {
            lastFrame = 0L
            shiftX = targetX
            shiftY = targetY
            running = false
        } else {
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun blob(canvas: Canvas, fx: Float, fy: Float, fr: Float, depth: Float, color: Int) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w * fx + shiftX * w * 0.10f * depth + kotlin.math.sin(time * 0.21f * depth + fx * 6f) * w * 0.12f
        val cy = h * fy + shiftY * h * 0.08f * depth + kotlin.math.cos(time * 0.17f * depth + fy * 6f) * h * 0.08f
        val r = max(w, h) * fr
        paint.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(
                Color.argb(115, Color.red(color), Color.green(color), Color.blue(color)),
                Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, paint)
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        blob(canvas, 0.15f, 0.20f, 0.55f, 1.0f, colors[0])
        blob(canvas, 0.90f, 0.35f, 0.50f, 1.6f, colors[1])
        blob(canvas, 0.45f, 0.95f, 0.60f, 0.7f, colors[2])
    }
}

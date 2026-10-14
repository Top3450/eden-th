// SPDX-FileCopyrightText: Eden TH
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.views

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.preference.PreferenceManager
import kotlin.math.abs

/**
 * Eden TH: how far the phone is tilted away from the way it is being held, for the game cards and
 * the background. Only the change matters - a slow drift pulls the neutral pose along, so the
 * cards rest flat whether the phone is held upright, flat or in bed, and lean when it is tilted.
 *
 * The sensor runs only while something listens (a card or background on screen) and the effect
 * is switched on, so an idle app does not keep it awake.
 */
object TiltSensor : SensorEventListener {
    fun interface Listener {
        /** [pitch] and [roll] in degrees, positive = top edge / right edge towards the user. */
        fun onTilt(pitch: Float, roll: Float)
    }

    const val PREF_ENABLED = "eden_th_tilt_effects"

    /** How far a card leans at most, in degrees. */
    const val MAX_TILT = 14f

    private val listeners = LinkedHashSet<Listener>()
    private var manager: SensorManager? = null
    private var registered = false
    private var enabled: Boolean? = null

    private val rotation = FloatArray(9)
    private val angles = FloatArray(3)
    private var neutralPitch = Float.NaN
    private var neutralRoll = Float.NaN

    private var sentPitch = 0f
    private var sentRoll = 0f

    var pitch = 0f
        private set
    var roll = 0f
        private set

    fun isEnabled(context: Context): Boolean {
        enabled?.let { return it }
        val on = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .getBoolean(PREF_ENABLED, true)
        enabled = on
        return on
    }

    fun setEnabled(context: Context, on: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext).edit()
            .putBoolean(PREF_ENABLED, on).apply()
        enabled = on
        pitch = 0f
        roll = 0f
        update(context)
        // Wakes every card and the background: on, they start floating; off, they settle flat.
        listeners.toList().forEach { it.onTilt(0f, 0f) }
    }

    fun add(context: Context, listener: Listener) {
        listeners.add(listener)
        update(context)
    }

    fun remove(context: Context, listener: Listener) {
        listeners.remove(listener)
        update(context)
    }

    private fun update(context: Context) {
        val want = listeners.isNotEmpty() && isEnabled(context)
        if (want == registered) return
        val sm = manager ?: (context.applicationContext.getSystemService(Context.SENSOR_SERVICE)
            as? SensorManager)?.also { manager = it } ?: return
        if (want) {
            val sensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
                ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                ?: return
            neutralPitch = Float.NaN
            neutralRoll = Float.NaN
            registered = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            sm.unregisterListener(this)
            registered = false
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, angles)
        val p = Math.toDegrees(angles[1].toDouble()).toFloat()
        val r = Math.toDegrees(angles[2].toDouble()).toFloat()
        if (neutralPitch.isNaN()) {
            neutralPitch = p
            neutralRoll = r
        }
        // The neutral pose follows the phone slowly (about two seconds), so only a change in how it
        // is held makes the cards lean.
        neutralPitch += wrap(p - neutralPitch) * 0.012f
        neutralRoll += wrap(r - neutralRoll) * 0.012f
        val dp = wrap(p - neutralPitch)
        val dr = wrap(r - neutralRoll)
        pitch = (dp * 1.4f).coerceIn(-MAX_TILT, MAX_TILT)
        roll = (dr * 1.4f).coerceIn(-MAX_TILT, MAX_TILT)
        if (abs(dp) > 60f || abs(dr) > 60f) { // turned right over: start again from here
            neutralPitch = p
            neutralRoll = r
        }
        // Sensor noise is a few tenths of a degree; passing it on would keep every card animating.
        if (abs(pitch - sentPitch) < 0.25f && abs(roll - sentRoll) < 0.25f) return
        sentPitch = pitch
        sentRoll = roll
        listeners.toList().forEach { it.onTilt(pitch, roll) }
    }

    private fun wrap(deg: Float): Float {
        var d = deg
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        return d
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

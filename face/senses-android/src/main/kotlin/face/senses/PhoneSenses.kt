package face.senses

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import face.api.DeviceState
import face.api.FaceController
import face.api.FaceReaction
import kotlin.math.abs
import kotlin.math.sqrt

// Turns what happens to the phone into face intent. Only listens between start() and stop(); needs no permissions.
public class PhoneSenses(context: Context, private val face: FaceController) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val sensors = app.getSystemService(SensorManager::class.java)
    private val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val main = Handler(Looper.getMainLooper())
    private val reportVolume = Runnable { reportVolume() }
    private var started = false
    private var state = DeviceState()
    private var plugged: Boolean? = null
    private var full = false
    private val shakePeaks = LongArray(SHAKE_PEAKS)
    private var shakeHead = 0
    private var lastPeak = 0L
    private var lastShake = 0L
    private var downSince = -1L

    private val battery = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onBattery(intent)
    }

    private val headset = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (isInitialStickyBroadcast) return
            if (intent.getIntExtra("state", 0) == 1) face.react(FaceReaction.HeadphonesIn)
        }
    }

    private val volume = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getIntExtra(EXTRA_STREAM_TYPE, -1) == AudioManager.STREAM_MUSIC) reportVolume()
        }
    }

    private val motion = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) = onMotion(event)

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    public fun start() {
        if (started) return
        started = true
        register(battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        register(headset, IntentFilter(Intent.ACTION_HEADSET_PLUG))
        register(volume, IntentFilter(VOLUME_CHANGED_ACTION))
        accelerometer?.let { sensors?.registerListener(motion, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    public fun stop() {
        if (!started) return
        started = false
        app.unregisterReceiver(battery)
        app.unregisterReceiver(headset)
        app.unregisterReceiver(volume)
        sensors?.unregisterListener(motion)
        main.removeCallbacks(reportVolume)
    }

    // Call from Activity.dispatchKeyEvent; it only observes, the key still changes the volume.
    public fun onKeyEvent(event: KeyEvent) {
        if (!started || event.action != KeyEvent.ACTION_DOWN) return
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            main.removeCallbacks(reportVolume)
            main.postDelayed(reportVolume, VOLUME_SETTLE_MILLIS)
        }
    }

    private fun register(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            app.registerReceiver(receiver, filter)
        }
    }

    private fun reportVolume() {
        val am = audio ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        face.showVolume(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max)
    }

    private fun onBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        if (level < 0) return
        val percent = level * 100 / scale
        val pluggedNow = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val known = plugged
        if (known != null && known != pluggedNow) face.react(if (pluggedNow) FaceReaction.PluggedIn else FaceReaction.Unplugged)
        plugged = pluggedNow
        val fullNow = pluggedNow && intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0) == BatteryManager.BATTERY_STATUS_FULL
        if (known != null && fullNow && !full) face.react(FaceReaction.BatteryFull)
        full = fullNow
        val low = if (state.batteryLow) percent < LOW_RECOVER else percent <= LOW_PERCENT
        update(state.copy(batteryLow = low, charging = pluggedNow))
    }

    private fun onMotion(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val now = event.timestamp
        if (sqrt(x * x + y * y + z * z) > SHAKE_FORCE && now - lastPeak > MIN_PEAK_GAP) {
            lastPeak = now
            shakePeaks[shakeHead] = now
            shakeHead = (shakeHead + 1) % shakePeaks.size
            val oldest = shakePeaks[shakeHead]
            if (oldest > 0L && now - oldest < SHAKE_WINDOW && now - lastShake > SHAKE_COOLDOWN) {
                lastShake = now
                shakePeaks.fill(0L)
                face.react(FaceReaction.Shaken)
            }
        }
        val flat = abs(x) < FLAT_TOLERANCE && abs(y) < FLAT_TOLERANCE
        if (z < FACE_DOWN_Z && flat) {
            if (downSince < 0L) downSince = now
            if (now - downSince > FACE_DOWN_HOLD) update(state.copy(faceDown = true))
        } else if (z > FACE_UP_Z || !flat) {
            downSince = -1L
            update(state.copy(faceDown = false))
        }
    }

    private fun update(next: DeviceState) {
        if (next == state) return
        state = next
        face.setDeviceState(next)
    }

    private companion object {
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        const val VOLUME_SETTLE_MILLIS = 60L
        const val LOW_PERCENT = 15
        const val LOW_RECOVER = 20
        const val SHAKE_FORCE = 19f
        const val SHAKE_PEAKS = 3
        const val MIN_PEAK_GAP = 100_000_000L
        const val SHAKE_WINDOW = 900_000_000L
        const val SHAKE_COOLDOWN = 2_500_000_000L
        const val FACE_DOWN_Z = -8.5f
        const val FACE_UP_Z = -6f
        const val FLAT_TOLERANCE = 3f
        const val FACE_DOWN_HOLD = 1_000_000_000L
    }
}

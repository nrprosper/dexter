package face.senses

import android.app.Activity
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
import android.os.PowerManager
import android.os.SystemClock
import android.view.KeyEvent
import face.api.DayPart
import face.api.DeviceState
import face.api.FaceController
import face.api.FaceReaction
import face.api.Heat
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.sqrt

// Turns what happens to the phone into face intent. Only listens between start() and stop(); needs no permissions.
public class PhoneSenses(context: Context, private val face: FaceController) {
    private val activity = context as? Activity
    private val app = context.applicationContext
    private val power = app.getSystemService(PowerManager::class.java)
    private val proximity = sensors(context)?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val light = sensors(context)?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val clockTick = Runnable { onClock() }
    private var stoppedAt = 0L
    private var greetedDay = -1
    private var lateNightDay = -1
    private var near = false
    private var lux = DEFAULT_LUX
    private var darkSince = -1L
    private var lastDimLight = 0L
    private var lastTooBright = 0L
    private var lastMotion = 0L
    private var baseX = Float.NaN
    private var baseY = 0f
    private var smoothX = 0f
    private var smoothY = 0f
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
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> onMotion(event)
                Sensor.TYPE_PROXIMITY -> onProximity(event)
                Sensor.TYPE_LIGHT -> onLight(event)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    private val saver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            update(state.copy(batterySaver = power?.isPowerSaveMode == true))
        }
    }

    private val thermal = PowerManager.OnThermalStatusChangedListener { onThermal(it) }

    private val screenshot: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Activity.ScreenCaptureCallback { face.react(FaceReaction.Screenshot) }
    } else {
        null
    }

    public fun start() {
        if (started) return
        started = true
        register(battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        register(headset, IntentFilter(Intent.ACTION_HEADSET_PLUG))
        register(volume, IntentFilter(VOLUME_CHANGED_ACTION))
        register(saver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        accelerometer?.let { sensors?.registerListener(motion, it, SensorManager.SENSOR_DELAY_GAME) }
        proximity?.let { sensors?.registerListener(motion, it, SensorManager.SENSOR_DELAY_NORMAL) }
        light?.let { sensors?.registerListener(motion, it, SensorManager.SENSOR_DELAY_NORMAL) }
        power?.let {
            it.addThermalStatusListener(app.mainExecutor, thermal)
            onThermal(it.currentThermalStatus)
            update(state.copy(batterySaver = it.isPowerSaveMode))
        }
        registerScreenshots()
        val greeted = onClock()
        val away = SystemClock.elapsedRealtime() - stoppedAt
        if (stoppedAt > 0L && away > WELCOME_AFTER_MILLIS && !greeted) {
            face.react(FaceReaction.WelcomeBack(longAbsence = away > LONG_ABSENCE_MILLIS))
        }
    }

    public fun stop() {
        if (!started) return
        started = false
        app.unregisterReceiver(battery)
        app.unregisterReceiver(headset)
        app.unregisterReceiver(volume)
        app.unregisterReceiver(saver)
        sensors?.unregisterListener(motion)
        power?.removeThermalStatusListener(thermal)
        unregisterScreenshots()
        main.removeCallbacks(reportVolume)
        main.removeCallbacks(clockTick)
        stoppedAt = SystemClock.elapsedRealtime()
    }

    private fun registerScreenshots() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val callback = screenshot as? Activity.ScreenCaptureCallback ?: return
        activity?.registerScreenCaptureCallback(app.mainExecutor, callback)
    }

    private fun unregisterScreenshots() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val callback = screenshot as? Activity.ScreenCaptureCallback ?: return
        activity?.unregisterScreenCaptureCallback(callback)
    }

    // Part of the day, plus a once-a-day morning greeting and after-midnight check-in; true if it greeted.
    private fun onClock(): Boolean {
        main.removeCallbacks(clockTick)
        main.postDelayed(clockTick, CLOCK_MILLIS)
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val day = now.get(Calendar.DAY_OF_YEAR)
        val part = when (hour) {
            in MORNING_START until DAY_START -> DayPart.Morning
            in DAY_START until EVENING_START -> DayPart.Day
            in EVENING_START until NIGHT_START -> DayPart.Evening
            else -> DayPart.Night
        }
        update(state.copy(dayPart = part))
        if (part == DayPart.Morning && greetedDay != day) {
            greetedDay = day
            face.react(FaceReaction.GoodMorning)
            return true
        }
        if (hour < LATE_NIGHT_END && lateNightDay != day) {
            lateNightDay = day
            face.react(FaceReaction.LateNight)
            return true
        }
        return false
    }

    private fun onThermal(status: Int) {
        val heat = when (status) {
            PowerManager.THERMAL_STATUS_NONE, PowerManager.THERMAL_STATUS_LIGHT -> Heat.Normal
            PowerManager.THERMAL_STATUS_MODERATE -> Heat.Warm
            PowerManager.THERMAL_STATUS_SEVERE -> Heat.Hot
            else -> Heat.Critical
        }
        update(state.copy(heat = heat))
    }

    private fun onProximity(event: SensorEvent) {
        near = event.values[0] < minOf(event.sensor.maximumRange, NEAR_CM)
        updateHandAndPocket(event.timestamp)
    }

    // Dark with hysteresis, the pocket is near plus dark for a moment, and a sudden jump to bright light squints.
    private fun onLight(event: SensorEvent) {
        val now = event.timestamp
        lux = event.values[0]
        if (lux < DIM_LUX) lastDimLight = now
        if (lux > BRIGHT_LUX && now - lastDimLight < BRIGHT_JUMP && now - lastTooBright > BRIGHT_COOLDOWN && !near) {
            lastTooBright = now
            face.react(FaceReaction.TooBright)
        }
        val dark = if (state.dark) lux < LIGHT_LUX else lux < DARK_LUX
        update(state.copy(dark = dark))
        updateHandAndPocket(now)
    }

    private fun updateHandAndPocket(now: Long) {
        val pocketLight = lux < POCKET_LUX
        if (near && pocketLight) {
            if (darkSince < 0L) darkSince = now
        } else {
            darkSince = -1L
        }
        val pocket = near && darkSince >= 0L && now - darkSince > POCKET_HOLD
        update(state.copy(handOver = near && !pocket, inPocket = pocket))
    }

    // Call from Activity.onKeyDown; it only observes, the key still changes the volume.
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
        updateTilt(x, y, now)
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

    // Tilt relative to how the phone is usually held: a slow baseline follows the grip, a quick filter smooths jitter.
    private fun updateTilt(x: Float, y: Float, now: Long) {
        val dt = if (lastMotion == 0L) 0f else ((now - lastMotion) / 1e9f).coerceAtMost(0.1f)
        lastMotion = now
        if (baseX.isNaN()) {
            baseX = x
            baseY = y
        }
        val slow = (dt / TILT_BASELINE_SECONDS).coerceAtMost(1f)
        val fast = (dt / TILT_SMOOTH_SECONDS).coerceAtMost(1f)
        baseX += (x - baseX) * slow
        baseY += (y - baseY) * slow
        smoothX += (-(x - baseX) / TILT_FULL - smoothX) * fast
        smoothY += ((y - baseY) / TILT_FULL - smoothY) * fast
        face.setTilt(smoothX.coerceIn(-1f, 1f), smoothY.coerceIn(-1f, 1f))
    }

    private fun update(next: DeviceState) {
        if (next == state) return
        state = next
        face.setDeviceState(next)
    }

    private companion object {
        fun sensors(context: Context): SensorManager? = context.applicationContext.getSystemService(SensorManager::class.java)

        const val DEFAULT_LUX = 100f
        const val DARK_LUX = 10f
        const val LIGHT_LUX = 25f
        const val POCKET_LUX = 5f
        const val DIM_LUX = 150f
        const val BRIGHT_LUX = 1_500f
        const val BRIGHT_JUMP = 1_000_000_000L
        const val BRIGHT_COOLDOWN = 5_000_000_000L
        const val NEAR_CM = 5f
        const val POCKET_HOLD = 1_000_000_000L
        const val TILT_BASELINE_SECONDS = 3f
        const val TILT_SMOOTH_SECONDS = 0.1f
        const val TILT_FULL = 4.9f
        const val CLOCK_MILLIS = 60_000L
        const val WELCOME_AFTER_MILLIS = 5_000L
        const val LONG_ABSENCE_MILLIS = 600_000L
        const val MORNING_START = 5
        const val DAY_START = 10
        const val EVENING_START = 18
        const val NIGHT_START = 23
        const val LATE_NIGHT_END = 1
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

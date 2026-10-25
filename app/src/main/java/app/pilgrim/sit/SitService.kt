package app.pilgrim.sit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import android.util.Log
import app.pilgrim.BuildConfig
import app.pilgrim.MainActivity
import app.pilgrim.R
import app.pilgrim.container
import app.pilgrim.domain.Geo
import app.pilgrim.domain.Kind
import app.pilgrim.domain.LatLon
import app.pilgrim.domain.Phase
import app.pilgrim.domain.SitRules
import app.pilgrim.domain.Why
import app.pilgrim.localized
import app.pilgrim.ru
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/** What a sitting is at: a place already in the book, or one from the common map. */
data class Target(
    val placeId: Long,
    val osm: String,
    val kind: Kind,
    val variant: Int,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radius: Double,
) {
    val at get() = LatLon(lat, lon)
}

/** A sitting as the screen sees it. */
data class SitState(
    val target: Target,
    val phase: Phase,
    val why: Why?,
    val sat: Long,
    /** Ms the screen has been lit, while it is. */
    val lit: Long?,
    /** Metres from the place, by the last good fix. */
    val metres: Double?,
    /** The stamp, once written. */
    val sittingId: Long = 0,
)

/** The one sitting there can be. */
object Session {
    val state = MutableStateFlow<SitState?>(null)

    fun begin(context: Context, target: Target) {
        state.value = SitState(target, Phase.DARKEN, null, 0, null, null)
        ContextCompat.startForegroundService(context, Intent(context, SitService::class.java))
    }

    /** "Get up". */
    fun stop(context: Context) {
        context.startService(Intent(context, SitService::class.java).setAction(SitService.STOP))
    }

    /** The result was seen: forget it. */
    fun clear() {
        if (state.value?.phase == Phase.DONE || state.value?.phase == Phase.BROKEN) state.value = null
    }
}

/**
 * Keeps the sitting honest while the screen is off: a foreground service with location, watching
 * the screen, the place and the phone's stillness, with a partial wake lock so the sensors keep
 * talking. Nothing is kept but the result.
 */
class SitService : Service(), SensorEventListener {
    private lateinit var rules: SitRules
    private lateinit var target: Target
    private val handler = Handler(Looper.getMainLooper())
    private var here: Here? = null
    private var lock: PowerManager.WakeLock? = null
    private var startedAt = 0L
    private var lastMetres: Double? = null
    private var buzzed = false
    private var finished = false
    private var lastNotified = -1L

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val now = System.currentTimeMillis()
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (rules.phase == Phase.DARKEN) startedAt = now
                    rules.screen(false, now)
                }
                Intent.ACTION_SCREEN_ON -> rules.screen(true, now)
                Intent.ACTION_USER_PRESENT -> rules.unlocked(now)
            }
            publish()
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            rules.tick(System.currentTimeMillis())
            publish()
            if (!finished) handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            if (::rules.isInitialized) {
                rules.stop(System.currentTimeMillis())
                publish()
            } else stopSelf()
            return START_NOT_STICKY
        }
        if (::rules.isInitialized) return START_NOT_STICKY
        val s = Session.state.value
        if (s == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        target = s.target
        rules = SitRules(target.radius, target.kind.slack)
        ServiceCompat.startForeground(this, ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        lock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pilgrim:sitting").apply { acquire(SitRules.CAP + 60_000) }
        ContextCompat.registerReceiver(
            this, screen,
            IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (!getSystemService(PowerManager::class.java).isInteractive) {
            startedAt = System.currentTimeMillis()
            rules.screen(false, startedAt)
        }
        here = Here(this, 4000L) { f ->
            // The demo's places are in Kazan wherever the phone is: there, only a faked location is checked.
            val m = if (BuildConfig.DEMO) 0.0 else Geo.distance(LatLon(f.lat, f.lon), target.at)
            if (f.accuracy <= SitRules.ROUGH) lastMetres = m
            rules.fix(System.currentTimeMillis(), m, f.accuracy, f.mock)
            publish()
        }.also { it.start() }
        getSystemService(SensorManager::class.java).let { sm ->
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        }
        handler.post(tick)
        return START_NOT_STICKY
    }

    // Motion: the spread of the acceleration's size over two-second windows. Lying on a bench it's
    // a few hundredths; walking with the phone, a couple of m/s².
    private var n = 0
    private var sum = 0.0
    private var sum2 = 0.0
    private var windowFrom = 0L

    override fun onSensorChanged(e: SensorEvent) {
        val a = sqrt((e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]).toDouble())
        val now = System.currentTimeMillis()
        if (windowFrom == 0L) windowFrom = now
        n++
        sum += a
        sum2 += a * a
        if (now - windowFrom >= 2000 && n > 3) {
            val mean = sum / n
            val sd = sqrt((sum2 / n - mean * mean).coerceAtLeast(0.0))
            rules.motion(sd > 1.4, now - windowFrom)
            n = 0; sum = 0.0; sum2 = 0.0; windowFrom = now
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun publish() {
        if (finished) return
        val now = System.currentTimeMillis()
        val lit = rules.litSince?.let { now - it }
        Session.state.value = SitState(target, rules.phase, rules.why, rules.sat, lit, lastMetres)
        if (rules.reached && !buzzed) {
            buzzed = true
            container.scope.launch { if (container.settings.now().buzz) Buzz(this@SitService).ten() }
            notifyNow()
        }
        val minute = rules.sat / 60_000
        if (minute != lastNotified && rules.phase == Phase.SITTING) { lastNotified = minute; notifyNow() }
        if (rules.over) finish()
    }

    private fun finish() {
        finished = true
        Log.i("Pilgrim", "sitting over: ${rules.phase} ${rules.why ?: ""} after ${rules.sat / 1000} s")
        handler.removeCallbacks(tick)
        val phase = rules.phase
        val state = SitState(target, phase, rules.why, rules.sat, null, lastMetres)
        Session.state.value = state
        if (phase == Phase.DONE) {
            val end = System.currentTimeMillis()
            val start = if (startedAt > 0) startedAt else end - rules.sat
            container.scope.launch {
                val id = container.saveSitting(target, start, end, (rules.sat / 1000).toInt())
                Session.state.value = state.copy(sittingId = id)
            }
        }
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        here?.stop()
        runCatching { unregisterReceiver(screen) }
        runCatching { getSystemService(SensorManager::class.java).unregisterListener(this) }
        lock?.let { if (it.isHeld) it.release() }
        // Killed from outside mid-sitting: it didn't count, but say so rather than spin forever.
        if (!finished && ::rules.isInitialized) Session.state.value = SitState(target, Phase.BROKEN, Why.STOPPED, rules.sat, null, lastMetres)
        super.onDestroy()
    }

    private fun notifyNow() {
        runCatching { getSystemService(NotificationManager::class.java).notify(ID, notification()) }
    }

    private fun notification(): Notification {
        val c = localized()
        val ru = c.ru
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, if (ru) "Сеанс" else "Sitting", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        val m = if (::rules.isInitialized) rules.sat / 60_000 else 0
        val text = when {
            !::rules.isInitialized || rules.phase == Phase.DARKEN -> if (ru) "Погасите экран — отсчёт пойдёт" else "Turn the screen off — the time will start"
            rules.reached -> if (ru) "$m мин. Можно остаться — время идёт" else "$m min. You may stay — the time runs on"
            else -> if (ru) "$m из 10 мин. Телефон лежит" else "$m of 10 min. The phone is resting"
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pilgrim)
            .setContentTitle(if (::target.isInitialized) target.name.ifBlank { target.kind.title(ru) } else c.getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val STOP = "app.pilgrim.STOP"
        private const val CHANNEL = "sitting"
        private const val ID = 7
    }
}

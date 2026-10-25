package app.pilgrim.sit

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/** A position fix: where the phone is, how sure it is, and whether an app is faking it. */
data class Fix(val lat: Double, val lon: Double, val accuracy: Float, val at: Long, val mock: Boolean = false)

fun Location.toFix() = Fix(
    latitude, longitude, if (hasAccuracy()) accuracy else 50f, time,
    mock = if (Build.VERSION.SDK_INT >= 31) isMock else @Suppress("DEPRECATION") isFromMockProvider,
)

/**
 * Where the phone is. Plain LocationManager (no Google services): GPS, with network fixes until
 * the satellites answer.
 */
class Here(private val context: Context, private val every: Long = 1000L, private val onFix: (Fix) -> Unit) {
    private val lm = context.getSystemService(LocationManager::class.java)
    private val listener = LocationListener { l -> onFix(l.toFix()) }

    fun permitted(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun on(): Boolean = LocationManagerCompat.isLocationEnabled(lm)

    /** Each provider on its own: with only approximate location allowed, GPS refuses, and that mustn't stop the others. */
    @SuppressLint("MissingPermission")
    fun start() {
        if (!permitted()) return
        val providers = lm.getProviders(true)
        for ((p, ms) in listOf(LocationManager.GPS_PROVIDER to every, "fused" to every, LocationManager.NETWORK_PROVIDER to every * 4)) {
            if (p in providers) runCatching { lm.requestLocationUpdates(p, ms, 0f, listener, Looper.getMainLooper()) }
        }
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 2 * 60_000L }
            .minByOrNull { it.accuracy }?.let { onFix(it.toFix()) }
    }

    /** The last place any provider knew, however old. */
    @SuppressLint("MissingPermission")
    fun lastKnown(): Fix? = if (!permitted()) null else lm.getProviders(true)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }?.toFix()

    fun stop() = lm.removeUpdates(listener)
}

/** The few buzzes the app makes: ten minutes in, and the stamp pressed. */
class Buzz(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java).defaultVibrator
    else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)

    /** Two soft pulses: ten minutes. Not an alarm; you may stay. */
    fun ten() {
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 260, 60), -1))
    }

    /** The thump of the stamp. */
    fun thump() {
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(
            if (vibrator.hasAmplitudeControl()) VibrationEffect.createWaveform(longArrayOf(0, 18, 40, 70), intArrayOf(0, 255, 0, 90), -1)
            else VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE),
        )
    }
}

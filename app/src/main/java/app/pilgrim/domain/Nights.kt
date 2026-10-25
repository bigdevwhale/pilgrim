package app.pilgrim.domain

import java.util.Locale
import kotlin.math.roundToLong

/**
 * Home, found without asking: where the phone is when the app is opened at night, counted once a
 * night, on a grid of about 300 m. The cell seen on the most nights (three at least) is home.
 */
object Nights {
    private const val CELL = 0.003

    fun isNight(hour: Int) = hour >= 22 || hour < 6

    fun cell(p: LatLon): String = "${(p.lat / CELL).roundToLong()}:${(p.lon / CELL).roundToLong()}"

    fun parse(s: String): Map<String, Int> = s.split(';').mapNotNull { e ->
        val k = e.substringBefore('=', "")
        val v = e.substringAfter('=', "").toIntOrNull()
        if (k.isEmpty() || v == null) null else k to v
    }.toMap()

    fun add(s: String, p: LatLon): String {
        val m = parse(s).toMutableMap()
        val k = cell(p)
        m[k] = (m[k] ?: 0) + 1
        // Keep the busiest cells only: a year of travel shouldn't grow this without end.
        return m.entries.sortedByDescending { it.value }.take(40).joinToString(";") { "${it.key}=${it.value}" }
    }

    fun home(s: String): LatLon? {
        val (k, n) = parse(s).maxByOrNull { it.value } ?: return null
        if (n < 3) return null
        val (a, b) = k.split(':').map { it.toLong() }
        return LatLon(a * CELL, b * CELL)
    }

    /** A cell's centre as "55.790,49.131", for the settings line. */
    fun show(p: LatLon) = "%.3f, %.3f".format(Locale.US, p.lat, p.lon)
}

/** Rules for marking a place by hand. */
object Marking {
    /** Not on your own doorstep. */
    const val FROM_HOME = 300.0

    /** Two new places a week at most. */
    const val PER_WEEK = 2

    /** A fix must be this good to put a place on the map. */
    const val ACCURACY = 30f

    /** Closer than this to a place already known: that place, not a new one. */
    const val TOO_CLOSE = 50.0

    /** A place on the common map can be tied to where you are if it's this close. */
    const val MOVE = 300.0

    enum class No { ROUGH, HOME, WEEK, CLOSE }

    fun check(fix: LatLon, accuracy: Float, home: LatLon?, markedThisWeek: Int, known: List<LatLon>): No? = when {
        accuracy > ACCURACY -> No.ROUGH
        home != null && Geo.distance(home, fix) < FROM_HOME -> No.HOME
        markedThisWeek >= PER_WEEK -> No.WEEK
        known.any { Geo.distance(it, fix) < TOO_CLOSE } -> No.CLOSE
        else -> null
    }
}

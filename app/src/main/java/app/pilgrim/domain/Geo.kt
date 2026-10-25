package app.pilgrim.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A point on the earth. */
data class LatLon(val lat: Double, val lon: Double)

object Geo {
    private const val R = 6_371_000.0

    /** Metres between two points, haversine. */
    fun distance(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * R * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Degrees clockwise from north, from [a] toward [b]. */
    fun bearing(a: LatLon, b: LatLon): Double {
        val f1 = Math.toRadians(a.lat)
        val f2 = Math.toRadians(b.lat)
        val dl = Math.toRadians(b.lon - a.lon)
        val y = sin(dl) * cos(f2)
        val x = cos(f1) * sin(f2) - sin(f1) * cos(f2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /** A point [metres] away from [p] toward [deg]. */
    fun offset(p: LatLon, metres: Double, deg: Double): LatLon {
        val d = metres / R
        val t = Math.toRadians(deg)
        val f1 = Math.toRadians(p.lat)
        val l1 = Math.toRadians(p.lon)
        val f2 = Math.asin(sin(f1) * cos(d) + cos(f1) * sin(d) * cos(t))
        val l2 = l1 + atan2(sin(t) * sin(d) * cos(f1), cos(d) - sin(f1) * sin(f2))
        return LatLon(Math.toDegrees(f2), Math.toDegrees(l2))
    }

    fun median(xs: List<Double>): Double {
        if (xs.isEmpty()) return 0.0
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    private val compassRu = listOf("к северу", "к северо-востоку", "к востоку", "к юго-востоку", "к югу", "к юго-западу", "к западу", "к северо-западу")
    private val compassEn = listOf("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west")
    private val shortRu = listOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")
    private val shortEn = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    private fun octant(deg: Double) = ((deg + 22.5) / 45).toInt() % 8

    /** "к юго-западу" / "south-west". */
    fun direction(deg: Double, ru: Boolean) = (if (ru) compassRu else compassEn)[octant(deg)]

    fun short(deg: Double, ru: Boolean) = (if (ru) shortRu else shortEn)[octant(deg)]

    /** "120 м" / "1,4 км". */
    fun metres(m: Double, ru: Boolean): String = when {
        m < 1000 -> "${((m / 10).roundToInt() * 10).coerceAtLeast(10)} ${if (ru) "м" else "m"}"
        else -> "%.1f %s".format(m / 1000, if (ru) "км" else "km").let { if (ru) it.replace('.', ',') else it.replace(',', '.') }
    }

    /** How far, as a walk: "в десяти минутах ходьбы" / "ten minutes' walk". Rumours speak like this. */
    fun walk(m: Double, ru: Boolean): String = when {
        m <= 450 -> if (ru) "в пяти минутах ходьбы" else "five minutes' walk"
        m <= 900 -> if (ru) "в десяти минутах ходьбы" else "ten minutes' walk"
        m <= 1300 -> if (ru) "в четверти часа ходьбы" else "a quarter of an hour's walk"
        m <= 1800 -> if (ru) "в двадцати минутах ходьбы" else "twenty minutes' walk"
        m <= 2700 -> if (ru) "в получасе ходьбы" else "half an hour's walk"
        else -> if (ru) "в часе ходьбы" else "an hour's walk"
    }
}

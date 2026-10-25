package app.pilgrim.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** The weather at a sitting, as the stamp remembers it. */
enum class Sky {
    CLEAR, CLOUDY, OVERCAST, FOG, DRIZZLE, RAIN, STORM, SNOW, SLEET;

    /** The ink bled into wet paper. */
    val wet get() = this == DRIZZLE || this == RAIN || this == STORM || this == SLEET

    /** Frost on the stamp. */
    val frost get() = this == SNOW || this == SLEET

    fun title(ru: Boolean): String = when (this) {
        CLEAR -> if (ru) "ясно" else "clear"
        CLOUDY -> if (ru) "облачно" else "clouds"
        OVERCAST -> if (ru) "пасмурно" else "overcast"
        FOG -> if (ru) "туман" else "fog"
        DRIZZLE -> if (ru) "морось" else "drizzle"
        RAIN -> if (ru) "дождь" else "rain"
        STORM -> if (ru) "гроза" else "storm"
        SNOW -> if (ru) "снег" else "snow"
        SLEET -> if (ru) "мокрый снег" else "sleet"
    }

    companion object {
        /** The WMO weather code Open-Meteo reports. */
        fun wmo(code: Int): Sky = when (code) {
            0, 1 -> CLEAR
            2 -> CLOUDY
            3 -> OVERCAST
            45, 48 -> FOG
            51, 53, 55, 56, 57 -> DRIZZLE
            61, 63, 65, 80, 81, 82 -> RAIN
            66, 67 -> SLEET
            71, 73, 75, 77, 85, 86 -> SNOW
            95, 96, 99 -> STORM
            else -> CLOUDY
        }

        fun of(s: String?) = entries.firstOrNull { it.name == s }
    }
}

/** The colour of the ink, by where the sun was. */
enum class Ink(val argb: Int) {
    DAWN(0xFFA2741C.toInt()),
    DAY(0xFF8C5A2C.toInt()),
    DUSK(0xFFA8342A.toInt()),
    NIGHT(0xFF2F3B75.toInt()),
    ;

    fun title(ru: Boolean): String = when (this) {
        DAWN -> if (ru) "рассвет" else "dawn"
        DAY -> if (ru) "день" else "day"
        DUSK -> if (ru) "закат" else "dusk"
        NIGHT -> if (ru) "ночь" else "night"
    }

    fun ink(ru: Boolean): String = when (this) {
        DAWN -> if (ru) "золото" else "gold"
        DAY -> if (ru) "охра" else "ochre"
        DUSK -> if (ru) "киноварь" else "cinnabar"
        NIGHT -> if (ru) "индиго" else "indigo"
    }

    companion object {
        fun of(s: String?) = entries.firstOrNull { it.name == s } ?: DAY

        /**
         * Gold from three quarters of an hour before sunrise to an hour and a quarter after it,
         * cinnabar the same way around sunset, ochre between, indigo in the dark. Where the sun
         * doesn't rise or set that day, the clock decides.
         */
        fun at(at: Long, lat: Double, lon: Double, zone: ZoneId = ZoneId.systemDefault()): Ink {
            val day = Instant.ofEpochMilli(at).atOffset(ZoneOffset.UTC).toLocalDate()
            var polar = false
            for (d in -1L..1L) {
                val date = day.plusDays(d)
                val (rise, set) = Sun.times(date, lat, lon) ?: run { polar = true; continue }
                val midnight = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                val r = midnight + (rise * 60_000).toLong()
                val s = midnight + (set * 60_000).toLong()
                if (at in (r - 45 * 60_000)..<(r + 75 * 60_000)) return DAWN
                if (at in (s - 75 * 60_000)..<(s + 45 * 60_000)) return DUSK
                if (at in r..<s) return DAY
            }
            if (!polar) return NIGHT
            val h = Instant.ofEpochMilli(at).atZone(zone).hour
            return when (h) {
                in 5..6 -> DAWN
                in 7..17 -> DAY
                in 18..20 -> DUSK
                else -> NIGHT
            }
        }
    }
}

/** Sunrise and sunset, NOAA's short formula: good to a couple of minutes, enough for ink. */
object Sun {
    /** Minutes after UTC midnight of [date]; null where the sun stays up or down all day. */
    fun times(date: LocalDate, lat: Double, lon: Double): Pair<Double, Double>? {
        val g = 2 * Math.PI / 365 * (date.dayOfYear - 1)
        val eq = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val phi = Math.toRadians(lat)
        val x = cos(Math.toRadians(90.833)) / (cos(phi) * cos(decl)) - tan(phi) * tan(decl)
        if (x < -1 || x > 1) return null
        val ha = Math.toDegrees(acos(x))
        return (720 - 4 * (lon + ha) - eq) to (720 - 4 * (lon - ha) - eq)
    }
}

/** Meteorological seasons, turned around south of the equator. */
enum class Season {
    WINTER, SPRING, SUMMER, AUTUMN;

    fun title(ru: Boolean): String = when (this) {
        WINTER -> if (ru) "зима" else "winter"
        SPRING -> if (ru) "весна" else "spring"
        SUMMER -> if (ru) "лето" else "summer"
        AUTUMN -> if (ru) "осень" else "autumn"
    }

    companion object {
        fun of(date: LocalDate, lat: Double): Season {
            val north = when (date.monthValue) {
                12, 1, 2 -> WINTER
                3, 4, 5 -> SPRING
                6, 7, 8 -> SUMMER
                else -> AUTUMN
            }
            return if (lat >= 0) north else entries[(north.ordinal + 2) % 4]
        }
    }
}

package app.pilgrim.data

import app.pilgrim.domain.Kind
import app.pilgrim.domain.Sky
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot

private const val AGENT = "Pilgrim/1.0 (github.com/bigdevwhale/pilgrim)"

private fun http(url: String, post: String? = null, timeout: Int = 8000): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 7000
    c.readTimeout = timeout
    c.setRequestProperty("User-Agent", AGENT)
    try {
        if (post != null) {
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            c.outputStream.use { it.write(post.toByteArray()) }
        }
        if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
        return c.inputStream.bufferedReader().use { it.readText() }
    } finally {
        c.disconnect()
    }
}

/**
 * Places of power from OpenStreetMap through Overpass: houses of worship, wayside chapels, springs,
 * named or protected trees and stones, peaks and viewpoints. Only the rounded point asked about is sent.
 */
object Overpass {
    private val servers = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
    )

    fun query(lat: Double, lon: Double, radius: Int): String {
        val a = "around:$radius,%.4f,%.4f".format(Locale.US, lat, lon)
        return """
            [out:json][timeout:30];
            (
              nwr["amenity"="place_of_worship"]($a);
              nwr["historic"~"^(wayside_shrine|chapel)$"]($a);
              nwr["natural"="spring"]($a);
              node["natural"="tree"]["denotation"~"natural_monument|landmark"]($a);
              node["natural"="tree"]["name"]($a);
              nwr["natural"~"^(stone|rock)$"]["name"]($a);
              node["natural"="peak"]["name"]($a);
              node["tourism"="viewpoint"]["name"]($a);
            );
            out tags bb 600;
        """.trimIndent()
    }

    fun fetch(lat: Double, lon: Double, radius: Int, now: Long = System.currentTimeMillis()): List<Poi> {
        val body = "data=" + URLEncoder.encode(query(lat, lon, radius), "UTF-8")
        var last: Throwable? = null
        for (s in servers) {
            val r = runCatching { parse(http(s, body, timeout = 35_000), now) }
            if (r.isSuccess) return r.getOrThrow()
            last = r.exceptionOrNull()
        }
        throw last ?: IllegalStateException("no server")
    }

    fun parse(json: String, now: Long): List<Poi> {
        val els = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        val out = ArrayList<Poi>()
        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            val tags = e.optJSONObject("tags") ?: continue
            val map = tags.keys().asSequence().associateWith { tags.optString(it) }
            val (kind, variant) = classify(map) ?: continue
            val b = e.optJSONObject("bounds")
            val c = e.optJSONObject("center")
            val (lat, lon, radius) = when {
                e.has("lat") -> Triple(e.getDouble("lat"), e.getDouble("lon"), kind.radius)
                b != null -> {
                    val la = (b.getDouble("minlat") + b.getDouble("maxlat")) / 2
                    val lo = (b.getDouble("minlon") + b.getDouble("maxlon")) / 2
                    val h = (b.getDouble("maxlat") - b.getDouble("minlat")) * 111_320
                    val w = (b.getDouble("maxlon") - b.getDouble("minlon")) * 111_320 * cos(Math.toRadians(la))
                    // The area itself and a margin around it: a mosque's courtyard, a monastery's wall.
                    Triple(la, lo, (hypot(h, w) / 2 + 25).coerceIn(kind.radius, 300.0))
                }
                c != null -> Triple(c.getDouble("lat"), c.getDouble("lon"), kind.radius)
                else -> continue
            }
            val name = map["name"] ?: map["name:ru"] ?: map["name:en"] ?: ""
            out += Poi("${e.getString("type")}/${e.getLong("id")}", kind.name, variant, name.take(60), lat, lon, radius, now)
        }
        return out
    }

    /** A map feature's kind and silhouette variant, or null if it isn't a place of power. */
    fun classify(tags: Map<String, String>): Pair<Kind, Int>? {
        val religion = tags["religion"].orEmpty()
        val denomination = tags["denomination"].orEmpty()
        val building = tags["building"].orEmpty()
        return when {
            tags["amenity"] == "place_of_worship" -> when {
                building == "chapel" || tags["place_of_worship"] == "chapel" -> Kind.SHRINE to 0
                religion == "christian" -> Kind.CHURCH to (if ("orthodox" in denomination || denomination == "old_believers") 0 else 1)
                religion == "muslim" -> Kind.MOSQUE to 0
                religion == "jewish" -> Kind.SYNAGOGUE to 0
                religion.isNotEmpty() -> Kind.TEMPLE to 0
                building == "church" || building == "cathedral" -> Kind.CHURCH to 0
                building == "mosque" -> Kind.MOSQUE to 0
                building == "synagogue" -> Kind.SYNAGOGUE to 0
                else -> Kind.TEMPLE to 0
            }
            tags["historic"] == "wayside_shrine" || tags["historic"] == "chapel" -> Kind.SHRINE to 0
            tags["natural"] == "spring" -> Kind.SPRING to 0
            tags["natural"] == "tree" -> Kind.TREE to 0
            tags["natural"] == "stone" || tags["natural"] == "rock" -> Kind.STONE to 0
            tags["natural"] == "peak" || tags["tourism"] == "viewpoint" -> Kind.HEIGHT to 0
            else -> null
        }
    }
}

data class WeatherNow(val sky: Sky, val temp: Double, val windy: Boolean)

/** The weather at the place: Open-Meteo, or MET Norway where Open-Meteo is blocked. No key, rounded point. */
object Weather {
    fun fetch(lat: Double, lon: Double): WeatherNow = runCatching { openMeteo(lat, lon) }.getOrElse { metNo(lat, lon) }

    private fun openMeteo(lat: Double, lon: Double): WeatherNow {
        val j = JSONObject(http("https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code,wind_speed_10m".format(Locale.US, lat, lon)))
        val cur = j.getJSONObject("current")
        return WeatherNow(Sky.wmo(cur.getInt("weather_code")), cur.getDouble("temperature_2m"), cur.optDouble("wind_speed_10m", 0.0) >= 30)
    }

    private fun metNo(lat: Double, lon: Double): WeatherNow {
        val j = JSONObject(http("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.3f&lon=%.3f".format(Locale.US, lat, lon)))
        val first = j.getJSONObject("properties").getJSONArray("timeseries").getJSONObject(0).getJSONObject("data")
        val d = first.getJSONObject("instant").getJSONObject("details")
        val symbol = first.optJSONObject("next_1_hours")?.optJSONObject("summary")?.optString("symbol_code")
            ?: first.optJSONObject("next_6_hours")?.optJSONObject("summary")?.optString("symbol_code") ?: "cloudy"
        return WeatherNow(symbol(symbol), d.getDouble("air_temperature"), d.optDouble("wind_speed", 0.0) >= 8.5)
    }

    /** MET Norway's symbol codes ("lightrainshowers_day", "fog"…) as skies. */
    fun symbol(code: String): Sky {
        val c = code.substringBefore('_')
        return when {
            "thunder" in c -> Sky.STORM
            "sleet" in c -> Sky.SLEET
            "snow" in c -> Sky.SNOW
            c.startsWith("light") && "rain" in c -> Sky.DRIZZLE
            "rain" in c -> Sky.RAIN
            c == "fog" -> Sky.FOG
            c == "cloudy" -> Sky.OVERCAST
            c == "partlycloudy" -> Sky.CLOUDY
            else -> Sky.CLEAR
        }
    }
}

/**
 * A note for the mappers: "a spring here, not on the map". Anonymous OSM Notes need no account;
 * only the point, the kind and a fixed sentence are sent, never the person's own words.
 */
object OsmNote {
    fun text(kind: Kind, days: Int): String {
        val what = when (kind) {
            Kind.SPRING -> "A spring (natural=spring)"
            Kind.TREE -> "An old tree (natural=tree)"
            Kind.STONE -> "A notable stone (natural=stone)"
            Kind.HEIGHT -> "A viewpoint (tourism=viewpoint)"
            Kind.SHRINE -> "A wayside chapel or shrine (historic=wayside_shrine)"
            Kind.CHURCH, Kind.MOSQUE, Kind.SYNAGOGUE, Kind.TEMPLE -> "A place of worship (amenity=place_of_worship)"
            Kind.OWN -> "A quiet place"
        }
        return "$what is here but not on the map. Visited on $days different days. Sent from the Pilgrim app (github.com/bigdevwhale/pilgrim)."
    }

    fun send(lat: Double, lon: Double, text: String) {
        val body = "lat=%.6f&lon=%.6f&text=%s".format(Locale.US, lat, lon, URLEncoder.encode(text, "UTF-8"))
        http("https://api.openstreetmap.org/api/0.6/notes.json", body)
    }
}

package app.pilgrim.data

import app.pilgrim.AppContainer
import app.pilgrim.domain.Ink
import app.pilgrim.domain.Kind
import app.pilgrim.domain.Origin
import app.pilgrim.domain.Sky
import app.pilgrim.sit.Fix
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * A year of a book for the demo build and the renders: Kazan, where a mosque and a cathedral stand
 * a few hundred metres apart, a bench by the lake that became a place of its own, and a trip to
 * St Petersburg with a walk between four houses of four faiths.
 */
object DemoSeed {
    private val zone = ZoneId.of("Europe/Moscow")

    /** Where the demo phone is: in the courtyard of the Marjani mosque. */
    val here = Fix(55.77815, 49.11655, 8f, 0L)

    private class P(val key: String, val kind: Kind, val variant: Int, val ru: String, val en: String, val lat: Double, val lon: Double, val origin: Origin = Origin.OSM, val radius: Double = kind.radius)

    private val places = listOf(
        P("marjani", Kind.MOSQUE, 0, "Мечеть Марджани", "Marjani Mosque", 55.77810, 49.11660, radius = 70.0),
        P("blag", Kind.CHURCH, 0, "Благовещенский собор", "Annunciation Cathedral", 55.79990, 49.10770, radius = 80.0),
        P("kul", Kind.MOSQUE, 0, "Кул Шариф", "Qolşärif Mosque", 55.79830, 49.10520, radius = 90.0),
        P("petro", Kind.CHURCH, 0, "Петропавловский собор", "Peter and Paul Cathedral", 55.79200, 49.11290, radius = 70.0),
        P("all", Kind.TEMPLE, 0, "Храм всех религий", "Temple of All Religions", 55.80080, 48.97640, radius = 70.0),
        P("spring", Kind.SPRING, 0, "Родник у Лебяжьего", "Spring by Lebyazhye", 55.77380, 48.98120),
        P("oak", Kind.TREE, 0, "Старый дуб", "The old oak", 55.75700, 49.15300),
        P("kaban", Kind.OWN, 0, "Скамейка у Кабана", "Bench by Lake Kaban", 55.77680, 49.12400, Origin.OWN),
        P("yard", Kind.OWN, 0, "Двор детства", "Childhood yard", 55.78620, 49.13560, Origin.OWN),
        P("kazan", Kind.CHURCH, 0, "Казанский собор", "Kazan Cathedral", 59.93420, 30.32460, radius = 90.0),
        P("datsan", Kind.TEMPLE, 0, "Дацан Гунзэчойнэй", "Datsan Gunzechoinei", 59.98400, 30.25800),
        P("syn", Kind.SYNAGOGUE, 0, "Большая хоральная синагога", "Grand Choral Synagogue", 59.92270, 30.29430, radius = 60.0),
        P("mosque", Kind.MOSQUE, 0, "Соборная мечеть", "Cathedral Mosque", 59.95500, 30.32360, radius = 70.0),
        P("xenia", Kind.SHRINE, 0, "Часовня Ксении", "Xenia's Chapel", 59.94830, 30.24930),
    )

    private class S(val date: String, val time: String, val min: Int, val place: String, val sky: Sky, val windy: Boolean, val left: Pair<String, String>, val took: Pair<String, String>)

    private val sittings = listOf(
        S("2025-10-04", "18:05", 14, "kaban", Sky.CLOUDY, false, "спешку" to "hurry", "воздух" to "air"),
        S("2025-10-19", "07:20", 11, "kaban", Sky.FOG, false, "сон" to "sleep", "туман" to "fog"),
        S("2025-11-08", "12:30", 10, "marjani", Sky.OVERCAST, false, "суету" to "fuss", "порядок" to "order"),
        S("2025-11-22", "15:40", 18, "petro", Sky.SNOW, false, "холод" to "cold", "свет" to "light"),
        S("2025-12-28", "16:10", 21, "kaban", Sky.SNOW, true, "обиду" to "grudge", "тишину" to "silence"),
        S("2026-01-17", "13:00", 12, "blag", Sky.CLEAR, false, "страх" to "fear", "тепло" to "warmth"),
        S("2026-02-07", "21:30", 10, "marjani", Sky.SNOW, false, "тревогу" to "worry", "сон" to "sleep"),
        S("2026-03-21", "06:20", 15, "spring", Sky.FOG, false, "зиму" to "winter", "воду" to "water"),
        S("2026-03-29", "17:50", 13, "kaban", Sky.DRIZZLE, false, "злость" to "anger", "дождь" to "rain"),
        S("2026-04-25", "10:00", 25, "oak", Sky.CLOUDY, false, "" to "", "" to ""),
        S("2026-05-09", "19:40", 16, "all", Sky.CLEAR, true, "гордость" to "pride", "простор" to "space"),
        S("2026-05-30", "09:30", 10, "syn", Sky.CLOUDY, false, "шум" to "noise", "память" to "memory"),
        S("2026-05-30", "11:40", 12, "kazan", Sky.CLEAR, false, "усталость" to "tiredness", "прохладу" to "coolness"),
        S("2026-05-30", "14:20", 11, "mosque", Sky.CLEAR, false, "жару" to "heat", "тень" to "shade"),
        S("2026-05-30", "18:30", 20, "datsan", Sky.CLEAR, true, "спешку" to "hurry", "ветер" to "wind"),
        S("2026-05-31", "10:15", 10, "xenia", Sky.RAIN, false, "сомнение" to "doubt", "надежду" to "hope"),
        S("2026-06-27", "21:20", 30, "kaban", Sky.CLEAR, false, "жару" to "heat", "прохладу" to "coolness"),
        S("2026-07-18", "12:10", 10, "kul", Sky.CLEAR, false, "суету" to "fuss", "высоту" to "height"),
        S("2026-08-15", "04:50", 12, "spring", Sky.CLEAR, false, "бессонницу" to "insomnia", "холод" to "chill"),
        S("2026-09-12", "18:20", 14, "marjani", Sky.RAIN, false, "обиду" to "grudge", "терпение" to "patience"),
        S("2026-10-03", "14:00", 42, "oak", Sky.OVERCAST, true, "лето" to "summer", "листья" to "leaves"),
        S("2026-10-18", "16:30", 12, "kaban", Sky.DRIZZLE, false, "усталость" to "tiredness", "покой" to "calm"),
        S("2026-10-24", "17:10", 10, "yard", Sky.CLOUDY, false, "взрослость" to "grown-up-ness", "детство" to "childhood"),
    )

    private fun at(date: String, time: String) = LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    suspend fun seedIfEmpty(c: AppContainer, ru: Boolean = Locale.getDefault().language == "ru") {
        val dao = c.db.dao()
        if (dao.places().isNotEmpty()) return
        val ids = HashMap<String, Long>()
        val first = sittings.groupBy { it.place }.mapValues { (_, v) -> at(v.first().date, v.first().time) }
        for ((i, p) in places.withIndex()) {
            ids[p.key] = dao.insert(
                Place(
                    osm = if (p.origin == Origin.OWN) "" else "node/${9_100_000 + i}", origin = p.origin.name, kind = p.kind.name, variant = p.variant,
                    name = if (ru) p.ru else p.en, title = if (p.key == "kaban") (if (ru) "Тихая вода" else "Still Water") else "",
                    lat = p.lat, lon = p.lon, radius = p.radius, createdAt = (first[p.key] ?: 0L) - 60_000, seed = 1000L + i * 7919,
                ),
            )
        }
        for (s in sittings) {
            val start = at(s.date, s.time)
            val end = start + s.min * 60_000L
            val p = places.first { it.key == s.place }
            dao.insert(
                Sitting(
                    placeId = ids.getValue(s.place), startAt = start, endAt = end, seconds = s.min * 60 + 17,
                    ink = Ink.at((start + end) / 2, p.lat, p.lon, zone).name, sky = s.sky.name, windy = s.windy,
                    temp = when (s.date.substring(5, 7)) { "12", "01", "02" -> -9; "03", "11" -> 1; "04", "10" -> 7; else -> 19 },
                    left = if (ru) s.left.first else s.left.second, took = if (ru) s.took.first else s.took.second,
                ),
            )
        }
        dao.insert(Note(placeId = ids.getValue("marjani"), text = if (ru) "Если ты здесь снова — значит, зима прошла не зря." else "If you're here again, the winter wasn't wasted.", leftAt = at("2025-11-08", "12:45"), openedAt = at("2026-02-07", "21:45")))
        dao.insert(Note(placeId = ids.getValue("kaban"), text = if (ru) "Летом тут пахнет липой. Запомни, какой была вода." else "In summer it smells of linden here. Remember what the water was like.", leftAt = at("2026-06-27", "21:55")))
        dao.insert(Note(placeId = ids.getValue("oak"), text = if (ru) "Осень. Посмотри, каким он стал без листьев." else "Autumn. See what he looks like without leaves.", leftAt = at("2026-10-03", "14:50")))

        val now = System.currentTimeMillis()
        dao.insertPois(
            listOf(
                Poi("node/9200001", Kind.MOSQUE.name, 0, if (ru) "Мечеть Марджани" else "Marjani Mosque", 55.77810, 49.11660, 70.0, now),
                Poi("node/9200002", Kind.MOSQUE.name, 0, if (ru) "Галеевская мечеть" else "Galeev Mosque", 55.77700, 49.11400, 50.0, now),
                Poi("node/9200003", Kind.MOSQUE.name, 0, if (ru) "Апанаевская мечеть" else "Apanaev Mosque", 55.77990, 49.11830, 50.0, now),
                Poi("node/9200004", Kind.SPRING.name, 0, if (ru) "Ключик" else "Little Key", 55.78750, 49.12450, 35.0, now),
                Poi("node/9200005", Kind.TREE.name, 0, "", 55.78310, 49.15080, 30.0, now),
            ),
        )
        c.settings.update {
            it.copy(
                owner = if (ru) "Анна" else "Anna", homeLat = 55.7900, homeLon = 49.1300, nights = "18597:16377=41",
                rumor = "node/9200004", rumorAt = now, told = "node/9200004",
                // A notebook is named by its first place: the bench in Kazan, the synagogue in Petersburg.
                tetrads = "${ids.getValue("kaban")}=${if (ru) "Казань" else "Kazan"}\n${ids.getValue("syn")}=${if (ru) "Петербург" else "St Petersburg"}",
                mannersSeen = "MOSQUE,CHURCH,OWN",
            )
        }
    }
}

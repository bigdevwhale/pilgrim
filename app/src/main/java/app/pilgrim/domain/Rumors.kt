package app.pilgrim.domain

import app.pilgrim.data.Poi
import kotlin.random.Random

/**
 * Once a week the book tells of one place nearby, the way people tell of a spring: roughly where,
 * not exactly. The rest of the common map stays hidden; places are found, not chosen from a list.
 * Only places from the common map are told of: places people marked themselves stay theirs.
 */
object Rumors {
    const val WEEK = 7 * 24 * 60 * 60_000L
    const val NEAR = 300.0
    const val FAR = 3_000.0

    /** How far the circle on the map is from the place, at most, and how big it is. */
    const val FUZZ = 110.0
    const val CIRCLE = 220.0

    /**
     * This week's place: within walking distance of [from], not sat at, not told of before. Named
     * places and kinds not in the book yet come first.
     */
    fun pick(pois: List<Poi>, from: LatLon, visited: Set<String>, told: Set<String>, kindsInBook: Set<Kind>, seed: Long): Poi? {
        val fit = pois.filter { it.osm !in visited && it.osm !in told && Geo.distance(from, it.at) in NEAR..FAR }
        if (fit.isEmpty()) return null
        val best = fit.maxOf { score(it, kindsInBook) }
        val top = fit.filter { score(it, kindsInBook) == best }.sortedBy { it.osm }
        return top[Random(seed).nextInt(top.size)]
    }

    private fun score(p: Poi, kinds: Set<Kind>) = (if (p.name.isNotBlank()) 2 else 0) + (if (p.kindOf !in kinds) 1 else 0) + (if (p.kindOf == Kind.HEIGHT) -1 else 0)

    /** Where the rumour's circle sits: off the place by up to [FUZZ], the same every time. */
    fun circle(p: Poi): LatLon {
        val r = Random(p.osm.hashCode().toLong())
        return Geo.offset(p.at, FUZZ * (.4 + r.nextDouble() * .6), r.nextDouble() * 360)
    }

    /** "Говорят, в десяти минутах ходьбы к северу от дома бьёт родник — «Ключик». …" */
    fun text(p: Poi, from: LatLon, home: Boolean, ru: Boolean): String {
        val m = Geo.distance(from, p.at)
        val dir = Geo.direction(Geo.bearing(from, p.at), ru)
        val name = p.name.takeIf { it.isNotBlank() }?.let { if (ru) " — «$it»" else " — “$it”" }.orEmpty()
        val where = if (ru) "${Geo.walk(m, true)} $dir ${if (home) "от дома" else "отсюда"}" else "${Geo.walk(m, false)} $dir of ${if (home) "home" else "here"}"
        return if (ru) when (p.kindOf) {
            Kind.SPRING -> "Говорят, $where бьёт родник$name. Вода в нём холодная даже в июле."
            Kind.TREE -> "Говорят, $where растёт старое дерево$name. Его помнят дольше, чем дома вокруг."
            Kind.STONE -> "Говорят, $where лежит камень$name. Мимо проходят, не глядя, — а зря."
            Kind.HEIGHT -> "Говорят, $where есть место, откуда далеко видно$name."
            Kind.SHRINE -> "Говорят, $where стоит часовня$name. Её мало кто замечает."
            Kind.CHURCH -> "Говорят, $where стоит церковь$name. Туда заходят и просто посидеть."
            Kind.MOSQUE -> "Говорят, $where есть мечеть$name. Между намазами во дворе тихо."
            Kind.SYNAGOGUE -> "Говорят, $where есть синагога$name. Старая, со своими историями."
            Kind.TEMPLE -> "Говорят, $where есть храм$name, где пахнет благовониями."
            Kind.OWN -> "Говорят, $where есть тихое место$name."
        } else when (p.kindOf) {
            Kind.SPRING -> "They say there's a spring $where$name. The water is cold even in July."
            Kind.TREE -> "They say an old tree grows $where$name. It's remembered longer than the houses around it."
            Kind.STONE -> "They say a stone lies $where$name. People walk past without a look — a pity."
            Kind.HEIGHT -> "They say there's a place you can see far from, $where$name."
            Kind.SHRINE -> "They say a small chapel stands $where$name. Few people notice it."
            Kind.CHURCH -> "They say there's a church $where$name. People drop in just to sit."
            Kind.MOSQUE -> "They say there's a mosque $where$name. Between prayers the courtyard is quiet."
            Kind.SYNAGOGUE -> "They say there's a synagogue $where$name. An old one, with its stories."
            Kind.TEMPLE -> "They say there's a temple $where$name, smelling of incense."
            Kind.OWN -> "They say there's a quiet place $where$name."
        }
    }
}

package app.pilgrim.domain

import app.pilgrim.data.Place
import app.pilgrim.data.Sitting
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun day(at: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()

/** One stamp as the book shows it: the sitting, its place, and how it is printed. */
data class Stamp(
    val sitting: Sitting,
    val place: Place,
    /** The n-th sitting at this place. */
    val number: Int,
    /** A marked place still in pencil: fewer than three days there yet. */
    val pencil: Boolean,
)

/** A notebook of the book: the places around one town. The first is home's. */
data class Tetrad(val index: Int, val anchor: Place, val places: List<Place>) {
    val ids = places.map { it.id }.toSet()
}

/** A page of a notebook: up to four stamps, or one day's walk between places. */
sealed class Page {
    data class Stamps(val stamps: List<Stamp>) : Page()
    data class Walk(val date: LocalDate, val stamps: List<Stamp>) : Page()
}

object Book {
    /** Places farther than this from a notebook's first place start a notebook of their own. */
    const val FAR = 80_000.0

    /** Days a marked place needs before it is inked. */
    const val DAYS = 3

    /** A day with this many places is a walk: a page of its own, the stamps on one thread. */
    const val WALK = 3

    fun stamps(places: List<Place>, sittings: List<Sitting>): List<Stamp> {
        val byId = places.associateBy { it.id }
        val count = HashMap<Long, Int>()
        val days = HashMap<Long, MutableSet<LocalDate>>()
        return sittings.sortedBy { it.startAt }.mapNotNull { s ->
            val p = byId[s.placeId] ?: return@mapNotNull null
            val n = (count[p.id] ?: 0) + 1
            count[p.id] = n
            val d = days.getOrPut(p.id) { HashSet() }
            d += day(s.startAt)
            Stamp(s, p, n, pencil = p.originOf == Origin.OWN && d.size < DAYS)
        }
    }

    /** Distinct days sat at a place. */
    fun days(placeId: Long, sittings: List<Sitting>): Int = sittings.filter { it.placeId == placeId }.map { day(it.startAt) }.toSet().size

    /** A marked place still waiting for its third day. */
    fun pencil(p: Place, sittings: List<Sitting>) = p.originOf == Origin.OWN && days(p.id, sittings) < DAYS

    /**
     * Places grouped into notebooks: home's first, then each faraway town in the order it was
     * first visited.
     */
    fun tetrads(places: List<Place>, sittings: List<Sitting>, home: LatLon?): List<Tetrad> {
        if (places.isEmpty()) return emptyList()
        val first = sittings.groupBy { it.placeId }.mapValues { (_, v) -> v.minOf { it.startAt } }
        val order = places.sortedBy { first[it.id] ?: it.createdAt }
        val anchors = ArrayList<LatLon>()
        val members = ArrayList<ArrayList<Place>>()
        // Home's notebook comes first even when the first place was sat at on a trip.
        home?.let { anchors.add(it); members.add(arrayListOf()) }
        for (p in order) {
            var i = anchors.indexOfFirst { Geo.distance(it, p.at) <= FAR }
            if (i < 0) {
                anchors += p.at
                members.add(arrayListOf())
                i = anchors.lastIndex
            }
            members[i] += p
        }
        return anchors.indices.filter { members[it].isNotEmpty() }.mapIndexed { n, i -> Tetrad(n, members[i].first(), members[i]) }
    }

    /** A notebook's pages, in time: four stamps to a page, a walk on a page of its own. */
    fun pages(t: Tetrad, stamps: List<Stamp>): List<Page> {
        val mine = stamps.filter { it.place.id in t.ids }
        val walks = mine.groupBy { day(it.sitting.startAt) }.filterValues { v -> v.map { it.place.id }.toSet().size >= WALK }
        val pages = ArrayList<Page>()
        var run = ArrayList<Stamp>()
        val walked = HashSet<LocalDate>()
        for (s in mine) {
            val d = day(s.sitting.startAt)
            if (d in walks) {
                if (d in walked) continue
                walked += d
                if (run.isNotEmpty()) { pages += Page.Stamps(run); run = ArrayList() }
                pages += Page.Walk(d, walks.getValue(d).take(7))
                continue
            }
            run += s
            if (run.size == 4) { pages += Page.Stamps(run); run = ArrayList() }
        }
        if (run.isNotEmpty()) pages += Page.Stamps(run)
        return pages
    }

    /** The seasons sat in at a place; all four close the year's circle. */
    fun seasons(p: Place, sittings: List<Sitting>): Map<Season, Sitting> =
        sittings.filter { it.placeId == p.id }.sortedBy { it.startAt }.associateBy { Season.of(day(it.startAt), p.lat) }

    fun circle(p: Place, sittings: List<Sitting>) = seasons(p, sittings).size == 4

    /** Days that were walks, newest first. */
    fun walks(stamps: List<Stamp>): List<Pair<LocalDate, List<Stamp>>> =
        stamps.groupBy { day(it.sitting.startAt) }.filterValues { v -> v.map { it.place.id }.toSet().size >= WALK }
            .toList().sortedByDescending { it.first }
}

/** A year rolled up. */
data class Scroll(
    val year: Int,
    val stamps: List<Stamp>,
    val seconds: Long,
    val places: Int,
    val walks: Int,
    val circles: Int,
    val words: List<Pair<String, String>>,
) {
    companion object {
        fun of(year: Int, stamps: List<Stamp>, sittings: List<Sitting>): Scroll {
            val mine = stamps.filter { day(it.sitting.startAt).year == year }
            val circled = mine.map { it.place }.distinctBy { it.id }.count { p ->
                Book.seasons(p, sittings.filter { day(it.startAt).year <= year }).size == 4
            }
            return Scroll(
                year = year,
                stamps = mine,
                seconds = mine.sumOf { it.sitting.seconds.toLong() },
                places = mine.map { it.place.id }.toSet().size,
                walks = Book.walks(mine).size,
                circles = circled,
                words = mine.map { it.sitting.left to it.sitting.took }.filter { it.first.isNotBlank() || it.second.isNotBlank() },
            )
        }
    }
}

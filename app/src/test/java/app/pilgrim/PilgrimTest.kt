package app.pilgrim

import app.pilgrim.data.Overpass
import app.pilgrim.data.Place
import app.pilgrim.data.Poi
import app.pilgrim.data.Sitting
import app.pilgrim.domain.Book
import app.pilgrim.domain.Geo
import app.pilgrim.domain.Ink
import app.pilgrim.domain.Kind
import app.pilgrim.domain.LatLon
import app.pilgrim.domain.Marking
import app.pilgrim.domain.Nights
import app.pilgrim.domain.Origin
import app.pilgrim.domain.Page
import app.pilgrim.domain.Phase
import app.pilgrim.domain.Rumors
import app.pilgrim.domain.Season
import app.pilgrim.domain.SitRules
import app.pilgrim.domain.Sun
import app.pilgrim.domain.Why
import app.pilgrim.domain.Words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class PilgrimTest {
    private val min = 60_000L

    private fun sitting(rules: SitRules, from: Long, to: Long, step: Long = 1000) {
        var t = from
        while (t <= to && !rules.over) { rules.tick(t); t += step }
    }

    @Test fun tenMinutesInTheDarkMakeAStamp() {
        val r = SitRules(60.0, 30.0)
        r.tick(5_000)
        assertEquals("nothing counts before the screen goes dark", 0L, r.sat)
        r.screen(false, 10_000)
        sitting(r, 10_000, 10_000 + 11 * min)
        assertTrue(r.reached)
        assertEquals(Phase.SITTING, r.phase)
        r.unlocked(10_000 + 11 * min)
        assertEquals(Phase.DONE, r.phase)
    }

    @Test fun aGlanceIsForgivenPickingUpIsNot() {
        val r = SitRules(60.0, 30.0)
        r.screen(false, 0)
        sitting(r, 0, 2 * min)
        r.screen(true, 2 * min)
        sitting(r, 2 * min, 2 * min + 5_000)
        r.screen(false, 2 * min + 5_000)
        sitting(r, 2 * min + 5_000, 3 * min)
        assertEquals(Phase.SITTING, r.phase)
        assertEquals(3 * min, r.sat)
        r.screen(true, 3 * min)
        sitting(r, 3 * min, 3 * min + 12_000)
        assertEquals(Phase.BROKEN, r.phase)
        assertEquals(Why.SCREEN, r.why)
    }

    @Test fun walkingAwayEndsItButNotAfterTen() {
        val r = SitRules(60.0, 30.0)
        r.screen(false, 0)
        for (k in 0..5) r.fix(k * 4000L, 20.0, 10f, false)
        sitting(r, 0, 4 * min)
        for (k in 0..30) { r.fix(4 * min + k * 4000L, 300.0, 10f, false); r.tick(4 * min + k * 4000L) }
        assertEquals(Why.LEFT, r.why)

        val late = SitRules(60.0, 30.0)
        late.screen(false, 0)
        sitting(late, 0, 11 * min)
        for (k in 0..30) { late.fix(11 * min + k * 4000L, 300.0, 10f, false); late.tick(11 * min + k * 4000L) }
        assertEquals("after ten minutes the stamp is yours", Phase.DONE, late.phase)
    }

    @Test fun roughFixesIndoorsDontCountAgainst() {
        val r = SitRules(60.0, 30.0)
        r.screen(false, 0)
        for (k in 0..60) { r.fix(k * 4000L, 400.0, 150f, false); r.tick(k * 4000L) }
        assertEquals(Phase.SITTING, r.phase)
    }

    @Test fun motionAndMock() {
        val r = SitRules(60.0, 30.0)
        r.screen(false, 0)
        repeat(29) { r.motion(true, 2000) }
        assertEquals(Phase.SITTING, r.phase)
        repeat(2) { r.motion(true, 2000) }
        assertEquals(Why.MOVED, r.why)
        val m = SitRules(60.0, 30.0)
        m.screen(false, 0)
        sitting(m, 0, 12 * min)
        m.fix(12 * min, 5.0, 3f, true)
        assertEquals(Phase.BROKEN, m.phase)
        assertEquals(Why.MOCK, m.why)
    }

    @Test fun inkFollowsTheSun() {
        val kazan = ZoneId.of("Europe/Moscow")
        fun at(s: String) = LocalDateTime.parse(s).atZone(kazan).toInstant().toEpochMilli()
        val (rise, set) = Sun.times(LocalDate.parse("2026-03-20"), 55.79, 49.12)!!
        assertEquals("equinox sunrise in Kazan ~02:50 UTC", 170.0, rise, 15.0)
        assertEquals("a little over twelve hours of day", 730.0, set - rise, 15.0)
        assertEquals(Ink.DAY, Ink.at(at("2026-06-21T13:00"), 55.79, 49.12, kazan))
        assertEquals(Ink.NIGHT, Ink.at(at("2026-06-22T00:30"), 55.79, 49.12, kazan))
        assertEquals(Ink.DAWN, Ink.at(at("2026-03-20T06:00"), 55.79, 49.12, kazan))
        assertEquals(Ink.DUSK, Ink.at(at("2026-03-20T18:00"), 55.79, 49.12, kazan))
        // Svalbard in June: the sun never sets; the clock decides.
        assertEquals(Ink.DAY, Ink.at(LocalDateTime.parse("2026-06-21T12:00").toInstant(ZoneOffset.UTC).toEpochMilli(), 78.2, 15.6, ZoneOffset.UTC))
    }

    @Test fun seasonsTurnSouthOfTheEquator() {
        assertEquals(Season.WINTER, Season.of(LocalDate.parse("2026-01-10"), 55.0))
        assertEquals(Season.SUMMER, Season.of(LocalDate.parse("2026-01-10"), -33.9))
    }

    private val zone = ZoneId.systemDefault()
    private fun ms(s: String) = LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()
    private fun sit(id: Long, place: Long, at: String, minutes: Int = 10) = Sitting(id = id, placeId = place, startAt = ms(at), endAt = ms(at) + minutes * min, seconds = minutes * 60)

    @Test fun aMarkedPlaceIsInkedOnItsThirdDay() {
        val bench = Place(id = 1, origin = Origin.OWN.name, kind = Kind.OWN.name, lat = 55.77, lon = 49.12)
        val sittings = listOf(sit(1, 1, "2026-01-01T10:00"), sit(2, 1, "2026-01-01T18:00"), sit(3, 1, "2026-01-05T10:00"), sit(4, 1, "2026-02-01T10:00"))
        val stamps = Book.stamps(listOf(bench), sittings)
        assertEquals(listOf(true, true, true, false), stamps.map { it.pencil })
        assertEquals(listOf(1, 2, 3, 4), stamps.map { it.number })
        assertFalse(Book.pencil(bench, sittings))
        assertTrue(Book.pencil(bench, sittings.take(3)))
    }

    @Test fun farawayTownsGetNotebooksAndWalksGetPages() {
        val home = LatLon(55.79, 49.13)
        val kazan = (1L..3L).map { Place(id = it, kind = Kind.CHURCH.name, lat = 55.79 + it * .001, lon = 49.12) }
        val spb = (4L..7L).map { Place(id = it, kind = Kind.MOSQUE.name, lat = 59.93 + it * .001, lon = 30.32) }
        val sittings = listOf(sit(1, 1, "2026-01-01T10:00"), sit(2, 2, "2026-01-02T10:00"), sit(3, 3, "2026-01-03T10:00")) +
            (4L..7L).map { sit(it, it, "2026-05-30T1${it}:00") } + sit(8, 1, "2026-06-01T10:00")
        val tetrads = Book.tetrads(spb + kazan, sittings, home)
        assertEquals(2, tetrads.size)
        assertEquals(setOf(1L, 2L, 3L), tetrads[0].ids)
        val stamps = Book.stamps(spb + kazan, sittings)
        val pages = Book.pages(tetrads[1], stamps)
        assertEquals(1, pages.size)
        assertTrue(pages[0] is Page.Walk)
        assertEquals(4, (pages[0] as Page.Walk).stamps.size)
        assertEquals(1, Book.pages(tetrads[0], stamps).size)
    }

    @Test fun theYearsCircle() {
        val p = Place(id = 1, lat = 55.0, lon = 49.0)
        val s = listOf(sit(1, 1, "2026-01-10T10:00"), sit(2, 1, "2026-04-10T10:00"), sit(3, 1, "2026-07-10T10:00"))
        assertFalse(Book.circle(p, s))
        assertTrue(Book.circle(p, s + sit(4, 1, "2026-10-10T10:00")))
    }

    @Test fun rumoursAreTheCommonMapsNearbyAndNew() {
        val home = LatLon(55.79, 49.13)
        fun poi(id: String, kind: Kind, name: String, north: Double) = Poi(id, kind.name, 0, name, Geo.offset(home, north, 0.0).lat, home.lon, 40.0)
        val pois = listOf(poi("a", Kind.SPRING, "Ключик", 800.0), poi("b", Kind.CHURCH, "", 900.0), poi("c", Kind.TREE, "Дуб", 100.0), poi("d", Kind.STONE, "Камень", 9000.0))
        val pick = Rumors.pick(pois, home, emptySet(), emptySet(), emptySet(), 1)
        assertEquals("a", pick?.osm)
        assertNull(Rumors.pick(pois, home, setOf("a"), setOf("b"), emptySet(), 1))
        val text = Rumors.text(pick!!, home, true, true)
        assertTrue(text, text.startsWith("Говорят, в десяти минутах ходьбы к северу от дома бьёт родник — «Ключик»."))
        val circle = Rumors.circle(pick)
        assertTrue(Geo.distance(circle, pick.at) <= Rumors.FUZZ + 1)
    }

    @Test fun overpassKinds() {
        assertEquals(Kind.CHURCH to 0, Overpass.classify(mapOf("amenity" to "place_of_worship", "religion" to "christian", "denomination" to "russian_orthodox")))
        assertEquals(Kind.CHURCH to 1, Overpass.classify(mapOf("amenity" to "place_of_worship", "religion" to "christian", "denomination" to "lutheran")))
        assertEquals(Kind.MOSQUE to 0, Overpass.classify(mapOf("amenity" to "place_of_worship", "religion" to "muslim")))
        assertEquals(Kind.TEMPLE to 0, Overpass.classify(mapOf("amenity" to "place_of_worship", "religion" to "buddhist")))
        assertEquals(Kind.SHRINE to 0, Overpass.classify(mapOf("historic" to "wayside_shrine")))
        assertEquals(Kind.SPRING to 0, Overpass.classify(mapOf("natural" to "spring")))
        assertNull(Overpass.classify(mapOf("amenity" to "cafe")))
        val json = """{"elements":[
            {"type":"node","id":1,"lat":55.79,"lon":49.1,"tags":{"natural":"spring","name":"Ключ"}},
            {"type":"way","id":2,"bounds":{"minlat":55.796,"minlon":49.105,"maxlat":55.797,"maxlon":49.107},"tags":{"amenity":"place_of_worship","religion":"muslim","name":"Кул Шариф"}},
            {"type":"node","id":3,"lat":55.0,"lon":49.0,"tags":{"shop":"bakery"}}
        ]}"""
        val pois = Overpass.parse(json, 0)
        assertEquals(2, pois.size)
        assertEquals("node/1", pois[0].osm)
        assertEquals(Kind.SPRING.radius, pois[0].radius, .01)
        assertTrue("a way's radius covers its area", pois[1].radius > 60)
        assertEquals(55.7965, pois[1].lat, 1e-6)
    }

    @Test fun homeIsWhereTheNightsAre() {
        var s = ""
        val home = LatLon(55.7901, 49.1302)
        s = Nights.add(s, home)
        s = Nights.add(s, LatLon(59.93, 30.32))
        assertNull(Nights.home(s))
        s = Nights.add(Nights.add(s, LatLon(55.7903, 49.1299)), home)
        val h = Nights.home(s)
        assertNotNull(h)
        assertTrue(Geo.distance(h!!, home) < 300)
    }

    @Test fun markingRules() {
        val home = LatLon(55.79, 49.13)
        val far = Geo.offset(home, 2000.0, 90.0)
        assertNull(Marking.check(far, 10f, home, 0, emptyList()))
        assertEquals(Marking.No.ROUGH, Marking.check(far, 45f, home, 0, emptyList()))
        assertEquals(Marking.No.HOME, Marking.check(Geo.offset(home, 100.0, 0.0), 10f, home, 0, emptyList()))
        assertEquals(Marking.No.WEEK, Marking.check(far, 10f, home, 2, emptyList()))
        assertEquals(Marking.No.CLOSE, Marking.check(far, 10f, home, 0, listOf(Geo.offset(far, 20.0, 0.0))))
    }

    @Test fun words() {
        assertEquals("XII", Words.roman(12))
        assertEquals("25 · X · 2026", Words.stampDate(LocalDate.parse("2026-10-25")))
        assertEquals("1 ч 20 мин", Words.duration(80 * 60, true))
        assertEquals("21 печать", Words.stamps(21, true))
        assertEquals("12 печатей", Words.stamps(12, true))
        assertEquals("обиду", Words.word("  обиду и злость"))
        assertEquals("в получасе ходьбы", Geo.walk(2500.0, true))
    }
}

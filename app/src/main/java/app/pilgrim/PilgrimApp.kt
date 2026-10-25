package app.pilgrim

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import app.pilgrim.data.DemoSeed
import app.pilgrim.data.Note
import app.pilgrim.data.OsmNote
import app.pilgrim.data.Overpass
import app.pilgrim.data.PilgrimDb
import app.pilgrim.data.Place
import app.pilgrim.data.Poi
import app.pilgrim.data.SettingsStore
import app.pilgrim.data.Sitting
import app.pilgrim.data.Weather
import app.pilgrim.domain.Book
import app.pilgrim.domain.Geo
import app.pilgrim.domain.Ink
import app.pilgrim.domain.Kind
import app.pilgrim.domain.LatLon
import app.pilgrim.domain.Nights
import app.pilgrim.domain.Origin
import app.pilgrim.domain.Rumors
import app.pilgrim.domain.Words
import app.pilgrim.sit.Fix
import app.pilgrim.sit.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.osmdroid.config.Configuration as Osm
import java.io.File
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.random.Random

class AppContainer(private val context: Context) {
    val db = PilgrimDb.create(context)
    val settings = SettingsStore(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dao get() = db.dao()
    private val fetching = Mutex()

    /** Fire-and-forget so a write survives the screen being closed right after tapping. */
    fun write(block: suspend AppContainer.() -> Unit) {
        scope.launch { block() }
    }

    /** Places from the common map around a point, from the cache. */
    suspend fun pois(p: LatLon, metres: Double): List<Poi> = withContext(Dispatchers.IO) {
        val dLat = metres / 111_320
        val dLon = metres / (111_320 * cos(Math.toRadians(p.lat)).coerceAtLeast(.01))
        dao.pois(p.lat - dLat, p.lat + dLat, p.lon - dLon, p.lon + dLon).filter { Geo.distance(p, it.at) <= metres }
    }

    /**
     * Makes sure the common map's places within [AREA] of [p] are cached: fetched once, then again
     * after a month. Returns false when the map couldn't be reached.
     */
    suspend fun ensureAround(p: LatLon): Boolean = fetching.withLock {
        if (BuildConfig.DEMO) return true
        val now = System.currentTimeMillis()
        val s = settings.now()
        val areas = s.areas.split(';').mapNotNull { a ->
            val v = a.split(',')
            if (v.size != 4) null else runCatching { listOf(v[0].toDouble(), v[1].toDouble(), v[2].toDouble(), v[3].toDouble()) }.getOrNull()
        }
        val fresh = areas.filter { now - it[3].toLong() < MONTH }
        if (fresh.any { Geo.distance(LatLon(it[0], it[1]), p) + 1_200 <= it[2] }) return true
        val got = withContext(Dispatchers.IO) { runCatching { Overpass.fetch(p.lat, p.lon, AREA.toInt(), now) }.getOrNull() } ?: return false
        dao.insertPois(got)
        dao.dropPois(now - 6 * MONTH)
        val entry = "%.4f,%.4f,%d,%d".format(java.util.Locale.US, p.lat, p.lon, AREA.toInt(), now)
        settings.update { it.copy(areas = (fresh.takeLast(30).map { a -> "%.4f,%.4f,%d,%d".format(java.util.Locale.US, a[0], a[1], a[2].toInt(), a[3].toLong()) } + entry).joinToString(";")) }
        true
    }

    /** A fix at night: one more mark toward where home is. */
    suspend fun night(f: Fix) {
        if (f.accuracy > 100 || f.mock || !Nights.isNight(LocalTime.now().hour)) return
        val today = java.time.LocalDate.now().let { if (LocalTime.now().hour < 6) it.minusDays(1) else it }.toEpochDay()
        settings.update { s ->
            if (s.lastNight == today) return@update s
            val nights = Nights.add(s.nights, LatLon(f.lat, f.lon))
            val home = if (s.homeManual) s.home else Nights.home(nights) ?: s.home
            s.copy(nights = nights, lastNight = today, homeLat = home?.lat ?: Double.NaN, homeLon = home?.lon ?: Double.NaN)
        }
    }

    /** This week's rumour, chosen anew once a week or when its place has been sat at. */
    suspend fun rumor(from: LatLon) {
        val s = settings.now()
        val now = System.currentTimeMillis()
        val places = dao.places()
        val visited = places.map { it.osm }.filter { it.isNotEmpty() }.toSet()
        if (s.rumor.isNotEmpty() && s.rumor !in visited && now - s.rumorAt < Rumors.WEEK) return
        val centre = s.home ?: from
        val pick = Rumors.pick(pois(centre, Rumors.FAR), centre, visited, s.toldSet, places.map { it.kindOf }.toSet(), now / Rumors.WEEK) ?: return
        settings.update { it.copy(rumor = pick.osm, rumorAt = now, told = (it.toldSet + pick.osm).joinToString(",")) }
    }

    suspend fun poi(osm: String): Poi? = dao.poi(osm)

    /** Ten minutes or more: the place goes into the book if it wasn't there, and the stamp is written. */
    suspend fun saveSitting(t: Target, start: Long, end: Long, seconds: Int): Long {
        val place = (if (t.placeId != 0L) dao.place(t.placeId) else null)
            ?: t.osm.takeIf { it.isNotEmpty() }?.let { dao.placeByOsm(it) }
            ?: Place(osm = t.osm, origin = Origin.OSM.name, kind = t.kind.name, variant = t.variant, name = t.name, lat = t.lat, lon = t.lon, radius = t.radius, seed = Random.nextLong(1, 1_000_000))
                .let { it.copy(id = dao.insert(it)) }
        val w = withTimeoutOrNull(10_000) { withContext(Dispatchers.IO) { runCatching { Weather.fetch(place.lat, place.lon) }.getOrNull() } }
        val ink = Ink.at((start + end) / 2, place.lat, place.lon, ZoneId.systemDefault())
        return dao.insert(
            Sitting(
                placeId = place.id, startAt = start, endAt = end, seconds = seconds, ink = ink.name,
                sky = w?.sky?.name.orEmpty(), windy = w?.windy ?: false, temp = w?.temp?.roundToInt() ?: Sitting.NO_TEMP,
            ),
        )
    }

    suspend fun words(sittingId: Long, left: String, took: String) {
        val s = dao.sitting(sittingId) ?: return
        dao.update(s.copy(left = Words.word(left), took = Words.word(took)))
    }

    suspend fun leaveNote(placeId: Long, text: String) {
        dao.insert(Note(placeId = placeId, text = text.trim().take(1000), leftAt = System.currentTimeMillis()))
    }

    suspend fun openNote(n: Note) = dao.update(n.copy(openedAt = System.currentTimeMillis()))

    /** A place marked by hand, in pencil. */
    suspend fun mark(kind: Kind, name: String, f: Fix): Long = dao.insert(
        Place(origin = Origin.OWN.name, kind = kind.name, name = name.trim().take(60), lat = f.lat, lon = f.lon, radius = kind.radius, seed = Random.nextLong(1, 1_000_000)),
    )

    /** A place from the common map tied to where it really is. */
    suspend fun move(p: Poi, f: Fix): Long {
        dao.placeByOsm(p.osm)?.let { old ->
            dao.update(old.copy(origin = Origin.MOVED.name, lat = f.lat, lon = f.lon, radius = maxOf(p.kindOf.radius, 40.0)))
            return old.id
        }
        return dao.insert(
            Place(osm = p.osm, origin = Origin.MOVED.name, kind = p.kind, variant = p.variant, name = p.name, lat = f.lat, lon = f.lon, radius = maxOf(p.kindOf.radius, 40.0), seed = Random.nextLong(1, 1_000_000)),
        )
    }

    suspend fun update(p: Place) = dao.update(p)

    /** Tells the mappers a place exists; true if it went. */
    suspend fun sendNote(p: Place, days: Int): Boolean = withContext(Dispatchers.IO) {
        if (BuildConfig.DEMO) return@withContext true
        runCatching { OsmNote.send(p.lat, p.lon, OsmNote.text(p.kindOf, days)) }.isSuccess.also { ok -> if (ok) dao.update(p.copy(noted = true)) }
    }

    /** Forgets a place, its stamps and its notes. */
    suspend fun forget(p: Place) {
        dao.deleteSittings(p.id)
        dao.deleteNotes(p.id)
        dao.deletePlace(p.id)
    }

    /** How many places were marked by hand in the last seven days. */
    suspend fun markedThisWeek(): Int {
        val since = System.currentTimeMillis() - Rumors.WEEK
        return dao.places().count { it.originOf == Origin.OWN && it.createdAt >= since }
    }

    suspend fun pencilDays(p: Place) = Book.days(p.id, dao.sittings())

    companion object {
        const val AREA = 3_000.0
        const val MONTH = 30L * 24 * 60 * 60_000
    }
}

class PilgrimApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Osm.getInstance().apply {
            userAgentValue = "Pilgrim/1.0 (github.com/bigdevwhale/pilgrim)"
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        if (BuildConfig.DEMO) container.scope.launch { DemoSeed.seedIfEmpty(container) }
    }
}

val Context.container: AppContainer get() = (applicationContext as PilgrimApp).container

/** Resources in the in-app language, which AppCompat doesn't localise outside activities before Android 13. */
fun Context.localized(): Context {
    val tags = AppCompatDelegate.getApplicationLocales()
    if (tags.isEmpty) return this
    val config = Configuration(resources.configuration)
    config.setLocale(tags[0])
    return createConfigurationContext(config)
}

/** Is this context in Russian? */
val Context.ru: Boolean get() = resources.configuration.locales[0].language == "ru"

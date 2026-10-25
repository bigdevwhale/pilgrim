package app.pilgrim.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.pilgrim.container
import app.pilgrim.data.Note
import app.pilgrim.data.Place
import app.pilgrim.data.Settings
import app.pilgrim.data.Sitting
import app.pilgrim.domain.Book
import app.pilgrim.domain.Kind
import app.pilgrim.domain.Page
import app.pilgrim.domain.Stamp
import app.pilgrim.domain.Tetrad
import app.pilgrim.domain.Words
import app.pilgrim.domain.day
import app.pilgrim.domain.t
import app.pilgrim.ru
import app.pilgrim.sit.Session
import app.pilgrim.domain.Phase
import app.pilgrim.ui.art.Look
import app.pilgrim.ui.book.BookScreen
import app.pilgrim.ui.book.ScrollScreen
import app.pilgrim.ui.components.rememberNow
import app.pilgrim.ui.here.HereScreen
import app.pilgrim.ui.here.MarkScreen
import app.pilgrim.ui.map.MapScreen
import app.pilgrim.ui.onboarding.WelcomeScreen
import app.pilgrim.ui.place.PlaceScreen
import app.pilgrim.ui.settings.SettingsScreen
import app.pilgrim.ui.sit.AfterScreen
import app.pilgrim.ui.sit.SitScreen
import app.pilgrim.ui.theme.C
import app.pilgrim.ui.theme.T
import java.time.LocalDate

/** Everything written down, and what follows from it, at one moment. Screens read through here. */
@Immutable
class Home(
    val places: List<Place>,
    val sittings: List<Sitting>,
    val notes: List<Note>,
    val settings: Settings,
    val now: Long,
    val ru: Boolean,
) {
    val today: LocalDate = day(now)
    val stamps: List<Stamp> = Book.stamps(places, sittings)
    val tetrads: List<Tetrad> = Book.tetrads(places, sittings, settings.home)
    private val byPlace = stamps.groupBy { it.place.id }
    private val pageCache = HashMap<Int, List<Page>>()

    fun place(id: Long): Place? = places.firstOrNull { it.id == id }
    fun stampsAt(id: Long): List<Stamp> = byPlace[id].orEmpty()
    fun stamp(sittingId: Long): Stamp? = stamps.firstOrNull { it.sitting.id == sittingId }
    fun pages(t: Tetrad): List<Page> = pageCache.getOrPut(t.index) { Book.pages(t, stamps) }
    fun tetradOf(placeId: Long): Tetrad? = tetrads.firstOrNull { placeId in it.ids }

    val seconds: Long = sittings.sumOf { it.seconds.toLong() }
    val years: List<Int> = stamps.map { day(it.sitting.startAt).year }.distinct().sorted()

    /** The note still in a place's crevice. */
    fun sealed(placeId: Long): Note? = notes.firstOrNull { it.placeId == placeId && it.openedAt == 0L }

    /** The note a sitting may open: left on an earlier day. */
    fun ripe(placeId: Long, sitting: Sitting): Note? = sealed(placeId)?.takeIf { day(it.leftAt) < day(sitting.startAt) }

    fun tetradName(t: Tetrad): String = settings.tetradName(t.anchor.id) ?: t(ru, "Тетрадь ${Words.roman(t.index + 1)}", "Notebook ${Words.roman(t.index + 1)}")

    fun look(s: Stamp): Look = Look(
        kind = s.place.kindOf, variant = s.place.variant, ink = s.sitting.inkOf.argb, sky = s.sitting.skyOf, windy = s.sitting.windy,
        minutes = s.sitting.minutes, placeSeed = s.place.seed, seed = s.sitting.id * 7919 + s.sitting.startAt / 1000,
        title = s.place.name.ifBlank { s.place.kindOf.title(ru) }, date = Words.stampDate(day(s.sitting.startAt)), number = s.number, pencil = s.pencil,
    )

    /** A place not sat at yet, as a pencil outline: for marked places waiting for their first day. */
    fun outline(p: Place): Look = Look(
        kind = p.kindOf, variant = p.variant, ink = 0, sky = null, windy = false, minutes = 10, placeSeed = p.seed, seed = p.seed,
        title = p.name.ifBlank { p.kindOf.title(ru) }, date = Words.stampDate(day(p.createdAt)), number = 0, pencil = true,
    )

    fun kindsInBook(): Set<Kind> = places.map { it.kindOf }.toSet()
}

/** Everything a screen can open. */
class Go(private val nav: NavHostController) {
    private fun to(route: String) = nav.navigate(route) { launchSingleTop = true }
    private fun tab(route: String) = nav.navigate(route) { popUpTo("here"); launchSingleTop = true }
    fun here() = nav.navigate("here") { popUpTo("here") { inclusive = true }; launchSingleTop = true }
    fun book() = tab("book")
    fun map() = tab("map")
    fun place(id: Long) = to("place/$id")
    fun sit() = to("sit")
    fun after(id: Long) = nav.navigate("after/$id") { popUpTo("here"); launchSingleTop = true }
    fun mark() = to("mark")
    fun settings() = to("settings")
    fun scroll(year: Int) = to("scroll/$year")
    fun guide() = to("guide")
    fun back() { nav.popBackStack() }
}

@Composable
fun PilgrimRoot(settings: Settings) {
    val context = LocalContext.current
    val container = context.container
    val nav = rememberNavController()
    val start = remember { if (settings.onboarded) "here" else "welcome" }
    val dao = container.db.dao()
    val places by dao.observePlaces().collectAsStateWithLifecycle(initialValue = emptyList())
    val sittings by dao.observeSittings().collectAsStateWithLifecycle(initialValue = emptyList())
    val notes by dao.observeNotes().collectAsStateWithLifecycle(initialValue = emptyList())
    val now = rememberNow(60_000)
    val ru = context.ru
    val home = remember(places, sittings, notes, settings, now, ru) { Home(places, sittings, notes, settings, now, ru) }
    val go = remember(nav) { Go(nav) }
    val route = nav.currentBackStackEntryAsState().value?.destination?.route

    // A sitting in progress always shows; a finished one opens its stamp.
    val session by Session.state.collectAsStateWithLifecycle()
    LaunchedEffect(session?.phase, session?.sittingId, route) {
        val s = session ?: return@LaunchedEffect
        when {
            s.phase == Phase.DONE && s.sittingId != 0L && home.stamp(s.sittingId) != null -> {
                Session.clear()
                go.after(s.sittingId)
            }
            (s.phase == Phase.DARKEN || s.phase == Phase.SITTING || s.phase == Phase.BROKEN) && route != "sit" && route != null && route != "welcome" -> go.sit()
        }
    }

    Column(Modifier.fillMaxSize().background(C.paper)) {
        Box(Modifier.weight(1f)) {
            NavHost(
                navController = nav,
                startDestination = start,
                enterTransition = { fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(150)) },
            ) {
                val id = listOf(navArgument("id") { type = NavType.LongType })
                fun arg(e: androidx.navigation.NavBackStackEntry) = e.arguments?.getLong("id") ?: 0L
                composable("welcome") {
                    WelcomeScreen(home) { name ->
                        container.write { this.settings.update { it.copy(onboarded = true, owner = name.trim()) } }
                        nav.navigate("here") { popUpTo("welcome") { inclusive = true } }
                    }
                }
                composable("guide") { WelcomeScreen(home, again = true) { go.back() } }
                composable("here") { HereScreen(home, go) }
                composable("book") { BookScreen(home, go) }
                composable("map") { MapScreen(home, go) }
                composable("place/{id}", arguments = id) { e -> PlaceScreen(arg(e), home, go) }
                composable("sit") { SitScreen(go) }
                composable("after/{id}", arguments = id) { e -> AfterScreen(arg(e), home, go) }
                composable("mark") { MarkScreen(home, go) }
                composable("settings") { SettingsScreen(home, go) }
                composable("scroll/{year}", arguments = listOf(navArgument("year") { type = NavType.IntType })) { e -> ScrollScreen(e.arguments?.getInt("year") ?: home.today.year, home, go) }
            }
        }
        if (route == "here" || route == "book" || route == "map") Tabs(route, home.ru, go)
    }
}

/** Three words at the foot of the page, the open one underlined in cinnabar. */
@Composable
fun Tabs(route: String, ru: Boolean, go: Go) {
    Column(Modifier.fillMaxWidth().background(C.paper2).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            for ((r, label, open) in listOf(
                Triple("here", t(ru, "Здесь", "Here"), go::here),
                Triple("book", t(ru, "Подорожная", "The book"), go::book),
                Triple("map", t(ru, "Карта", "Map"), go::map),
            )) {
                val on = r == route
                Column(Modifier.weight(1f).height(56.dp).clickable { if (!on) open() }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    Text(label, style = T.Button.copy(color = if (on) C.ink else C.muted), textAlign = TextAlign.Center)
                    Box(Modifier.padding(top = 4.dp).width(if (on) 28.dp else 0.dp).height(2.dp).background(C.cinnabar))
                }
            }
        }
    }
}

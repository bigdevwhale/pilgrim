package app.pilgrim

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import app.pilgrim.data.DemoSeed
import app.pilgrim.domain.Phase
import app.pilgrim.sit.SitState
import app.pilgrim.ui.Go
import app.pilgrim.ui.Home
import app.pilgrim.ui.Tabs
import app.pilgrim.ui.book.BookScreen
import app.pilgrim.ui.book.ScrollScreen
import app.pilgrim.ui.components.Art
import app.pilgrim.ui.here.HereScreen
import app.pilgrim.ui.here.MarkScreen
import app.pilgrim.ui.here.target
import app.pilgrim.ui.map.MapScreen
import app.pilgrim.ui.onboarding.WelcomeScreen
import app.pilgrim.ui.place.PlaceScreen
import app.pilgrim.ui.settings.SettingsScreen
import app.pilgrim.ui.sit.AfterScreen
import app.pilgrim.ui.sit.SitScreen
import app.pilgrim.ui.theme.PilgrimTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Every main screen over the demo year, drawn to PNGs (run with `-Prender`), English and Russian. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
abstract class ScreensBase {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    abstract val lang: String
    private val out by lazy { File(System.getProperty("render.out") ?: "build/art", "screens-$lang").apply { mkdirs() } }

    private fun home(): Home = runBlocking {
        Art.still = true
        val app = ApplicationProvider.getApplicationContext<PilgrimApp>()
        val c = app.container
        DemoSeed.seedIfEmpty(c, lang == "ru")
        val dao = c.db.dao()
        Home(dao.places(), dao.sittings().sortedBy { it.startAt }, dao.observeNotes().first(), c.settings.now(), System.currentTimeMillis(), lang == "ru")
    }

    private fun shot(name: String, tab: String? = null, content: @Composable (Home, Go) -> Unit) {
        val h = home()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val go = Go(rememberNavController())
            PilgrimTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) { content(h, go) }
                    if (tab != null) Tabs(tab, h.ru, go)
                }
            }
        }
        rule.mainClock.advanceTimeBy(3_000)
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            File(out, "$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun Home.byName(ru: String, en: String) = places.first { it.name == ru || it.name == en }

    @Test fun welcome() = shot("0-welcome") { h, _ -> WelcomeScreen(h.let { Home(it.places, it.sittings, it.notes, it.settings.copy(owner = ""), it.now, it.ru) }) {} }
    @Test fun welcome2() = shot("0-welcome-2") { h, _ -> WelcomeScreen(h, startStep = 1) {} }
    @Test fun here() = shot("1-here", "here") { h, go -> HereScreen(h, go) }
    @Test fun sit() = shot("2-sit") { h, go ->
        val p = h.byName("Мечеть Марджани", "Marjani Mosque")
        SitScreen(go, SitState(p.target(), Phase.SITTING, null, 7 * 60_000L + 42_000, null, 12.0))
    }
    @Test fun after() = shot("3-after") { h, go ->
        val p = h.byName("Скамейка у Кабана", "Bench by Lake Kaban")
        AfterScreen(h.stampsAt(p.id).last().sitting.id, h, go, pressedAlready = true)
    }
    @Test fun book() = shot("4-book", "book") { h, go -> BookScreen(h, go) }
    @Test fun bookPage() = shot("5-page", "book") { h, go -> BookScreen(h, go, startPage = 2) }
    @Test fun walk() = shot("6-walk", "book") { h, go -> BookScreen(h, go, startTetrad = 1, startPage = 1) }
    @Test fun place() = shot("7-place") { h, go -> PlaceScreen(h.byName("Скамейка у Кабана", "Bench by Lake Kaban").id, h, go) }
    @Test fun map() = shot("8-map", "map") { h, go -> MapScreen(h, go) }
    @Test fun scroll() = shot("9-scroll") { h, go -> ScrollScreen(2026, h, go) }
    @Test fun mark() = shot("10-mark") { h, go -> MarkScreen(h, go) }
    @Test fun settings() = shot("11-settings") { h, go -> SettingsScreen(h, go) }
}

class RenderArtScreensEn : ScreensBase() {
    override val lang = "en"
}

@Config(qualifiers = "ru-w411dp-h891dp-xxhdpi")
class RenderArtScreensRu : ScreensBase() {
    override val lang = "ru"
}

package app.pilgrim.ui.here

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pilgrim.BuildConfig
import app.pilgrim.container
import app.pilgrim.data.DemoSeed
import app.pilgrim.data.Place
import app.pilgrim.data.Poi
import app.pilgrim.domain.Book
import app.pilgrim.domain.Geo
import app.pilgrim.domain.Kind
import app.pilgrim.domain.LatLon
import app.pilgrim.domain.Marking
import app.pilgrim.domain.Rumors
import app.pilgrim.domain.Words
import app.pilgrim.domain.day
import app.pilgrim.domain.t
import app.pilgrim.sit.Fix
import app.pilgrim.sit.Session
import app.pilgrim.sit.Target
import app.pilgrim.ui.Go
import app.pilgrim.ui.Home
import app.pilgrim.ui.components.Art
import app.pilgrim.ui.components.Card
import app.pilgrim.ui.components.Gap
import app.pilgrim.ui.components.InkButton
import app.pilgrim.ui.components.KindMark
import app.pilgrim.ui.components.Label
import app.pilgrim.ui.components.LineButton
import app.pilgrim.ui.components.Link
import app.pilgrim.ui.components.Rule
import app.pilgrim.ui.components.StampImage
import app.pilgrim.ui.components.rememberWhereabouts
import app.pilgrim.ui.theme.C
import app.pilgrim.ui.theme.T
import app.pilgrim.ui.theme.laid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A place near the phone: in the book already, or on the common map. */
class Near(val target: Target, val metres: Double, val inside: Boolean, val place: Place?, val poi: Poi?) {
    val kind get() = target.kind
}

/** How close counts as "here", given how sure the fix is. */
fun inside(metres: Double, accuracy: Float, radius: Double) = metres - minOf(accuracy.toDouble(), 40.0) <= radius

/** A sitting may begin only on a fix this good. */
const val START_ACCURACY = 60f

fun Place.target() = Target(id, osm, kindOf, variant, name, lat, lon, radius)
fun Poi.target() = Target(0, osm, kindOf, variant, name, lat, lon, radius)

/**
 * Where you are: the place you're at and the button to leave the phone, or the places nearby, or
 * nothing nearby and a way to mark a place of your own. This week's rumour below.
 */
@Composable
fun HereScreen(h: Home, go: Go) {
    val ru = h.ru
    val context = LocalContext.current
    val c = context.container
    val wh = rememberWhereabouts(if (BuildConfig.DEMO || Art.still) DemoSeed.here else null)
    val fix = wh.fix
    var fetched by remember { mutableIntStateOf(0) }
    var offline by remember { mutableStateOf(false) }
    val cell = fix?.let { "%.3f:%.3f".format(it.lat, it.lon) }
    LaunchedEffect(cell) {
        val f = fix ?: return@LaunchedEffect
        if (Art.still) { fetched++; return@LaunchedEffect }
        c.night(f)
        val ok = c.ensureAround(LatLon(f.lat, f.lon))
        offline = !ok
        if (ok) c.rumor(LatLon(f.lat, f.lon))
        fetched++
    }
    val near by produceState(emptyList<Near>(), fix?.let { "%.4f:%.4f:%.0f".format(it.lat, it.lon, it.accuracy) }, h.places, fetched) {
        val f = fix ?: return@produceState
        value = withContext(Dispatchers.IO) { nearby(c, h.places, f) }
    }
    val here = near.filter { it.inside }.minByOrNull { it.metres }
    var manners by remember { mutableStateOf<Kind?>(null) }
    var moving by remember { mutableStateOf<Near?>(null) }
    var pending by remember { mutableStateOf<Target?>(null) }

    fun begin(t: Target) {
        Session.begin(context, t)
        go.sit()
    }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { pending?.let { begin(it) }; pending = null }
    fun sit(t: Target) {
        if (t.kind.name !in h.settings.mannersSet) {
            manners = t.kind
            pending = t
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && !Art.still) {
            pending = t
            notify.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else begin(t)
    }

    Column(Modifier.fillMaxSize().laid().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t(ru, "Подорожная", "Pilgrim"), style = T.Title.copy(fontSize = 27.sp))
                Text(
                    t(ru, "В тишине: ", "In silence: ") + Words.duration(h.seconds, ru) + " · " + Words.stamps(h.stamps.size, ru),
                    style = T.Small,
                )
            }
            IconButton(onClick = go::settings) { Icon(Icons.Outlined.Settings, null, tint = C.muted) }
        }
        Gap(8)
        Rule()
        Gap(14)

        when {
            !wh.permitted -> Card {
                Text(t(ru, "Где вы?", "Where are you?"), style = T.Heading)
                Gap(6)
                Text(t(ru, "Подорожная узнаёт место силы по геолокации: печать ставится только там, где вы правда сидели. Ничего не уходит с телефона, кроме округлённой точки для карты мест и погоды.", "The book knows a place of power by location: a stamp only goes where you really sat. Nothing leaves the phone but a rounded point for the map of places and the weather."), style = T.Body)
                Gap(12)
                InkButton(t(ru, "Разрешить геолокацию", "Allow location"), wh.ask, Modifier.fillMaxWidth())
            }
            !wh.on -> Card {
                Text(t(ru, "Геолокация выключена", "Location is off"), style = T.Heading)
                Gap(12)
                InkButton(t(ru, "Включить", "Turn it on"), wh.ask, Modifier.fillMaxWidth())
            }
            fix == null -> Card { Text(t(ru, "Ищем вас…", "Finding you…"), style = T.Italic) }
            here != null -> AtPlace(h, here, fix, onSit = { sit(here.target) }, onManners = { manners = here.kind })
            else -> Card {
                Text(
                    if (near.isEmpty()) t(ru, "Рядом нет мест силы с общей карты", "No places of power from the common map nearby")
                    else t(ru, "Вы не у места силы", "You're not at a place of power"),
                    style = T.Heading,
                )
                Gap(4)
                Text(
                    t(ru, "Точность: ", "Accuracy: ") + Geo.metres(fix.accuracy.toDouble(), ru) + if (offline) t(ru, " · карта мест недоступна, только ваши места", " · the map of places can't be reached; your places only") else "",
                    style = T.Small,
                )
                if (near.isNotEmpty()) {
                    Gap(10)
                    Label(t(ru, "В пяти минутах", "Within five minutes"))
                    for (n in near.take(6)) NearRow(n, fix, ru) { if (n.poi != null) moving = n }
                    if (near.any { it.poi != null }) Text(
                        t(ru, "Место на карте есть, но точка не там, где оно на самом деле? Нажмите на него — и привяжите к месту, где стоите.", "Is the place on the map, but its point is off? Tap it to tie it to where you're standing."),
                        style = T.Small, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        Gap(12)
        if (fix != null && wh.ready) Link(t(ru, "＋ Отметить место здесь", "＋ Mark a place here"), onClick = go::mark)

        Rumor(h, fix)

        h.stamps.lastOrNull()?.let { s ->
            Gap(18)
            Label(t(ru, "Последняя печать", "Last stamp"))
            Row(Modifier.fillMaxWidth().clickable { go.place(s.place.id) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                StampImage(h.look(s), 84.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(s.place.shown(ru), style = T.Body)
                    Text(Words.date(day(s.sitting.startAt), ru) + " · " + Words.duration(s.sitting.seconds.toLong(), ru), style = T.Small)
                    if (s.sitting.left.isNotBlank() || s.sitting.took.isNotBlank()) Text(words(s.sitting.left, s.sitting.took, ru), style = T.Hand.copy(fontSize = 21.sp, color = C.muted))
                }
            }
        }
        Gap(24)
    }

    manners?.let { k ->
        Manners(k, ru, starting = pending != null, onDismiss = { manners = null; pending = null }) {
            manners = null
            c.write { settings.update { it.copy(mannersSeen = (it.mannersSet + k.name).joinToString(",")) } }
            pending?.let { p ->
                pending = null
                if (Build.VERSION.SDK_INT >= 33 && !Art.still) { pending = p; notify.launch(Manifest.permission.POST_NOTIFICATIONS) } else begin(p)
            }
        }
    }
    moving?.let { n ->
        val ok = (fix?.accuracy ?: 999f) <= Marking.ACCURACY
        AlertDialog(
            onDismissRequest = { moving = null },
            title = { Text(n.target.name.ifBlank { n.kind.title(ru) }) },
            text = {
                Text(
                    if (ok) t(ru, "На общей карте это место в ${Geo.metres(n.metres, true)} отсюда. Если оно на самом деле здесь — привяжите его к месту, где вы стоите. Карту для всех это не меняет: только вашу подорожную.", "On the common map this place is ${Geo.metres(n.metres, false)} from here. If it's really here, tie it to where you're standing. This doesn't change the map for everyone, only your book.")
                    else t(ru, "Чтобы привязать место, нужна точность лучше ${Marking.ACCURACY.toInt()} м — сейчас ${Geo.metres(fix?.accuracy?.toDouble() ?: 0.0, true)}. Постойте на открытом месте минуту.", "To tie a place, accuracy must be better than ${Marking.ACCURACY.toInt()} m — now it's ${Geo.metres(fix?.accuracy?.toDouble() ?: 0.0, false)}. Stand in the open for a minute."),
                )
            },
            confirmButton = {
                TextButton(enabled = ok, onClick = {
                    val p = n.poi
                    val f = fix
                    moving = null
                    if (p != null && f != null) c.write { move(p, f) }
                }) { Text(t(ru, "Оно здесь", "It's here")) }
            },
            dismissButton = { TextButton(onClick = { moving = null }) { Text(t(ru, "Отмена", "Cancel")) } },
        )
    }
}

fun words(left: String, took: String, ru: Boolean): String = listOfNotNull(
    left.takeIf { it.isNotBlank() }?.let { t(ru, "оставлено: ", "left: ") + it },
    took.takeIf { it.isNotBlank() }?.let { t(ru, "взято: ", "took: ") + it },
).joinToString(" · ")

/** Places within five minutes' walk: the book's own first, then the common map's. */
suspend fun nearby(c: app.pilgrim.AppContainer, places: List<Place>, f: Fix): List<Near> {
    val me = LatLon(f.lat, f.lon)
    val mine = places.map { p -> val d = Geo.distance(me, p.at); Near(p.target(), d, inside(d, f.accuracy, p.radius), p, null) }
    val known = places.map { it.osm }.filter { it.isNotEmpty() }.toSet()
    val theirs = c.pois(me, Rumors.NEAR).filter { it.osm !in known }.map { p -> val d = Geo.distance(me, p.at); Near(p.target(), d, inside(d, f.accuracy, p.radius), null, p) }
    return (mine.filter { it.metres <= Rumors.NEAR } + theirs).sortedBy { it.metres }
}

@Composable
private fun AtPlace(h: Home, n: Near, fix: Fix, onSit: () -> Unit, onManners: () -> Unit) {
    val ru = h.ru
    val p = n.place
    val count = p?.let { h.stampsAt(it.id).size } ?: 0
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindMark(n.kind, n.target.variant, 54.dp, C.ink, pencil = p != null && Book.pencil(p, h.sittings))
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Label(t(ru, "Вы у места", "You're at"))
                Text(p?.shown(ru) ?: n.target.name.ifBlank { n.kind.title(ru) }, style = T.Heading)
                Text(
                    n.kind.title(ru) + " · " + Geo.metres(n.metres, ru) + " · " + if (count == 0) t(ru, "впервые", "first time") else t(ru, "были ${Words.times(count, true)}", "been ${Words.times(count, false)}"),
                    style = T.Small,
                )
            }
        }
        if (p != null && Book.pencil(p, h.sittings)) {
            Gap(8)
            Text(t(ru, "Карандашом: ${Book.days(p.id, h.sittings)} из ${Book.DAYS} дней. Место станет чернильным, когда вы придёте сюда в третий день.", "In pencil: ${Book.days(p.id, h.sittings)} of ${Book.DAYS} days. The place is inked on your third day here."), style = T.Small)
        }
        if (p != null && h.sealed(p.id) != null) {
            Gap(6)
            Text(t(ru, "В щели ждёт записка.", "A note waits in the crevice."), style = T.Italic)
        }
        Gap(14)
        val rough = fix.accuracy > START_ACCURACY
        InkButton(t(ru, "Оставляю телефон", "I'm leaving the phone"), onSit, Modifier.fillMaxWidth(), enabled = !rough)
        if (rough) Text(t(ru, "Точность ${Geo.metres(fix.accuracy.toDouble(), true)} — подождите, пока GPS уточнит.", "Accuracy ${Geo.metres(fix.accuracy.toDouble(), false)} — wait for the GPS to settle."), style = T.Small, modifier = Modifier.padding(top = 6.dp))
        Gap(8)
        Text(
            t(ru, "Десять минут без телефона: погасите экран и отложите его. Если взять телефон в руки или уйти — печати не будет. После десяти минут можно сидеть сколько угодно.", "Ten minutes without the phone: turn the screen off and put it away. Pick it up or walk off, and there's no stamp. After ten minutes, sit as long as you like."),
            style = T.Small,
        )
        Link(t(ru, "Как входить", "How to come in"), onClick = onManners)
    }
}

@Composable
private fun NearRow(n: Near, fix: Fix, ru: Boolean, onTap: () -> Unit) {
    val me = LatLon(fix.lat, fix.lon)
    Row(Modifier.fillMaxWidth().clickable(onClick = onTap).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        KindMark(n.kind, n.target.variant, 30.dp, C.muted)
        Text(n.target.name.ifBlank { n.kind.title(ru) }, style = T.Body, modifier = Modifier.weight(1f).padding(start = 10.dp), maxLines = 1)
        Text(Geo.metres(n.metres, ru) + " " + Geo.short(Geo.bearing(me, n.target.at), ru), style = T.Small)
        Text("↑", style = T.Body.copy(color = C.cinnabar), modifier = Modifier.padding(start = 6.dp).width(14.dp).rotate(Geo.bearing(me, n.target.at).toFloat()))
    }
}

/** This week's rumour, on a scrap of paper. */
@Composable
private fun Rumor(h: Home, fix: Fix?) {
    val ru = h.ru
    val c = LocalContext.current.container
    val poi by produceState<Poi?>(null, h.settings.rumor) { value = h.settings.rumor.takeIf { it.isNotEmpty() }?.let { c.poi(it) } }
    val p = poi ?: return
    if (h.places.any { it.osm == p.osm }) return
    val home = h.settings.home
    val from = home ?: fix?.let { LatLon(it.lat, it.lon) } ?: return
    Gap(18)
    Label(t(ru, "Слух недели", "This week's rumour"))
    Gap(6)
    Card(Modifier.rotate(-.8f)) {
        Text(Rumors.text(p, from, home != null, ru), style = T.Hand.copy(fontSize = 24.sp, lineHeight = 27.sp))
        Gap(4)
        Text(t(ru, "На карте — кругом, неточно. Найти придётся самим.", "On the map as a circle, roughly. You'll have to find it."), style = T.Small)
    }
}

/** How to come in: shown once before the first sitting at each kind of place, and on request. */
@Composable
fun Manners(k: Kind, ru: Boolean, starting: Boolean, onDismiss: () -> Unit, onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { KindMark(k, 0, 44.dp, C.ink) },
        title = { Text(t(ru, "Как входить", "How to come in") + " · " + k.title(ru).lowercase(), style = T.Heading) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (line in k.manners(ru)) Row {
                    Text("◆", style = T.Small.copy(color = C.cinnabar, fontSize = 10.sp), modifier = Modifier.padding(top = 5.dp, end = 8.dp))
                    Text(line, style = T.Body.copy(fontSize = 16.sp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onOk) { Text(if (starting) t(ru, "Понятно, сесть", "Got it, sit down") else t(ru, "Понятно", "Got it")) } },
        dismissButton = if (starting) ({ TextButton(onClick = onDismiss) { Text(t(ru, "Не сейчас", "Not now")) } }) else null,
        containerColor = C.paper,
    )
}

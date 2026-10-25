package app.pilgrim.ui.place

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pilgrim.container
import app.pilgrim.data.OsmNote
import app.pilgrim.data.Place
import app.pilgrim.domain.Book
import app.pilgrim.domain.Kind
import app.pilgrim.domain.Origin
import app.pilgrim.domain.Season
import app.pilgrim.domain.Stamp
import app.pilgrim.domain.Words
import app.pilgrim.domain.day
import app.pilgrim.domain.t
import app.pilgrim.ui.Go
import app.pilgrim.ui.Home
import app.pilgrim.ui.book.StampDialog
import app.pilgrim.ui.components.Card
import app.pilgrim.ui.components.Gap
import app.pilgrim.ui.components.HandField
import app.pilgrim.ui.components.KindMark
import app.pilgrim.ui.components.Label
import app.pilgrim.ui.components.LineButton
import app.pilgrim.ui.components.Link
import app.pilgrim.ui.components.Page
import app.pilgrim.ui.components.Rule
import app.pilgrim.ui.components.StampImage
import app.pilgrim.ui.theme.C
import app.pilgrim.ui.theme.T

/** A place's page: its circle of the year, its stamps, the crevice, how to come in. */
@Composable
fun PlaceScreen(id: Long, h: Home, go: Go) {
    val ru = h.ru
    val c = LocalContext.current.container
    val p = h.place(id) ?: return
    val stamps = h.stampsAt(id)
    var open by remember { mutableStateOf<Stamp?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var forgetting by remember { mutableStateOf(false) }
    val days = Book.days(p.id, h.sittings)
    val pencil = Book.pencil(p, h.sittings)

    Page(p.shown(ru), go::back) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindMark(p.kindOf, p.variant, 40.dp, C.muted, pencil = pencil)
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    p.kindOf.title(ru) + " · " + when (p.originOf) {
                        Origin.OSM -> t(ru, "с общей карты", "from the common map")
                        Origin.OWN -> t(ru, "отмечено вами", "marked by you")
                        Origin.MOVED -> t(ru, "привязано вами", "tied by you")
                    },
                    style = T.Small,
                )
                if (p.title.isNotBlank() && p.name.isNotBlank() && p.title != p.name) Text(p.name, style = T.Small)
                Text(
                    if (stamps.isEmpty()) t(ru, "Ещё не сидели", "Not sat at yet")
                    else Words.times(stamps.size, ru).let { t(ru, "Были $it", "Been $it") } + " · " + Words.duration(stamps.sumOf { it.sitting.seconds.toLong() }, ru) +
                        " · " + t(ru, "с ", "since ") + Words.date(day(stamps.first().sitting.startAt), ru),
                    style = T.Small,
                )
            }
        }
        Gap(14)
        if (p.originOf == Origin.OWN && pencil) {
            Card {
                Text(t(ru, "Карандашом: ${Words.days(days, true)} из ${Book.DAYS}", "In pencil: ${Words.days(days, false)} of ${Book.DAYS}"), style = T.Heading)
                Text(t(ru, "Место станет чернильным на третий день здесь.", "The place is inked on your third day here."), style = T.Body)
            }
            Gap(12)
        } else if (p.originOf == Origin.OWN && p.kindOf != Kind.OWN && !p.noted) {
            Card { NoteForMappers(h, p, days) }
            Gap(12)
        }

        Circle(h, p) { open = it }
        Gap(12)

        h.sealed(p.id)?.let { n ->
            Card {
                Label(t(ru, "Записка в щели", "A note in the crevice"))
                Text(t(ru, "Сложена ${Words.date(day(n.leftAt), true)}. Откроется, когда вы вернётесь сюда в другой день и просидите десять минут.", "Folded on ${Words.date(day(n.leftAt), false)}. It opens when you come back on another day and sit ten minutes."), style = T.Body)
            }
            Gap(12)
        }

        if (stamps.isNotEmpty()) {
            Label(t(ru, "Печати", "Stamps"))
            Gap(6)
            for (row in stamps.reversed().chunked(2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (s in row) Column(Modifier.weight(1f).clickable { open = s }, horizontalAlignment = Alignment.CenterHorizontally) {
                        StampImage(h.look(s), 150.dp)
                        Text(Words.date(day(s.sitting.startAt), ru), style = T.Small, textAlign = TextAlign.Center)
                    }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
                Gap(8)
            }
        }
        Gap(8)
        Rule()
        Gap(8)
        Label(t(ru, "Как входить", "How to come in"))
        Gap(4)
        for (line in p.kindOf.manners(ru)) Row(Modifier.padding(vertical = 3.dp)) {
            Text("◆", style = T.Small.copy(color = C.cinnabar, fontSize = 10.sp), modifier = Modifier.padding(top = 5.dp, end = 8.dp))
            Text(line, style = T.Body.copy(fontSize = 16.sp))
        }
        Gap(16)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LineButton(t(ru, "На карте", "On the map"), go::map)
            if (p.originOf == Origin.OWN || Book.circle(p, h.sittings)) LineButton(t(ru, "Назвать", "Rename"), { renaming = true })
        }
        Gap(4)
        Link(t(ru, "Забыть место", "Forget this place"), color = C.muted) { forgetting = true }
        Gap(24)
    }

    open?.let { s -> StampDialog(h, s, onPlace = null) { open = null } }
    if (renaming) {
        var name by remember { mutableStateOf(if (p.originOf == Origin.OWN) p.name else p.title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(t(ru, "Как вы его зовёте", "What you call it")) },
            text = { HandField(name, { name = it.take(60) }, p.kindOf.title(ru), caps = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    renaming = false
                    c.write { update(if (p.originOf == Origin.OWN) p.copy(name = name.trim()) else p.copy(title = name.trim())) }
                }) { Text(t(ru, "Сохранить", "Save")) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(t(ru, "Отмена", "Cancel")) } },
            containerColor = C.paper,
        )
    }
    if (forgetting) AlertDialog(
        onDismissRequest = { forgetting = false },
        title = { Text(t(ru, "Забыть место?", "Forget this place?")) },
        text = { Text(t(ru, "Вместе с ним уйдут ${Words.stamps(stamps.size, true)} и записки в щели. Это не вернуть.", "${Words.stamps(stamps.size, false).replaceFirstChar { it.uppercase() }} and the notes in its crevice go with it. This can't be undone.")) },
        confirmButton = { TextButton(onClick = { forgetting = false; go.back(); c.write { forget(p) } }) { Text(t(ru, "Забыть", "Forget"), color = C.cinnabar) } },
        dismissButton = { TextButton(onClick = { forgetting = false }) { Text(t(ru, "Оставить", "Keep it")) } },
        containerColor = C.paper,
    )
}

/** The year's circle: a stamp for each season sat in, in its quarter; a name in the middle once it closes. */
@Composable
private fun Circle(h: Home, p: Place, onOpen: (Stamp) -> Unit) {
    val ru = h.ru
    val seasons = Book.seasons(p, h.sittings)
    val stamps = h.stampsAt(p.id)
    Card {
        Label(t(ru, "Круг года", "The year's circle") + " · ${seasons.size}/4")
        Gap(8)
        Box(Modifier.fillMaxWidth().size(280.dp), contentAlignment = Alignment.Center) {
            for ((i, season) in listOf(Season.WINTER, Season.SPRING, Season.SUMMER, Season.AUTUMN).withIndex()) {
                val align = listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomEnd, Alignment.BottomStart)[i]
                val s = seasons[season]?.let { x -> stamps.firstOrNull { it.sitting.id == x.id } }
                Column(Modifier.align(align).width(128.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (s != null) Box(Modifier.clickable { onOpen(s) }) { StampImage(h.look(s), 112.dp) }
                    else Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) { StampImage(h.outline(p).copy(title = " ", date = " "), 96.dp) }
                    Text(season.title(ru), style = T.Caps.copy(fontSize = 10.5.sp, color = if (s != null) C.ink else C.faint))
                }
            }
            if (seasons.size == 4) Text(
                p.title.ifBlank { t(ru, "без имени", "unnamed") },
                style = T.Hand.copy(fontSize = 26.sp, color = C.cinnabar), textAlign = TextAlign.Center, modifier = Modifier.width(110.dp).padding(top = 4.dp),
            )
        }
        if (seasons.size < 4) Text(
            t(ru, "Посидите здесь в каждое время года — и круг замкнётся.", "Sit here in every season and the circle closes."),
            style = T.Small, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Telling the common map that this place exists: a fixed sentence and a point, nothing of yours. */
@Composable
fun NoteForMappers(h: Home, p: Place, days: Int) {
    val ru = h.ru
    val context = LocalContext.current
    val c = context.container
    var asking by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(p.noted) }
    if (sent) {
        Text(t(ru, "Картографы получили заметку. Спасибо.", "The mappers have the note. Thank you."), style = T.Small)
        return
    }
    Gap(8)
    Text(t(ru, "Этого места нет на общей карте. Подсказать картографам OpenStreetMap?", "This place isn't on the common map. Tell the OpenStreetMap mappers?"), style = T.Body)
    Gap(8)
    LineButton(t(ru, "Оставить заметку для картографов", "Leave a note for the mappers"), { asking = true })
    if (asking) AlertDialog(
        onDismissRequest = { asking = false },
        title = { Text(t(ru, "Заметка на OpenStreetMap", "A note on OpenStreetMap")) },
        text = {
            Column {
                Text(t(ru, "Уйдёт анонимно, без аккаунта: точка места и одна фраза по-английски. Её увидят все — так устроены заметки OSM.", "It goes anonymously, without an account: the place's point and one sentence. Everyone can see it — that's how OSM notes work."), style = T.Small)
                Gap(8)
                Text(OsmNote.text(p.kindOf, days), style = T.Italic.copy(fontSize = 15.sp, color = C.ink))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                asking = false
                c.write {
                    val ok = sendNote(p, days)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (ok) sent = true
                        else Toast.makeText(context, t(ru, "Не получилось отправить. Попробуйте позже.", "Couldn't send it. Try later."), Toast.LENGTH_SHORT).show()
                    }
                }
            }) { Text(t(ru, "Отправить", "Send")) }
        },
        dismissButton = { TextButton(onClick = { asking = false }) { Text(t(ru, "Отмена", "Cancel")) } },
        containerColor = C.paper,
    )
}

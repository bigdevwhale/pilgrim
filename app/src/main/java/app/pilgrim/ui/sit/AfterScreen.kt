package app.pilgrim.ui.sit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pilgrim.container
import app.pilgrim.data.Sitting
import app.pilgrim.domain.Book
import app.pilgrim.domain.Kind
import app.pilgrim.domain.Origin
import app.pilgrim.domain.Words
import app.pilgrim.domain.day
import app.pilgrim.domain.t
import app.pilgrim.sit.Buzz
import app.pilgrim.ui.Go
import app.pilgrim.ui.Home
import app.pilgrim.ui.components.Art
import app.pilgrim.ui.components.Card
import app.pilgrim.ui.components.Centered
import app.pilgrim.ui.components.Gap
import app.pilgrim.ui.components.HandField
import app.pilgrim.ui.components.InkButton
import app.pilgrim.ui.components.Label
import app.pilgrim.ui.components.LineButton
import app.pilgrim.ui.components.Link
import app.pilgrim.ui.components.Page
import app.pilgrim.ui.components.Quiet
import app.pilgrim.ui.components.StampImage
import app.pilgrim.ui.here.words
import app.pilgrim.ui.place.NoteForMappers
import app.pilgrim.ui.theme.C
import app.pilgrim.ui.theme.T

/** One line about a sitting: "12 мин · закат · дождь, +7°". */
fun about(s: Sitting, ru: Boolean): String = listOfNotNull(
    Words.duration(s.seconds.toLong(), ru),
    s.inkOf.title(ru),
    s.skyOf?.let { sky -> sky.title(ru) + (if (s.temp != Sitting.NO_TEMP) ", ${if (s.temp > 0) "+" else ""}${s.temp}°" else "") + (if (s.windy) t(ru, ", ветер", ", wind") else "") },
).joinToString(" · ")

/**
 * After ten minutes: two words — what was left here, what was taken — then the stamp comes down.
 * A note from an earlier day may wait in the crevice; one may be left for next time. A marked place
 * may just have been inked; a year may just have come full circle.
 */
@Composable
fun AfterScreen(id: Long, h: Home, go: Go, pressedAlready: Boolean = false) {
    val ru = h.ru
    val context = LocalContext.current
    val c = context.container
    val stamp = h.stamp(id) ?: return
    val s = stamp.sitting
    val p = stamp.place
    var left by rememberSaveable { mutableStateOf(s.left) }
    var took by rememberSaveable { mutableStateOf(s.took) }
    var pressed by rememberSaveable { mutableStateOf(pressedAlready || s.left.isNotBlank() || s.took.isNotBlank()) }
    val drop = remember { Animatable(if (pressed) 1f else 0f) }
    LaunchedEffect(pressed) {
        if (pressed && drop.value < 1f && !Art.still) {
            drop.animateTo(1f, tween(260))
            Buzz(context).thump()
        }
    }

    Page(p.shown(ru), { go.here() }) {
        Text(about(s, ru), style = T.Small)
        Gap(18)
        if (!pressed) {
            Label(t(ru, "Что вы оставили здесь?", "What did you leave here?"))
            HandField(left, { left = Words.word(it) }, t(ru, "одно слово", "one word"))
            Gap(18)
            Label(t(ru, "Что взяли с собой?", "What did you take with you?"))
            HandField(took, { took = Words.word(it) }, t(ru, "одно слово", "one word"))
            Gap(26)
            InkButton(t(ru, "Поставить печать", "Press the stamp"), {
                c.write { words(s.id, left, took) }
                pressed = true
            }, Modifier.fillMaxWidth())
            Gap(8)
            Quiet(t(ru, "Можно и без слов.", "Words are optional."))
        } else {
            Centered {
                Box(Modifier.scale(1.5f - drop.value * .5f).alpha(drop.value)) { StampImage(h.look(stamp), 250.dp) }
            }
            Gap(6)
            val w = words(left, took, ru)
            if (w.isNotEmpty()) Text(w, style = T.Hand.copy(fontSize = 25.sp), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Gap(14)
            AnimatedVisibility(drop.value >= 1f, enter = fadeIn(tween(400))) {
                Column { Afterwards(h, stamp.sitting, p.id, go) }
            }
        }
        Gap(28)
    }
}

@Composable
private fun Afterwards(h: Home, s: Sitting, placeId: Long, go: Go) {
    val ru = h.ru
    val c = LocalContext.current.container
    val p = h.place(placeId) ?: return
    val days = Book.days(p.id, h.sittings)
    val mine = h.stampsAt(p.id)
    val idx = mine.indexOfFirst { it.sitting.id == s.id }

    // A marked place: still pencil, or inked just now.
    if (p.originOf == Origin.OWN) {
        val justInked = idx >= 0 && !mine[idx].pencil && (idx == 0 || mine[idx - 1].pencil)
        if (justInked) {
            Card {
                Text(t(ru, "Место стало чернильным", "The place is inked"), style = T.Heading)
                Text(t(ru, "Три дня — и оно ваше по-настоящему. Его печать теперь ставится чернилами.", "Three days, and it's truly yours. Its stamp is in ink from now on."), style = T.Body)
                if (p.kindOf != Kind.OWN && !p.noted) NoteForMappers(h, p, days)
            }
            Gap(12)
        } else if (days < Book.DAYS) {
            Card {
                Text(t(ru, "Карандашом: ${Words.days(days, true)} из ${Book.DAYS}", "In pencil: ${Words.days(days, false)} of ${Book.DAYS}"), style = T.Heading)
                Text(t(ru, "Приходите сюда в другой день — на третий место станет чернильным.", "Come back on another day — on the third, the place is inked."), style = T.Body)
            }
            Gap(12)
        }
    }

    // A year come full circle with this sitting.
    val seasons = Book.seasons(p, h.sittings)
    val before = Book.seasons(p, h.sittings.filter { it.id != s.id })
    if (seasons.size == 4 && before.size == 3) {
        var name by rememberSaveable { mutableStateOf(p.title) }
        Card {
            Text(t(ru, "Круг года замкнулся", "The year has come full circle"), style = T.Heading)
            Text(t(ru, "Вы сидели здесь зимой, весной, летом и осенью. Дайте месту своё имя — оно будет только в вашей подорожной.", "You've sat here in winter, spring, summer and autumn. Give the place a name of your own — it'll be in your book only."), style = T.Body)
            Gap(6)
            HandField(name, { name = it.take(40) }, t(ru, "Тихая вода", "Still water"), caps = true)
            Gap(10)
            LineButton(t(ru, "Назвать", "Name it"), { c.write { update(p.copy(title = name.trim())) } }, enabled = name.isNotBlank() && name.trim() != p.title)
        }
        Gap(12)
    }

    // The crevice: a note from an earlier day opens now; one may be left for next time.
    val ripe = h.ripe(p.id, s)
    var unfolded by remember { mutableStateOf<app.pilgrim.data.Note?>(null) }
    if (ripe != null || unfolded != null) {
        val shown = unfolded
        Card(onClick = if (shown == null && ripe != null) ({ unfolded = ripe; c.write { openNote(ripe) } }) else null) {
            Label(t(ru, "Записка в щели", "A note in the crevice"))
            if (shown == null && ripe != null) {
                Text(t(ru, "Вы оставили её ${Words.date(day(ripe.leftAt), true)}. Развернуть?", "You left it on ${Words.date(day(ripe.leftAt), false)}. Unfold it?"), style = T.Body)
            } else if (shown != null) Text(shown.text, style = T.Hand.copy(fontSize = 25.sp), modifier = Modifier.rotate(-.6f).padding(top = 6.dp))
        }
        Gap(12)
    }
    var writing by remember { mutableStateOf(false) }
    if (h.sealed(p.id) == null || unfolded != null) Link(t(ru, "Оставить записку в щели — откроется в следующий раз", "Leave a note in the crevice — it opens next time"), onClick = { writing = true })
    if (writing) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { writing = false },
            title = { Text(t(ru, "Записка в щели", "A note in the crevice")) },
            text = {
                Column {
                    Text(t(ru, "Прочитать её можно будет только здесь, в другой день, после десяти минут.", "It can only be read here, on another day, after ten minutes."), style = T.Small)
                    Gap(8)
                    HandField(text, { text = it.take(500) }, t(ru, "себе — в следующий раз", "to yourself, next time"), single = false, caps = true)
                }
            },
            confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { writing = false; c.write { leaveNote(p.id, text) } }) { Text(t(ru, "Сложить", "Fold it in")) } },
            dismissButton = { TextButton(onClick = { writing = false }) { Text(t(ru, "Отмена", "Cancel")) } },
            containerColor = C.paper,
        )
    }
    Gap(16)
    InkButton(t(ru, "В подорожную", "Into the book"), go::book, Modifier.fillMaxWidth())
    Gap(8)
    Centered { Link(t(ru, "К месту", "The place's page"), color = C.muted) { go.place(p.id) } }
}

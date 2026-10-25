package app.pilgrim.ui.here

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pilgrim.BuildConfig
import app.pilgrim.container
import app.pilgrim.data.DemoSeed
import app.pilgrim.domain.Geo
import app.pilgrim.domain.Kind
import app.pilgrim.domain.LatLon
import app.pilgrim.domain.Marking
import app.pilgrim.domain.t
import app.pilgrim.ui.Go
import app.pilgrim.ui.Home
import app.pilgrim.ui.components.Art
import app.pilgrim.ui.components.Card
import app.pilgrim.ui.components.Gap
import app.pilgrim.ui.components.HandField
import app.pilgrim.ui.components.InkButton
import app.pilgrim.ui.components.KindMark
import app.pilgrim.ui.components.Label
import app.pilgrim.ui.components.Page
import app.pilgrim.ui.components.rememberWhereabouts
import app.pilgrim.ui.theme.C
import app.pilgrim.ui.theme.R2
import app.pilgrim.ui.theme.T

/**
 * Marking a place of one's own, here and now: only from a good GPS fix, not on one's own doorstep,
 * two a week at most. It goes in pencil and is inked on the third day.
 */
@Composable
fun MarkScreen(h: Home, go: Go) {
    val ru = h.ru
    val c = LocalContext.current.container
    val wh = rememberWhereabouts(if (BuildConfig.DEMO || Art.still) DemoSeed.here.copy(lat = 55.7712, lon = 49.1301, accuracy = 6f) else null)
    var kind by rememberSaveable { mutableStateOf(Kind.SPRING) }
    var name by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val week by produceState(0) { value = c.markedThisWeek() }
    val f = wh.fix
    val known by produceState(emptyList<LatLon>(), f?.let { "%.4f:%.4f".format(it.lat, it.lon) }, h.places) {
        val fx = f ?: return@produceState
        val me = LatLon(fx.lat, fx.lon)
        value = h.places.map { it.at } + c.pois(me, Marking.TOO_CLOSE * 2).map { it.at }
    }
    val no = f?.let { Marking.check(LatLon(it.lat, it.lon), it.accuracy, h.settings.home, week, known) }

    Page(t(ru, "Отметить место", "Mark a place"), go::back) {
        Gap(6)
        Text(
            t(ru, "Место силы не обязательно есть на карте. Отметьте то, что здесь, — оно ляжет в подорожную карандашом. Чернильным станет, когда вы придёте сюда в третий день.", "A place of power needn't be on the map. Mark what's here: it goes into the book in pencil, and is inked when you come here on a third day."),
            style = T.Body,
        )
        Gap(16)
        Label(t(ru, "Что здесь", "What's here"))
        Gap(8)
        for (row in Kind.pickable.chunked(5)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in row) {
                    val on = k == kind
                    Column(
                        Modifier.weight(1f).clip(R2.Md).border(if (on) 1.6.dp else 1.dp, if (on) C.cinnabar else C.line, R2.Md).clickable { kind = k }.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        KindMark(k, 0, 34.dp, if (on) C.cinnabar else C.ink)
                        Text(k.title(ru), style = T.Small.copy(fontSize = 11.sp, lineHeight = 13.sp, color = if (on) C.cinnabar else C.muted), textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
            Gap(8)
        }
        Gap(10)
        Label(t(ru, "Как вы его зовёте", "What you call it"))
        HandField(name, { name = it.take(60) }, t(ru, "Родник в овраге", "The spring in the ravine"), caps = true)
        Gap(18)
        Card {
            val acc = f?.accuracy
            Text(
                when {
                    !wh.permitted -> t(ru, "Нужна геолокация.", "Location is needed.")
                    f == null -> t(ru, "Ищем вас…", "Finding you…")
                    else -> t(ru, "Точность: ${Geo.metres(acc!!.toDouble(), true)}. Отмечено на этой неделе: $week из ${Marking.PER_WEEK}.", "Accuracy: ${Geo.metres(acc!!.toDouble(), false)}. Marked this week: $week of ${Marking.PER_WEEK}.")
                },
                style = T.Small,
            )
            no?.let {
                Gap(4)
                Text(
                    when (it) {
                        Marking.No.ROUGH -> t(ru, "Точка ставится только по GPS точнее ${Marking.ACCURACY.toInt()} м. Выйдите на открытое место и подождите минуту.", "A place is only put down from a GPS fix better than ${Marking.ACCURACY.toInt()} m. Step into the open and wait a minute.")
                        Marking.No.HOME -> t(ru, "Это слишком близко к дому — ближе ${Marking.FROM_HOME.toInt()} м. Место силы — это куда приходят.", "This is too close to home — within ${Marking.FROM_HOME.toInt()} m. A place of power is somewhere you go to.")
                        Marking.No.WEEK -> t(ru, "На этой неделе вы уже отметили ${Marking.PER_WEEK} места. Новые — через несколько дней.", "You've marked ${Marking.PER_WEEK} places this week. More in a few days.")
                        Marking.No.CLOSE -> t(ru, "Рядом уже есть место — сядьте у него.", "There's a place right here already — sit at that one.")
                    },
                    style = T.Body.copy(fontSize = 15.sp, color = C.cinnabar),
                )
            }
        }
        Gap(16)
        InkButton(t(ru, "Отметить карандашом", "Mark in pencil"), {
            val fx = f ?: return@InkButton
            if (saving) return@InkButton
            saving = true
            c.write { mark(kind, name.ifBlank { kind.title(ru) }, fx) }
            go.back()
        }, Modifier.fillMaxWidth(), enabled = f != null && no == null)
        Gap(10)
        Text(
            t(ru, "Ваши места не попадают в слухи и никуда не отправляются. Общей карте можно будет подсказать о нём потом — если захотите.", "Your places are never told as rumours and are sent nowhere. You can tell the common map about it later, if you like."),
            style = T.Small,
        )
        Gap(24)
    }
}

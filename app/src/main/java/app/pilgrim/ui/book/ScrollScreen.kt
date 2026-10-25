package app.pilgrim.ui.book

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pilgrim.domain.Book
import app.pilgrim.domain.Scroll
import app.pilgrim.domain.Words
import app.pilgrim.domain.t
import app.pilgrim.ui.Go
import app.pilgrim.ui.Home
import app.pilgrim.ui.components.Gap
import app.pilgrim.ui.components.Label
import app.pilgrim.ui.components.Page
import app.pilgrim.ui.components.Rule
import app.pilgrim.ui.theme.C
import app.pilgrim.ui.theme.R2
import app.pilgrim.ui.theme.T
import kotlin.math.cos
import kotlin.math.max

/**
 * A year rolled up: how long in silence, how many places, the walks, the circles closed, a small
 * map of each town's places in the colours of their inks, and every pair of words, in order.
 * The year in progress is a scroll still being written.
 */
@Composable
fun ScrollScreen(year: Int, h: Home, go: Go) {
    val ru = h.ru
    var y by remember { mutableIntStateOf(year) }
    val sc = remember(y, h.stamps) { Scroll.of(y, h.stamps, h.sittings) }
    Page(t(ru, "Свиток года", "The year's scroll"), go::back) {
        if (h.years.size > 1) Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (yy in h.years) Text(
                "$yy", style = T.Button.copy(color = if (yy == y) C.paper else C.ink),
                modifier = Modifier.clip(R2.Md).background(if (yy == y) C.leather else Color.Transparent).border(1.dp, C.line, R2.Md).clickable { y = yy }.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        Roller()
        Column(Modifier.fillMaxWidth().background(C.paper).border(.6.dp, C.line).padding(horizontal = 18.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(t(ru, "Свиток", "Scroll") + " · " + y, style = T.Title)
            if (y == h.today.year) Text(t(ru, "ещё пишется", "still being written"), style = T.Italic)
            Gap(8)
            Rule(Modifier.width(200.dp))
            Gap(10)
            val lines = listOf(
                t(ru, "В тишине", "In silence") to Words.duration(sc.seconds, ru),
                t(ru, "Печатей", "Stamps") to "${sc.stamps.size}",
                t(ru, "Мест", "Places") to "${sc.places}",
                t(ru, "Хождений", "Walks") to "${sc.walks}",
                t(ru, "Кругов года", "Circles closed") to "${sc.circles}",
            )
            for ((k, v) in lines) Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(k, style = T.Body, modifier = Modifier.weight(1f))
                Text(v, style = T.Body.copy(color = C.cinnabar))
            }
            Gap(14)
            val towns = h.tetrads.filter { tt -> sc.stamps.any { it.place.id in tt.ids } }
            for (tt in towns) {
                Label(h.tetradName(tt))
                Gap(4)
                MiniMap(sc.stamps.filter { it.place.id in tt.ids })
                Gap(12)
            }
            if (sc.words.isNotEmpty()) {
                Rule(Modifier.width(200.dp))
                Gap(8)
                Label(t(ru, "Оставлено · взято", "Left · taken"))
                Gap(6)
                for ((l, tk) in sc.words) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(l.ifBlank { "—" }, style = T.Hand.copy(fontSize = 22.sp, color = C.muted), modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    Text("  ◆  ", style = T.Small.copy(fontSize = 9.sp, color = C.cinnabar))
                    Text(tk.ifBlank { "—" }, style = T.Hand.copy(fontSize = 22.sp), modifier = Modifier.weight(1f))
                }
            }
            if (sc.stamps.isEmpty()) Text(t(ru, "В этом году печатей пока нет.", "No stamps this year yet."), style = T.Italic)
        }
        Roller()
        Gap(24)
    }
}

/** The wooden rod a scroll is wound on. */
@Composable
private fun Roller() {
    Box(
        Modifier.fillMaxWidth().height(14.dp).clip(R2.Md)
            .background(Brush.verticalGradient(listOf(Color(0xFF6B4630), C.leatherDark, Color(0xFF6B4630)))),
    )
}

/** A town's places as dots in their inks, the walks' threads between them, by their real places. */
@Composable
private fun MiniMap(stamps: List<app.pilgrim.domain.Stamp>) {
    val places = stamps.map { it.place }.distinctBy { it.id }
    Canvas(Modifier.fillMaxWidth().height(170.dp).border(.6.dp, C.line)) {
        if (places.isEmpty()) return@Canvas
        val lat0 = places.map { it.lat }.average()
        val k = cos(Math.toRadians(lat0))
        val xs = places.map { it.lon * k }
        val ys = places.map { it.lat }
        val span = max(max(xs.max() - xs.min(), ys.max() - ys.min()), .004)
        val cx = (xs.max() + xs.min()) / 2
        val cy = (ys.max() + ys.min()) / 2
        val scale = (minOf(size.width, size.height) * .8f) / span
        fun at(p: app.pilgrim.data.Place) = Offset((size.width / 2 + (p.lon * k - cx) * scale).toFloat(), (size.height / 2 - (p.lat - cy) * scale).toFloat())
        // Faint grid, like a surveyor's sheet.
        val g = 24.dp.toPx()
        var x = 0f
        while (x < size.width) { drawLine(C.line.copy(alpha = .35f), Offset(x, 0f), Offset(x, size.height), 1f); x += g }
        var yy = 0f
        while (yy < size.height) { drawLine(C.line.copy(alpha = .35f), Offset(0f, yy), Offset(size.width, yy), 1f); yy += g }
        for ((_, walk) in Book.walks(stamps)) {
            val pts = walk.sortedBy { it.sitting.startAt }.map { at(it.place) }
            for (i in 1 until pts.size) drawLine(C.cinnabar.copy(alpha = .6f), pts[i - 1], pts[i], 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))
        }
        for (p in places) {
            val mine = stamps.filter { it.place.id == p.id }
            val col = Color(mine.last().sitting.inkOf.argb)
            val r = (4 + mine.size * 1.6f).coerceAtMost(13f).dp.toPx()
            drawCircle(col.copy(alpha = .25f), r * 1.6f, at(p))
            drawCircle(col, r, at(p))
        }
    }
}

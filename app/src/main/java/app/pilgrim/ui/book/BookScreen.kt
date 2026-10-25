package app.pilgrim.ui.book

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.pilgrim.container
import app.pilgrim.domain.Page
import app.pilgrim.domain.Stamp
import app.pilgrim.domain.Tetrad
import app.pilgrim.domain.Words
import app.pilgrim.domain.day
import app.pilgrim.domain.t
import app.pilgrim.ui.Go
import app.pilgrim.ui.Home
import app.pilgrim.ui.components.Card
import app.pilgrim.ui.components.Gap
import app.pilgrim.ui.components.HandField
import app.pilgrim.ui.components.Label
import app.pilgrim.ui.components.Link
import app.pilgrim.ui.components.Rule
import app.pilgrim.ui.components.StampImage
import app.pilgrim.ui.here.words
import app.pilgrim.ui.sit.about
import app.pilgrim.ui.theme.C
import app.pilgrim.ui.theme.Display
import app.pilgrim.ui.theme.R2
import app.pilgrim.ui.theme.T
import app.pilgrim.ui.theme.laid
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * The book itself: a shelf of notebooks (home's first, then each faraway town), and the open one's
 * pages, turned sideways. The title page, then four stamps to a page, and a walk on a page of its own.
 */
@Composable
fun BookScreen(h: Home, go: Go, startTetrad: Int = 0, startPage: Int = 0) {
    val ru = h.ru
    var which by rememberSaveable { mutableIntStateOf(startTetrad) }
    val tetrad = h.tetrads.getOrNull(which) ?: h.tetrads.firstOrNull()
    var open by remember { mutableStateOf<Stamp?>(null) }
    var naming by remember { mutableStateOf<Tetrad?>(null) }

    Column(Modifier.fillMaxSize().laid().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t(ru, "Подорожная", "The book"), style = T.Title.copy(fontSize = 27.sp), modifier = Modifier.weight(1f))
            if (h.years.isNotEmpty()) Link(t(ru, "Свиток года", "The year's scroll")) { go.scroll(h.years.last()) }
        }
        if (h.tetrads.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (tt in h.tetrads) Cover(tt.index, h.tetradName(tt), tt == tetrad) { which = tt.index }
        }
        if (tetrad == null) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                TitlePage(h, null) {}
            }
            return@Column
        }
        val pages = h.pages(tetrad)
        val pager = rememberPagerState(initialPage = startPage.coerceAtMost(pages.size)) { pages.size + 1 }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp), pageSpacing = 12.dp, key = { "${tetrad.index}:$it" }) { i ->
            Sheet(i, pages.size + 1) {
                if (i == 0) TitlePage(h, tetrad) { naming = tetrad }
                else when (val pg = pages[i - 1]) {
                    is Page.Stamps -> StampsPage(h, pg.stamps) { open = it }
                    is Page.Walk -> WalkPage(h, pg) { open = it }
                }
            }
        }
    }
    open?.let { s -> StampDialog(h, s, onPlace = { open = null; go.place(s.place.id) }) { open = null } }
    naming?.let { tt ->
        val c = LocalContext.current.container
        var name by remember { mutableStateOf(h.settings.tetradName(tt.anchor.id).orEmpty()) }
        AlertDialog(
            onDismissRequest = { naming = null },
            title = { Text(t(ru, "Имя тетради", "The notebook's name")) },
            text = { HandField(name, { name = it.take(40) }, t(ru, "Казань", "Kazan"), caps = true) },
            confirmButton = { TextButton(onClick = { naming = null; c.write { settings.update { it.withTetradName(tt.anchor.id, name) } } }) { Text(t(ru, "Сохранить", "Save")) } },
            dismissButton = { TextButton(onClick = { naming = null }) { Text(t(ru, "Отмена", "Cancel")) } },
            containerColor = C.paper,
        )
    }
}

/** A notebook on the shelf: dark leather, a gold numeral. */
@Composable
private fun Cover(index: Int, name: String, on: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(54.dp, 72.dp).shadow(if (on) 4.dp else 1.dp, R2.Sm).clip(R2.Sm).background(if (on) C.leather else C.leatherDark)
                .border(1.dp, C.gold.copy(alpha = if (on) .8f else .35f), R2.Sm).padding(4.dp).border(.6.dp, C.gold.copy(alpha = .5f), R2.Sm),
            contentAlignment = Alignment.Center,
        ) { Text(Words.roman(index + 1), style = T.Heading.copy(fontSize = 19.sp, color = C.gold)) }
        Text(name, style = T.Small.copy(fontSize = 12.sp, color = if (on) C.ink else C.muted), maxLines = 1, modifier = Modifier.width(76.dp).padding(top = 4.dp), textAlign = TextAlign.Center)
    }
}

/** A page of the book: paper, a hairline frame, the page number below. */
@Composable
private fun Sheet(index: Int, count: Int, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().shadow(6.dp, R2.Sm).clip(R2.Sm).laid().border(1.dp, C.line, R2.Sm)) {
        Box(Modifier.fillMaxSize().padding(10.dp).border(.6.dp, C.line.copy(alpha = .6f)).padding(12.dp)) { content() }
        Text(
            if (index == 0) "" else "— ${index} —", style = T.Small.copy(fontSize = 12.sp, color = C.faint),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
        )
        if (index < count - 1) Text("›", style = T.Heading.copy(color = C.faint), modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp))
    }
}

/** The title page: the book's name in old letters, whose it is, the notebook, the count. */
@Composable
private fun TitlePage(h: Home, tetrad: Tetrad?, onName: () -> Unit) {
    val ru = h.ru
    val mine = tetrad?.let { tt -> h.stamps.filter { it.place.id in tt.ids } }.orEmpty()
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(t(ru, "ПОДОРОЖНАЯ", "PILGRIM'S\nPASS"), style = T.Title.copy(fontFamily = Display, fontSize = 38.sp, lineHeight = 44.sp, color = C.cinnabar), textAlign = TextAlign.Center)
        Gap(6)
        Rule(Modifier.width(180.dp))
        Gap(10)
        if (h.settings.owner.isNotBlank()) Text(t(ru, "выдана: ", "issued to: ") + h.settings.owner, style = T.Italic.copy(fontSize = 18.sp, color = C.ink))
        Gap(24)
        if (tetrad == null) {
            Text(t(ru, "Пока ни одной печати.\nНайдите место силы рядом — на вкладке «Здесь».", "No stamps yet.\nFind a place of power nearby, on the Here tab."), style = T.Body, textAlign = TextAlign.Center)
            return@Column
        }
        Label(t(ru, "Тетрадь ${Words.roman(tetrad.index + 1)}", "Notebook ${Words.roman(tetrad.index + 1)}"))
        Text(h.tetradName(tetrad), style = T.Heading.copy(fontSize = 24.sp), modifier = Modifier.clickable(onClick = onName).padding(4.dp))
        Gap(4)
        mine.firstOrNull()?.let { Text(t(ru, "начата ", "begun ") + Words.date(day(it.sitting.startAt), ru), style = T.Small) }
        Gap(20)
        Text(
            Words.places(tetrad.places.size, ru) + " · " + Words.stamps(mine.size, ru) + "\n" + t(ru, "в тишине ", "in silence ") + Words.duration(mine.sumOf { it.sitting.seconds.toLong() }, ru),
            style = T.Body, textAlign = TextAlign.Center,
        )
        Gap(28)
        Text(t(ru, "листайте →", "turn the page →"), style = T.Italic.copy(fontSize = 14.sp, color = C.faint))
    }
}

/** Four stamps, set down by hand: not quite in a grid. */
@Composable
private fun StampsPage(h: Home, stamps: List<Stamp>, onOpen: (Stamp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val size = minOf(maxWidth / 2 - 6.dp, maxHeight / 2 - 22.dp)
        for ((i, s) in stamps.withIndex()) {
            val r = Random(s.sitting.id)
            val col = i % 2
            val row = i / 2
            val x = (maxWidth / 2) * col + (maxWidth / 2 - size) / 2 + ((r.nextFloat() - .5f) * 14).dp
            val y = (maxHeight / 2) * row + (maxHeight / 2 - size - 20.dp) / 2 + ((r.nextFloat() - .5f) * 16).dp
            Column(Modifier.offset(x, y).clickable { onOpen(s) }, horizontalAlignment = Alignment.CenterHorizontally) {
                StampImage(h.look(s), size)
                val w = words(s.sitting.left, s.sitting.took, h.ru)
                if (w.isNotEmpty()) Text(w, style = T.Hand.copy(fontSize = 15.sp, lineHeight = 16.sp, color = C.muted), maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.width(size))
            }
        }
    }
}

/** A day's walk: the places of the day on one thread, in the order you came to them. */
@Composable
private fun WalkPage(h: Home, pg: Page.Walk, onOpen: (Stamp) -> Unit) {
    val ru = h.ru
    val time = DateTimeFormatter.ofPattern("HH:mm")
    Column(Modifier.fillMaxSize()) {
        Label(t(ru, "Хождение", "A walk"), color = C.cinnabar)
        Text(Words.date(pg.date, ru), style = T.Heading)
        Text(Words.places(pg.stamps.map { it.place.id }.toSet().size, ru) + " · " + Words.duration(pg.stamps.sumOf { it.sitting.seconds.toLong() }, ru), style = T.Small)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp)) {
            val n = pg.stamps.size
            val size: Dp = minOf(maxWidth * .5f, maxHeight / n * 1.45f)
            val step = (maxHeight - size) / (n - 1).coerceAtLeast(1)
            val pos = pg.stamps.indices.map { i -> Pair(if (i % 2 == 0) 0.dp else maxWidth - size, step * i) }
            Canvas(Modifier.fillMaxSize()) {
                val path = Path()
                pos.forEachIndexed { i, (x, y) ->
                    val cx = (x + size / 2).toPx()
                    val cy = (y + size / 2).toPx()
                    if (i == 0) path.moveTo(cx, cy) else {
                        val (px, py) = pos[i - 1]
                        val pcx = (px + size / 2).toPx()
                        val pcy = (py + size / 2).toPx()
                        path.cubicTo(pcx, (pcy + cy) / 2, cx, (pcy + cy) / 2, cx, cy)
                    }
                }
                drawPath(path, C.cinnabar.copy(alpha = .55f), style = Stroke(1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))))
                pos.forEach { (x, y) -> drawCircle(C.cinnabar.copy(alpha = .7f), 3.dp.toPx(), Offset((x + size / 2).toPx(), (y + size / 2).toPx())) }
            }
            pg.stamps.forEachIndexed { i, s ->
                val (x, y) = pos[i]
                Box(Modifier.offset(x, y).clickable { onOpen(s) }) { StampImage(h.look(s), size) }
                val tx = if (i % 2 == 0) x + size + 4.dp else x - 90.dp
                Column(Modifier.offset(tx, y + size / 2 - 22.dp).width(86.dp), horizontalAlignment = if (i % 2 == 0) Alignment.Start else Alignment.End) {
                    Text(java.time.Instant.ofEpochMilli(s.sitting.startAt).atZone(java.time.ZoneId.systemDefault()).format(time), style = T.Caps.copy(color = C.cinnabar))
                    Text(s.sitting.took.ifBlank { s.sitting.left }, style = T.Hand.copy(fontSize = 18.sp, lineHeight = 19.sp, color = C.muted), maxLines = 1)
                }
            }
        }
    }
}

/** One stamp, close: when, how long, the weather, the two words. */
@Composable
fun StampDialog(h: Home, s: Stamp, onPlace: (() -> Unit)?, onDismiss: () -> Unit) {
    val ru = h.ru
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.shadow(10.dp, R2.Md).background(C.paper), onClick = onDismiss) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { StampImage(h.look(s), 250.dp) }
            Gap(6)
            Text(s.place.shown(ru), style = T.Heading, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            val at = java.time.Instant.ofEpochMilli(s.sitting.startAt).atZone(java.time.ZoneId.systemDefault())
            Text(Words.date(at.toLocalDate(), ru) + ", " + at.format(DateTimeFormatter.ofPattern("HH:mm")), style = T.Small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(about(s.sitting, ru) + " · " + s.sitting.inkOf.ink(ru), style = T.Small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (s.pencil) Text(t(ru, "карандашом", "in pencil"), style = T.Italic, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            val w = words(s.sitting.left, s.sitting.took, ru)
            if (w.isNotEmpty()) Text(w, style = T.Hand.copy(fontSize = 24.sp), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            if (onPlace != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Link(t(ru, "Страница места", "The place's page"), onClick = onPlace) }
        }
    }
}

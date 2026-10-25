package app.pilgrim.ui.components

import android.os.SystemClock
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pilgrim.domain.Kind
import app.pilgrim.ru
import app.pilgrim.ui.art.Look
import app.pilgrim.ui.art.Silhouette
import app.pilgrim.ui.art.StampArt
import app.pilgrim.ui.theme.C
import app.pilgrim.ui.theme.R2
import app.pilgrim.ui.theme.T
import app.pilgrim.ui.theme.laid
import kotlinx.coroutines.delay

/** Screens are drawn still for the renders: no clocks, no live map. */
object Art {
    @Volatile var still = false
}

@Composable
fun ru(): Boolean = LocalContext.current.ru

/** A card of the book's paper, a shade darker, with a hairline. */
@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(R2.Md)
            .background(C.paper2.copy(alpha = .72f))
            .border(1.dp, C.line, R2.Md)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Cinnabar, for the one thing to do. */
@Composable
fun InkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, color: Color = C.cinnabar) {
    // These buttons save and leave: a second tap while the screen is going would save twice.
    val last = remember { longArrayOf(0) }
    Row(
        modifier
            .height(54.dp)
            .shadow(if (enabled) 3.dp else 0.dp, R2.Md)
            .clip(R2.Md)
            .background(if (enabled) color else C.line)
            .then(
                if (enabled) Modifier.clickable {
                    val now = SystemClock.uptimeMillis()
                    if (now - last[0] > 700) { last[0] = now; onClick() }
                } else Modifier,
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = C.paper)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = T.Button, color = if (enabled) C.paper else C.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** An outlined button, for the second thing. */
@Composable
fun LineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, color: Color = C.ink) {
    Row(
        modifier
            .height(48.dp)
            .clip(R2.Md)
            .border(1.3.dp, if (enabled) color else C.line, R2.Md)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val tint = if (enabled) color else C.faint
        if (icon != null) {
            Icon(icon, null, Modifier.size(19.dp), tint = tint)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = T.Button.copy(fontSize = 15.sp), color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A quiet text link. */
@Composable
fun Link(text: String, modifier: Modifier = Modifier, color: Color = C.cinnabar, onClick: () -> Unit) {
    Text(text, style = T.Small.copy(color = color), modifier = modifier.clip(R2.Sm).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 2.dp))
}

/** Small caps over a section. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = C.muted) {
    Text(text.uppercase(), style = T.Caps.copy(color = color), modifier = modifier)
}

/** A rule with a diamond in the middle, as books are ornamented. */
@Composable
fun Rule(modifier: Modifier = Modifier, color: Color = C.line) {
    Canvas(modifier.fillMaxWidth().height(12.dp)) {
        val y = size.height / 2
        val m = size.width / 2
        val d = 4.dp.toPx()
        drawLine(color, Offset(0f, y), Offset(m - d * 2.5f, y), 1.dp.toPx())
        drawLine(color, Offset(m + d * 2.5f, y), Offset(size.width, y), 1.dp.toPx())
        val p = androidx.compose.ui.graphics.Path().apply { moveTo(m, y - d); lineTo(m + d, y); lineTo(m, y + d); lineTo(m - d, y); close() }
        drawPath(p, color)
    }
}

/** A screen of laid paper with a back arrow and a title, scrolling. */
@Composable
fun Page(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    // Edge to edge the window no longer shrinks for the keyboard: the page does, so a focused field scrolls into view.
    Column(modifier.fillMaxSize().laid().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = C.ink) }
            else Spacer(Modifier.width(16.dp))
            Text(title, style = T.Heading.copy(fontSize = 21.sp), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            actions()
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal = 18.dp),
            content = content,
        )
    }
}

/** A stamp, inked once and kept. */
@Composable
fun StampImage(look: Look, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bmp = remember(look, px) { StampArt.bitmap(context, look, px).asImageBitmap() }
    Image(bmp, null, modifier.size(size))
}

/** A kind's silhouette alone, for pickers and lists. */
@Composable
fun KindMark(kind: Kind, variant: Int = 0, size: Dp = 36.dp, color: Color = C.ink, pencil: Boolean = false, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.argb((color.alpha * 255).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()) }
        Silhouette.draw(drawContext.canvas.nativeCanvas, kind, variant, 0f, 0f, this.size.minDimension, p, pencil)
    }
}

/** A one-line field written in the hand: one word, a name. */
@Composable
fun HandField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, style: TextStyle = T.Hand, single: Boolean = true, caps: Boolean = false) {
    Box(modifier.fillMaxWidth()) {
        if (value.isEmpty()) Text(hint, style = style.copy(color = C.faint))
        BasicTextField(
            value, onChange, singleLine = single, textStyle = style, cursorBrush = SolidColor(C.cinnabar),
            keyboardOptions = KeyboardOptions(capitalization = if (caps) KeyboardCapitalization.Sentences else KeyboardCapitalization.None, imeAction = if (single) ImeAction.Done else ImeAction.Default),
            modifier = Modifier.fillMaxWidth(),
        )
        Canvas(Modifier.fillMaxWidth().height(1.dp).align(Alignment.BottomStart)) { drawLine(C.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
    }
}

/** Draws with an android.graphics.Canvas: the art is written for it. */
@Composable
fun NativeCanvas(modifier: Modifier, draw: (android.graphics.Canvas, Float, Float) -> Unit) {
    Canvas(modifier) { drawContext.canvas.nativeCanvas.let { draw(it, size.width, size.height) } }
}

/** The current time, ticking every [period] ms (still for the renders). */
@Composable
fun rememberNow(period: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(period) {
        if (Art.still) return@LaunchedEffect
        while (true) {
            delay(period)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
fun KeepScreenOn() {
    val view: View = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
fun Gap(h: Int) = Spacer(Modifier.height(h.dp))

@Composable
fun Centered(modifier: Modifier = Modifier, content: @Composable () -> Unit) = Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content() }

/** Centred italic, for the quiet lines. */
@Composable
fun Quiet(text: String, modifier: Modifier = Modifier, color: Color = C.muted) {
    Text(text, style = T.Italic.copy(color = color), textAlign = TextAlign.Center, modifier = modifier.fillMaxWidth())
}

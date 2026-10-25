package app.pilgrim.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.pilgrim.R
import kotlin.random.Random

/**
 * A pilgrim's book: laid paper, brown-black ink, a dark leather cover with gold tooling, cinnabar
 * for the one thing to do. Only the daylight look; the sitting itself is dark.
 */
object C {
    val paper = Color(0xFFF2E9D5)
    val paper2 = Color(0xFFE8DCC2)
    val ink = Color(0xFF2B231D)
    val muted = Color(0xFF6E5D4B)
    val faint = Color(0xFF9C8B74)
    val line = Color(0xFFC9B795)
    val cinnabar = Color(0xFFA8342A)
    val leather = Color(0xFF4A2620)
    val leatherDark = Color(0xFF34190F)
    val gold = Color(0xFFC9A55A)
    val graphite = Color(0xFF6B6760)
    val night = Color(0xFF14100C)
    val nightInk = Color(0xFFE6D8BC)
}

/** Ruslan Display: the old letters of the title and the headings. */
val Display = FontFamily(Font(R.font.ruslandisplay_regular))

/** Old Standard: the body of the book. */
val Serif = FontFamily(
    Font(R.font.oldstandard_regular, FontWeight.Normal),
    Font(R.font.oldstandard_bold, FontWeight.Bold),
    Font(R.font.oldstandard_italic, FontWeight.Normal, FontStyle.Italic),
)

/** Caveat: the two words, and notes in the crevice. */
val Hand = FontFamily(Font(R.font.caveat))

object T {
    val Title = TextStyle(fontFamily = Display, fontSize = 30.sp, lineHeight = 36.sp, color = C.ink, letterSpacing = 0.02.em)
    val Heading = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 25.sp, color = C.ink)
    val Body = TextStyle(fontFamily = Serif, fontSize = 17.sp, lineHeight = 24.sp, color = C.ink)
    val Small = TextStyle(fontFamily = Serif, fontSize = 14.5.sp, lineHeight = 20.sp, color = C.muted)
    val Italic = TextStyle(fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 23.sp, color = C.muted)
    val Caps = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.14.em, color = C.muted)
    val Hand = TextStyle(fontFamily = app.pilgrim.ui.theme.Hand, fontSize = 27.sp, lineHeight = 30.sp, color = C.ink)
    val Button = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 16.5.sp, letterSpacing = 0.02.em)
}

object R2 {
    val Sm = RoundedCornerShape(2.dp)
    val Md = RoundedCornerShape(4.dp)
}

/**
 * Laid paper, as it was made by hand: fine laid lines across, chain lines down every inch or so,
 * a few fibres and specks. One tile, repeated.
 */
object Laid {
    private var tile: Bitmap? = null

    fun tile(): Bitmap = tile ?: run {
        val w = 240
        val h = 240
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(0xFFF2E9D5.toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.strokeWidth = 1f
        for (y in 0 until h step 3) {
            p.color = if (y % 6 == 0) 0x0D6B5434 else 0x07FFFFFF
            c.drawLine(0f, y + .5f, w.toFloat(), y + .5f, p)
        }
        p.strokeWidth = 2f
        p.color = 0x0F6B5434
        c.drawLine(w / 2f, 0f, w / 2f, h.toFloat(), p)
        c.drawLine(0f, 0f, 0f, h.toFloat(), p)
        val r = Random(11)
        repeat(90) {
            p.color = if (r.nextBoolean()) 0x147A6040 else 0x0CFFFFFF
            p.strokeWidth = .8f + r.nextFloat()
            val x = r.nextFloat() * w
            val y = r.nextFloat() * h
            val l = 4 + r.nextFloat() * 14
            for (dx in intArrayOf(-w, 0, w)) for (dy in intArrayOf(-h, 0, h)) c.drawLine(x + dx, y + dy, x + dx + l, y + dy + (r.nextFloat() - .5f) * 3, p)
        }
        repeat(160) {
            p.color = 0x1A8A6E46
            c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, .5f + r.nextFloat() * .7f, p)
        }
        b.also { tile = it }
    }
}

fun Modifier.laid(): Modifier = drawBehind {
    drawRect(ShaderBrush(ImageShader(Laid.tile().asImageBitmap(), TileMode.Repeated, TileMode.Repeated)))
}

@Composable
fun PilgrimTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = C.cinnabar,
            onPrimary = C.paper,
            primaryContainer = C.paper2,
            onPrimaryContainer = C.ink,
            secondary = C.leather,
            background = C.paper,
            surface = C.paper,
            surfaceContainer = C.paper,
            surfaceContainerHigh = C.paper,
            surfaceContainerHighest = C.paper2,
            surfaceContainerLow = C.paper,
            onBackground = C.ink,
            onSurface = C.ink,
            onSurfaceVariant = C.muted,
            outline = C.line,
            outlineVariant = C.line,
        ),
        typography = MaterialTheme.typography.copy(
            bodyLarge = T.Body,
            bodyMedium = T.Body.copy(fontSize = 16.sp),
            labelLarge = T.Button,
            titleLarge = T.Heading,
            headlineSmall = T.Heading,
        ),
        content = content,
    )
}

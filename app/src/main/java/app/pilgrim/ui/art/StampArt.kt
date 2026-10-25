package app.pilgrim.ui.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import app.pilgrim.R
import app.pilgrim.domain.Kind
import app.pilgrim.domain.Sky
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Everything that makes one stamp what it is. */
data class Look(
    val kind: Kind,
    val variant: Int,
    val ink: Int,
    val sky: Sky?,
    val windy: Boolean,
    val minutes: Int,
    /** The place's seed: its frame's shape. */
    val placeSeed: Long,
    /** The sitting's seed: the tilt, the gaps in the ink, the pressure. */
    val seed: Long,
    val title: String,
    val date: String,
    val number: Int,
    val pencil: Boolean,
)

const val GRAPHITE = 0xFF6B6760.toInt()

/**
 * Stamps, inked by hand in code. A frame by the place (round, oval, octagonal or scalloped), the
 * place's name and the date on the band, the silhouette in the middle. Rings by how long you sat;
 * the ink's colour by the sun; the weather on the paper: rain bleeds, snow leaves frost, wind
 * smears a second strike, fog fades. A place still in pencil is only outlined.
 */
object StampArt {
    private var bold: Typeface? = null
    private val cache = LruCache<String, Bitmap>(48)

    fun fonts(context: Context) {
        if (bold == null) bold = runCatching { ResourcesCompat.getFont(context, R.font.oldstandard_bold) }.getOrNull() ?: Typeface.SERIF
    }

    fun bitmap(context: Context, look: Look, px: Int): Bitmap {
        fonts(context)
        val key = "$look@$px"
        cache.get(key)?.let { return it }
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        draw(Canvas(b), look, px)
        cache.put(key, b)
        return b
    }

    fun draw(out: Canvas, look: Look, px: Int) {
        val s = px.toFloat()
        val layer = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(layer)
        val r = Random(look.seed)
        val col = if (look.pencil) GRAPHITE else look.ink
        c.save()
        c.rotate((r.nextFloat() - .5f) * 16f, s / 2, s / 2)
        frame(c, look, s, col)
        c.restore()
        if (!look.pencil) wear(c, look, s, r)

        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        val dir = Random(look.seed * 31).nextFloat() * 6.283f
        if (look.windy && !look.pencil) {
            p.alpha = 70
            out.drawBitmap(layer, cos(dir) * s * .014f, sin(dir) * s * .014f, p)
        }
        if (look.sky?.wet == true && !look.pencil) {
            p.alpha = 34
            val d = s * .007f
            for (k in 0 until 8) out.drawBitmap(layer, cos(k * .785f) * d, sin(k * .785f) * d, p)
        }
        p.alpha = when {
            look.pencil -> 210
            look.sky == Sky.FOG -> 150
            else -> 232
        }
        out.drawBitmap(layer, 0f, 0f, p)
        layer.recycle()
    }

    /** The frame, the band of words, the silhouette. */
    private fun frame(c: Canvas, look: Look, s: Float, col: Int) {
        val cx = s / 2
        val cy = s / 2
        val big = s * .46f
        val shape = (look.placeSeed % 4).toInt().let { if (it < 0) -it else it }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = col
            strokeWidth = s * .024f
            if (look.pencil) pathEffect = DashPathEffect(floatArrayOf(s * .03f, s * .012f), 0f)
        }
        val thin = Paint(stroke).apply { strokeWidth = s * .008f }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; style = if (look.pencil) Paint.Style.STROKE else Paint.Style.FILL; strokeWidth = s * .011f }

        c.drawPath(outline(shape, cx, cy, big), stroke)
        if (look.minutes >= 20) c.drawPath(outline(shape, cx, cy, big * .925f), thin)
        if (look.minutes >= 40) {
            val m = PathMeasure(outline(shape, cx, cy, big * .885f), true)
            val n = 44
            val pos = FloatArray(2)
            for (i in 0 until n) {
                m.getPosTan(m.length * i / n, pos, null)
                c.drawCircle(pos[0], pos[1], s * .0085f, fill.apply { style = Paint.Style.FILL })
            }
            fill.style = if (look.pencil) Paint.Style.STROKE else Paint.Style.FILL
        }
        val band = if (look.minutes >= 40) .84f else if (look.minutes >= 20) .87f else .9f
        val outer = big * band
        val inner = big * .62f
        c.drawCircle(cx, cy, inner, thin)

        // The words on the band: the place above, the date below.
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = col
            typeface = bold
            letterSpacing = .08f
            if (look.pencil) { style = Paint.Style.FILL; alpha = 200 }
        }
        val mid = (outer + inner) / 2
        val oval = RectF(cx - mid, cy - mid, cx + mid, cy + mid)
        // The name takes the top 220°, the date the bottom between the two diamonds.
        val rad = Math.PI.toFloat() / 180
        val arc = 220 * rad * mid * .94f
        fun fit(t: String): String {
            text.textSize = s * .078f
            var u = t.uppercase()
            while (text.measureText(u) > arc && text.textSize > s * .05f) text.textSize *= .95f
            while (text.measureText(u) > arc && u.length > 3) u = u.dropLast(2) + "…"
            return u
        }
        val top = fit(look.title)
        val topPath = Path().apply { addArc(oval, 160f, 220f) }
        c.drawTextOnPath(top, topPath, (220 * rad * mid - text.measureText(top)) / 2, text.textSize * .36f, text)
        text.textSize = s * .064f
        val date = look.date
        val botPath = Path().apply { addArc(oval, 160f, -140f) }
        c.drawTextOnPath(date, botPath, (140 * rad * mid - text.measureText(date)) / 2, text.textSize * .36f, text)
        // Little diamonds where the words meet.
        for (deg in intArrayOf(20, 160)) {
            val x = cx + mid * cos(deg * rad)
            val y = cy + mid * sin(deg * rad)
            c.drawPath(Path().apply { moveTo(x, y - s * .018f); lineTo(x + s * .012f, y); lineTo(x, y + s * .018f); lineTo(x - s * .012f, y); close() }, fill.apply { style = Paint.Style.FILL })
        }
        fill.style = if (look.pencil) Paint.Style.STROKE else Paint.Style.FILL

        // The silhouette, and the visit's number under it.
        val box = inner * 1.5f
        Silhouette.draw(c, look.kind, look.variant, cx - box / 2, cy - inner * .86f, box, fill, look.pencil)
        if (look.number > 0) {
            text.textSize = s * .05f
            text.letterSpacing = .04f
            val n = "№ ${look.number}"
            c.drawText(n, cx - text.measureText(n) / 2, cy + inner * .9f, text)
        }
    }

    private fun outline(shape: Int, cx: Float, cy: Float, r: Float): Path = Path().apply {
        when (shape) {
            0 -> addCircle(cx, cy, r, Path.Direction.CW)
            1 -> addOval(RectF(cx - r, cy - r * .9f, cx + r, cy + r * .9f), Path.Direction.CW)
            2 -> {
                for (i in 0 until 8) {
                    val a = (i * 45 + 22.5) * Math.PI / 180
                    val x = cx + r * 1.02f * cos(a).toFloat()
                    val y = cy + r * 1.02f * sin(a).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            else -> {
                // Scalloped: a rosette of small arcs.
                val n = 24
                for (i in 0..n) {
                    val a = i * 2 * Math.PI / n
                    val x = cx + r * cos(a).toFloat()
                    val y = cy + r * sin(a).toFloat()
                    if (i == 0) moveTo(x, y) else {
                        val m = (i - .5) * 2 * Math.PI / n
                        quadTo(cx + r * 1.07f * cos(m).toFloat(), cy + r * 1.07f * sin(m).toFloat(), x, y)
                    }
                }
                close()
            }
        }
    }

    /** The ink as hand-stamped: gaps where the rubber missed, one side pressed lighter, frost on snow days. */
    private fun wear(c: Canvas, look: Look, s: Float, r: Random) {
        val erase = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
        repeat(150) {
            erase.alpha = 120 + r.nextInt(136)
            c.drawCircle(r.nextFloat() * s, r.nextFloat() * s, s * (.002f + r.nextFloat() * .009f), erase)
        }
        erase.alpha = 255
        erase.strokeWidth = s * .004f
        repeat(5) {
            val x = r.nextFloat() * s
            val y = r.nextFloat() * s
            val a = r.nextFloat() * 6.283f
            val l = s * (.05f + r.nextFloat() * .12f)
            c.drawLine(x, y, x + cos(a) * l, y + sin(a) * l, erase)
        }
        val a = r.nextFloat() * 6.283f
        val g = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            shader = LinearGradient(s / 2 - cos(a) * s / 2, s / 2 - sin(a) * s / 2, s / 2 + cos(a) * s / 2, s / 2 + sin(a) * s / 2, 0x00000000, 0x88000000.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, s, s, g)
        if (look.sky?.frost == true) {
            erase.strokeWidth = s * .0055f
            erase.strokeCap = Paint.Cap.ROUND
            repeat(16) {
                val x = r.nextFloat() * s
                val y = r.nextFloat() * s
                val l = s * (.014f + r.nextFloat() * .022f)
                for (k in 0 until 3) {
                    val t = k * Math.PI.toFloat() / 3 + r.nextFloat() * .3f
                    c.drawLine(x - cos(t) * l, y - sin(t) * l, x + cos(t) * l, y + sin(t) * l, erase)
                }
            }
            repeat(80) { c.drawCircle(r.nextFloat() * s, r.nextFloat() * s, s * .004f, erase) }
        }
    }
}

/** The silhouettes, in a 100 × 100 box, ground at 96. Buildings and nature only. */
object Silhouette {
    private fun rect(l: Float, t: Float, r: Float, b: Float) = Path().apply { addRect(l, t, r, b, Path.Direction.CW) }
    private fun circle(x: Float, y: Float, r: Float) = Path().apply { addCircle(x, y, r, Path.Direction.CW) }
    private fun poly(vararg p: Float) = Path().apply {
        moveTo(p[0], p[1])
        var i = 2
        while (i < p.size) { lineTo(p[i], p[i + 1]); i += 2 }
        close()
    }

    private fun union(vararg ps: Path): Path {
        val out = Path(ps[0])
        for (i in 1 until ps.size) out.op(ps[i], Path.Op.UNION)
        return out
    }

    /** A round-topped arch: a door or a window. */
    private fun arch(l: Float, r: Float, top: Float, bottom: Float) = Path().apply {
        val w = r - l
        moveTo(l, bottom)
        lineTo(l, top + w / 2)
        arcTo(RectF(l, top, r, top + w), 180f, 180f)
        lineTo(r, bottom)
        close()
    }

    /** A pointed arch. */
    private fun pointed(l: Float, r: Float, top: Float, bottom: Float) = Path().apply {
        val m = (l + r) / 2
        val w = r - l
        moveTo(l, bottom)
        lineTo(l, top + w * .8f)
        quadTo(l, top + w * .15f, m, top)
        quadTo(r, top + w * .15f, r, top + w * .8f)
        lineTo(r, bottom)
        close()
    }

    private fun onion(cx: Float, base: Float, half: Float, height: Float) = Path().apply {
        moveTo(cx - half * .78f, base)
        cubicTo(cx - half * 1.5f, base - height * .35f, cx - half * .55f, base - height * .72f, cx, base - height)
        cubicTo(cx + half * .55f, base - height * .72f, cx + half * 1.5f, base - height * .35f, cx + half * .78f, base)
        close()
    }

    private fun roof(top: Float, ht: Float, hb: Float, h: Float) = Path().apply {
        moveTo(50 - ht, top)
        lineTo(50 + ht, top)
        quadTo(50 + hb - 7, top + h, 50 + hb + 4, top + h - 4)
        lineTo(50 + hb - 2, top + h + 2.5f)
        lineTo(50 - hb + 2, top + h + 2.5f)
        lineTo(50 - hb - 4, top + h - 4)
        quadTo(50 - hb + 7, top + h, 50 - ht, top)
        close()
    }

    /** The solid shape and the holes cut into it (doors, windows). */
    fun shape(kind: Kind, variant: Int): Pair<Path, Path?> = when (kind) {
        Kind.CHURCH -> if (variant == 0) union(
            rect(28f, 52f, 72f, 96f), rect(16f, 70f, 84f, 96f), rect(41f, 35f, 59f, 53f), onion(50f, 36f, 11f, 24f),
            rect(49.2f, 4f, 50.8f, 13f), circle(50f, 4.5f, 2f),
            rect(23f, 60f, 31f, 71f), onion(27f, 61f, 5f, 12f), rect(69f, 60f, 77f, 71f), onion(73f, 61f, 5f, 12f),
        ) to union(arch(44f, 56f, 76f, 96f), arch(47.5f, 52.5f, 40f, 49f), arch(21f, 25f, 77f, 86f), arch(75f, 79f, 77f, 86f))
        else union(
            rect(18f, 62f, 82f, 96f), poly(16f, 63f, 50f, 46f, 84f, 63f), rect(40f, 30f, 60f, 96f), poly(38f, 31f, 50f, 3f, 62f, 31f),
        ) to union(arch(44f, 56f, 78f, 96f), circle(50f, 46f, 5f), arch(24f, 30f, 72f, 84f), arch(70f, 76f, 72f, 84f))

        Kind.MOSQUE -> union(
            rect(22f, 62f, 80f, 96f), rect(34f, 53f, 68f, 63f), Path().apply { moveTo(33f, 55f); cubicTo(31f, 30f, 71f, 30f, 69f, 55f); close() },
            rect(50.2f, 28f, 51.8f, 37f), circle(51f, 27.5f, 2f),
            rect(84.5f, 22f, 89.5f, 96f), rect(82f, 38f, 92f, 41.5f), poly(84f, 22.5f, 87f, 7f, 90f, 22.5f),
            rect(11.5f, 36f, 15.5f, 96f), rect(9.5f, 48f, 17.5f, 51f), poly(11f, 36.5f, 13.5f, 26f, 16f, 36.5f),
        ) to union(pointed(45f, 57f, 72f, 96f), pointed(28f, 33f, 70f, 82f), pointed(69f, 74f, 70f, 82f))

        Kind.SYNAGOGUE -> union(
            rect(14f, 32f, 30f, 96f), circle(22f, 32f, 8f), rect(70f, 32f, 86f, 96f), circle(78f, 32f, 8f),
            rect(30f, 46f, 70f, 96f), Path().apply { moveTo(30f, 47f); arcTo(RectF(30f, 27f, 70f, 67f), 180f, 180f); close() },
        ) to union(arch(44f, 56f, 76f, 96f), circle(50f, 50f, 7f), arch(19.5f, 24.5f, 46f, 62f), arch(75.5f, 80.5f, 46f, 62f), arch(19.5f, 24.5f, 70f, 84f), arch(75.5f, 80.5f, 70f, 84f))

        Kind.TEMPLE -> union(
            rect(32f, 80f, 68f, 96f), roof(70f, 14f, 36f, 9f),
            rect(36f, 58f, 64f, 72f), roof(49f, 11f, 28f, 8f),
            rect(40f, 40f, 60f, 51f), roof(31f, 7f, 20f, 8f),
            rect(49f, 11f, 51f, 32f), rect(46f, 17f, 54f, 18.6f), rect(46.5f, 21.5f, 53.5f, 23.1f), rect(47f, 26f, 53f, 27.6f), circle(50f, 9.5f, 2.6f),
        ) to union(rect(45f, 85f, 55f, 96f), rect(46f, 62f, 54f, 70f), rect(47.5f, 43f, 52.5f, 49f))

        Kind.SHRINE -> union(
            rect(38f, 50f, 62f, 94f), poly(32f, 52f, 50f, 34f, 68f, 52f), rect(46.5f, 25f, 53.5f, 37f), poly(45.5f, 26f, 50f, 16f, 54.5f, 26f), rect(32f, 92f, 68f, 96f),
        ) to union(arch(44f, 56f, 58f, 78f), circle(50f, 30.5f, 1.8f))

        Kind.SPRING -> union(
            Path().apply {
                moveTo(50f, 14f)
                cubicTo(50f, 14f, 29f, 44f, 29f, 57f)
                cubicTo(29f, 69f, 38f, 77f, 50f, 77f)
                cubicTo(62f, 77f, 71f, 69f, 71f, 57f)
                cubicTo(71f, 44f, 50f, 14f, 50f, 14f)
                close()
            },
            ring(50f, 87f, 26f, 4.5f, 2.6f), ring(50f, 90f, 40f, 7f, 2.2f),
        ) to Path().apply { addOval(RectF(37f, 52f, 43f, 66f), Path.Direction.CW) }

        Kind.TREE -> union(
            Path().apply {
                moveTo(32f, 96f)
                quadTo(43f, 93f, 45f, 83f)
                cubicTo(46f, 74f, 46f, 66f, 44.5f, 56f)
                lineTo(55.5f, 56f)
                cubicTo(54f, 66f, 54f, 74f, 55f, 83f)
                quadTo(57f, 93f, 68f, 96f)
                close()
            },
            circle(50f, 34f, 22f), circle(31f, 43f, 15f), circle(69f, 43f, 15f), circle(39f, 23f, 14f), circle(61f, 23f, 14f), circle(50f, 49f, 13f),
            rect(8f, 95.5f, 92f, 98f),
        ) to union(circle(42f, 36f, 2.2f), circle(58f, 30f, 1.8f), circle(63f, 46f, 1.6f), circle(34f, 47f, 1.6f))

        Kind.STONE -> union(
            Path().apply { moveTo(16f, 96f); cubicTo(14f, 78f, 26f, 56f, 46f, 54f); cubicTo(64f, 52f, 79f, 65f, 82f, 82f); quadTo(84f, 92f, 80f, 96f); close() },
            Path().apply { moveTo(77f, 96f); cubicTo(79f, 85f, 90f, 83f, 95f, 96f); close() },
            rect(6f, 95.5f, 94f, 98f),
        ) to union(
            Path().apply { moveTo(40f, 62f); lineTo(47f, 72f); lineTo(44f, 80f); lineTo(46f, 80.5f); lineTo(49.5f, 72f); lineTo(42f, 61.5f); close() },
            Path().apply { moveTo(60f, 64f); lineTo(64f, 70f); lineTo(65.5f, 69.5f); lineTo(61.5f, 63.5f); close() },
        )

        Kind.HEIGHT -> union(
            poly(4f, 96f, 34f, 42f, 46f, 60f, 64f, 26f, 96f, 96f), circle(24f, 22f, 7.5f), rect(2f, 95.5f, 98f, 98f),
        ) to poly(64f, 26f, 56.5f, 40f, 60.5f, 37.5f, 64f, 43f, 67.5f, 37.5f, 71.5f, 40f)

        Kind.OWN -> union(
            ring(50f, 50f, 15f, 15f, 2.6f),
            rect(20f, 72f, 80f, 77f), rect(22f, 58f, 78f, 62.5f), rect(22f, 64.5f, 78f, 67.5f),
            rect(25f, 56f, 28.5f, 77f), rect(71.5f, 56f, 75f, 77f), rect(26f, 77f, 29.5f, 96f), rect(70.5f, 77f, 74f, 96f),
            rect(8f, 95.5f, 92f, 98f),
        ) to null
    }

    /** An oval ring as a filled shape (so it unions with the rest). */
    private fun ring(cx: Float, cy: Float, rx: Float, ry: Float, w: Float): Path {
        val o = Path().apply { addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW) }
        val i = Path().apply { addOval(RectF(cx - rx + w, cy - ry + w * .6f, cx + rx - w, cy + ry - w * .6f), Path.Direction.CW) }
        o.op(i, Path.Op.DIFFERENCE)
        return o
    }

    private val shapes = HashMap<String, Pair<Path, Path>>()

    /** Draws a silhouette in a [size] square at ([left], [top]). In pencil it is outlined. */
    fun draw(c: Canvas, kind: Kind, variant: Int, left: Float, top: Float, size: Float, paint: Paint, pencil: Boolean) {
        // The outline (for pencil) and the shape with its doors cut out (for ink).
        val (solid, cut) = shapes.getOrPut("$kind$variant") {
            val (sol, holes) = shape(kind, variant)
            Path(sol).apply { holes?.let { h -> addPath(h) } } to (holes?.let { h -> Path(sol).apply { op(h, Path.Op.DIFFERENCE) } } ?: sol)
        }
        c.save()
        c.translate(left, top)
        c.scale(size / 100f, size / 100f)
        val p = Paint(paint)
        if (pencil) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.6f
            p.strokeJoin = Paint.Join.ROUND
            c.drawPath(solid, p)
        } else {
            p.style = Paint.Style.FILL
            c.drawPath(cut, p)
        }
        c.restore()
    }
}

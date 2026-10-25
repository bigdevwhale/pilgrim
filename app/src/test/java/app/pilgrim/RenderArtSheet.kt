package app.pilgrim

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import app.pilgrim.domain.Ink
import app.pilgrim.domain.Kind
import app.pilgrim.domain.Sky
import app.pilgrim.ui.art.Look
import app.pilgrim.ui.art.StampArt
import app.pilgrim.ui.theme.Laid
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Every kind of stamp, every ink and weather, on laid paper (run with `-Prender`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RenderArtSheet {
    private val out = File(System.getProperty("render.out") ?: "build/art").apply { mkdirs() }

    @Test
    fun stamps() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cell = 300
        val kinds = Kind.entries.flatMap { k -> if (k == Kind.CHURCH) listOf(k to 0, k to 1) else listOf(k to 0) }
        val skies = listOf(null to false, Sky.RAIN to false, Sky.SNOW to false, Sky.CLEAR to true, Sky.FOG to false, null to false)
        val b = Bitmap.createBitmap(cell * skies.size, cell * kinds.size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val tile = Laid.tile()
        var y = 0
        while (y < b.height) { var x = 0; while (x < b.width) { c.drawBitmap(tile, x.toFloat(), y.toFloat(), null); x += tile.width }; y += tile.height }
        kinds.forEachIndexed { row, (k, v) ->
            skies.forEachIndexed { col, (sky, windy) ->
                val pencil = col == skies.lastIndex
                val look = Look(
                    kind = k, variant = v, ink = Ink.entries[(row + col) % 4].argb, sky = sky, windy = windy,
                    minutes = listOf(10, 22, 45, 12, 15, 10)[col], placeSeed = row.toLong(), seed = row * 17L + col,
                    title = if (row % 2 == 0) "Мечеть Марджани" else "Большая хоральная синагога", date = "25 · X · 2026", number = col + 1, pencil = pencil,
                )
                c.drawBitmap(StampArt.bitmap(ctx, look, cell - 20), col * cell + 10f, row * cell + 10f, Paint())
            }
        }
        File(out, "stamps.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The launcher icon at 512 px for the README: background, then foreground, as the launcher stacks them. */
    @Test
    fun icon() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val n = 512
        val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        for (id in intArrayOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground)) {
            val d = androidx.core.content.ContextCompat.getDrawable(ctx, id)!!
            // The adaptive icon's safe zone: the 108 dp canvas shows its middle 72 dp.
            d.setBounds(-n / 4, -n / 4, n + n / 4, n + n / 4)
            d.draw(c)
        }
        File(out, "logo.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

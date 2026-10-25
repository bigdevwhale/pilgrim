package app.pilgrim.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Typeface
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.pilgrim.BuildConfig
import app.pilgrim.R
import app.pilgrim.container
import app.pilgrim.ru
import app.pilgrim.data.DemoSeed
import app.pilgrim.data.Place
import app.pilgrim.domain.Book
import app.pilgrim.domain.Geo
import app.pilgrim.domain.LatLon
import app.pilgrim.domain.Rumors
import app.pilgrim.domain.t
import app.pilgrim.ui.Go
import app.pilgrim.ui.Home
import app.pilgrim.ui.art.StampArt
import app.pilgrim.ui.components.Art
import app.pilgrim.ui.components.NativeCanvas
import app.pilgrim.ui.components.rememberWhereabouts
import app.pilgrim.ui.theme.C
import app.pilgrim.ui.theme.T
import app.pilgrim.ui.theme.laid
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot

/** The map tiles as an old survey: colour drained toward sepia. */
private fun sepia(): ColorMatrixColorFilter {
    val m = ColorMatrix()
    m.setSaturation(0.2f)
    m.postConcat(ColorMatrix(floatArrayOf(1.0f, 0f, 0f, 0f, 6f, 0f, 0.93f, 0f, 0f, 2f, 0f, 0f, 0.76f, 0f, -6f, 0f, 0f, 0f, 1f, 0f)))
    return ColorMatrixColorFilter(m)
}

/** What the map shows: the book's places as small stamps, this week's rumour as a vague circle, you. */
class Layer(private val context: Context, private val density: Float) {
    private val serif: Typeface? = ResourcesCompat.getFont(context, R.font.caveat)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 17 * density; textAlign = Paint.Align.CENTER; color = 0xFF2B231D.toInt() }
    private val halo = Paint(label).apply { style = Paint.Style.STROKE; strokeWidth = 4 * density; color = 0xD9F2E9D5.toInt() }
    private val rumorFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22A8342A }
    private val rumorLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xAAA8342A.toInt(); strokeWidth = 1.6f * density; pathEffect = DashPathEffect(floatArrayOf(8 * density, 6 * density), 0f) }
    private val q = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 34 * density; textAlign = Paint.Align.CENTER; color = 0xCCA8342A.toInt() }
    private val me = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2F3B75.toInt() }
    private val meRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x332F3B75 }

    var marks: List<Pair<Place, Bitmap>> = emptyList()
    var rumor: LatLon? = null
    var here: LatLon? = null

    fun draw(c: Canvas, at: (LatLon) -> Point) {
        rumor?.let { r ->
            val p = at(r)
            val edge = at(Geo.offset(r, Rumors.CIRCLE, 90.0))
            val rad = hypot((edge.x - p.x).toFloat(), (edge.y - p.y).toFloat()).coerceAtLeast(26 * density)
            c.drawCircle(p.x.toFloat(), p.y.toFloat(), rad, rumorFill)
            c.drawCircle(p.x.toFloat(), p.y.toFloat(), rad, rumorLine)
            c.drawText("?", p.x.toFloat(), p.y + 12 * density, q)
        }
        for ((pl, b) in marks) {
            val p = at(pl.at)
            c.drawBitmap(b, p.x - b.width / 2f, p.y - b.height / 2f, null)
            c.drawText(pl.shown(context.ru), p.x.toFloat(), p.y + b.height / 2f + 15 * density, halo)
            c.drawText(pl.shown(context.ru), p.x.toFloat(), p.y + b.height / 2f + 15 * density, label)
        }
        here?.let { h ->
            val p = at(h)
            c.drawCircle(p.x.toFloat(), p.y.toFloat(), 14 * density, meRing)
            c.drawCircle(p.x.toFloat(), p.y.toFloat(), 5.5f * density, me)
        }
    }

    fun hit(x: Float, y: Float, at: (LatLon) -> Point): Place? = marks.map { it.first }
        .minByOrNull { val p = at(it.at); hypot(p.x - x, p.y - y) }
        ?.takeIf { val p = at(it.at); hypot(p.x - x, p.y - y) < 30 * density }
}

/**
 * The book's places on an old-looking map, this week's rumour as a circle with a question in it,
 * and you. The rest of the common map stays hidden: places are found by walking.
 */
@Composable
fun MapScreen(h: Home, go: Go) {
    val ru = h.ru
    val context = LocalContext.current
    val c = context.container
    val density = LocalDensity.current.density
    val wh = rememberWhereabouts(if (BuildConfig.DEMO || Art.still) DemoSeed.here else null)
    val layer = remember { Layer(context, density) }
    val px = (40 * density).toInt()
    layer.marks = remember(h.places, h.stamps) {
        h.places.map { p ->
            val last = h.stampsAt(p.id).lastOrNull()
            p to StampArt.bitmap(context, (if (last != null) h.look(last) else h.outline(p)).copy(title = " ", date = " ", number = 0, pencil = last?.pencil ?: true), px)
        }
    }
    val rumor by produceState<LatLon?>(null, h.settings.rumor, h.places) {
        value = h.settings.rumor.takeIf { it.isNotEmpty() }?.let { c.poi(it) }?.takeIf { p -> h.places.none { it.osm == p.osm } }?.let { Rumors.circle(it) }
    }
    layer.rumor = rumor
    layer.here = wh.fix?.let { LatLon(it.lat, it.lon) }
    val centre = wh.fix?.let { LatLon(it.lat, it.lon) } ?: h.settings.home ?: h.places.lastOrNull()?.at

    Box(Modifier.fillMaxSize()) {
        if (Art.still) StandIn(layer, centre, Modifier.fillMaxSize())
        else {
            val map = rememberMap { mv ->
                mv.overlays.add(object : Overlay() {
                    override fun draw(cv: Canvas, m: MapView, shadow: Boolean) {
                        if (shadow) return
                        layer.draw(cv) { m.projection.toPixels(GeoPoint(it.lat, it.lon), null) }
                    }

                    override fun onSingleTapConfirmed(e: MotionEvent, m: MapView): Boolean {
                        val p = layer.hit(e.x, e.y) { m.projection.toPixels(GeoPoint(it.lat, it.lon), null) } ?: return false
                        go.place(p.id)
                        return true
                    }
                })
            }
            LaunchedEffect(centre == null) { centre?.let { map.controller.setCenter(GeoPoint(it.lat, it.lon)) } }
            LaunchedEffect(layer.marks, rumor, layer.here) { map.invalidate() }
            AndroidView({ map }, Modifier.fillMaxSize())
        }
        Column(Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth()) {
            Box(Modifier.laid().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Column {
                    Text(t(ru, "Карта", "Map"), style = T.Title.copy(fontSize = 22.sp))
                    Text(
                        t(ru, "Ваши места — печатями. Круг с вопросом — слух недели: где-то там.", "Your places as stamps. The circle with a question is this week's rumour: somewhere in there."),
                        style = T.Small,
                    )
                }
            }
        }
        if (Book.stamps(h.places, h.sittings).isEmpty() && rumor == null) Text(
            t(ru, "Пока пусто.", "Empty so far."), style = T.Italic, modifier = Modifier.align(Alignment.Center),
        )
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun rememberMap(setup: (MapView) -> Unit): MapView {
    val context = LocalContext.current
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            overlayManager.tilesOverlay.setColorFilter(sepia())
            overlayManager.tilesOverlay.loadingBackgroundColor = 0xFFEDE3CC.toInt()
            overlayManager.tilesOverlay.loadingLineColor = 0xFFE0D3B6.toInt()
            controller.setZoom(14.0)
            setup(this)
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            map.onPause()
            map.onDetach()
        }
    }
    return map
}

/** For the renders: paper, a river and streets, the places where they'd be at zoom 14. */
@Composable
private fun StandIn(layer: Layer, centre: LatLon?, modifier: Modifier) {
    NativeCanvas(modifier) { cv, w, hgt ->
        cv.drawColor(0xFFEDE3CC.toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        p.color = 0xFFB9C3BE.toInt(); p.strokeWidth = hgt * .05f
        cv.drawPath(android.graphics.Path().apply { moveTo(-20f, hgt * .7f); cubicTo(w * .3f, hgt * .55f, w * .6f, hgt * .9f, w + 20, hgt * .6f) }, p)
        for (s in listOf(floatArrayOf(0f, .25f, 1f, .2f), floatArrayOf(0f, .48f, 1f, .52f), floatArrayOf(.3f, 0f, .35f, 1f), floatArrayOf(.72f, 0f, .66f, 1f))) {
            p.color = 0xFFD9CAA8.toInt(); p.strokeWidth = hgt * .03f
            cv.drawLine(s[0] * w, s[1] * hgt, s[2] * w, s[3] * hgt, p)
            p.color = 0xFFF7F0E0.toInt(); p.strokeWidth = hgt * .02f
            cv.drawLine(s[0] * w, s[1] * hgt, s[2] * w, s[3] * hgt, p)
        }
        val c0 = centre ?: return@NativeCanvas
        // Web Mercator at zoom 14 on a 256-px tile, scaled to the screen's density.
        val scale = 256.0 * (1 shl 14) / 360.0 * (w / 1080.0) * 2.6
        layer.draw(cv) { ll ->
            val x = w / 2 + (ll.lon - c0.lon) * scale
            val y = hgt / 2 - (Math.toDegrees(Math.log(Math.tan(Math.PI / 4 + Math.toRadians(ll.lat) / 2))) - Math.toDegrees(Math.log(Math.tan(Math.PI / 4 + Math.toRadians(c0.lat) / 2)))) * scale
            Point(x.toInt(), y.toInt())
        }
    }
}

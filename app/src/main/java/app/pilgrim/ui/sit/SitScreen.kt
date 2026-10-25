package app.pilgrim.ui.sit

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pilgrim.domain.Phase
import app.pilgrim.domain.SitRules
import app.pilgrim.domain.Why
import app.pilgrim.domain.t
import app.pilgrim.ru
import app.pilgrim.sit.Session
import app.pilgrim.sit.SitState
import app.pilgrim.ui.Go
import app.pilgrim.ui.components.Gap
import app.pilgrim.ui.components.KindMark
import app.pilgrim.ui.components.LineButton
import app.pilgrim.ui.theme.C
import app.pilgrim.ui.theme.Serif
import app.pilgrim.ui.theme.T

/**
 * The sitting, while you look at it — which you shouldn't for long. Dark, the place's silhouette
 * faint in the middle, the time sat. The service keeps the count with the screen off; this only shows it.
 */
@Composable
fun SitScreen(go: Go, demo: SitState? = null) {
    val context = LocalContext.current
    val ru = context.ru
    val live by Session.state.collectAsStateWithLifecycle()
    val s = demo ?: live
    var asking by remember { mutableStateOf(false) }
    val ink = C.nightInk
    // Light icons in the bars while the screen is dark.
    val view = LocalView.current
    DisposableEffect(view) {
        var c = view.context
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        val bars = (c as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        onDispose {
            bars?.isAppearanceLightStatusBars = true
            bars?.isAppearanceLightNavigationBars = true
        }
    }

    Box(Modifier.fillMaxSize().background(C.night)) {
        if (s == null) {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                LineButton(t(ru, "Назад", "Back"), { go.here() }, color = ink)
            }
            return@Box
        }
        Column(
            Modifier.align(Alignment.Center).systemBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            KindMark(s.target.kind, s.target.variant, 132.dp, ink.copy(alpha = .16f))
            Gap(12)
            Text(s.target.name.ifBlank { s.target.kind.title(ru) }, style = T.Heading.copy(color = ink.copy(alpha = .7f)), textAlign = TextAlign.Center)
            Gap(20)
            val sec = s.sat / 1000
            if (s.phase != Phase.BROKEN) Text("%02d:%02d".format(sec / 60, sec % 60), style = T.Title.copy(fontFamily = Serif, fontSize = 60.sp, lineHeight = 64.sp, color = ink, letterSpacing = 2.sp))
            Gap(10)
            val reached = s.sat >= SitRules.TEN
            val line = when (s.phase) {
                Phase.DARKEN -> t(ru, "Погасите экран и отложите телефон.\nОтсчёт пойдёт, когда экран погаснет.", "Turn the screen off and put the phone away.\nThe time starts when the screen goes dark.")
                Phase.SITTING -> when {
                    reached -> t(ru, "Десять минут прошли. Можно остаться —\nвремя идёт, пока телефон лежит.", "Ten minutes are in. You may stay —\nthe time runs while the phone rests.")
                    s.lit != null -> t(ru, "Экран горит. Погасите его — через ${((SitRules.GLANCE - s.lit) / 1000).coerceAtLeast(0)} с\nсеанс прервётся.", "The screen is on. Turn it off — in ${((SitRules.GLANCE - s.lit) / 1000).coerceAtLeast(0)} s\nthe sitting ends.")
                    else -> t(ru, "Тихо. Вы сидите.", "Quiet. You're sitting.")
                }
                Phase.DONE -> t(ru, "Ставим печать…", "Inking the stamp…")
                Phase.BROKEN -> when (s.why) {
                    Why.SCREEN -> t(ru, "Телефон взяли в руки.\nМесто подождёт.", "The phone was picked up.\nThe place will wait.")
                    Why.LEFT -> t(ru, "Вы отошли от места.\nМесто подождёт.", "You walked away from the place.\nThe place will wait.")
                    Why.MOVED -> t(ru, "Телефон слишком долго был в движении.\nМесто подождёт.", "The phone was moving too long.\nThe place will wait.")
                    Why.MOCK -> t(ru, "Геолокация подменена другим приложением —\nтак сеанс не засчитать.", "The location is faked by another app —\nthe sitting can't count.")
                    Why.STOPPED, null -> t(ru, "Вы встали раньше.\nМесто подождёт.", "You got up early.\nThe place will wait.")
                }
            }
            Text(line, style = T.Italic.copy(fontSize = 17.sp, lineHeight = 25.sp, color = ink.copy(alpha = .75f)), textAlign = TextAlign.Center)
            Gap(34)
            when (s.phase) {
                Phase.BROKEN -> LineButton(t(ru, "Хорошо", "All right"), { Session.clear(); go.here() }, Modifier.width(200.dp), color = ink)
                Phase.DONE -> Unit
                else -> LineButton(t(ru, "Встать", "Get up"), { if (reached) Session.stop(context) else asking = true }, Modifier.width(200.dp), color = ink)
            }
        }
    }
    if (asking) AlertDialog(
        onDismissRequest = { asking = false },
        title = { Text(t(ru, "Встать раньше?", "Get up early?")) },
        text = { Text(t(ru, "До десяти минут печати не будет. Место подождёт.", "Before ten minutes there's no stamp. The place will wait.")) },
        confirmButton = { TextButton(onClick = { asking = false; Session.stop(context) }) { Text(t(ru, "Встать", "Get up")) } },
        dismissButton = { TextButton(onClick = { asking = false }) { Text(t(ru, "Посидеть ещё", "Sit a while more")) } },
        containerColor = C.paper,
    )
}

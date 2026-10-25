package app.pilgrim.ui.components

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pilgrim.sit.Fix
import app.pilgrim.sit.Here

/**
 * The phone's position while the screen is open, and a way to ask for it: the permission, or
 * location switched on. [hint] is the last place the phone knew, however old.
 */
class Whereabouts(val fix: Fix?, val permitted: Boolean, val on: Boolean, val hint: Fix?, val ask: () -> Unit) {
    val ready get() = permitted && on
}

@Composable
fun rememberWhereabouts(demo: Fix? = null): Whereabouts {
    val context = LocalContext.current
    var fix by remember { mutableStateOf(demo) }
    val here = remember {
        Here(context) { f ->
            val old = fix
            // A rougher fix only replaces a better one when that one has gone stale.
            if (old == null || f.accuracy <= old.accuracy * 1.5f || f.at - old.at > 15_000) fix = f
        }
    }
    var permitted by remember { mutableStateOf(here.permitted()) }
    var on by remember { mutableStateOf(here.on()) }
    var hint by remember { mutableStateOf(demo ?: here.lastKnown()) }
    var restart by remember { mutableIntStateOf(0) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permitted = here.permitted()
        restart++
    }
    LifecycleResumeEffect(restart) {
        permitted = here.permitted()
        on = here.on()
        if (hint == null) hint = here.lastKnown()
        if (demo == null && !Art.still) here.start()
        onPauseOrDispose { here.stop() }
    }
    LaunchedEffect(demo) { if (demo != null) fix = demo }
    return Whereabouts(fix, permitted || demo != null, on || demo != null, fix ?: hint) {
        if (!permitted) ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        else runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
    }
}

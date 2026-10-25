package app.pilgrim.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pilgrim.domain.Ink
import app.pilgrim.domain.Kind
import app.pilgrim.domain.Sky
import app.pilgrim.domain.t
import app.pilgrim.ui.Home
import app.pilgrim.ui.art.Look
import app.pilgrim.ui.components.Gap
import app.pilgrim.ui.components.HandField
import app.pilgrim.ui.components.InkButton
import app.pilgrim.ui.components.KindMark
import app.pilgrim.ui.components.Label
import app.pilgrim.ui.components.Link
import app.pilgrim.ui.components.Rule
import app.pilgrim.ui.components.StampImage
import app.pilgrim.ui.theme.C
import app.pilgrim.ui.theme.T
import app.pilgrim.ui.theme.laid

/**
 * Three pages: what the book is, how the ten minutes are checked, whose book it is.
 * [again] shows it from the settings, without asking for the name.
 */
@Composable
fun WelcomeScreen(h: Home, again: Boolean = false, startStep: Int = 0, onDone: (String) -> Unit) {
    val ru = h.ru
    var step by rememberSaveable { mutableIntStateOf(startStep) }
    var name by rememberSaveable { mutableStateOf(h.settings.owner) }
    val last = if (again) 1 else 2

    Column(Modifier.fillMaxSize().laid().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Gap(28)
            when (step) {
                0 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy((-34).dp), verticalAlignment = Alignment.CenterVertically) {
                        for ((i, k) in listOf(Kind.SPRING, Kind.MOSQUE, Kind.CHURCH).withIndex()) {
                            val look = Look(k, 0, Ink.entries[(i + 1) % 4].argb, if (i == 0) Sky.RAIN else null, false, 10 + i * 15, i.toLong() + 1, 40L + i, listOf(t(ru, "Родник у оврага", "The ravine spring"), t(ru, "Мечеть Марджани", "Marjani Mosque"), t(ru, "Петропавловский собор", "Peter and Paul"))[i], listOf("4 · III · 2026", "8 · XI · 2025", "22 · XI · 2025")[i], i + 1, false)
                            Box(Modifier.rotate((i - 1) * 9f)) { StampImage(look, if (i == 1) 150.dp else 122.dp) }
                        }
                    }
                    Gap(22)
                    Text(t(ru, "Подорожная", "Pilgrim"), style = T.Title.copy(fontSize = 40.sp, lineHeight = 46.sp))
                    Gap(4)
                    Rule(Modifier.size(180.dp, 12.dp))
                    Gap(14)
                    Text(
                        t(ru, "У паломников была книжечка, куда в каждом святом месте ставили печать. Эта — такая же.", "Pilgrims carried a little book, stamped at every holy place they reached. This is one."),
                        style = T.Body.copy(fontSize = 18.sp, lineHeight = 26.sp), textAlign = TextAlign.Center,
                    )
                    Gap(12)
                    Text(
                        t(ru, "Приходите в место силы — храм, мечеть, синагогу, к роднику, старому дереву, камню. Посидите там десять минут без телефона. Получите печать, какой больше нет ни у кого: её цвет — от солнца, следы — от погоды.", "Come to a place of power — a church, a mosque, a synagogue, a spring, an old tree, a stone. Sit there ten minutes without the phone. Get a stamp no one else has: its colour from the sun, its marks from the weather."),
                        style = T.Body, textAlign = TextAlign.Center,
                    )
                }
                1 -> {
                    KindMark(Kind.OWN, 0, 90.dp, C.ink)
                    Gap(12)
                    Text(t(ru, "Десять минут — честно", "Ten minutes, honestly"), style = T.Title.copy(fontSize = 28.sp), textAlign = TextAlign.Center)
                    Gap(16)
                    for ((head, body) in listOf(
                        t(ru, "Вы у места", "You're at the place") to t(ru, "Геолокация: в радиусе места, несколько замеров подряд.", "Location: within the place's radius, over several fixes."),
                        t(ru, "Телефон лежит", "The phone rests") to t(ru, "Отсчёт идёт, пока экран погашен. Взглянуть на уведомление можно; взять телефон в руки — нет.", "The time runs while the screen is dark. A glance at a notification is fine; picking it up is not."),
                        t(ru, "Вы сидите", "You're sitting") to t(ru, "Датчик движения: если телефон едет в кармане — сеанс прерывается.", "The motion sensor: if the phone rides in a pocket, the sitting ends."),
                        t(ru, "Места нет на карте?", "Not on the map?") to t(ru, "Отметьте своё. Сначала карандашом — чернилами на третий день.", "Mark your own. In pencil first; inked on the third day."),
                    )) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                            Label(head, color = C.cinnabar)
                            Text(body, style = T.Body)
                        }
                    }
                    Gap(6)
                    Text(t(ru, "Нужны геолокация и уведомление сеанса. С телефона уходит только округлённая точка — для карты мест и погоды.", "Location and the sitting's notification are needed. Only a rounded point leaves the phone, for the map of places and the weather."), style = T.Small, textAlign = TextAlign.Center)
                }
                else -> {
                    StampImage(Look(Kind.OWN, 0, 0, null, false, 10, 3, 3, " ", " ", 0, true), 150.dp)
                    Gap(16)
                    Text(t(ru, "Чья подорожная?", "Whose book is it?"), style = T.Title.copy(fontSize = 28.sp), textAlign = TextAlign.Center)
                    Gap(8)
                    Text(t(ru, "Имя будет на титульном листе. Можно оставить пустым.", "The name goes on the title page. You may leave it empty."), style = T.Small, textAlign = TextAlign.Center)
                    Gap(18)
                    HandField(name, { name = it.take(40) }, t(ru, "ваше имя", "your name"), caps = true)
                }
            }
            Gap(20)
        }
        InkButton(
            if (step < last) t(ru, "Дальше", "Next") else if (again) t(ru, "Понятно", "Got it") else t(ru, "Открыть подорожную", "Open the book"),
            { if (step < last) step++ else onDone(name) }, Modifier.fillMaxWidth(),
        )
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            if (step > 0) Link(t(ru, "Назад", "Back"), color = C.muted) { step-- } else Gap(36)
        }
    }
}

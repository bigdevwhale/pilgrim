package app.pilgrim.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import app.pilgrim.BuildConfig
import app.pilgrim.container
import app.pilgrim.data.DemoSeed
import app.pilgrim.domain.Nights
import app.pilgrim.domain.SitRules
import app.pilgrim.domain.t
import app.pilgrim.ui.Go
import app.pilgrim.ui.Home
import app.pilgrim.ui.components.Art
import app.pilgrim.ui.components.Card
import app.pilgrim.ui.components.CommunityLinks
import app.pilgrim.ui.components.Gap
import app.pilgrim.ui.components.HandField
import app.pilgrim.ui.components.Label
import app.pilgrim.ui.components.LineButton
import app.pilgrim.ui.components.Link
import app.pilgrim.ui.components.Page
import app.pilgrim.ui.components.rememberWhereabouts
import app.pilgrim.ui.theme.C
import app.pilgrim.ui.theme.T

@Composable
fun SettingsScreen(h: Home, go: Go) {
    val ru = h.ru
    val c = LocalContext.current.container
    val s = h.settings
    var owner by remember { mutableStateOf(s.owner) }
    val wh = rememberWhereabouts(if (BuildConfig.DEMO || Art.still) DemoSeed.here else null)

    Page(t(ru, "Настройки", "Settings"), go::back) {
        Gap(4)
        Card {
            Label(t(ru, "Чья подорожная", "Whose book"))
            HandField(owner, { owner = it.take(40); c.write { settings.update { st -> st.copy(owner = owner.trim()) } } }, t(ru, "имя на титуле", "the name on the title page"), caps = true)
        }
        Gap(12)
        Card {
            Label(t(ru, "Дом", "Home"))
            val home = s.home
            Text(
                when {
                    home == null -> t(ru, "Ещё не найден. Подорожная узнаёт дом сама — по тому, где телефон бывает ночью, когда вы открываете приложение. Нужно три ночи.", "Not found yet. The book finds home by itself — where the phone is at night when you open the app. It takes three nights.")
                    s.homeManual -> t(ru, "Отмечен вами: ", "Set by you: ") + Nights.show(home)
                    else -> t(ru, "Найден по ночам: ", "Found by nights: ") + Nights.show(home)
                },
                style = T.Body,
            )
            Text(t(ru, "Дом нужен для слухов («к северу от дома») и чтобы своё место нельзя было отметить у себя под окном. С телефона он никуда не уходит.", "Home is for rumours (\"north of home\") and so a place of your own can't be marked under your window. It never leaves the phone."), style = T.Small, modifier = Modifier.padding(top = 4.dp))
            Gap(10)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LineButton(t(ru, "Дом здесь", "Home is here"), {
                    val f = wh.fix ?: return@LineButton
                    c.write { settings.update { it.copy(homeLat = f.lat, homeLon = f.lon, homeManual = true) } }
                }, enabled = wh.fix != null)
                if (home != null) LineButton(t(ru, "Забыть", "Forget"), { c.write { settings.update { it.copy(homeLat = Double.NaN, homeLon = Double.NaN, homeManual = false, nights = "") } } })
            }
        }
        Gap(12)
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t(ru, "Вибрация на десятой минуте", "A buzz at ten minutes"), style = T.Body)
                    Text(t(ru, "Два мягких толчка. Не будильник: можно остаться.", "Two soft pulses. Not an alarm: you may stay."), style = T.Small)
                }
                Switch(s.buzz, { v -> c.write { settings.update { it.copy(buzz = v) } } }, colors = SwitchDefaults.colors(checkedTrackColor = C.cinnabar))
            }
        }
        Gap(12)
        Card {
            Label(t(ru, "Язык", "Language"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                for ((tag, name) in listOf("" to t(ru, "Как в системе", "System"), "ru" to "Русский", "en" to "English")) {
                    val on = AppCompatDelegate.getApplicationLocales().toLanguageTags() == tag
                    LineButton(name, { AppCompatDelegate.setApplicationLocales(if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)) }, color = if (on) C.cinnabar else C.ink)
                }
            }
        }
        Gap(12)
        Card {
            Text(t(ru, "Как проверяется", "How it's checked"), style = T.Heading)
            Gap(4)
            for (line in listOf(
                t(ru, "Место — по геолокации: вы в его радиусе, по нескольким замерам. Подменённая геолокация не засчитывается.", "The place — by location: you're within its radius, over several fixes. A faked location doesn't count."),
                t(ru, "Без телефона — по экрану: отсчёт идёт, пока экран погашен. Взглянуть на уведомление можно; экран горит ${SitRules.GLANCE / 1000} секунд подряд — телефон взяли в руки.", "Without the phone — by the screen: the time runs while it's dark. A glance at a notification is fine; lit for ${SitRules.GLANCE / 1000} seconds in a row, the phone was picked up."),
                t(ru, "Сидите — по датчику движения: минута ходьбы с телефоном прерывает сеанс.", "Sitting — by the motion sensor: a minute of walking with the phone ends the sitting."),
                t(ru, "После десяти минут печать ваша, что бы ни случилось.", "After ten minutes the stamp is yours, whatever happens."),
            )) Row(Modifier.padding(vertical = 3.dp)) {
                Text("◆", style = T.Small.copy(color = C.cinnabar, fontSize = 10.sp), modifier = Modifier.padding(top = 5.dp, end = 8.dp))
                Text(line, style = T.Body.copy(fontSize = 15.5.sp))
            }
            Gap(6)
            Text(t(ru, "С телефона уходит только округлённая точка: в OpenStreetMap — чтобы найти места рядом, и в Open-Meteo — погода на печати. Слова, записки и дом остаются у вас.", "Only a rounded point leaves the phone: to OpenStreetMap, for places nearby, and to Open-Meteo, for the weather on the stamp. Words, notes and home stay with you."), style = T.Small)
        }
        Gap(12)
        CommunityLinks(ru)
        Gap(8)
        Link(t(ru, "Показать вступление", "Show the introduction"), onClick = go::guide)
        Text("${t(ru, "Подорожная", "Pilgrim")} ${BuildConfig.VERSION_NAME} · " + t(ru, "данные карты © участники OpenStreetMap", "map data © OpenStreetMap contributors"), style = T.Small.copy(fontSize = 12.sp, color = C.faint), modifier = Modifier.padding(vertical = 8.dp))
        Gap(16)
    }
}

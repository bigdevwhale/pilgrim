package app.pilgrim.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.pilgrim.domain.t
import app.pilgrim.ui.theme.C
import app.pilgrim.ui.theme.T

/** The StillCraft bot: deep links file a bug or an idea for this app, or follow its releases. */
object Community {
    private const val BOT = "https://t.me/ArcaneWhisperBot?start="
    const val SLUG = "pilgrim"
    const val BUG = "${BOT}bug_$SLUG"
    const val IDEA = "${BOT}wish_$SLUG"
    const val FOLLOW = "${BOT}f_$SLUG"
    const val CHAT = "https://t.me/+Q4gFEDk8O842OGVi"
}

/** Report a bug, suggest an idea, follow updates, join the chat; each opens Telegram. */
@Composable
fun CommunityLinks(ru: Boolean) {
    val ctx = LocalContext.current
    fun open(url: String) { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    Card {
        Text(t(ru, "Сообщество", "Community"), style = T.Heading)
        Text(
            t(ru, "Приложение делается вместе с теми, кто им пользуется. Всё идёт через бота StillCraft в Telegram.", "The app is made together with the people who use it. It all goes through the StillCraft bot on Telegram."),
            style = T.Small, modifier = Modifier.padding(bottom = 4.dp),
        )
        for ((icon, label, sub, url) in listOf(
            Item("🐞", t(ru, "Сообщить о баге", "Report a bug"), t(ru, "что сломалось и как повторить", "what broke and how to repeat it"), Community.BUG),
            Item("💡", t(ru, "Предложить идею", "Suggest an idea"), t(ru, "функция, вид места, печать", "a feature, a kind of place, a stamp"), Community.IDEA),
            Item("🔔", t(ru, "Подписаться на обновления", "Follow updates"), t(ru, "бот напишет, когда выйдет версия", "the bot writes when a version is out"), Community.FOLLOW),
            Item("💬", t(ru, "Чат StillCraft", "StillCraft chat"), t(ru, "все приложения и люди", "all the apps and people"), Community.CHAT),
        )) {
            Row(Modifier.fillMaxWidth().clickable { open(url) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(icon, style = T.Heading, modifier = Modifier.width(34.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, style = T.Body)
                    Text(sub, style = T.Small)
                }
                Text("›", style = T.Heading.copy(color = C.cinnabar))
            }
        }
    }
}

private data class Item(val icon: String, val label: String, val sub: String, val url: String)

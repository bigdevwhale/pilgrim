package app.pilgrim.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.pilgrim.domain.LatLon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class Settings(
    val onboarded: Boolean = false,
    /** Whose book: written on the title page. */
    val owner: String = "",
    val homeLat: Double = Double.NaN,
    val homeLon: Double = Double.NaN,
    /** Home set by hand; nights no longer move it. */
    val homeManual: Boolean = false,
    /** Where the phone has been at night, as "cell=count;…": the most frequent cell is home. */
    val nights: String = "",
    /** The last night written down, as an epoch day. */
    val lastNight: Long = 0,
    /** This week's rumour: a place's OSM id, and when it was first told. */
    val rumor: String = "",
    val rumorAt: Long = 0,
    /** Rumours already told, comma-separated. */
    val told: String = "",
    /** Areas already fetched from the common map, as "lat,lon,radius,at;…". */
    val areas: String = "",
    /** Notebooks' own names, as "placeId=name" lines. */
    val tetrads: String = "",
    /** Kinds whose manners were shown once before a first sitting. */
    val mannersSeen: String = "",
    /** A short buzz when ten minutes are in. */
    val buzz: Boolean = true,
) {
    val home: LatLon? get() = if (homeLat.isNaN() || homeLon.isNaN()) null else LatLon(homeLat, homeLon)

    fun tetradName(anchor: Long): String? = tetrads.lines().firstOrNull { it.startsWith("$anchor=") }?.substringAfter('=')?.takeIf { it.isNotBlank() }

    fun withTetradName(anchor: Long, name: String): Settings {
        val rest = tetrads.lines().filter { it.isNotBlank() && !it.startsWith("$anchor=") }
        return copy(tetrads = (rest + listOfNotNull(name.trim().replace("\n", " ").takeIf { it.isNotEmpty() }?.let { "$anchor=$it" })).joinToString("\n"))
    }

    val toldSet: Set<String> get() = told.split(',').filter { it.isNotBlank() }.toSet()
    val mannersSet: Set<String> get() = mannersSeen.split(',').filter { it.isNotBlank() }.toSet()
}

class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<Settings> = store.data.map { it.read() }

    private fun Preferences.read() = Settings(
        onboarded = this[ONBOARDED] ?: false,
        owner = this[OWNER] ?: "",
        homeLat = this[HOME_LAT] ?: Double.NaN,
        homeLon = this[HOME_LON] ?: Double.NaN,
        homeManual = this[HOME_MANUAL] ?: false,
        nights = this[NIGHTS] ?: "",
        lastNight = this[LAST_NIGHT] ?: 0,
        rumor = this[RUMOR] ?: "",
        rumorAt = this[RUMOR_AT] ?: 0,
        told = this[TOLD] ?: "",
        areas = this[AREAS] ?: "",
        tetrads = this[TETRADS] ?: "",
        mannersSeen = this[MANNERS] ?: "",
        buzz = this[BUZZ] ?: true,
    )

    suspend fun now(): Settings = settings.first()

    suspend fun update(change: (Settings) -> Settings) {
        store.edit {
            val s = change(it.read())
            it[ONBOARDED] = s.onboarded
            it[OWNER] = s.owner
            if (s.homeLat.isNaN()) it.remove(HOME_LAT) else it[HOME_LAT] = s.homeLat
            if (s.homeLon.isNaN()) it.remove(HOME_LON) else it[HOME_LON] = s.homeLon
            it[HOME_MANUAL] = s.homeManual
            it[NIGHTS] = s.nights
            it[LAST_NIGHT] = s.lastNight
            it[RUMOR] = s.rumor
            it[RUMOR_AT] = s.rumorAt
            it[TOLD] = s.told
            it[AREAS] = s.areas
            it[TETRADS] = s.tetrads
            it[MANNERS] = s.mannersSeen
            it[BUZZ] = s.buzz
        }
    }

    private companion object {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val OWNER = stringPreferencesKey("owner")
        val HOME_LAT = doublePreferencesKey("home_lat")
        val HOME_LON = doublePreferencesKey("home_lon")
        val HOME_MANUAL = booleanPreferencesKey("home_manual")
        val NIGHTS = stringPreferencesKey("nights")
        val LAST_NIGHT = longPreferencesKey("last_night")
        val RUMOR = stringPreferencesKey("rumor")
        val RUMOR_AT = longPreferencesKey("rumor_at")
        val TOLD = stringPreferencesKey("told")
        val AREAS = stringPreferencesKey("areas")
        val TETRADS = stringPreferencesKey("tetrads")
        val MANNERS = stringPreferencesKey("manners")
        val BUZZ = booleanPreferencesKey("buzz")
    }
}

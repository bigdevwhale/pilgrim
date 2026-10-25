package app.pilgrim.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import app.pilgrim.domain.Ink
import app.pilgrim.domain.Kind
import app.pilgrim.domain.LatLon
import app.pilgrim.domain.Origin
import app.pilgrim.domain.Sky
import kotlinx.coroutines.flow.Flow

/** A place in the book: one sat at, or marked. */
@Entity(tableName = "place", indices = [Index("osm")])
data class Place(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "node/123", "way/45"; empty for a place marked by hand. */
    val osm: String = "",
    val origin: String = Origin.OSM.name,
    val kind: String = Kind.OWN.name,
    /** A silhouette's variant: an onion dome or a spire. */
    val variant: Int = 0,
    val name: String = "",
    /** The name given once the year has come full circle here. */
    val title: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val radius: Double = 40.0,
    val createdAt: Long = System.currentTimeMillis(),
    val seed: Long = 0,
    /** A note left for the mappers (OSM Notes) that this place exists. */
    val noted: Boolean = false,
) {
    val kindOf get() = Kind.of(kind)
    val originOf get() = Origin.of(origin)
    val at get() = LatLon(lat, lon)

    /** Its own name if the circle gave it one, else the map's, else the kind. */
    fun shown(ru: Boolean) = title.ifBlank { name.ifBlank { kindOf.title(ru) } }
}

/** Ten minutes or more at a place: one stamp. */
@Entity(tableName = "sitting", indices = [Index("placeId")])
data class Sitting(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: Long,
    val startAt: Long,
    val endAt: Long,
    val seconds: Int,
    val ink: String = Ink.DAY.name,
    val sky: String = "",
    val windy: Boolean = false,
    val temp: Int = NO_TEMP,
    /** One word: what was left here. */
    val left: String = "",
    /** One word: what was taken. */
    val took: String = "",
) {
    val inkOf get() = Ink.of(ink)
    val skyOf get() = Sky.of(sky)
    val minutes get() = seconds / 60

    companion object {
        const val NO_TEMP = Int.MIN_VALUE
    }
}

/** A note in the crevice: opens on a sitting at the same place on a later day. */
@Entity(tableName = "note", indices = [Index("placeId")])
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: Long,
    val text: String,
    val leftAt: Long,
    val openedAt: Long = 0,
)

/** A place on the common map near where the phone has been: the source of "nearby" and of rumours. */
@Entity(tableName = "poi")
data class Poi(
    @PrimaryKey val osm: String,
    val kind: String,
    val variant: Int = 0,
    val name: String = "",
    val lat: Double,
    val lon: Double,
    val radius: Double,
    val fetchedAt: Long = System.currentTimeMillis(),
) {
    val kindOf get() = Kind.of(kind)
    val at get() = LatLon(lat, lon)
    fun shown(ru: Boolean) = name.ifBlank { kindOf.title(ru) }
}

@Dao
interface PilgrimDao {
    @Query("SELECT * FROM place ORDER BY createdAt") fun observePlaces(): Flow<List<Place>>
    @Query("SELECT * FROM sitting ORDER BY startAt") fun observeSittings(): Flow<List<Sitting>>
    @Query("SELECT * FROM note ORDER BY leftAt") fun observeNotes(): Flow<List<Note>>

    @Query("SELECT * FROM place") suspend fun places(): List<Place>
    @Query("SELECT * FROM sitting") suspend fun sittings(): List<Sitting>
    @Query("SELECT * FROM place WHERE id = :id") suspend fun place(id: Long): Place?
    @Query("SELECT * FROM place WHERE osm = :osm AND osm != '' LIMIT 1") suspend fun placeByOsm(osm: String): Place?
    @Query("SELECT * FROM sitting WHERE id = :id") suspend fun sitting(id: Long): Sitting?

    @Query("SELECT * FROM poi WHERE lat BETWEEN :s AND :n AND lon BETWEEN :w AND :e")
    suspend fun pois(s: Double, n: Double, w: Double, e: Double): List<Poi>

    @Query("SELECT * FROM poi WHERE osm = :osm") suspend fun poi(osm: String): Poi?

    @Insert suspend fun insert(p: Place): Long
    @Insert suspend fun insert(s: Sitting): Long
    @Insert suspend fun insert(n: Note): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPois(p: List<Poi>)
    @Update suspend fun update(p: Place)
    @Update suspend fun update(s: Sitting)
    @Update suspend fun update(n: Note)

    @Query("DELETE FROM place WHERE id = :id") suspend fun deletePlace(id: Long)
    @Query("DELETE FROM sitting WHERE placeId = :id") suspend fun deleteSittings(id: Long)
    @Query("DELETE FROM note WHERE placeId = :id") suspend fun deleteNotes(id: Long)
    @Query("DELETE FROM poi WHERE fetchedAt < :before") suspend fun dropPois(before: Long)
}

@Database(entities = [Place::class, Sitting::class, Note::class, Poi::class], version = 1, exportSchema = true)
abstract class PilgrimDb : RoomDatabase() {
    abstract fun dao(): PilgrimDao

    companion object {
        fun create(context: Context): PilgrimDb = Room.databaseBuilder(context, PilgrimDb::class.java, "pilgrim.db").build()
    }
}

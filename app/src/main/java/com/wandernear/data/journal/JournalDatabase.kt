package com.wandernear.data.journal

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to the journal. Reads return a [Flow] so the UI refreshes
 * automatically whenever something is saved, edited, or deleted. Only the
 * saved-place methods are used in J1; later steps add methods for visits,
 * bucket items, and photos.
 */
@Dao
interface JournalDao {

    @Query("SELECT * FROM saved_place ORDER BY updatedAt DESC")
    fun savedPlaces(): Flow<List<SavedPlace>>

    @Query("SELECT * FROM saved_place WHERE id = :id")
    fun savedPlace(id: Long): Flow<SavedPlace?>

    @Insert
    suspend fun insert(place: SavedPlace): Long

    @Update
    suspend fun update(place: SavedPlace)

    @Delete
    suspend fun delete(place: SavedPlace)
}

/** The traveller's private journal database (separate from the city pack). */
@Database(
    entities = [SavedPlace::class, VisitDate::class, BucketItem::class, Photo::class],
    version = 1,
    exportSchema = false,
)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao

    companion object {
        @Volatile
        private var instance: JournalDatabase? = null

        /** One shared database instance for the whole app. */
        fun get(context: Context): JournalDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JournalDatabase::class.java,
                    "journal.db",
                ).build().also { instance = it }
            }
    }
}

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

    // --- Bucket list (what's left = the rows still status = "todo") ---
    @Query("SELECT * FROM bucket_item WHERE savedPlaceId = :placeId ORDER BY createdAt")
    fun bucketItems(placeId: Long): Flow<List<BucketItem>>

    @Insert
    suspend fun insert(item: BucketItem): Long

    @Update
    suspend fun update(item: BucketItem)

    @Delete
    suspend fun delete(item: BucketItem)

    // --- Visit dates (each visit gives one anniversary) ---
    @Query("SELECT * FROM visit_date WHERE savedPlaceId = :placeId ORDER BY visitedOn DESC")
    fun visits(placeId: Long): Flow<List<VisitDate>>

    @Insert
    suspend fun insert(visit: VisitDate): Long

    @Delete
    suspend fun delete(visit: VisitDate)

    // --- Photos ---
    @Query("SELECT * FROM photo WHERE savedPlaceId = :placeId ORDER BY createdAt")
    fun photos(placeId: Long): Flow<List<Photo>>

    @Insert
    suspend fun insert(photo: Photo): Long

    @Update
    suspend fun update(photo: Photo)

    @Delete
    suspend fun delete(photo: Photo)
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

package com.wandernear.data.journal

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The traveller's private journal tables. These live in their OWN database
 * (journal.db), completely separate from the city data pack, so refreshing a
 * city can never delete the user's memories.
 *
 * All four tables are defined up front (even though J1 only uses `saved_place`)
 * so later journal steps never need a risky database migration on data the user
 * has already saved. Child rows are deleted automatically when their saved place
 * is deleted (ForeignKey CASCADE).
 */

/**
 * A place the traveller saved. Self-contained: the name and coordinates are
 * snapshotted so it survives even if the city pack is refreshed or the place
 * later disappears from OpenStreetMap.
 */
@Entity(tableName = "saved_place")
data class SavedPlace(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lat: Double,
    val lng: Double,
    val category: String? = null,
    val subcategory: String? = null,
    val osmType: String? = null,   // link back to the pack place, if it came from one
    val osmId: Long? = null,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

/** One dated visit to a saved place (each visit gives one anniversary). */
@Entity(
    tableName = "visit_date",
    foreignKeys = [ForeignKey(
        entity = SavedPlace::class,
        parentColumns = ["id"],
        childColumns = ["savedPlaceId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("savedPlaceId")],
)
data class VisitDate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val savedPlaceId: Long,
    val visitedOn: Long,       // epoch millis of the visit day
    val note: String? = null,
    val createdAt: Long,
)

/** A "want to do" / "done" checklist item for a saved place. */
@Entity(
    tableName = "bucket_item",
    foreignKeys = [ForeignKey(
        entity = SavedPlace::class,
        parentColumns = ["id"],
        childColumns = ["savedPlaceId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("savedPlaceId")],
)
data class BucketItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val savedPlaceId: Long,
    val text: String,
    val status: String = "todo",   // "todo" | "done"  ("what's left" = the todo items)
    val doneOn: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A photo copied into the app's private storage for a saved place. */
@Entity(
    tableName = "photo",
    foreignKeys = [ForeignKey(
        entity = SavedPlace::class,
        parentColumns = ["id"],
        childColumns = ["savedPlaceId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("savedPlaceId")],
)
data class Photo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val savedPlaceId: Long,
    val filePath: String,          // path inside the app's private files dir
    val caption: String? = null,
    val createdAt: Long,
)

package com.wandernear.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Reads the bundled, read-only Melbourne data pack.
 *
 * The pack ships inside the app's assets. On first use it is copied once into
 * the app's private storage, then opened read-only. We open the SQLite file
 * directly (rather than through Room) because the pack is read-only and already
 * contains the full-text search index we built in the data pipeline.
 */
class CityDatabase(private val context: Context) {

    private fun open(): SQLiteDatabase {
        val dbFile = File(context.filesDir, DB_NAME)
        if (!dbFile.exists()) {
            context.assets.open(DB_NAME).use { input ->
                dbFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    /** A quick summary used by the first screen to prove the data really loaded. */
    fun stats(): CityStats = open().use { db ->
        val total = db.rawQuery("SELECT COUNT(*) FROM place", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
        val byCategory = linkedMapOf<String, Int>()
        db.rawQuery(
            "SELECT category, COUNT(*) FROM place GROUP BY category ORDER BY COUNT(*) DESC",
            null,
        ).use { c ->
            while (c.moveToNext()) byCategory[c.getString(0)] = c.getInt(1)
        }
        val attribution = db.rawQuery("SELECT attribution FROM city LIMIT 1", null).use { c ->
            if (c.moveToFirst()) c.getString(0) else ""
        }
        CityStats(total, byCategory, attribution)
    }

    private companion object {
        const val DB_NAME = "melbourne.db"
    }
}

/** A small snapshot of what's in the data pack, shown on the home screen. */
data class CityStats(
    val total: Int,
    val byCategory: Map<String, Int>,
    val attribution: String,
)

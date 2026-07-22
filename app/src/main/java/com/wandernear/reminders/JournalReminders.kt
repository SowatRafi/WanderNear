package com.wandernear.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wandernear.core.model.LatLng
import com.wandernear.core.model.haversineKm
import com.wandernear.data.LocationProvider
import com.wandernear.data.journal.JournalDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * The two journal reminders, both grounded in real saved data:
 *  1. Anniversaries — "N years ago today you visited X" from your visit dates.
 *  2. Nearby nudge — "you're back near X" with what's still on its bucket list.
 *
 * Both post through [Notifier], which silently does nothing if notifications
 * are off. The anniversary check also runs daily in the background via
 * WorkManager, so it fires even when the app isn't opened.
 */
object JournalReminders {

    /** Posts an anniversary notification for any visit whose day-of-year is today. */
    suspend fun checkAnniversaries(context: Context) {
        val dao = JournalDatabase.get(context).journalDao()
        val today = LocalDate.now()
        dao.allVisitsWithPlace().forEach { visit ->
            val date = Instant.ofEpochMilli(visit.visitedOn).atZone(ZoneId.systemDefault()).toLocalDate()
            val sameDay = date.monthValue == today.monthValue && date.dayOfMonth == today.dayOfMonth
            if (sameDay && date.year < today.year) {
                val years = today.year - date.year
                Notifier.notify(
                    context = context,
                    id = ("anniv:${visit.placeName}:${visit.visitedOn}").hashCode(),
                    title = "A WanderNear anniversary",
                    text = "$years year${plural(years)} ago today you visited ${visit.placeName}.",
                )
            }
        }
    }

    /** If you're within ~300 m of a saved place that still has to-do items, nudge you. */
    suspend fun checkNearbyNudge(context: Context) {
        val here = LocationProvider.lastKnown(context) ?: return   // no permission / no fix
        val dao = JournalDatabase.get(context).journalDao()
        dao.allSavedPlaces().forEach { place ->
            val nearby = haversineKm(here, LatLng(place.lat, place.lng)) <= 0.3
            if (nearby) {
                val todos = dao.todoCount(place.id)
                if (todos > 0) {
                    Notifier.notify(
                        context = context,
                        id = ("nudge:${place.id}").hashCode(),
                        title = "You're back near ${place.name}",
                        text = "$todos thing${plural(todos)} still on your bucket list here.",
                    )
                    return   // one nudge is enough
                }
            }
        }
    }

    /** Schedules the daily background anniversary check (kept if already scheduled). */
    fun scheduleDaily(context: Context) {
        val request = PeriodicWorkRequestBuilder<AnniversaryWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "anniversary_check",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun plural(n: Int) = if (n > 1) "s" else ""
}

/** Runs the anniversary check once a day in the background. */
class AnniversaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        JournalReminders.checkAnniversaries(applicationContext)
        return Result.success()
    }
}

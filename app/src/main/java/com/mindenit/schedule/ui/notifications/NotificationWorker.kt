package com.mindenit.schedule.ui.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import com.mindenit.schedule.data.EventRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class NotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val eventId = inputData.getLong(KEY_EVENT_ID, -1L)
        val startEpoch = inputData.getLong(KEY_EVENT_START, -1L)
        val offsetMin = inputData.getInt(KEY_OFFSET_MIN, 5)
        if (eventId <= 0L || startEpoch <= 0L) return Result.success()

        val startLdt = LocalDateTime.ofInstant(Instant.ofEpochSecond(startEpoch), ZoneId.systemDefault())
        val date = startLdt.toLocalDate()

        // Warm month cache
        try { EventRepository.ensureMonthCached(applicationContext, YearMonth.from(date)) } catch (_: Throwable) {}

        val events = EventRepository.getEventsForDate(applicationContext, date)
        val event = events.firstOrNull { it.id == eventId && it.start == startLdt }
            ?: events.firstOrNull { it.id == eventId }
            ?: return Result.success()

        // If we're late (past event start), skip
        if (LocalDateTime.now().isAfter(event.start)) return Result.success()

        NotificationHelper.notifyForEvent(applicationContext, event, offsetMin)
        return Result.success()
    }

    companion object {
        private const val KEY_EVENT_ID = "event_id"
        private const val KEY_EVENT_START = "event_start"
        private const val KEY_OFFSET_MIN = "offset_min"

        fun data(eventId: Long, eventStartEpoch: Long, offsetMin: Int): Data =
            Data.Builder()
                .putLong(KEY_EVENT_ID, eventId)
                .putLong(KEY_EVENT_START, eventStartEpoch)
                .putInt(KEY_OFFSET_MIN, offsetMin)
                .build()
    }
}

package com.mindenit.schedule.ui.notifications

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZoneId
import com.mindenit.schedule.data.EventRepository
import com.mindenit.schedule.data.SchedulesStorage

object NotificationsScheduler {
    private const val TAG_PRE_EVENT = "notif_pre_event"

    // Legacy API kept for compatibility; now delegates to scheduleForUpcoming
    fun scheduleInitialWaves(context: Context) {
        scheduleForUpcoming(context)
    }

    fun scheduleForUpcoming(context: Context) {
        // Fast-escape if there's no active schedule
        val storage = SchedulesStorage(context)
        if (storage.getActive() == null) return

        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val offsets = (sp.getString("pref_notif_offsets", null)
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it in 5..30 && it % 5 == 0 }
            ?.sorted()
            ?.toSet()
            ?: setOf(5))

        val now = java.time.LocalDateTime.now()
        val today = now.toLocalDate()
        val tomorrow = today.plusDays(1)

        val eventsToday = EventRepository.getEventsForDate(context, today)
        val eventsTomorrow = EventRepository.getEventsForDate(context, tomorrow)
        val upcoming = (eventsToday + eventsTomorrow).filter { it.start.isAfter(now) }
        val wm = WorkManager.getInstance(context)

        wm.cancelAllWorkByTag(TAG_PRE_EVENT)

        for (e in upcoming) {
            val startInstant = e.start.atZone(ZoneId.systemDefault()).toEpochSecond()
            for (off in offsets) {
                val triggerAt = e.start.minusMinutes(off.toLong())
                if (triggerAt.isAfter(now)) {
                    val delay = Duration.between(now, triggerAt).coerceAtMost(Duration.ofDays(7))
                    val req = OneTimeWorkRequestBuilder<NotificationWorker>()
                        .setInitialDelay(delay)
                        .setInputData(NotificationWorker.data(e.id, startInstant, off))
                        .addTag(TAG_PRE_EVENT)
                        .build()
                    val uniqueName = "pre_evt_${e.id}_${startInstant}_${off}"
                    wm.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, req)
                }
            }
        }
    }
}

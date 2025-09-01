package com.mindenit.schedule.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mindenit.schedule.network.EventDto
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object EventRepository {
    private const val PREFS = "events_cache"
    private const val KEY_DATA = "data_" // + type_id_yyyy-MM
    private const val KEY_DAY = "day_"   // + type_id_yyyy-MM
    private const val TAG = "EventRepository"

    private val gson = Gson()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun cacheKey(type: ScheduleType, id: Long): String = "${type}_${id}"
    private fun monthKey(type: ScheduleType, id: Long, ym: YearMonth): String = cacheKey(type, id) + "_" + ym.toString()

    suspend fun ensureMonthCached(context: Context, ym: YearMonth) {
        val storage = SchedulesStorage(context)
        val active = storage.getActive()

        Log.d(TAG, "ensureMonthCached: ym=$ym, active=$active")

        if (active == null) {
            Log.w(TAG, "No active schedule found")
            return
        }

        val monthKey = monthKey(active.first, active.second, ym)

        val today = LocalDate.now()
        val lastDay = prefs(context).getString(KEY_DAY + monthKey, null)

        Log.d(TAG, "Cache check: monthKey=$monthKey, lastDay=$lastDay, today=$today")

        if (lastDay == today.toString()) {
            Log.d(TAG, "Data already cached for today")
            return // already cached today for this month
        }

        val start = ym.atDay(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        val end = ym.atEndOfMonth().plusDays(1).atStartOfDay(ZoneId.systemDefault()).minusNanos(1).toEpochSecond()

        Log.d(TAG, "Fetching data from API: type=${active.first}, id=${active.second}, start=$start, end=$end")

        try {
            val dto: List<EventDto> = withContext(Dispatchers.IO) {
                when (active.first) {
                    ScheduleType.GROUP -> com.mindenit.schedule.network.ApiClient.api.getGroupSchedule(active.second, start, end).data
                    ScheduleType.TEACHER -> com.mindenit.schedule.network.ApiClient.api.getTeacherSchedule(active.second, start, end).data
                    ScheduleType.AUDITORIUM -> com.mindenit.schedule.network.ApiClient.api.getAuditoriumSchedule(active.second, start, end).data
                }
            }

            Log.d(TAG, "API response: ${dto.size} events received")
            dto.forEachIndexed { index, event ->
                Log.d(TAG, "Event $index: ${event.subject.title} at ${event.auditorium.name} on ${event.startedAt}")
            }

            val json = gson.toJson(dto)
            prefs(context).edit {
                putString(KEY_DATA + monthKey, json)
                putString(KEY_DAY + monthKey, today.toString())
            }

            Log.d(TAG, "Data cached successfully")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to fetch data from API", e)
            // keep old cache; do not update stamp so we'll retry next time
        }
    }

    private fun loadCachedDtos(context: Context, ym: YearMonth): List<EventDto> {
        val storage = SchedulesStorage(context)
        val active = storage.getActive()

        if (active == null) {
            Log.w(TAG, "No active schedule for loading cache")
            return emptyList()
        }

        val monthKey = monthKey(active.first, active.second, ym)
        val json = prefs(context).getString(KEY_DATA + monthKey, null)

        Log.d(TAG, "Loading cached data: monthKey=$monthKey, hasData=${json != null}")

        if (json == null) {
            Log.w(TAG, "No cached data found")
            return emptyList()
        }

        return try {
            val type = object : TypeToken<List<com.mindenit.schedule.network.EventDto>>() {}.type
            val events = gson.fromJson<List<com.mindenit.schedule.network.EventDto>>(json, type) ?: emptyList()
            Log.d(TAG, "Loaded ${events.size} events from cache")
            events
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to parse cached data", e)
            emptyList()
        }
    }

    fun getEventsForDate(context: Context, date: LocalDate): List<Event> {
        val ym = YearMonth.from(date)
        val events = loadCachedDtos(context, ym)
            .map { it.toDomain() }
            .filter { !it.start.toLocalDate().isAfter(date) && !it.end.toLocalDate().isBefore(date) }
            .sortedBy { it.start }

        Log.d(TAG, "getEventsForDate: date=$date, found ${events.size} events")
        return events
    }

    fun getEventsForWeek(context: Context, startOfWeek: LocalDate): List<Event> {
        val end = startOfWeek.plusDays(6)
        val ymStart = YearMonth.from(startOfWeek)
        val ymEnd = YearMonth.from(end)
        val dtos = if (ymStart == ymEnd) {
            loadCachedDtos(context, ymStart)
        } else {
            loadCachedDtos(context, ymStart) + loadCachedDtos(context, ymEnd)
        }
        val events = dtos
            .map { it.toDomain() }
            .filter { !(it.end.toLocalDate().isBefore(startOfWeek) || it.start.toLocalDate().isAfter(end)) }
            .sortedBy { it.start }

        Log.d(TAG, "getEventsForWeek: startOfWeek=$startOfWeek, found ${events.size} events")
        return events
    }

    fun getCountForDate(context: Context, date: LocalDate): Int {
        val count = getEventsForDate(context, date).size
        Log.d(TAG, "getCountForDate: date=$date, count=$count")
        return count
    }

    fun clearAll(context: Context) {
        prefs(context).edit { clear() }
    }

    fun getSubjectNamesFromCache(context: Context): Map<Long, String> {
        val map = mutableMapOf<Long, String>()
        val all = prefs(context).all
        val gson = gson
        all.forEach { (k, v) ->
            if (k.startsWith(KEY_DATA) && v is String) {
                try {
                    val type = object : com.google.gson.reflect.TypeToken<List<com.mindenit.schedule.network.EventDto>>() {}.type
                    val events: List<com.mindenit.schedule.network.EventDto> = gson.fromJson(v, type) ?: emptyList()
                    for (e in events) {
                        val id = e.subject.id
                        if (!map.containsKey(id)) map[id] = e.subject.title
                    }
                } catch (_: Throwable) { }
            }
        }
        return map
    }
}

private fun com.mindenit.schedule.network.EventDto.toDomain(): Event {
    return Event(
        id = id,
        start = startedAt.toLocalDateTime(),
        end = endedAt.toLocalDateTime(),
        type = type,
        auditorium = Event.Auditorium(auditorium.id.toLong(), auditorium.name),
        numberPair = numberPair,
        subject = Event.Subject(subject.id, subject.title, subject.brief),
        groups = groups.map { Event.Group(it.id, it.name) },
        teachers = teachers.map { Event.Teacher(it.id, it.fullName, it.shortName) }
    )
}

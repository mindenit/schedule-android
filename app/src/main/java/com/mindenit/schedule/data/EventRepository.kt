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
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object EventRepository {
    private const val PREFS = "events_cache"
    private const val KEY_DATA = "data_" // + type_id_yyyy-MM
    private const val KEY_DAY = "day_"   // + type_id_yyyy-MM
    private const val TAG = "EventRepository"

    private val gson = Gson()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun cacheKey(type: ScheduleType, id: Long): String = "${type}_${id}"
    private fun monthKey(type: ScheduleType, id: Long, ym: YearMonth): String = cacheKey(type, id) + "_" + ym.toString()

    // In-memory month cache to avoid repeated JSON parsing and filtering
    private data class MonthCache(
        val ym: YearMonth,
        val all: List<Event>,
        val byDate: Map<LocalDate, List<Event>>
    )

    private val memoryCache = ConcurrentHashMap<String, MonthCache>()
    private val monthLocks = ConcurrentHashMap<String, Mutex>()

    fun invalidateMemory() {
        memoryCache.clear()
        monthLocks.clear()
    }

    suspend fun ensureMonthCached(context: Context, ym: YearMonth) {
        val storage = SchedulesStorage(context)
        val active = storage.getActive()

        Log.d(TAG, "ensureMonthCached: ym=$ym, active=$active")

        if (active == null) {
            Log.w(TAG, "No active schedule found")
            return
        }

        val mKey = monthKey(active.first, active.second, ym)

        val today = LocalDate.now()
        val lastDay = prefs(context).getString(KEY_DAY + mKey, null)

        Log.d(TAG, "Cache check: monthKey=$mKey, lastDay=$lastDay, today=$today")

        if (lastDay == today.toString()) {
            Log.d(TAG, "Data already cached for today")
            // Still ensure in-memory cache is warm (lazy if missing)
            prewarmMonthIndex(context, ym)
            return
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

            val json = gson.toJson(dto)
            prefs(context).edit {
                putString(KEY_DATA + mKey, json)
                putString(KEY_DAY + mKey, today.toString())
            }

            // Build and populate memory cache immediately to avoid UI hiccups later
            try {
                storeMonthInMemory(mKey, ym, dto.map { it.toDomain() })
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to prebuild memory cache for month=$ym", e)
                // Memory cache is optional; fall back to lazy build on demand
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

        val mKey = monthKey(active.first, active.second, ym)
        val json = prefs(context).getString(KEY_DATA + mKey, null)

        Log.d(TAG, "Loading cached data: monthKey=$mKey, hasData=${json != null}")

        if (json == null) {
            Log.w(TAG, "No cached data found")
            return emptyList()
        }

        return try {
            val type = object : TypeToken<List<com.mindenit.schedule.network.EventDto>>() {}.type
            gson.fromJson<List<com.mindenit.schedule.network.EventDto>>(json, type) ?: emptyList()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to parse cached data", e)
            emptyList()
        }
    }

    private suspend fun getOrBuildMonthCache(context: Context, ym: YearMonth): MonthCache? {
        val storage = SchedulesStorage(context)
        val active = storage.getActive() ?: return null
        val mKey = monthKey(active.first, active.second, ym)

        memoryCache[mKey]?.let { return it }

        val lock = monthLocks.getOrPut(mKey) { Mutex() }
        return lock.withLock {
            memoryCache[mKey]?.let { return@withLock it }

            val dtos = withContext(Dispatchers.IO) { loadCachedDtos(context, ym) }
            if (dtos.isEmpty()) return@withLock null

            val domain = dtos.map { it.toDomain() }
            val byDate = buildIndexByDate(domain, ym)
            val cache = MonthCache(ym, domain, byDate)
            memoryCache[mKey] = cache
            cache
        }
    }

    private fun storeMonthInMemory(mKey: String, ym: YearMonth, domain: List<Event>) {
        val byDate = buildIndexByDate(domain, ym)
        memoryCache[mKey] = MonthCache(ym, domain, byDate)
    }

    private fun buildIndexByDate(events: List<Event>, ym: YearMonth): Map<LocalDate, List<Event>> {
        if (events.isEmpty()) return emptyMap()
        val first = ym.atDay(1)
        val last = ym.atEndOfMonth()
        val map = HashMap<LocalDate, MutableList<Event>>()
        for (e in events) {
            // Clamp to month window; include multi-day overlaps
            var d = e.start.toLocalDate()
            val endD = e.end.toLocalDate()
            if (endD.isBefore(first) || d.isAfter(last)) continue
            if (d.isBefore(first)) d = first
            val lastIncl = if (endD.isAfter(last)) last else endD
            var cur = d
            while (!cur.isAfter(lastIncl)) {
                map.getOrPut(cur) { mutableListOf() }.add(e)
                cur = cur.plusDays(1)
            }
        }
        // Sort each day's events by start time for stable UI
        return map.mapValues { (_, list) -> list.sortedBy { it.start } }
    }

    suspend fun prewarmMonthIndex(context: Context, ym: YearMonth) {
        try { getOrBuildMonthCache(context, ym) } catch (_: Throwable) {}
    }

    // Fast, non-blocking accessors that rely only on in-memory cache.
    // Return empty list if cache is not yet built.
    fun getEventsForDateFast(context: Context, date: LocalDate): List<Event> {
        val storage = SchedulesStorage(context)
        val active = storage.getActive() ?: return emptyList()
        val ym = YearMonth.from(date)
        val mKey = monthKey(active.first, active.second, ym)
        val list = memoryCache[mKey]?.byDate?.get(date).orEmpty()
        return list
    }

    fun getEventsForWeekFast(context: Context, startOfWeek: LocalDate): List<Event> {
        val storage = SchedulesStorage(context)
        val active = storage.getActive() ?: return emptyList()
        val end = startOfWeek.plusDays(6)
        val ymStart = YearMonth.from(startOfWeek)
        val ymEnd = YearMonth.from(end)
        val keyStart = monthKey(active.first, active.second, ymStart)
        val keyEnd = monthKey(active.first, active.second, ymEnd)
        val all = when {
            ymStart == ymEnd -> memoryCache[keyStart]?.all.orEmpty()
            else -> memoryCache[keyStart]?.all.orEmpty() + memoryCache[keyEnd]?.all.orEmpty()
        }
        if (all.isEmpty()) return emptyList()
        return all.asSequence()
            .filter { !(it.end.toLocalDate().isBefore(startOfWeek) || it.start.toLocalDate().isAfter(end)) }
            .sortedBy { it.start }
            .toList()
    }

    fun getEventsForDate(context: Context, date: LocalDate): List<Event> {
        val ym = YearMonth.from(date)
        return try {
            val cache = kotlinx.coroutines.runBlocking { getOrBuildMonthCache(context, ym) }
            val list = cache?.byDate?.get(date).orEmpty()
            Log.d(TAG, "getEventsForDate: date=$date, found ${list.size} events")
            list
        } catch (e: Throwable) {
            Log.w(TAG, "Fallback path for getEventsForDate due to ${e.message}")
            // Fallback to previous implementation if something goes wrong
            val events = loadCachedDtos(context, ym)
                .map { it.toDomain() }
                .filter { !it.start.toLocalDate().isAfter(date) && !it.end.toLocalDate().isBefore(date) }
                .sortedBy { it.start }
            Log.d(TAG, "getEventsForDate[fallback]: date=$date, found ${events.size} events")
            events
        }
    }

    fun getEventsForWeek(context: Context, startOfWeek: LocalDate): List<Event> {
        val end = startOfWeek.plusDays(6)
        val ymStart = YearMonth.from(startOfWeek)
        val ymEnd = YearMonth.from(end)
        return try {
            val cacheStart = kotlinx.coroutines.runBlocking { getOrBuildMonthCache(context, ymStart) }
            val cacheEnd = if (ymEnd == ymStart) cacheStart else kotlinx.coroutines.runBlocking { getOrBuildMonthCache(context, ymEnd) }
            val all = when {
                cacheStart == null && cacheEnd == null -> emptyList()
                cacheStart != null && cacheEnd == null -> cacheStart.all
                cacheStart == null && cacheEnd != null -> cacheEnd.all
                else -> cacheStart!!.all + cacheEnd!!.all
            }
            val events = all
                .asSequence()
                .filter { !(it.end.toLocalDate().isBefore(startOfWeek) || it.start.toLocalDate().isAfter(end)) }
                .sortedBy { it.start }
                .toList()
            Log.d(TAG, "getEventsForWeek: startOfWeek=$startOfWeek, found ${events.size} events")
            events
        } catch (e: Throwable) {
            Log.w(TAG, "Fallback path for getEventsForWeek due to ${e.message}")
            val dtos = if (ymStart == ymEnd) {
                loadCachedDtos(context, ymStart)
            } else {
                loadCachedDtos(context, ymStart) + loadCachedDtos(context, ymEnd)
            }
            val events = dtos
                .map { it.toDomain() }
                .filter { !(it.end.toLocalDate().isBefore(startOfWeek) || it.start.toLocalDate().isAfter(end)) }
                .sortedBy { it.start }
            Log.d(TAG, "getEventsForWeek[fallback]: startOfWeek=$startOfWeek, found ${events.size} events")
            return events
        }
    }

    fun getCountForDate(context: Context, date: LocalDate): Int {
        val count = getEventsForDate(context, date).size
        Log.d(TAG, "getCountForDate: date=$date, count=$count")
        return count
    }

    fun clearAll(context: Context) {
        prefs(context).edit { clear() }
        invalidateMemory()
    }

    fun getSubjectNamesFromCache(context: Context): Map<Long, String> {
        // Try to reuse in-memory months first to avoid JSON parsing of all months
        val names = HashMap<Long, String>()
        memoryCache.values.forEach { cache ->
            cache.all.forEach { e -> if (!names.containsKey(e.subject.id)) names[e.subject.id] = e.subject.title }
        }
        if (names.isNotEmpty()) return names

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

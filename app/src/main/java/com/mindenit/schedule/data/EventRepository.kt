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
import com.mindenit.schedule.network.Net

object EventRepository {
    private const val PREFS = "events_cache"
    private const val KEY_DATA = "data_" // + type_id_yyyy-MM
    private const val KEY_DAY = "day_"   // + type_id_yyyy-MM
    private const val TAG = "EventRepository"

    private val gson = Gson()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun cacheKey(type: ScheduleType, id: Long): String = "${'$'}{type}_${'$'}{id}"
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
            LogStorage.i(context, "Update", "$ym: пропущено — немає активного розкладу")
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

        if (!Net.hasInternet(context)) {
            Log.d(TAG, "Skip fetch: no internet")
            LogStorage.i(context, "Update", "${ym}: пропущено — немає інтернету")
            return
        }

        val start = ym.atDay(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        val end = ym.atEndOfMonth().plusDays(1).atStartOfDay(ZoneId.systemDefault()).minusNanos(1).toEpochSecond()

        Log.d(TAG, "Fetching data from API: type=${'$'}{active.first}, id=${'$'}{active.second}, start=$start, end=$end")
        LogStorage.i(context, "Update", "${ym}: початок оновлення (type=${active.first}, id=${active.second})")

        try {
            val dto: List<EventDto> = withContext(Dispatchers.IO) {
                when (active.first) {
                    ScheduleType.GROUP -> com.mindenit.schedule.network.ApiClient.api.getGroupSchedule(active.second, start, end).data
                    ScheduleType.TEACHER -> com.mindenit.schedule.network.ApiClient.api.getTeacherSchedule(active.second, start, end).data
                    ScheduleType.AUDITORIUM -> com.mindenit.schedule.network.ApiClient.api.getAuditoriumSchedule(active.second, start, end).data
                }
            }

            Log.d(TAG, "API response: ${'$'}{dto.size} events received")

            val oldDtos = withContext(Dispatchers.IO) { loadCachedDtos(context, ym) }
            val diff = computeDiff(oldDtos, dto)

            if (diff.hasChanges) {
                LogStorage.i(context, "Update", "${ym}: знайдено зміни: ${diff.summary}")
                // Log details (limit to avoid spam)
                val limit = 50
                diff.details.take(limit).forEach { d ->
                    LogStorage.i(context, "Update", "${ym}: ${d}")
                }
                if (diff.details.size > limit) {
                    LogStorage.i(context, "Update", "${ym}: …та ще ${diff.details.size - limit} змін")
                }
                val json = gson.toJson(dto)
                prefs(context).edit {
                    putString(KEY_DATA + mKey, json)
                    putString(KEY_DAY + mKey, today.toString())
                }
                // Rebuild memory cache for this month immediately
                try {
                    storeMonthInMemory(mKey, ym, dto.map { it.toDomain() })
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to prebuild memory cache for month=$ym", e)
                }
                Log.d(TAG, "Data cached successfully with changes")
            } else {
                LogStorage.i(context, "Update", "${ym}: змін немає (${dto.size} подій), кеш лишився без змін")
                // Still stamp the day to avoid repetitive fetches the same day
                prefs(context).edit { putString(KEY_DAY + mKey, today.toString()) }
                // Keep memory cache as is; optionally warm it if empty
                prewarmMonthIndex(context, ym)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to fetch data from API", e)
            LogStorage.i(context, "Update", "${ym}: помилка оновлення — ${e.javaClass.simpleName}: ${e.message}")
            // keep old cache; do not update stamp so we'll retry next time
        }
    }

    private data class DiffResult(
        val hasChanges: Boolean,
        val added: Int,
        val removed: Int,
        val changed: Int,
        val details: List<String>
    ) {
        val summary: String
            get() {
                val parts = mutableListOf<String>()
                if (added > 0) parts.add("додано $added")
                if (removed > 0) parts.add("видалено $removed")
                if (changed > 0) parts.add("змінено $changed")
                return if (parts.isEmpty()) "без змін" else parts.joinToString(", ")
            }
    }

    private fun computeDiff(old: List<EventDto>, cur: List<EventDto>): DiffResult {
        if (old.isEmpty() && cur.isEmpty()) return DiffResult(false, 0, 0, 0, emptyList())
        val oldById = old.associateBy { it.id }
        val curById = cur.associateBy { it.id }
        val addedIds = curById.keys - oldById.keys
        val removedIds = oldById.keys - curById.keys
        var changed = 0
        val detail = mutableListOf<String>()
        val shared = oldById.keys.intersect(curById.keys)
        for (id in shared) {
            val a = oldById[id]!!
            val b = curById[id]!!
            if (!dtoEquals(a, b)) {
                changed++
                detail.add("#$id: ${a.subject.title} → зміни у події")
            }
        }
        val has = addedIds.isNotEmpty() || removedIds.isNotEmpty() || changed > 0
        if (addedIds.isNotEmpty()) detail.add("додано: ${addedIds.size}")
        if (removedIds.isNotEmpty()) detail.add("видалено: ${removedIds.size}")
        return DiffResult(has, addedIds.size, removedIds.size, changed, detail)
    }

    private fun dtoEquals(a: EventDto, b: EventDto): Boolean {
        if (a.id != b.id) return false
        return a.startedAt == b.startedAt &&
            a.endedAt == b.endedAt &&
            a.type == b.type &&
            a.auditorium.id == b.auditorium.id &&
            a.auditorium.name == b.auditorium.name &&
            a.numberPair == b.numberPair &&
            a.subject.id == b.subject.id &&
            a.subject.title == b.subject.title &&
            a.subject.brief == b.subject.brief &&
            a.groups.map { it.id } == b.groups.map { it.id } &&
            a.teachers.map { it.id } == b.teachers.map { it.id }
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

        Log.d(TAG, "Loading cached data: monthKey=$mKey, hasData=${'$'}{json != null}")

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

    // Stable comparator for events across all views
    private val eventComparator = compareBy<Event>(
        { it.numberPair }, // primary: declared pair number
        { it.start },
        { it.subject.brief.ifBlank { it.subject.title } },
        { it.id }
    )

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
        // Sort each day's events with the stable comparator for consistent UI ordering
        return map.mapValues { (_, list) -> list.sortedWith(eventComparator) }
    }

    suspend fun prewarmMonthIndex(context: Context, ym: YearMonth) {
        try { getOrBuildMonthCache(context, ym) } catch (_: Throwable) {}
    }

    // Local filter predicate using stored filters
    private fun applyLocalFilters(context: Context, events: List<Event>): List<Event> {
        val storage = SchedulesStorage(context)
        val active = storage.getActive()
        val isGroup = active?.first == ScheduleType.GROUP
        val filters = if (isGroup) FiltersStorage(context).get(active!!.first, active.second) else FiltersStorage.Filters()

        // Always exclude hidden subjects from calendar views
        val baseSeq = events.asSequence().filter { it.subject.id !in HiddenSubjectsStorage(context).getAll() }

        if (!isGroup) return baseSeq.toList()

        var seq = baseSeq
        if (filters.lessonTypes.isNotEmpty()) seq = seq.filter { it.type in filters.lessonTypes }
        if (filters.teachers.isNotEmpty()) seq = seq.filter { e -> e.teachers.any { it.id in filters.teachers } }
        if (filters.auditoriums.isNotEmpty()) seq = seq.filter { e -> e.auditorium.id in filters.auditoriums }
        if (filters.subjects.isNotEmpty()) seq = seq.filter { e -> e.subject.id in filters.subjects }
        return seq.toList()
    }

    // Fast, non-blocking accessors that rely only on in-memory cache.
    // Return empty list if cache is not yet built.
    fun getEventsForDateFast(context: Context, date: LocalDate): List<Event> {
        val storage = SchedulesStorage(context)
        val active = storage.getActive() ?: return emptyList()
        val ym = YearMonth.from(date)
        val mKey = monthKey(active.first, active.second, ym)
        val list = memoryCache[mKey]?.byDate?.get(date).orEmpty()
        return applyLocalFilters(context, list)
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
        val filtered = all.asSequence()
            .filter { !(it.end.toLocalDate().isBefore(startOfWeek) || it.start.toLocalDate().isAfter(end)) }
            .sortedWith(eventComparator)
            .toList()
        return applyLocalFilters(context, filtered)
    }

    fun getEventsForDate(context: Context, date: LocalDate): List<Event> {
        val ym = YearMonth.from(date)
        return try {
            val cache = kotlinx.coroutines.runBlocking { getOrBuildMonthCache(context, ym) }
            val list = cache?.byDate?.get(date).orEmpty()
            Log.d(TAG, "getEventsForDate: date=$date, found ${'$'}{list.size} events (before local filters)")
            applyLocalFilters(context, list)
        } catch (e: Throwable) {
            Log.w(TAG, "Fallback path for getEventsForDate due to ${'$'}{e.message}")
            val events = loadCachedDtos(context, ym)
                .map { it.toDomain() }
                .filter { !it.start.toLocalDate().isAfter(date) && !it.end.toLocalDate().isBefore(date) }
                .sortedWith(eventComparator)
            applyLocalFilters(context, events)
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
                .sortedWith(eventComparator)
                .toList()
            Log.d(TAG, "getEventsForWeek: startOfWeek=$startOfWeek, found ${'$'}{events.size} events (before local filters)")
            applyLocalFilters(context, events)
        } catch (e: Throwable) {
            Log.w(TAG, "Fallback path for getEventsForWeek due to ${'$'}{e.message}")
            val dtos = if (ymStart == ymEnd) {
                loadCachedDtos(context, ymStart)
            } else {
                loadCachedDtos(context, ymStart) + loadCachedDtos(context, ymEnd)
            }
            val events = dtos
                .map { it.toDomain() }
                .filter { !(it.end.toLocalDate().isBefore(startOfWeek) || it.start.toLocalDate().isAfter(end)) }
                .sortedWith(eventComparator)
            return applyLocalFilters(context, events)
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

    /**
     * Clear cache when active schedule changes to force fresh data load
     */
    fun clearCacheForScheduleChange(context: Context) {
        Log.d(TAG, "clearCacheForScheduleChange: clearing all caches due to schedule change")
        invalidateMemory()
        // Clear SharedPreferences cache for the current active schedule
        val storage = SchedulesStorage(context)
        val active = storage.getActive()
        if (active != null) {
            val keyPrefix = KEY_DATA + cacheKey(active.first, active.second)
            val dayPrefix = KEY_DAY + cacheKey(active.first, active.second)
            prefs(context).edit {
                // Remove all cached data for this schedule
                val all = prefs(context).all
                all.keys.filter { it.startsWith(keyPrefix) || it.startsWith(dayPrefix) }.forEach { key ->
                    remove(key)
                }
            }
        }
    }

    fun clearStampForMonth(context: Context, ym: YearMonth) {
        val storage = SchedulesStorage(context)
        val active = storage.getActive() ?: return
        val mKey = monthKey(active.first, active.second, ym)
        prefs(context).edit { remove(KEY_DAY + mKey) }
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

    fun getCachedTeachersForActive(context: Context): Map<Long, Pair<String, String>> {
        val storage = SchedulesStorage(context)
        val active = storage.getActive() ?: return emptyMap()
        val prefix = KEY_DATA + cacheKey(active.first, active.second) + "_"
        val out = LinkedHashMap<Long, Pair<String, String>>()
        prefs(context).all.forEach { (k, v) ->
            if (k.startsWith(prefix) && v is String) {
                try {
                    val type = object : com.google.gson.reflect.TypeToken<List<com.mindenit.schedule.network.EventDto>>() {}.type
                    val events: List<com.mindenit.schedule.network.EventDto> = gson.fromJson(v, type) ?: emptyList()
                    for (e in events) {
                        e.teachers.forEach { t ->
                            if (!out.containsKey(t.id)) out[t.id] = t.fullName to t.shortName
                        }
                    }
                } catch (_: Throwable) { }
            }
        }
        return out
    }

    fun getCachedAuditoriumsForActive(context: Context): Map<Long, String> {
        val storage = SchedulesStorage(context)
        val active = storage.getActive() ?: return emptyMap()
        val prefix = KEY_DATA + cacheKey(active.first, active.second) + "_"
        val out = LinkedHashMap<Long, String>()
        prefs(context).all.forEach { (k, v) ->
            if (k.startsWith(prefix) && v is String) {
                try {
                    val type = object : com.google.gson.reflect.TypeToken<List<com.mindenit.schedule.network.EventDto>>() {}.type
                    val events: List<com.mindenit.schedule.network.EventDto> = gson.fromJson(v, type) ?: emptyList()
                    for (e in events) {
                        val aId = e.auditorium.id.toLong()
                        if (!out.containsKey(aId)) out[aId] = e.auditorium.name
                    }
                } catch (_: Throwable) { }
            }
        }
        return out
    }

    fun getCachedSubjectsForActive(context: Context): Map<Long, Pair<String, String>> {
        val storage = SchedulesStorage(context)
        val active = storage.getActive() ?: return emptyMap()
        val prefix = KEY_DATA + cacheKey(active.first, active.second) + "_"
        val out = LinkedHashMap<Long, Pair<String, String>>()
        prefs(context).all.forEach { (k, v) ->
            if (k.startsWith(prefix) && v is String) {
                try {
                    val type = object : com.google.gson.reflect.TypeToken<List<com.mindenit.schedule.network.EventDto>>() {}.type
                    val events: List<com.mindenit.schedule.network.EventDto> = gson.fromJson(v, type) ?: emptyList()
                    for (e in events) {
                        val s = e.subject
                        if (!out.containsKey(s.id)) out[s.id] = s.title to s.brief
                    }
                } catch (_: Throwable) { }
            }
        }
        return out
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

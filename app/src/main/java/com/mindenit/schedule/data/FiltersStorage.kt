package com.mindenit.schedule.data

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class FiltersStorage(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    data class Filters(
        val lessonTypes: Set<String> = emptySet(),
        val teachers: Set<Long> = emptySet(),
        val auditoriums: Set<Long> = emptySet(),
        val subjects: Set<Long> = emptySet()
    )

    fun get(type: ScheduleType, id: Long): Filters {
        val json = prefs.getString(key(type, id), null) ?: return Filters()
        return try {
            gson.fromJson(json, Filters::class.java) ?: Filters()
        } catch (_: Throwable) { Filters() }
    }

    fun set(type: ScheduleType, id: Long, filters: Filters) {
        prefs.edit { putString(key(type, id), gson.toJson(filters)) }
    }

    fun clear(type: ScheduleType, id: Long) {
        prefs.edit { remove(key(type, id)) }
    }

    fun clearAll() { prefs.edit { clear() } }

    private fun key(type: ScheduleType, id: Long) = "filters_${type}_${id}"

    companion object {
        private const val PREFS = "filters_storage"
        fun clearAll(context: Context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { clear() } }
    }
}


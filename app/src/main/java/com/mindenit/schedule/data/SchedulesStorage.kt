package com.mindenit.schedule.data

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SchedulesStorage(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getAll(): List<ScheduleEntry> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ScheduleEntry>>() {}.type
            gson.fromJson<List<ScheduleEntry>>(json, type) ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun add(entry: ScheduleEntry) {
        val list = getAll().toMutableList()
        val idx = list.indexOfFirst { it.type == entry.type && it.id == entry.id }
        if (idx >= 0) list[idx] = entry else list.add(entry)
        save(list)
    }

    fun remove(entry: ScheduleEntry) {
        val list = getAll().filterNot { it.type == entry.type && it.id == entry.id }
        save(list)
        // If the removed entry was active, clear the active selection
        val currentActive = getActive()
        if (currentActive?.first == entry.type && currentActive.second == entry.id) {
            clearActive()
        }
    }

    fun clear() {
        prefs.edit { remove(KEY) }
    }

    private fun save(list: List<ScheduleEntry>) {
        val json = gson.toJson(list)
        prefs.edit { putString(KEY, json) }
    }

    fun setActive(type: ScheduleType, id: Long) {
        prefs.edit {
            putString(KEY_ACTIVE_TYPE, type.toString())
            putLong(KEY_ACTIVE_ID, id)
        }
    }

    fun getActive(): Pair<ScheduleType, Long>? {
        val t = prefs.getString(KEY_ACTIVE_TYPE, null) ?: return null
        val id = prefs.getLong(KEY_ACTIVE_ID, -1L).takeIf { it >= 0 } ?: return null
        return ScheduleType.from(t) to id
    }

    fun clearActive() {
        prefs.edit {
            remove(KEY_ACTIVE_TYPE)
            remove(KEY_ACTIVE_ID)
        }
    }

    companion object {
        private const val PREFS = "schedules_storage"
        private const val KEY = "entries"
        private const val KEY_ACTIVE_TYPE = "active_type"
        private const val KEY_ACTIVE_ID = "active_id"
    }
}

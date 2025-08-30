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
    }

    private fun save(list: List<ScheduleEntry>) {
        val json = gson.toJson(list)
        prefs.edit { putString(KEY, json) }
    }

    companion object {
        private const val PREFS = "schedules_storage"
        private const val KEY = "entries"
    }
}


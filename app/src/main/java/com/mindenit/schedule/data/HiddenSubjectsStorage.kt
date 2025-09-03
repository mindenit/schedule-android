package com.mindenit.schedule.data

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class HiddenSubjectsStorage(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getAll(): Set<Long> {
        val json = prefs.getString(KEY_IDS, null) ?: return emptySet()
        return try {
            val type = object : TypeToken<Set<Long>>() {}.type
            gson.fromJson<Set<Long>>(json, type) ?: emptySet()
        } catch (_: Throwable) { emptySet() }
    }

    fun isHidden(id: Long): Boolean = getAll().contains(id)

    fun add(id: Long) {
        val set = getAll().toMutableSet()
        if (set.add(id)) save(set)
    }

    fun remove(id: Long) {
        val set = getAll().toMutableSet()
        if (set.remove(id)) save(set)
    }

    fun clear() { prefs.edit { remove(KEY_IDS) } }

    private fun save(set: Set<Long>) {
        prefs.edit { putString(KEY_IDS, gson.toJson(set)) }
    }

    companion object {
        private const val PREFS = "hidden_subjects"
        private const val KEY_IDS = "ids"
        fun clearAll(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { clear() }
        }
    }
}


package com.mindenit.schedule.data

import android.content.Context
import android.text.format.DateFormat
import androidx.core.content.edit
import java.util.Date

class LogStorage(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()

    fun log(tag: String, message: String) {
        val ts = DateFormat.format("yyyy-MM-dd HH:mm:ss", Date()).toString()
        val line = "[$ts][$tag] $message"
        synchronized(lock) {
            // Migrate if needed
            migrateIfNeeded()
            val cur = prefs.getString(KEY_TEXT, "") ?: ""
            val lines = if (cur.isEmpty()) emptyList() else cur.split('\n')
            val newLines = (lines + line).takeLast(MAX_LINES)
            prefs.edit { putString(KEY_TEXT, newLines.joinToString("\n")) }
        }
    }

    fun getAll(): List<String> {
        migrateIfNeeded()
        val cur = prefs.getString(KEY_TEXT, "") ?: ""
        if (cur.isEmpty()) return emptyList()
        return cur.split('\n')
    }

    fun getText(): String = getAll().joinToString("\n")

    fun clear() { prefs.edit { remove(KEY_TEXT); remove(KEY_LINES) } }

    private fun migrateIfNeeded() {
        if (!prefs.contains(KEY_TEXT) && prefs.contains(KEY_LINES)) {
            val set = prefs.getStringSet(KEY_LINES, emptySet()) ?: emptySet()
            // Best-effort: keep as-is order (sets are unordered) by sorting by timestamp prefix if present
            val migrated = set.sorted()
            prefs.edit {
                putString(KEY_TEXT, migrated.joinToString("\n"))
                remove(KEY_LINES)
            }
        }
    }

    companion object {
        private const val PREFS = "logs_storage"
        private const val KEY_TEXT = "text"
        // Legacy key for migration
        private const val KEY_LINES = "lines"
        private const val MAX_LINES = 2000
        fun i(ctx: Context, tag: String, message: String) = LogStorage(ctx).log(tag, message)
    }
}

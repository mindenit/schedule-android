package com.mindenit.schedule.data

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * Simple SharedPreferences-based storage for per-subject links.
 */
class SubjectLinksStorage(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    data class SubjectLink(
        val id: String = UUID.randomUUID().toString(),
        val subjectId: Long,
        val title: String = "",
        val url: String,
        val isPrimaryVideo: Boolean = false
    ) {
        fun isVideoConference(): Boolean {
            val u = url.lowercase()
            return u.contains("meet.google") || u.contains("zoom")
        }
    }

    fun getLinks(subjectId: Long): List<SubjectLink> {
        val json = prefs.getString(key(subjectId), null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SubjectLink>>() {}.type
            gson.fromJson<List<SubjectLink>>(json, type) ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun upsert(link: SubjectLink) {
        val list = getLinks(link.subjectId).toMutableList()
        // Coerce primary flag off for non-video links
        val normalized = if (link.isVideoConference()) link else link.copy(isPrimaryVideo = false)
        val idx = list.indexOfFirst { it.id == normalized.id }
        if (idx >= 0) list[idx] = normalized else list.add(normalized)
        // Ensure only one primary among video-conference links
        if (normalized.isPrimaryVideo && normalized.isVideoConference()) {
            for (i in list.indices) {
                if (list[i].id != normalized.id && list[i].isVideoConference()) {
                    list[i] = list[i].copy(isPrimaryVideo = false)
                }
            }
        }
        save(normalized.subjectId, list)
    }

    fun delete(subjectId: Long, linkId: String) {
        val list = getLinks(subjectId).filterNot { it.id == linkId }
        save(subjectId, list)
    }

    fun setPrimary(subjectId: Long, linkId: String) {
        val list = getLinks(subjectId).toMutableList()
        var changed = false
        val isVideo = list.find { it.id == linkId }?.isVideoConference() == true
        if (!isVideo) return
        for (i in list.indices) {
            val l = list[i]
            val newPrimary = l.id == linkId
            val updated = if (l.isVideoConference()) l.copy(isPrimaryVideo = newPrimary) else l.copy(isPrimaryVideo = false)
            if (updated != l) {
                list[i] = updated
                changed = true
            }
        }
        if (changed) save(subjectId, list)
    }

    private fun save(subjectId: Long, list: List<SubjectLink>) {
        val json = gson.toJson(list)
        prefs.edit { putString(key(subjectId), json) }
    }

    private fun key(subjectId: Long) = KEY_PREFIX + subjectId

    companion object {
        private const val PREFS = "subject_links"
        private const val KEY_PREFIX = "links_"
    }
}

package com.mindenit.schedule.data

import com.google.gson.annotations.SerializedName

enum class ScheduleType {
    @SerializedName("group") GROUP,
    @SerializedName("teacher") TEACHER,
    @SerializedName("auditorium") AUDITORIUM;

    override fun toString(): String = when (this) {
        GROUP -> "group"
        TEACHER -> "teacher"
        AUDITORIUM -> "auditorium"
    }

    companion object {
        fun from(value: String): ScheduleType = when (value.lowercase()) {
            "group" -> GROUP
            "teacher" -> TEACHER
            "auditorium" -> AUDITORIUM
            else -> GROUP
        }
    }
}

data class ScheduleEntry(
    val type: ScheduleType,
    val id: Long,
    val name: String,
    val payload: String? = null
) {
    companion object {
        fun from(type: String, id: Long, name: String, payload: String? = null): ScheduleEntry =
            ScheduleEntry(ScheduleType.from(type), id, name, payload)
    }
}

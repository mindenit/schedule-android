package com.mindenit.schedule.network

data class ApiResponse<T>(
    val success: Boolean,
    val data: T,
    val message: String,
    val error: Any? = null
)

data class EventDto(
    val id: Long,
    val startedAt: Double,
    val endedAt: Double,
    val type: String,
    val auditorium: AuditoriumDto,
    val numberPair: Int,
    val subject: SubjectDto,
    val groups: List<GroupDto>,
    val teachers: List<TeacherDto>
)

data class AuditoriumDto(val id: Double, val name: String)

data class SubjectDto(val id: Long, val title: String, val brief: String)

// For subjects list endpoint: uses 'name' and 'brief'
data class SubjectListItemDto(val id: Long, val brief: String, val name: String)

data class GroupDto(val id: Long, val name: String)

data class TeacherDto(val id: Long, val fullName: String, val shortName: String)

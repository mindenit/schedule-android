package com.mindenit.schedule.data

import retrofit2.http.GET

// Generic envelope used by most endpoints
data class ApiResponse<T>(
    val success: Boolean,
    val data: T,
    val message: String?,
    val error: Any?
)

// Models
data class Group(
    val id: Long,
    val name: String,
    val directionId: Long?,
    val specialityId: Long?
)

data class Teacher(
    val id: Long,
    val fullName: String,
    val shortName: String,
    val departmentId: Long?
)

data class Auditorium(
    val id: Long,
    val name: String,
    val floor: Int,
    val hasPower: Boolean,
    val buildingId: String
)

// Health check model
data class Health(
    val uptime: Double,
    val message: String,
    val date: String
)

interface ApiService {
    @GET("api/health")
    suspend fun health(): Health

    @GET("api/groups")
    suspend fun getGroups(): ApiResponse<List<Group>>

    @GET("api/teachers")
    suspend fun getTeachers(): ApiResponse<List<Teacher>>

    @GET("api/auditoriums")
    suspend fun getAuditoriums(): ApiResponse<List<Auditorium>>
}

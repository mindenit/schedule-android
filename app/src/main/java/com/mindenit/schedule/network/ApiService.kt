package com.mindenit.schedule.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("api/health")
    suspend fun health(): Response<Unit>

    @GET("api/groups/{id}/schedule")
    suspend fun getGroupSchedule(
        @Path("id") id: Long,
        @Query("startedAt") startedAt: Long?,
        @Query("endedAt") endedAt: Long?,
        @Query("filters.lessonTypes") lessonTypes: String? = null,
        @Query("filters.teachers") teachers: String? = null,
        @Query("filters.auditoriums") auditoriums: String? = null,
        @Query("filters.subjects") subjects: String? = null
    ): com.mindenit.schedule.network.ApiResponse<List<com.mindenit.schedule.network.EventDto>>

    @GET("api/teachers/{id}/schedule")
    suspend fun getTeacherSchedule(
        @Path("id") id: Long,
        @Query("startedAt") startedAt: Long?,
        @Query("endedAt") endedAt: Long?,
        @Query("filters.lessonTypes") lessonTypes: String? = null,
        @Query("filters.groups") groups: String? = null,
        @Query("filters.auditoriums") auditoriums: String? = null,
        @Query("filters.subjects") subjects: String? = null
    ): com.mindenit.schedule.network.ApiResponse<List<com.mindenit.schedule.network.EventDto>>

    @GET("api/auditoriums/{id}/schedule")
    suspend fun getAuditoriumSchedule(
        @Path("id") id: Long,
        @Query("startedAt") startedAt: Long?,
        @Query("endedAt") endedAt: Long?,
        @Query("filters.lessonTypes") lessonTypes: String? = null,
        @Query("filters.teachers") teachers: String? = null,
        @Query("filters.groups") groups: String? = null,
        @Query("filters.subjects") subjects: String? = null
    ): com.mindenit.schedule.network.ApiResponse<List<com.mindenit.schedule.network.EventDto>>
}

package com.mindenit.schedule.data

import android.util.Log

class Repository(private val api: ApiService = ApiClient.service) {
    private val tag = "Repository"

    suspend fun fetchGroups(): List<Group> {
        Log.d(tag, "Requesting groups...")
        val resp = api.getGroups()
        Log.d(tag, "Groups response: success=${resp.success}, count=${resp.data.size}, message=${resp.message}")
        return resp.data
    }

    suspend fun fetchTeachers(): List<Teacher> {
        Log.d(tag, "Requesting teachers...")
        val resp = api.getTeachers()
        Log.d(tag, "Teachers response: success=${resp.success}, count=${resp.data.size}, message=${resp.message}")
        return resp.data
    }

    suspend fun fetchAuditoriums(): List<Auditorium> {
        Log.d(tag, "Requesting auditoriums...")
        val resp = api.getAuditoriums()
        Log.d(tag, "Auditoriums response: success=${resp.success}, count=${resp.data.size}, message=${resp.message}")
        return resp.data
    }

    suspend fun health(): Health {
        Log.d(tag, "Requesting health...")
        val resp = api.health()
        Log.d(tag, "Health: uptime=${resp.uptime}, message=${resp.message}, date=${resp.date}")
        return resp
    }
}

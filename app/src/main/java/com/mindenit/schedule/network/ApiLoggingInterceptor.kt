package com.mindenit.schedule.network

import com.mindenit.schedule.AppCtx
import com.mindenit.schedule.data.LogStorage
import okhttp3.Interceptor
import okhttp3.Response

class ApiLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val t0 = System.nanoTime()
        try {
            LogStorage.i(AppCtx.app, "API", "→ ${request.method} ${request.url}")
            val response = chain.proceed(request)
            val t1 = System.nanoTime()
            val ms = (t1 - t0) / 1_000_000
            LogStorage.i(AppCtx.app, "API", "← ${response.code} ${response.message} (${ms}ms) ${request.url}")
            return response
        } catch (e: Throwable) {
            val t1 = System.nanoTime()
            val ms = (t1 - t0) / 1_000_000
            LogStorage.i(AppCtx.app, "API", "✖ ${request.method} ${request.url} failed: ${e.javaClass.simpleName}: ${e.message} (${ms}ms)")
            throw e
        }
    }
}


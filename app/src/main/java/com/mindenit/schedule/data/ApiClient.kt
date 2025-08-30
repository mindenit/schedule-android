package com.mindenit.schedule.data

import android.util.Log
import com.mindenit.schedule.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val TAG = "ApiClient"

    private val httpLogging: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
        }.apply { level = HttpLoggingInterceptor.Level.BODY }
    }

    // Custom interceptor to log a concise start/end of each call
    private val conciseLogger = Interceptor { chain ->
        val request = chain.request()
        val started = System.nanoTime()
        Log.d(TAG, "➡ ${request.method} ${request.url}")
        val response: Response = try {
            chain.proceed(request)
        } catch (t: Throwable) {
            Log.e(TAG, "❌ ${request.method} ${request.url} failed: ${t.message}", t)
            throw t
        }
        val tookMs = (System.nanoTime() - started) / 1_000_000
        Log.d(TAG, "⬅ ${response.code} ${request.method} ${request.url} in ${tookMs}ms")
        response
    }

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(conciseLogger)
            .addInterceptor(httpLogging)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        val baseUrl = BuildConfig.API_BASE_URL
        Log.d(TAG, "Using baseUrl=$baseUrl")
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttp)
            .build()
    }

    val service: ApiService by lazy { retrofit.create(ApiService::class.java) }
}

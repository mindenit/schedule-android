package com.mindenit.schedule

import android.app.Application
import com.mindenit.schedule.data.LogStorage

class ScheduleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCtx.init(this)
        // Log app start for diagnostics
        try { LogStorage.i(this, "App", "Application started") } catch (_: Throwable) {}
    }
}


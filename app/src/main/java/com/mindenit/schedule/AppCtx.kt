package com.mindenit.schedule

import android.content.Context

object AppCtx {
    @Volatile
    lateinit var app: Context
        private set

    fun init(context: Context) {
        app = context.applicationContext
    }
}


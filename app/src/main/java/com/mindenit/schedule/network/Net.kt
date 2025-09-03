package com.mindenit.schedule.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object Net {
    fun hasInternet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        val has = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) else true
        return has && validated
    }
}


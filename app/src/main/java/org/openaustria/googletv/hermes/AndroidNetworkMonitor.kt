package org.openaustria.googletv.hermes

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/** Prüft vor dem Senden, ob das Gerät überhaupt ein Netzwerk hat (braucht ACCESS_NETWORK_STATE). */
class AndroidNetworkMonitor(context: Context) {

    private val connectivity =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isOnline(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        @Suppress("DEPRECATION")
        return connectivity.activeNetworkInfo?.isConnected == true
    }
}

package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Whether the device currently has a network with the internet capability - a one-shot check
 * (not a live listener), used only to decide up front whether to even attempt a network fetch
 * versus going straight to a local cache. This is deliberately not the source of truth for actual
 * reachability - a fetch can still fail (or succeed) despite what this reports - it's just cheap
 * enough to check first and skip a guaranteed-to-fail request/timeout when it says no. */
fun isOnline(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return true
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

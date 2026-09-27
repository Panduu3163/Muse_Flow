package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Whether Android currently has a network it has validated against the public internet. This is
 * still only a snapshot (use [observeOnline] for UI), but checking VALIDATED avoids treating a
 * captive portal or broken Wi-Fi connection as usable streaming connectivity. */
fun isOnline(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return true
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

/**
 * Live, process-safe connectivity status. `INTERNET` alone only means that a network claims it can
 * reach the internet; `VALIDATED` excludes captive portals and broken Wi-Fi, which are both
 * effectively offline to a streaming app.
 */
fun observeOnline(context: Context): Flow<Boolean> = callbackFlow {
    val manager = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    if (manager == null) {
        trySend(true)
        close()
        return@callbackFlow
    }

    trySend(isOnline(context))
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            trySend(
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            )
        }

        override fun onLost(network: Network) {
            trySend(false)
        }
    }
    manager.registerDefaultNetworkCallback(callback)
    awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
}.distinctUntilChanged()

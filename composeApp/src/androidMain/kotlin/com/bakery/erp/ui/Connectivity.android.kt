package com.bakery.erp.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private fun NetworkCapabilities?.hasInternet(): Boolean =
    this != null &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

@Composable
actual fun rememberIsOnline(): State<Boolean> {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    val online = remember(manager) {
        mutableStateOf(manager.getNetworkCapabilities(manager.activeNetwork).hasInternet())
    }
    DisposableEffect(manager) {
        // La red por defecto: al cambiar de wifi a datos llega onCapabilitiesChanged de la
        // nueva, y onLost solo cuando ya no queda ninguna.
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                online.value = caps.hasInternet()
            }
            override fun onLost(network: Network) {
                online.value = false
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }
    return online
}

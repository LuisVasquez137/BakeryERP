package com.bakery.erp.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val LOCATION_TIMEOUT_MILLIS = 15_000L

@Composable
actual fun rememberLocationRequester(): LocationRequester {
    val context = LocalContext.current
    var permissionContinuation by remember { mutableStateOf<CancellableContinuation<Boolean>?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionContinuation?.let { cont -> if (cont.isActive) cont.resume(granted) }
        permissionContinuation = null
    }
    return remember(context) {
        AndroidLocationRequester(context) { cont ->
            permissionContinuation = cont
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}

private class AndroidLocationRequester(
    private val context: Context,
    private val launchPermissionRequest: (CancellableContinuation<Boolean>) -> Unit
) : LocationRequester {

    override suspend fun request(): LocationResult {
        val granted = hasPermission() || askPermission()
        if (!granted) return LocationResult.Denied
        val location = fetchLocation() ?: return LocationResult.Unavailable
        return LocationResult.Granted(location.latitude, location.longitude)
    }

    private fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private suspend fun askPermission(): Boolean =
        suspendCancellableCoroutine { cont -> launchPermissionRequest(cont) }

    @SuppressLint("MissingPermission") // se llama solo después de confirmar el permiso arriba
    private suspend fun fetchLocation(): Location? = withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { cont ->
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val provider = manager?.let(::pickProvider)
            if (manager == null || provider == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val cancellationSignal = CancellationSignal()
                    cont.invokeOnCancellation { cancellationSignal.cancel() }
                    manager.getCurrentLocation(
                        provider,
                        cancellationSignal,
                        ContextCompat.getMainExecutor(context)
                    ) { location -> if (cont.isActive) cont.resume(location) }
                } else {
                    @Suppress("DEPRECATION")
                    manager.requestSingleUpdate(
                        provider,
                        object : LocationListener {
                            override fun onLocationChanged(location: Location) {
                                if (cont.isActive) cont.resume(location)
                            }
                            @Deprecated("Deprecated in Java")
                            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                            override fun onProviderEnabled(provider: String) {}
                            override fun onProviderDisabled(provider: String) {
                                if (cont.isActive) cont.resume(null)
                            }
                        },
                        Looper.getMainLooper()
                    )
                }
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }
    }

    private fun pickProvider(manager: LocationManager): String? = when {
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }
}

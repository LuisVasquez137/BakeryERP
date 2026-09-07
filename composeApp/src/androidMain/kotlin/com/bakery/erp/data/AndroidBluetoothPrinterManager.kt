package com.bakery.erp.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.bakery.erp.domain.BluetoothPermissionDeniedException
import com.bakery.erp.domain.BluetoothPrinterManager
import com.bakery.erp.domain.PairedBluetoothDevice
import com.bakery.erp.domain.SavedPrinterConfig

private const val PREFS_NAME = "bakery_erp_printer"
private const val KEY_DEVICE_NAME = "bt_device_name"
private const val KEY_DEVICE_ADDRESS = "bt_device_address"
private const val KEY_PAPER_WIDTH = "bt_paper_width_mm"

class AndroidBluetoothPrinterManager(
    private val appContext: Context
) : BluetoothPrinterManager {

    override val isPlatformSupported: Boolean = true

    private val prefs by lazy { appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    private fun adapter(): BluetoothAdapter? {
        val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    private fun hasConnectPermission(): Boolean {
        // BLUETOOTH_CONNECT solo aplica en Android 12+. En anteriores el permiso BLUETOOTH
        // (declarado en manifest) basta.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    override suspend fun listPairedDevices(): Result<List<PairedBluetoothDevice>> {
        if (!hasConnectPermission()) {
            return Result.failure(BluetoothPermissionDeniedException("Falta permiso de Bluetooth"))
        }
        val adapter = adapter()
            ?: return Result.failure(IllegalStateException("Este dispositivo no tiene Bluetooth"))
        if (!adapter.isEnabled) {
            return Result.failure(IllegalStateException("Activa el Bluetooth en la tablet"))
        }
        return runCatching {
            adapter.bondedDevices
                .orEmpty()
                .map { PairedBluetoothDevice(name = it.name ?: "(sin nombre)", address = it.address) }
                .sortedBy { it.name.lowercase() }
        }
    }

    override fun getSavedConfig(): SavedPrinterConfig? {
        val name = prefs.getString(KEY_DEVICE_NAME, null) ?: return null
        val address = prefs.getString(KEY_DEVICE_ADDRESS, null) ?: return null
        val width = prefs.getInt(KEY_PAPER_WIDTH, 58)
        return SavedPrinterConfig(deviceName = name, deviceAddress = address, paperWidthMm = width)
    }

    override fun saveConfig(config: SavedPrinterConfig) {
        prefs.edit()
            .putString(KEY_DEVICE_NAME, config.deviceName)
            .putString(KEY_DEVICE_ADDRESS, config.deviceAddress)
            .putInt(KEY_PAPER_WIDTH, config.paperWidthMm)
            .apply()
    }

    override fun clearConfig() {
        prefs.edit()
            .remove(KEY_DEVICE_NAME)
            .remove(KEY_DEVICE_ADDRESS)
            .remove(KEY_PAPER_WIDTH)
            .apply()
    }
}

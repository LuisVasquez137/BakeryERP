package com.bakery.erp.domain

/** Dispositivo Bluetooth emparejado disponible para imprimir. */
data class PairedBluetoothDevice(
    val name: String,
    val address: String
)

/** Configuración persistida de la impresora Bluetooth seleccionada. */
data class SavedPrinterConfig(
    val deviceName: String,
    val deviceAddress: String,
    /** Ancho del rollo en mm — 58 o 80 son los típicos. */
    val paperWidthMm: Int
)

/** Razones por las que una operación de Bluetooth puede no estar disponible. */
enum class BluetoothUnavailableReason {
    /** La plataforma no soporta Bluetooth (web, iOS por ahora). */
    PLATFORM_NOT_SUPPORTED,
    /** Falta el permiso BLUETOOTH_CONNECT (Android 12+). */
    PERMISSION_DENIED,
    /** Bluetooth está apagado en el dispositivo. */
    BLUETOOTH_OFF,
    /** No hay dispositivos emparejados. */
    NO_PAIRED_DEVICES
}

/** Excepción lanzada cuando falta el permiso runtime de Bluetooth (Android 12+). */
class BluetoothPermissionDeniedException(message: String = "Permiso de Bluetooth denegado") : RuntimeException(message)

/**
 * Gestiona configuración + listado de impresoras Bluetooth térmicas.
 * Impresión real se hace en [ReceiptPrinter] usando esta config.
 *
 * - En Android usa [android.bluetooth.BluetoothAdapter] + SharedPreferences.
 * - En web/iOS retorna no-op (no soporta Bluetooth nativo).
 */
interface BluetoothPrinterManager {
    /** ¿Esta plataforma puede potencialmente imprimir por Bluetooth? */
    val isPlatformSupported: Boolean

    /** Lista dispositivos emparejados. Falla con razón clara si no se puede. */
    suspend fun listPairedDevices(): Result<List<PairedBluetoothDevice>>

    /** Retorna la config guardada o null si nunca se ha configurado. */
    fun getSavedConfig(): SavedPrinterConfig?

    /** Persiste la config para usarse en futuras impresiones. */
    fun saveConfig(config: SavedPrinterConfig)

    /** Borra la config (útil para "Cambiar impresora"). */
    fun clearConfig()
}

class NoOpBluetoothPrinterManager : BluetoothPrinterManager {
    override val isPlatformSupported: Boolean = false
    override suspend fun listPairedDevices(): Result<List<PairedBluetoothDevice>> =
        Result.failure(UnsupportedOperationException("Bluetooth no soportado en esta plataforma"))
    override fun getSavedConfig(): SavedPrinterConfig? = null
    override fun saveConfig(config: SavedPrinterConfig) {}
    override fun clearConfig() {}
}

package com.bakery.erp.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.bakery.erp.domain.BluetoothPrinterManager
import com.bakery.erp.domain.DocumentPrinter
import com.bakery.erp.domain.PrintMediaSize
import com.bakery.erp.domain.PrintableDocument
import com.bakery.erp.util.chunkAtLineBreaks
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// UUID estándar SPP — usado por casi todas las térmicas Bluetooth.
private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

/**
 * Tamaño de cada envío y pausa entre envíos.
 *
 * Una térmica de rollo tiene un búfer de 1–4 KB y **no negocia control de flujo** por
 * SPP: lo que llega con el búfer lleno lo descarta sin avisar, y el `write()` del lado
 * de Android igual reporta éxito porque los bytes sí salieron del teléfono. Con un
 * ticket de venta nunca se notó —cabe entero—, pero una orden de producción de varias
 * masas pasa los 4 KB y sale cortada a la mitad.
 *
 * La pausa es lo que hace el trabajo: le da a la impresora tiempo de imprimir lo que
 * tiene y liberar búfer. 256 B cada 40 ms son ~6 KB/s, muy por encima de lo que el
 * mecanismo térmico alcanza a sacar en papel, así que el cuello de botella sigue siendo
 * la impresora y no la espera.
 */
private const val CHUNK_BYTES = 256
private const val CHUNK_DELAY_MS = 40L

/**
 * Espera final antes de cerrar el socket. `close()` retorna de inmediato y descarta la
 * cola de transmisión pendiente, así que sin esto se pierde el último tramo — incluido
 * el corte de papel.
 */
private const val DRAIN_DELAY_MS = 1500L


/**
 * Transporte de impresión en Android, común a tickets y órdenes de producción:
 *
 *  1. Si hay impresora térmica configurada y el documento trae ESC/POS para su ancho
 *     de rollo → abre socket RFCOMM y lo envía.
 *  2. Si eso falla (apagada, fuera de alcance) o no hay térmica → cae al Print
 *     Framework nativo, que deja al usuario elegir PDF, WiFi u otro PrintService.
 *
 * El permiso runtime de Bluetooth NO se pide acá: la pantalla ya lo resolvió al
 * configurar la impresora (`BluetoothPrinterPickerDialog`). Si el usuario lo revocó
 * después, el socket falla y el respaldo del sistema atiende la impresión igual.
 */
class AndroidDocumentPrinter(
    private val appContext: Context,
    private val btManager: BluetoothPrinterManager
) : DocumentPrinter {

    override val isAvailable: Boolean = true

    // El Print Framework lee el WebView de forma asíncrona después de que print()
    // retorna; si se recolecta antes, el trabajo sale en blanco.
    private var pendingWebView: WebView? = null

    override suspend fun print(document: PrintableDocument): Result<Unit> {
        val config = btManager.getSavedConfig()
        val escPos = config?.let { document.escPosByWidthMm[it.paperWidthMm] }
        if (config != null && escPos != null) {
            val bt = tryBluetoothPrint(escPos, config.deviceAddress)
            if (bt.isSuccess) return bt
            // Bluetooth falló — cae al diálogo del sistema como respaldo.
        }
        return systemPrintFallback(document)
    }

    @SuppressLint("MissingPermission")
    private suspend fun tryBluetoothPrint(
        bytes: ByteArray,
        deviceAddress: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                ?: throw IllegalStateException("Bluetooth no disponible")
            val adapter = manager.adapter
                ?: throw IllegalStateException("Adaptador Bluetooth no disponible")
            if (!adapter.isEnabled) throw IllegalStateException("Bluetooth apagado")

            // Cancelar descubrimiento si está activo — acelera la conexión.
            runCatching { adapter.cancelDiscovery() }

            val device = adapter.getRemoteDevice(deviceAddress)
            var socket: BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                val stream = socket.outputStream
                // A ritmo, no de un tirón: ver CHUNK_BYTES.
                chunkAtLineBreaks(bytes, CHUNK_BYTES).forEach { chunk ->
                    stream.write(chunk)
                    stream.flush()
                    delay(CHUNK_DELAY_MS)
                }
                // El stream NO se cierra acá: cerrarlo (o cerrar el socket) devuelve
                // enseguida y descarta lo que siga en la cola de transmisión. Hay que
                // darle a la impresora tiempo de vaciar su búfer antes de colgar.
                delay(DRAIN_DELAY_MS)
            } finally {
                runCatching { socket?.close() }
            }
        }
    }

    private suspend fun systemPrintFallback(document: PrintableDocument): Result<Unit> {
        val activity = CurrentActivityHolder.activity
            ?: return Result.failure(IllegalStateException(
                "No se pudo imprimir por Bluetooth y no hay una pantalla activa para mostrar " +
                    "el diálogo del sistema."
            ))
        val deferred = CompletableDeferred<Result<Unit>>()
        val jobName = document.jobName.ifEmpty { "Documento" }
        val mediaSize = when (document.mediaSize) {
            PrintMediaSize.RECEIPT -> PrintAttributes.MediaSize.ISO_A6
            PrintMediaSize.PAGE -> PrintAttributes.MediaSize.ISO_A4
        }

        Handler(Looper.getMainLooper()).post {
            try {
                val webView = WebView(activity)
                pendingWebView = webView
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        try {
                            val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                                ?: throw IllegalStateException("PrintManager no disponible")
                            val adapter = view.createPrintDocumentAdapter(jobName)
                            val attributes = PrintAttributes.Builder()
                                .setMediaSize(mediaSize)
                                .setResolution(PrintAttributes.Resolution("default", "default", 203, 203))
                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                .build()
                            printManager.print(jobName, adapter, attributes)
                            deferred.complete(Result.success(Unit))
                        } catch (t: Throwable) {
                            deferred.complete(Result.failure(t))
                        } finally {
                            Handler(Looper.getMainLooper()).postDelayed({ pendingWebView = null }, 5_000)
                        }
                    }
                }
                webView.loadDataWithBaseURL(null, document.html, "text/html", "UTF-8", null)
            } catch (t: Throwable) {
                pendingWebView = null
                deferred.complete(Result.failure(t))
            }
        }
        return deferred.await()
    }
}

package com.bakery.erp.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Estado de conexión de TODA la app, para que un ViewModel sepa si hay internet sin pasar
 * por la UI, y la franja de arriba sepa cuántas ventas faltan por subir.
 *
 * - [isOnline] lo alimenta `ui/ConnectivityBanner.kt` (que está en la raíz de la app y se
 *   compone siempre) con lo que detecta cada plataforma.
 * - [pendingSales] lo lleva `FirebaseSaleRepository`: ventas guardadas en el equipo que el
 *   servidor todavía no confirmó. Vive en memoria; si cierran la app sin internet, las ventas
 *   siguen en la cola de Firestore y suben solas, solo que el contador arranca en cero.
 * - [failedSales] son las que el servidor RECHAZÓ al subir (la venta se deshace en el
 *   equipo). Es el caso que no puede pasar en silencio: el dinero ya está en la gaveta.
 */
object ConnectionStatus {
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _pendingSales = MutableStateFlow(0)
    val pendingSales: StateFlow<Int> = _pendingSales.asStateFlow()

    private val _failedSales = MutableStateFlow<List<FailedSaleUpload>>(emptyList())
    val failedSales: StateFlow<List<FailedSaleUpload>> = _failedSales.asStateFlow()

    /**
     * Si la plataforma guarda en disco la cola de escrituras de Firestore. Android sí (por
     * defecto); web no todavía, y ahí una venta sin internet se perdería al recargar.
     */
    var offlineSalesSupported: Boolean = true

    fun setOnline(online: Boolean) { _isOnline.value = online }

    fun salePending() = _pendingSales.update { it + 1 }
    fun saleSettled() = _pendingSales.update { (it - 1).coerceAtLeast(0) }

    fun saleFailed(failure: FailedSaleUpload) = _failedSales.update { it + failure }
    fun dismissFailedSale(saleId: String) = _failedSales.update { list -> list.filterNot { it.saleId == saleId } }
}

/** Una venta que el equipo cobró pero el servidor no aceptó. Se anota a mano. */
data class FailedSaleUpload(
    val saleId: String,
    val total: Double,
    val createdAt: Long,
    val summary: String,
    val message: String
)

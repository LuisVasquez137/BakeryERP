package com.bakery.erp.model

data class DispatchNote(
    val id: String = "",
    val lotId: String = "",
    val sourceOrderId: String = "",
    val originBranchId: String = "",
    val originBranchName: String = "",
    /** BRANCH | CUSTOMER — tipo de destino del despacho. */
    val destinationType: String = DispatchDestinationType.BRANCH,
    val destinationBranchId: String = "",
    val destinationBranchName: String = "",
    /** Si [destinationType] = CUSTOMER, ID del [ExternalCustomer] receptor. */
    val destinationCustomerId: String = "",
    val destinationCustomerName: String = "",
    val productionDate: Long = 0L,
    val shiftId: String = "",
    val shiftName: String = "",
    val status: String = DispatchStatus.DRAFT,
    val deliveryRunId: String = "",
    // Trazabilidad
    val packedByUserId: String = "",
    val packedByUserName: String = "",
    val packedAt: Long? = null,
    val dispatchedAt: Long? = null,
    /** Acuse de llegada del repartidor: la encargada verifica que llegaron los tipos de pan
     *  pero el conteo final lo hace después. Sello distinto a `receivedAt` (conteo cerrado). */
    val arrivedAt: Long? = null,
    val arrivedByUserId: String = "",
    val arrivedByUserName: String = "",
    val receivedByUserId: String = "",
    val receivedByUserName: String = "",
    val receivedAt: Long? = null,
    // Valoración
    val totalValueWholesale: Double = 0.0,
    val priceListId: String = "",
    val priceListName: String = "",
    val hasPriceMissing: Boolean = false,
    val hasDiscrepancy: Boolean = false,
    val notes: String? = null,
    /**
     * Estado que tenía la nota justo antes de cancelarse. Es lo que la devuelve a su
     * lugar al restaurarla (`READY` vuelve a "Listas", `DRAFT` vuelve a la matriz de
     * empaque) en vez de adivinar un estado de arranque. Vacío = nunca se canceló.
     */
    val statusBeforeCancel: String = "",
    val cancelledAt: Long? = null,
    val cancelledByUserId: String = "",
    val cancelledByUserName: String = "",
    val cancelReason: String? = null,
    /**
     * El pan de esta nota entró al inventario de terminado de la planta al cancelarla.
     *
     * Se guarda como hecho y no se re-deduce del estado: al restaurar hay que devolver
     * exactamente lo que se ingresó, y una nota cancelada antes de esta función no
     * movió nada — sin la bandera, restaurarla descontaría de la planta un pan que
     * nunca se le sumó.
     */
    val plantAbsorbedAtCancel: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lineCount: Int = 0,
    /**
     * Panes de la nota y panes ya marcados como empacados en la matriz. Caché del
     * encabezado para que el tablero de Reparto dibuje la barra de empaque en vivo
     * sin leer las líneas de cada nota; la recalcula `refreshPackedUnits` desde las
     * líneas cada vez que se marca o corrige una, así un descuadre se corrige solo.
     * En 0 en notas anteriores: el tablero cae a contar productos marcados.
     */
    val plannedUnits: Double = 0.0,
    val packedUnits: Double = 0.0,
    /**
     * Canastas que salieron para esta sucursal (al cerrar el empaque) y las que llegaron
     * (al firmar la llegada). Cada canasta va a una sola sucursal aunque lleve varios
     * panes, así que contarlas separa "se perdió una canasta en el camino" de "faltó pan
     * dentro de la canasta" — que con solo el conteo de panes no se distinguían. 0 / null
     * en notas anteriores. Ver `basketCheck`.
     */
    val basketsSent: Int = 0,
    val basketsReceived: Int? = null,
    val lines: List<DispatchNoteLine> = emptyList()
)

object DispatchStatus {
    const val DRAFT                      = "DRAFT"
    const val READY                      = "READY"
    const val IN_TRANSIT                 = "IN_TRANSIT"
    /** Repartidor llegó, encargada reportó la llegada (verificó tipos de pan, no cantidades).
     *  El conteo final queda pendiente — la encargada lo cierra después con calma. */
    const val ARRIVED                    = "ARRIVED"
    const val DELIVERED                  = "DELIVERED"
    const val DELIVERED_WITH_DISCREPANCY = "DELIVERED_WITH_DISCREPANCY"
    const val CANCELLED                  = "CANCELLED"

    fun label(status: String) = when (status) {
        DRAFT                      -> "Por empacar"
        READY                      -> "Lista para envío"
        IN_TRANSIT                 -> "En tránsito"
        ARRIVED                    -> "Llegó · falta conteo"
        DELIVERED                  -> "Recibida"
        DELIVERED_WITH_DISCREPANCY -> "Recibida con discrepancia"
        CANCELLED                  -> "Cancelada"
        else                       -> "Desconocido"
    }

    /** Estados que cierran el ciclo del despacho (no se pueden revertir). */
    fun isTerminal(status: String) = status in listOf(DELIVERED, DELIVERED_WITH_DISCREPANCY, CANCELLED)

    /** Estados en los que el repartidor ya cumplió su parte (puede irse). */
    fun isDriverDone(status: String) = status in listOf(ARRIVED, DELIVERED, DELIVERED_WITH_DISCREPANCY, CANCELLED)
}

object DispatchDestinationType {
    const val BRANCH = "BRANCH"
    const val CUSTOMER = "CUSTOMER"
}

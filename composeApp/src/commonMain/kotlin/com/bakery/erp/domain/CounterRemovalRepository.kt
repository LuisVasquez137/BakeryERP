package com.bakery.erp.domain

import com.bakery.erp.model.CounterRemoval
import com.bakery.erp.model.Employee

/**
 * Salidas del mostrador sin venta (merma, accidente, regalo). Ver `model/CounterRemoval.kt`.
 *
 * Un solo camino para los tres efectos: encabezado → inventario de tienda → cargo a la
 * colaboradora (accidente). Encabezado primero: lo que quede a medias se ve con su referencia.
 */
interface CounterRemovalRepository {

    /** Id para un registro nuevo: la foto se sube con él antes de escribir el encabezado. */
    fun newId(companyId: String): String

    /**
     * Escribe la salida con [CounterRemoval.id] ya asignado, descuenta el inventario de tienda
     * y, si es accidente, le carga [CounterRemoval.chargeAmount] a [responsible]. Devuelve lo
     * guardado.
     */
    suspend fun register(companyId: String, removal: CounterRemoval, responsible: Employee?): CounterRemoval

    suspend fun getForSession(companyId: String, sessionId: String): List<CounterRemoval>

    /** De la sucursal ([branchId] vacío = todas) en el rango, la más reciente primero. */
    suspend fun getInRange(companyId: String, branchId: String, fromMillis: Long, untilMillis: Long): List<CounterRemoval>

    /**
     * Perdona un accidente: marca el encabezado y escribe el abono en la cuenta (nunca borra el
     * cargo). Vuelve a leer y valida con `accidentForgiveBlocker`; lanza con el mensaje si no.
     */
    suspend fun forgive(
        companyId: String,
        removalId: String,
        byUserId: String,
        byName: String,
        byEmployeeId: String,
        reason: String
    ): CounterRemoval
}

/** Foto del producto dañado. */
interface CounterRemovalPhotoUploader {
    suspend fun upload(bytes: ByteArray, companyId: String, removalId: String): String
}

/** Sin storage (iOS por ahora): no hay dónde guardar la foto, y la merma la exige. */
class NoOpCounterRemovalPhotoUploader : CounterRemovalPhotoUploader {
    override suspend fun upload(bytes: ByteArray, companyId: String, removalId: String): String = ""
}

package com.bakery.erp.domain

import com.bakery.erp.model.FelBranchAssignment
import com.bakery.erp.model.FelCertifyResult
import com.bakery.erp.model.FelInvoiceRequest
import com.bakery.erp.model.FelCredentialsInput
import com.bakery.erp.model.FelProfile

/**
 * Facturación electrónica: la configuración del dueño (NITs emisores y qué sucursal factura
 * con cuál) y las llamadas del cobro. Toda operación con Digifact pasa por Cloud Functions:
 * las credenciales nunca llegan a la app.
 */
interface FelRepository {
    suspend fun getProfiles(companyId: String): List<FelProfile>

    /**
     * Crea o actualiza un perfil por la Cloud Function `felGuardarPerfil`, que es la única que
     * puede escribir las credenciales (fuera del árbol de la empresa, que cualquier empleado
     * lee). Si vienen [credentials], la función las prueba contra Digifact **antes** de
     * guardarlas y lanza con el motivo si no entran. Devuelve el id del perfil.
     */
    suspend fun saveProfile(
        companyId: String,
        profile: FelProfile,
        credentials: FelCredentialsInput?
    ): String

    /**
     * Deja a [assignments] facturando con [profileId] y quita el NIT a [releasedBranchIds]
     * (las que estaban con este perfil y se desmarcaron). Escritura parcial sobre cada
     * sucursal: un `set()` desde acá le borraría el resto de la ficha.
     */
    suspend fun assignBranches(
        companyId: String,
        profileId: String,
        assignments: List<FelBranchAssignment>,
        releasedBranchIds: List<String>
    )

    /**
     * Certifica las ventas de un cobro en una sola factura (Cloud Function
     * `felCertificarVenta`). Idempotente del lado del servidor: repetir con las mismas ventas
     * devuelve el mismo documento en vez de emitir otro. No lanza por fallas de Digifact —
     * vuelven como estado PENDIENTE/RECHAZADO con el motivo—; sí lanza si la llamada no llegó.
     */
    suspend fun certifySales(
        companyId: String,
        saleIds: List<String>,
        request: FelInvoiceRequest,
        /**
         * Volver a facturar ventas cuya factura se anuló o fue rechazada (el cliente dio mal su
         * NIT). El servidor sube la versión: id de documento y referencia nuevos.
         */
        reissue: Boolean = false
    ): FelCertifyResult

    /**
     * Anula la factura ante la SAT. La venta sigue igual: no devuelve dinero ni inventario.
     * Lanza con el motivo de Digifact si la rechaza (p. ej. fuera del plazo de anulación).
     */
    suspend fun voidInvoice(companyId: String, dteId: String, reason: String)

    /** Nombre registrado en la SAT para ese NIT, o null si no existe. */
    suspend fun lookupNit(companyId: String, branchId: String, nit: String): String?
}

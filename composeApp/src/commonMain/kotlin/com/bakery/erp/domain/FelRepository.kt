package com.bakery.erp.domain

import com.bakery.erp.model.FelBranchAssignment
import com.bakery.erp.model.FelCredentialsInput
import com.bakery.erp.model.FelProfile

/**
 * Configuración de la facturación electrónica: NITs emisores y qué sucursal factura con cuál.
 * Certificar y anular viven en la Cloud Function; esto es solo lo que el dueño configura.
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
}

package com.bakery.erp.domain

import com.bakery.erp.model.PlantIssue

/**
 * Vales de salida de material de planta. Ver [PlantIssue] para el porqué del módulo.
 *
 * Este repositorio **solo guarda el documento**: el descuento de stock, el Kardex y
 * el consumo FEFO de lotes los hace `IngredientRepository.updateBranchStock`, que ya
 * es el único cuello de botella por el que pasan los cuatro caminos que bajan
 * inventario. Duplicar esa mecánica acá sería el quinto camino al que se le olvida
 * algo. La orquestación vive en `PlantIssueViewModel`.
 */
interface PlantIssueRepository {

    /** Devuelve el vale ya con su id asignado. */
    suspend fun createIssue(companyId: String, issue: PlantIssue): PlantIssue

    /**
     * Vales de las sucursales dadas dentro del rango.
     *
     * El filtro por fecha va **en memoria**, no en el servidor: mismo motivo que
     * `getPurchases`. `issuedAt` puede venir como `Timestamp` en documentos escritos
     * por un script y como número en los de la app, y Firestore ordena por tipo antes
     * que por valor — un `where issuedAt >= x` numérico dejaría fuera en silencio
     * justo los más antiguos.
     */
    suspend fun getIssuesInRange(
        companyId: String,
        branchIds: List<String>,
        fromMillis: Long,
        untilMillis: Long
    ): List<PlantIssue>

    /**
     * Últimos vales de una sucursal, más reciente primero. Alimenta el aviso
     * "Último saco: hace 2 días" del diálogo — la guarda contra anotar dos veces
     * el mismo saco.
     */
    suspend fun getRecentIssues(companyId: String, branchId: String, limit: Int = 60): List<PlantIssue>

    /** Vales atados a un lote de producción, para mostrarlos en su detalle. */
    suspend fun getIssuesForLot(companyId: String, lotId: String): List<PlantIssue>

    /**
     * Borra un vale. **No revierte inventario**: quien lo llama tiene que devolver el
     * stock y dejar su propio movimiento de Kardex, igual que hace el borrado de una
     * compra. Nada desaparece del Kardex — el reverso se escribe, no se borra.
     */
    suspend fun deleteIssue(companyId: String, issueId: String)
}

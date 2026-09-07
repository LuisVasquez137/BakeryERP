package com.bakery.erp.model

data class ProductionShift(
    val id: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val name: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val active: Boolean = true,
    /**
     * Productos que este turno NUNCA produce (ej. de noche los panaderos también
     * empacan el despacho, así que hacen menos variedad). El reparto automático
     * entre turnos manda el 100% de estos productos al otro turno — nunca se
     * dividen. Vacío = sin restricción, el turno puede hornear cualquier producto.
     */
    val excludedProductIds: List<String> = emptyList(),
    /**
     * En qué viaje del camión sale el pan de este turno: `1` = primer viaje (el que
     * sale temprano), `2` = segundo. `0` = sin configurar.
     *
     * No se deriva de [startTime] a propósito: el turno de noche empieza a las 22:00
     * y su pan sale a las 5 de la mañana — ordenar por hora de inicio pondría el
     * turno de día primero, justo al revés de como sale el camión.
     *
     * Es el orden en que se listan los turnos de una planta (antes venían en el
     * orden arbitrario de Firestore, así que cuál turno era la columna izquierda
     * del reparto podía cambiar entre cargas) y el que le da sentido a pedir algo
     * "para el primer viaje".
     */
    val dispatchOrder: Int = 0,
    val createdByUserId: String = "",
    val createdByUserName: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

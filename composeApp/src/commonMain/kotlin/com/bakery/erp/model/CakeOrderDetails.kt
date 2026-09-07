package com.bakery.erp.model

/**
 * Datos específicos de un pedido de pastel. Se serializa plano dentro del doc
 * de [CustomerOrder] (no es subcolección).
 *
 * Mapea 1-a-1 los campos del formato físico "Encargos Pasteles" de BakeOS.
 */
data class CakeOrderDetails(
    /** FONDANT | CREAM | OTHER. */
    val cakeType: String = CakeType.FONDANT,
    /** COLD | AMBIENT — refrigerado o temperatura ambiente. */
    val temperature: String = CakeTemperature.AMBIENT,
    /** Número total de porciones que debe rendir el pastel. */
    val totalPortions: Int = 0,
    /** Sabor del biscocho (vainilla, chocolate, etc.). */
    val biscocho: String = "",
    /** Tipo de relleno. */
    val filling: String = "",
    /** Otras especificaciones libres (decoración, mensaje, color, etc.). */
    val extras: String = "",
    /** URL de la firma del cliente (foto desde el form, opcional). */
    val clientSignatureUrl: String = "",
)

object CakeType {
    const val FONDANT = "FONDANT"
    const val CREAM = "CREAM"
    const val OTHER = "OTHER"

    fun label(type: String) = when (type) {
        FONDANT -> "Pastel fondant"
        CREAM -> "Pastel crema"
        OTHER -> "Otro"
        else -> "Desconocido"
    }
}

object CakeTemperature {
    const val COLD = "COLD"
    const val AMBIENT = "AMBIENT"

    fun label(value: String) = when (value) {
        COLD -> "Frío"
        AMBIENT -> "Ambiente"
        else -> "Desconocido"
    }
}

package com.bakery.erp.model

data class Supplier(
    val id: String = "",
    val company: String = "",         // Nombre de la empresa
    val salesperson: String = "",     // Nombre del vendedor/contacto
    val phone: String = "",           // Número de teléfono
    val address: String? = null,      // Dirección (opcional)
    val notes: String? = null,
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

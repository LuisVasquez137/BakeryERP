package com.bakery.erp.model

data class Organization(
    val id: String,
    val name: String,
    val ownerEmail: String,
    val subscriptionPlan: String,
    val createdAt: Long,
    val inviteCode: String,
    /**
     * `true` si la panadería acepta entregar pedidos a domicilio a sus revendedores.
     * Controla las opciones que ve el revendedor al registrarse: si es `false`, solo
     * puede elegir sucursal de retiro; si es `true`, puede elegir entre retiro o
     * domicilio (y se le pide ubicación una sola vez).
     */
    val acceptsResellerDelivery: Boolean = false
)

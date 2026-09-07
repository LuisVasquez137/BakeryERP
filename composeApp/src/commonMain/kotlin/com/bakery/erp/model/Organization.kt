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
    val acceptsResellerDelivery: Boolean = false,
    /**
     * Qué flujos de compra usa esta panadería. No toda panadería revende, y casi ninguna
     * pequeña lleva inventario de cloro: sin estas banderas, la pantalla más sensible a la
     * velocidad de captura se llena de secciones vacías.
     *
     * Todas arrancan en `false` para que ninguna empresa ya operando vea nada nuevo hasta
     * que lo pida. La materia prima no lleva bandera — es el flujo base, siempre está.
     */
    val usesResalePurchases: Boolean = false,
    val usesSupplyPurchases: Boolean = false,
    val usesServicePurchases: Boolean = false
)

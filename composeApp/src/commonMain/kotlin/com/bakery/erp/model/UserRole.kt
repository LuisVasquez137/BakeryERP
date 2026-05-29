package com.bakery.erp.model

enum class UserRole {
    OWNER,
    ADMIN,
    MANAGER,
    EMPLOYEE,
    /** Tienda revendedora con login propio. Sólo ve sus pedidos y precios mayoristas. */
    RESELLER
}

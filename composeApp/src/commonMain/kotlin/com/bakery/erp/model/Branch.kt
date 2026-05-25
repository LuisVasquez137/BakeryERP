package com.bakery.erp.model

data class Branch(
    val id: String = "",
    val orgId: String = "",
    val name: String = "",
    val location: String = "",
    val phone: String = "",
    val managerName: String = "",
    val notes: String? = null,
    val capabilities: List<BranchCapability> = emptyList(),
    val assignedProductIds: List<String> = emptyList(),
    /** Lista de precios usada cuando esta sucursal recibe un despacho. Vacío = usar
     *  la lista default WHOLESALE del sistema. Usualmente solo se setea en sucursales
     *  con [BranchCapability.CAN_SELL] que reciben producto de plantas productoras. */
    val defaultPriceListId: String = "",
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

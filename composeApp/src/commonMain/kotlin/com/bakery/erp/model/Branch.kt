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
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

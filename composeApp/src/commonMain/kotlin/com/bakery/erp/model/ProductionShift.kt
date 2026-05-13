package com.bakery.erp.model

data class ProductionShift(
    val id: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val name: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val active: Boolean = true,
    val createdByUserId: String = "",
    val createdByUserName: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

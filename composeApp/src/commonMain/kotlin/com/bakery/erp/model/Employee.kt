package com.bakery.erp.model

data class Employee(
    val id: String = "",
    val name: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val position: String = "",
    val phone: String = "",
    val phoneAux: String = "",
    val email: String = "",
    val address: String = "",
    val hireDate: Long = 0L,
    val notes: String = "",
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

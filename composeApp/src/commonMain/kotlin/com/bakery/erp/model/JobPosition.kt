package com.bakery.erp.model

data class JobPosition(
    val id: String = "",
    val title: String = "",
    val department: String = "",
    val responsibilities: List<String> = emptyList(),
    val requirements: List<String> = emptyList(),
    val minSalary: Double = 0.0,
    val maxSalary: Double = 0.0,
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

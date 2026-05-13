package com.bakery.erp.model

data class Organization(
    val id: String,
    val name: String,
    val ownerEmail: String,
    val subscriptionPlan: String,
    val createdAt: Long,
    val inviteCode: String
)

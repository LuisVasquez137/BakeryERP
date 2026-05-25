package com.bakery.erp.domain

interface GoogleAuthHandler {
    val isAvailable: Boolean get() = true
    suspend fun getIdToken(): String?
}

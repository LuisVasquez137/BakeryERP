package com.bakery.erp.domain

interface GoogleAuthHandler {
    suspend fun getIdToken(): String?
}

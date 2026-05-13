package com.bakery.erp.auth

import com.bakery.erp.domain.GoogleAuthHandler

// Stub para iOS. Google Sign-In en iOS requiere el SDK nativo de Google — fuera de alcance.
class GoogleAuthHandlerStub : GoogleAuthHandler {
    override suspend fun getIdToken(): String? = null
}

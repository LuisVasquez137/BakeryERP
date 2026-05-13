package com.bakery.erp.auth

import com.bakery.erp.domain.GoogleAuthHandler

// Stub para JS/Web. Google Sign-In web usa Firebase Auth popup/redirect — fuera de alcance.
class GoogleAuthHandlerStub : GoogleAuthHandler {
    override suspend fun getIdToken(): String? = null
}
